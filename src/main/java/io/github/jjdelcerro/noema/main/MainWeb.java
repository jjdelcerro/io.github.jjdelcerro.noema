package io.github.jjdelcerro.noema.main;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentConsole;
import io.github.jjdelcerro.noema.lib.AgentLocator;
import io.github.jjdelcerro.noema.lib.AgentManager;
import io.github.jjdelcerro.noema.lib.AgentPaths;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import io.github.jjdelcerro.noema.ui.AgentUILocator;
import io.github.jjdelcerro.noema.ui.AgentUIManager;
import io.github.jjdelcerro.noema.ui.AgentUISettings;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Punto de entrada para ejecución exclusivamente como Servidor Web (Headless /
 * Daemon).
 * <p>
 * No inicializa entornos gráficos (Swing, Lanterna ni JLine). Opera sobre el
 * espacio de trabajo actual (o el especificado con -w / --workspace) y expone
 * la interfaz de usuario web y la API REST/SSE a través de Javalin.
 */
public class MainWeb {

  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

  public static void main(String[] args) {
    Path workspace = resolveWorkspace(args);

    // 1. Registrar el gestor de UI Headless / Consola de Servidor
    AgentUILocator.registerAgentUIManager(new ServerAgentUIManager());

    // 2. Instanciar AgentManager y cargar la configuración del workspace
    AgentManager manager = AgentLocator.getAgentManager();
    AgentPaths paths = manager.createAgentPaths(workspace);
    AgentSettings settings = manager.createSettings(paths);

    settings.setupSettings();
    settings.load();

    // 3. Validación estricta (Fail-Fast)
    if (!BootUtils.areSettingsValid(settings)) {
      System.err.println();
      System.err.println("================================================================================");
      System.err.println(" [ERROR] Configuración incompleta en el espacio de trabajo:");
      System.err.println("         " + paths.getWorkspaceFolder());
      System.err.println();
      System.err.println(" Noema requiere credenciales y modelos configurados para los servicios");
      System.err.println(" de Razonamiento y Memoria antes de iniciar en modo servidor web.");
      System.err.println();
      System.err.println(" Para configurar este espacio de trabajo:");
      System.err.println("   1. Ejecuta Noema con interfaz gráfica:  java -jar ... --gui");
      System.err.println("   2. O ejecuta la interfaz de terminal:   java -jar ... --tui");
      System.err.println("   3. O edita el fichero: .noema-agent/var/config/settings.json");
      System.err.println("================================================================================");
      System.err.println();
      System.exit(1);
    }

    // 4. Registrar workspace en los ajustes globales
    settings.setLastWorkspacePath(paths.getWorkspaceFolder().toString());

    // 5. Inicializar y arrancar el motor del agente y el servidor web (Javalin)
    Agent agent = null;
    try {
      agent = BootUtils.init(settings);
      agent.start();

      int port = settings.getPropertyAsInt("server/port", 8080);
      String address = settings.getPropertyAsString("server/address", "127.0.0.1");
      
      int h2Port = settings.getPropertyAsInt("debug/h2_webport", 8082);
      String h2Address = settings.getPropertyAsString("debug/h2_webaddress", "127.0.0.1");

      NoemaWebServer.startServer(agent, address, port);

      // 6. Banner informativo de arranque
      System.out.println();
      System.out.println("================================================================================");
      System.out.printf("  %s v%s - Modo Servidor Web Activo%n", manager.getName(), manager.getVersion());
      System.out.println("================================================================================");
      System.out.println("  Espacio de trabajo : " + paths.getWorkspaceFolder());
      System.out.printf("  Interfaz Web       : http://%s:%d%n", address, port);
      System.out.printf("  Consola H2         : http://%s:%d%n", h2Address, h2Port);
      System.out.println("--------------------------------------------------------------------------------");
      System.out.println("  Presione Ctrl+C para detener el servidor.");
      System.out.println("================================================================================");
      System.out.println();

      final Agent runningAgent = agent;
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        System.out.println("\n[" + logTime() + "] Deteniendo servidor Noema...");
        try {
          runningAgent.stop();
        } catch (Exception ignored) {
        }
      }));

      // 7. Bloquear el hilo principal manteniendo el proceso activo
      // Esperar indefinidamente hasta señal de interrupción
      Thread.currentThread().join();

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      System.err.println("[" + logTime() + "] [ERROR FATAL] " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    } finally {
      if (agent != null) {
        agent.stop();
      }
    }
  }

  private static Path resolveWorkspace(String[] args) {
    for (int i = 0; i < args.length; i++) {
      if (("-w".equalsIgnoreCase(args[i]) || "--workspace".equalsIgnoreCase(args[i])) && i + 1 < args.length) {
        return Path.of(args[i + 1]).toAbsolutePath().normalize();
      }
    }
    return Path.of(".").toAbsolutePath().normalize();
  }

  private static String logTime() {
    return LocalDateTime.now().format(TIME_FMT);
  }

  // =========================================================================
  // IMPLEMENTACIÓN DE CONSOLA Y GESTOR HEADLESS
  // =========================================================================
  private static class ServerAgentUIManager implements AgentUIManager {

    private final AgentConsole console = new ServerAgentConsole();

    @Override
    public AgentConsole createConsole() {
      return this.console;
    }

    @Override
    public AgentUISettings createSettings(Agent agent) {
      return null; // No aplicable en modo servidor headless
    }

    @Override
    public AgentUISettings createSettings(AgentSettings settings, AgentConsole console) {
      return null;
    }
  }

  private static class ServerAgentConsole implements AgentConsole {

    @Override
    public boolean confirm(String message) {
      // En modo servidor desatendido se auto-aprueba y se registra en log
      System.out.println("[" + logTime() + "] [CONFIRM:AUTO_APPROVED] " + message.replace("\n", " "));
      return true;
    }

    @Override
    public void printSystemLog(String message) {
      System.out.println("[" + logTime() + "] [LOG] " + message);
    }

    @Override
    public void printSystemLog(String message, Format format) {
      printSystemLog(message);
    }

    @Override
    public void printSystemError(String message) {
      System.err.println("[" + logTime() + "] [ERR] " + message);
    }

    @Override
    public void printUserMessage(String message) {
      System.out.println("[" + logTime() + "] [USR] " + message);
    }

    @Override
    public void printModelResponse(String message) {
      System.out.println("[" + logTime() + "] [MODEL] " + message);
    }

    @Override
    public void printModelReasoning(String message) {
      System.out.println("[" + logTime() + "] [THINKING] " + message);
    }
  }
}
