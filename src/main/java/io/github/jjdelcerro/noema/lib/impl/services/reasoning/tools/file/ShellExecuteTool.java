package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file;

import io.github.jjdelcerro.noema.lib.impl.AbstractPaginatedAgentTool;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
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
public class ShellExecuteTool extends AbstractPaginatedAgentTool {

  public static final String TOOL_NAME = "shell_execute";
  private static final int MAX_SAVED_OUTPUTS = 20;
  private static final long WAIT_STEP_SECONDS = 30;

  private static Boolean firejailAvailable = null;

  private final Map<String, Path> outputRegistry;

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
    loadOutputsInformation();
  }

  private void loadOutputsInformation() {
    if (!Files.exists(agent.getPaths().getTempFolder())) {
      return;
    }

    try {
      List<Path> files = Files.list(agent.getPaths().getTempFolder())
              .filter(p -> p.toString().endsWith(".out"))
              .sorted(Comparator.comparingLong(p -> p.toFile().lastModified()))
              .toList();

      for (Path file : files) {
        String id = file.getFileName().toString().replace(".out", "");
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
            .description("Ejecuta comandos de sistema en una shell de Bash.\n" +
                    "**RESTRICCIONES CRÍTICAS:**\n" +
                    "  1. **No Interactividad:** La shell NO es interactiva. Evita estrictamente comandos que requieran entrada del usuario, contraseñas o confirmaciones (ej: 'sudo', 'apt' sin '-y').\n" +
                    "  2. **Supervisión Humana:** Para tareas largas, el usuario será consultado periódicamente y puede abortar el proceso.\n" +
                    "  3. **Flags no-interactivos:** Incluye flags de no-interactividad si el comando los soporta (ej: '-y', '--batch').\n" +
                    "\n" +
                    "**SEGURIDAD:** Si Firejail está disponible, los comandos se ejecutan en un sandbox con directorio home aislado y acceso restringido al proyecto.\n" +
                    "\n" +
                    getPaginationSystemInstruction())
            .addParameter("command", JsonSchemaProperty.STRING, 
                    JsonSchemaProperty.description("Comando completo a ejecutar. Incluye flags de no-interactividad si aplica (ej: '-y', '--batch')."))
            .build();
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_EXECUTION;
  }

  @Override
  public boolean isAvailableByDefault() {
    return this.isSecureShellExecutionAvailable();
  }

  @Override
  public String execute(String jsonArguments) {
    String executionId = "out_" + UUID.randomUUID().toString().substring(0, 8);
    Path outputFile = agent.getPaths().getTempFolder().resolve(executionId + ".out");

    try {
      Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
      String command = args.get("command");

      if (command == null || command.trim().isEmpty()) {
        return formatErrorResponse("El parámetro 'command' es obligatorio.");
      }

      ProcessBuilder pb = new ProcessBuilder("bash", "-c", this.getSecuredCommand(command));
      pb.directory(agent.getPaths().getWorkspaceFolder().toFile());
      pb.redirectErrorStream(true);

      Files.createDirectories(agent.getPaths().getTempFolder());
      Process process = pb.start();
      process.getOutputStream().close();

      Semaphore readPermission = new Semaphore(1);
      AtomicBoolean processFinished = new AtomicBoolean(false);

      @SuppressWarnings("SleepWhileInLoop")
      Thread readerThread = Thread.ofPlatform().start(() -> {
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

      boolean keepGoing = true;
      int totalSeconds = 0;

      while (process.isAlive() && keepGoing) {
        boolean finishedInStep = process.waitFor(WAIT_STEP_SECONDS, TimeUnit.SECONDS);
        if (!finishedInStep) {
          totalSeconds += WAIT_STEP_SECONDS;
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

      outputRegistry.put(executionId, outputFile.toAbsolutePath());

      int exitCode = process.isAlive() ? -1 : process.exitValue();

      String resourceId = getIdFromPath(outputFile);
      if (resourceId == null) {
        return formatErrorResponse("Error generando resource_id para la salida del comando.");
      }

      if (exitCode != 0) {
        LOGGER.warn("Comando falló con exit code: " + exitCode + ", command: " + command);
      }

      return servePaginatedResource(resourceId);

    } catch (Exception e) {
      LOGGER.warn("Error ejecutando comando, args=" + jsonArguments, e);
      return formatErrorResponse("Fallo en ejecución: " + e.getMessage());
    }
  }

  private String getSecuredCommand(String command) {
    if (!this.isSecureShellExecutionAvailable()) {
      return command;
    }

    Path agentFolder = agent.getPaths().getSandboxHomeFolder().toAbsolutePath().normalize();
    Path dataFolder = agent.getPaths().getDataFolder().toAbsolutePath().normalize();

    Path projectRoot = agentFolder.getParent();
    Path homeFolder = agentFolder.resolve("home");

    try {
      Files.createDirectories(homeFolder);
    } catch (IOException e) {
      LOGGER.warn("No se pudo crear/verificar la carpeta home del agente: " + homeFolder, e);
    }

    String escapedCommand = command.replace("\"", "\\\\\\\"");

    return String.format(
            "firejail --quiet --private=\"%s\" --whitelist=\"%s\" --blacklist=\"%s\" -- bash -c \"cd \\\"%s\\\"; %s\"",
            homeFolder.toString(),
            projectRoot.toString(),
            dataFolder.toString(),
            projectRoot.toString(),
            escapedCommand
    );
  }

  public synchronized boolean isSecureShellExecutionAvailable() {
    if (firejailAvailable != null) {
      return firejailAvailable;
    }

    try {
      Process process = new ProcessBuilder("which", "firejail").start();
      boolean exited = process.waitFor(2, TimeUnit.SECONDS);
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
