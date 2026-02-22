package io.github.jjdelcerro.chatagent.lib.impl.services.conversation.tools.file;

import io.github.jjdelcerro.chatagent.lib.impl.AbstractAgentTool;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.AgentTool;
import io.github.jjdelcerro.chatagent.lib.impl.services.conversation.ConversationService;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.collections4.map.LRUMap;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Herramienta de ejecución de comandos Shell (Bash). Gobierna el ciclo de vida
 * de los procesos, la captura en disco y la higiene de temporales.
 */
@SuppressWarnings("UseSpecificCatch")
public class ShellExecuteTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "shell_execute";
  private static final int MAX_SAVED_OUTPUTS = 20; // Cuota de archivos en disco
  private static final long WAIT_STEP_SECONDS = 30;

  private static Boolean firejailAvailable = null;

  // Mapa de salidas: ID (UUID) -> Path al fichero .out
  private final Map<String, Path> outputRegistry;
  private final File tmpFolder;

  /**
   * Extensión de LRUMap para gestionar el borrado físico de archivos al
   * desalojar entradas.
   */
  private class OutputLRUMap extends LRUMap<String, Path> {

    public OutputLRUMap(int size) {
      super(size);
    }

    @Override
    protected boolean removeLRU(LinkEntry<String, Path> entry) {
      try {
        Files.deleteIfExists(entry.getValue());
        LOGGER.info("Higiene: Eliminado temporal de shell expirado: {}", entry.getValue().getFileName());
      } catch (IOException e) {
        LOGGER.warn("No se pudo eliminar el temporal: " + entry.getValue(), e);
      }
      return true;
    }
  }

  public ShellExecuteTool(Agent agent) {
    super(agent);
    this.outputRegistry = Collections.synchronizedMap(new OutputLRUMap(MAX_SAVED_OUTPUTS));
    this.tmpFolder = new File(agent.getDataFolder(), "tmp");
    loadOutputsInformation();
  }

  /**
   * Rescata archivos de sesiones anteriores y activa la autolimpieza si se
   * supera la cuota.
   */
  private void loadOutputsInformation() {
    if (!tmpFolder.exists()) {
      return;
    }

    try {
      // Listar archivos .out ordenados por fecha de modificación (viejos primero)
      List<Path> files = Files.list(tmpFolder.toPath())
              .filter(p -> p.toString().endsWith(".out"))
              .sorted(Comparator.comparingLong(p -> p.toFile().lastModified()))
              .toList();

      for (Path file : files) {
        String id = file.getFileName().toString().replace(".out", "");
        // Al insertar, el LRUMap ejecutará removeLRU si excedemos MAX_SAVED_OUTPUTS
        outputRegistry.put(id, file.toAbsolutePath());
      }
      if (!files.isEmpty()) {
        LOGGER.info("Rehidratación: Cargadas {} salidas de comandos anteriores.", outputRegistry.size());
      }
    } catch (IOException e) {
      LOGGER.warn("Error rescatando temporales de shell", e);
    }
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name(TOOL_NAME)
            .description(
                    """
Ejecuta comandos de sistema en una shell de Bash. 

**RESTRICCIONES CRÍTICAS:** 
1. **No Interactividad:** La shell NO es interactiva. Evita estrictamente comandos que requieran entrada del usuario, contraseñas o confirmaciones (ej: 'sudo', 'apt' sin '-y', o scripts con prompts [y/n]). 
2. **Supervisión Humana:** Para tareas largas, el usuario humano será consultado cada cierto tiempo y tiene la autoridad de abortar el proceso. Prepárate para manejar estados 'aborted'. 
3. **Gestión de Salida:** Si el comando genera una salida extensa, esta se guardará en un recurso temporal y se te entregará un bloque inicial de líneas. 

**PROTOCOLO DE LECTURA:** Si la salida está truncada, verás un bloque [SYSTEM] con un HINT. Debes usar la herramienta 'shell_read_output' con el ID del recurso y el offset indicado para continuar la lectura si necesitas más información.
"""
            )
            .addParameter("command", JsonSchemaProperty.STRING, JsonSchemaProperty.description("Comando completo a ejecutar. Asegúrate de incluir flags de no-interactividad (ej: '-y' o '--batch') si el comando los soporta."))
            .build();
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_EXECUTION;
  }

  /**
   * Devuelve la ruta de una salida capturada.Usado por ShellReadOutputTool.
   *
   * @param id
   * @return
   */
  public Path getOutputPath(String id) {
    return outputRegistry.get(id);
  }

  @Override
  public boolean isAvailableByDefault() {
    return this.isSecureShellExecutionAvailable();
  }
  
  @Override
  public String execute(String jsonArguments) {
    String executionId = "out_" + UUID.randomUUID().toString().substring(0, 8);
    Path outputFile = tmpFolder.toPath().resolve(executionId + ".out");

    try {
      Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
      String command = args.get("command");

      ProcessBuilder pb = new ProcessBuilder("bash", "-c", this.getSecuredCommand(command));
      pb.directory(agent.getDataFolder().getParentFile());
      pb.redirectErrorStream(true);

      Files.createDirectories(tmpFolder.toPath());
      Process process = pb.start();
      process.getOutputStream().close();

      Semaphore readPermission = new Semaphore(1);
      AtomicBoolean processFinished = new AtomicBoolean(false);

      // HILO LECTOR: Vuelca bytes a disco mientras tenga permiso
      @SuppressWarnings("SleepWhileInLoop")
      Thread readerThread = Thread.ofVirtual().start(() -> {
        try (InputStream is = process.getInputStream(); OutputStream os = Files.newOutputStream(outputFile)) {

          byte[] buffer = new byte[4096];
          while (!processFinished.get()) {
            if (readPermission.tryAcquire(100, TimeUnit.MILLISECONDS)) {
              try {
                if (is.available() > 0) {
                  int read = is.read(buffer);
                  if (read > 0) {
                    os.write(buffer, 0, read);
                  }
                } else if (!process.isAlive()) {
                  break;
                }
              } finally {
                readPermission.release();
              }
              Thread.sleep(10);
            }
          }
          os.flush();
        } catch (Exception e) {
          LOGGER.warn("Error en hilo lector de Shell", e);
        }
      });

      // HILO PRINCIPAL: Vigilante del tiempo e interacción
      boolean keepGoing = true;
      int totalSeconds = 0;

      while (process.isAlive() && keepGoing) {
        boolean finishedInStep = process.waitFor(WAIT_STEP_SECONDS, TimeUnit.SECONDS);
        if (!finishedInStep) {
          totalSeconds += WAIT_STEP_SECONDS;
          // Pausamos al lector adquiriendo el semáforo
          readPermission.acquire();
          try {
            String msg = String.format("El comando '%s' lleva %ds. ¿Continuar?", command, totalSeconds);
            keepGoing = agent.getConsole().confirm(msg);
            if (!keepGoing) {
              process.destroyForcibly();
            }
          } finally {
            readPermission.release();
          }
        }
      }

      processFinished.set(true);
      readerThread.join(1000);

      // Registrar en el mapa para futura lectura/higiene
      outputRegistry.put(executionId, outputFile.toAbsolutePath());

      int exitCode = process.isAlive() ? -1 : process.exitValue();

      // DELEGACIÓN: Usamos FileReadTool para generar la respuesta paginada
      ConversationService conv = (ConversationService) agent.getService(ConversationService.NAME);
      FileReadTool fileRead = (FileReadTool) conv.getAvailableTool(FileReadTool.TOOL_NAME);

      // Inyectamos el código de salida en el prefijo de la respuesta
      String statusPrefix = (exitCode == 0) ? "" : "[SYSTEM: Command failed with exit code " + exitCode + "]\n";

      Map<String, Object> shellReadOutputArgs = new HashMap<>();
      shellReadOutputArgs.put("id", executionId);
      String readerOutput = fileRead.execute(
              outputFile,
              executionId,
              ShellReadOutputTool.TOOL_NAME,
              shellReadOutputArgs,
              0,
              1000
      );

      return statusPrefix + readerOutput;

    } catch (Exception e) {
      return error("Fallo en ejecución: " + e.getMessage());
    }
  }

  /**
   * Construye el comando final envuelto en Firejail siguiendo la arquitectura:
   * - agent/data: Blindada (Blacklist) 
   * - agent/home: Home persistente (Private)
   * - project_root: Visible (Whitelist)
   */
  private String getSecuredCommand(String command) {
    if( !this.isSecureShellExecutionAvailable() ) {
      return command;
    }
    // 1. Resolución de rutas absolutas
    // Asumimos que getAgentFolder() devuelve la carpeta "agent/"
    Path agentFolder = agent.getAgentFolder().toPath().toAbsolutePath().normalize();
    Path dataFolder = agent.getDataFolder().toPath().toAbsolutePath().normalize();
    
    Path projectRoot = agentFolder.getParent();
    Path homeFolder = agentFolder.resolve("home");

    // Aseguramos que la carpeta home existe antes de lanzar firejail
    try {
      Files.createDirectories(homeFolder);
    } catch (IOException e) {
      LOGGER.warn("No se pudo crear/verificar la carpeta home del agente: " + homeFolder, e);
    }

    // 2. Escapado del comando original
    // Al ir dentro de un bash -c "...", debemos escapar las comillas dobles internas
    String escapedCommand = command.replace("\"", "\\\"");

    // 3. Construcción del comando Firejail
    // --quiet: Silencia los mensajes de inicio de Firejail
    // --private: Usa la carpeta especificada como el $HOME del proceso
    // --whitelist: Permite acceso a la raíz del proyecto
    // --blacklist: Prohíbe explícitamente el acceso a la carpeta de datos del orquestador
    return String.format(
            "firejail --quiet --private=\"%s\" --whitelist=\"%s\" --blacklist=\"%s\" -- bash -c \"cd \\\"%s\\\"; %s\"",
            homeFolder.toString(),
            projectRoot.toString(),
            dataFolder.toString(),
            projectRoot.toString(),
            escapedCommand
    );
  }

  /**
   * Verifica si el binario 'firejail' está instalado y disponible en el PATH
   * del sistema.Cachea el resultado para optimizar ejecuciones posteriores.
   * @return 
   */
  public synchronized boolean isSecureShellExecutionAvailable() {
    if (firejailAvailable != null) {
      return firejailAvailable;
    }

    try {
      // Ejecutamos 'which firejail' para ver si el binario existe en el PATH
      Process process = new ProcessBuilder("which", "firejail").start();

      // Esperamos un tiempo ínfimo (max 2s) para la comprobación
      boolean exited = process.waitFor(2, TimeUnit.SECONDS);

      // Si el exitValue es 0, el binario existe y es accesible
      firejailAvailable = exited && process.exitValue() == 0;

      if (firejailAvailable) {
        LOGGER.info("Seguridad de sistema: Firejail detectado y activo.");
      } else {
        LOGGER.warn("Seguridad de sistema: Firejail NO detectado. Los comandos se ejecutarán sin sandbox.");
      }

    } catch (Exception e) {
      LOGGER.warn("Error comprobando disponibilidad de Firejail: " + e.getMessage());
      firejailAvailable = false;
    }

    return firejailAvailable;
  }

}
