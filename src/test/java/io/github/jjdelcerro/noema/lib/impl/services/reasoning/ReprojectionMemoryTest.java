package io.github.jjdelcerro.noema.lib.impl.services.reasoning;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentAccessControl;
import io.github.jjdelcerro.noema.lib.AgentActions;
import io.github.jjdelcerro.noema.lib.AgentPaths;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.FakeConsole;
import io.github.jjdelcerro.noema.lib.impl.AgentAccessControlImpl;
import io.github.jjdelcerro.noema.lib.impl.AgentActionsImpl;
import io.github.jjdelcerro.noema.lib.impl.AgentImpl;
import io.github.jjdelcerro.noema.lib.impl.AgentPathsImpl;
import io.github.jjdelcerro.noema.lib.impl.persistence.FakeSession;
import io.github.jjdelcerro.noema.lib.impl.persistence.FakeSourceOfTruth;
import io.github.jjdelcerro.noema.lib.impl.services.memory.tools.AnnotateObservationTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file.FileReadTool;
import io.github.jjdelcerro.noema.lib.impl.settings.AgentSettingsImpl;
import io.github.jjdelcerro.noema.lib.persistence.SourceOfTruth;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class ReprojectionMemoryTest extends AbstractScriptedTest {

  @TempDir
  Path workspaceDir;

  private Agent agent;
  private ScriptedChatModel scriptedModel;
  private FakeSession fakeSession;
  private ReasoningServiceImpl reasoningService;

  @BeforeEach
  public void setUp() {
    AgentPaths paths = new AgentPathsImpl(workspaceDir);
    paths.setupHierarchy();

    AgentSettings settings = new AgentSettingsImpl(paths);
    AgentActions actions = new AgentActionsImpl();
    AgentAccessControl accessControl = new AgentAccessControlImpl(settings, actions, workspaceDir);
    SourceOfTruth sot = new FakeSourceOfTruth();

    agent = new AgentImpl(null, null, settings, new FakeConsole(), sot, accessControl);
    scriptedModel = new ScriptedChatModel();

    fakeSession = new FakeSession(Agent.DEFAULT_SUBCHANNEL);
    fakeSession.setNeedCompaction(false);

    ReasoningServiceFactory factory = new ReasoningServiceFactory();
    reasoningService = new ReasoningServiceImpl(factory, agent) {
      @Override
      public Session createSession(String subchannel) {
        return fakeSession;
      }

      @Override
      public Agent.ChatModel getModel() {
        return scriptedModel;
      }

      @Override
      public boolean isRunning() {
        return true;
      }

      @Override
      public String getBaseSystemPrompt() {
        String prompt = "Eres Noema, un asistente de prueba.";
        this.lastestSystemPrompt = prompt;
        return prompt;
      }
    };
    for (AgentTool tool : reasoningService.getAvailableTools()) {
        reasoningService.setToolActive(tool.getName(), false);
    }
    reasoningService.addTool(new FileReadTool(agent));
    reasoningService.setToolActive("file_read", true);

    reasoningService.addTool(new AnnotateObservationTool(agent));
    reasoningService.setToolActive("annotate_observation", true);
  }

  // =========================================================================
  // IMPLEMENTACION DE GETTERS ABSTRACTOS
  // =========================================================================
  @Override
  protected Agent getAgent() {
    return this.agent;
  }

  @Override
  protected ScriptedChatModel getScriptedModel() {
    return this.scriptedModel;
  }

  @Override
  protected Session getSession() {
    return this.fakeSession;
  }

  @Override
  protected ReasoningServiceImpl getReasoningService() {
    return this.reasoningService;
  }

  // =========================================================================
  // METODOS DE COMPROBACION (PREDICADOS)
  // =========================================================================
  private boolean verifyNoEphemeralNotification(SimTurn turn) {
    return !hasEphemeralNotification();
  }

  private boolean verifyEphemeralNotificationTriggered(SimTurn turn) {
    return hasEphemeralNotification("servidor_antiguo.log");
  }

  private boolean verifySelectiveAmnesia(SimTurn turn) {
    boolean oldTrimmedInProjected = hasTrimmedResource("servidor_antiguo.log");
    boolean oldBodyMissingInProjected = !projectedContextContainsText("ERROR_ANTIGUO");

    boolean recentFullInProjected = hasFullResource("servidor_reciente.log");
    boolean recentBodyPresentInProjected = projectedContextContainsText("ERROR_RECIENTE");

    boolean oldBodyPreservedInSession = sessionContainsText("ERROR_ANTIGUO");
    boolean noNotificationActive = !hasEphemeralNotification();

    return oldTrimmedInProjected
            && oldBodyMissingInProjected
            && recentFullInProjected
            && recentBodyPresentInProjected
            && oldBodyPreservedInSession
            && noNotificationActive;
  }

  private boolean verifyAnnotationImmunity(SimTurn turn) {
    boolean oldLogTrimmed = hasTrimmedResource("servidor_antiguo.log");
    boolean oldLogRawGone = !projectedContextContainsText("Detalle log antiguo");
    boolean notePreservedInProjected = projectedContextContainsText("NOTA_CRITICA_TIMEOUT_0312");
    boolean notePreservedInSession = sessionContainsText("NOTA_CRITICA_TIMEOUT_0312");
    boolean noNotificationActive = !hasEphemeralNotification();

    return oldLogTrimmed
            && oldLogRawGone
            && notePreservedInProjected
            && notePreservedInSession
            && noNotificationActive;
  }

  // =========================================================================
  // CASOS DE PRUEBA
  // =========================================================================
  @Test
  @DisplayName("Debe gestionar el ciclo completo de poda, notificaciones efimeras y preservacion")
  public void testSelectiveAmnesia() throws Throwable {
    Path oldLogFile = workspaceDir.resolve("servidor_antiguo.log");
    Path recentLogFile = workspaceDir.resolve("servidor_reciente.log");

    String heavyContent1 = "ERROR_ANTIGUO: Fallo de conexion.\n" + StringUtils.repeat("Detalle log antiguo...\n", 150);
    String heavyContent2 = "ERROR_RECIENTE: Timeout en base de datos.\n" + StringUtils.repeat("Detalle log reciente...\n", 150);

    Files.writeString(oldLogFile, heavyContent1, StandardCharsets.UTF_8);
    Files.writeString(recentLogFile, heavyContent2, StandardCharsets.UTF_8);

    String oldFileArgs = "{\"path\": \"" + oldLogFile.getFileName().toString() + "\"}";
    String recentFileArgs = "{\"path\": \"" + recentLogFile.getFileName().toString() + "\"}";

    List<SimTurn> script = new ArrayList<>();

    // Turno 0: Lectura pesada antigua (Fase 1: dato fresco)
    script.add(turn(
            user("Por favor lee el log antiguo"),
            this::verifyNoEphemeralNotification,
            aiTool("file_read", oldFileArgs),
            ai("He analizado el log antiguo.")
    ));

    // Turnos 1 al 7: Conversacion intermedia (Fase 1: contexto < 20 mensajes)
    for (int i = 1; i <= 7; i++) {
      final int step = i;
      script.add(turn(
              user("Pregunta de conversacion " + step),
              this::verifyNoEphemeralNotification,
              ai("Respuesta intermedia " + step)
      ));
    }

    // Turno 8: Fase 2 (Zona de riesgo): contexto alcanza 20 mensajes (SystemMessage + 19)
    script.add(turn(
            user("Pregunta de conversacion 8"),
            this::verifyEphemeralNotificationTriggered,
            ai("Respuesta intermedia 8")
    ));

    // Turnos 9 al 19: Conversacion intermedia empujando el turno 0 hacia la poda definitiva
    for (int i = 9; i <= 19; i++) {
      final int step = i;
      script.add(turn(
              user("Pregunta de conversacion " + step),
              ai("Respuesta intermedia " + step)
      ));
    }

    // Turno 20: Lectura pesada reciente
    script.add(turn(
            user("Ahora lee el log reciente"),
            aiTool("file_read", recentFileArgs),
            ai("He analizado el log reciente.")
    ));

    // Turno 21: Fase 3 (Poda del viejo, frescura del nuevo, ningun recordatorio activo)
    script.add(turn(
            user("Cual es la conclusion final?"),
            this::verifySelectiveAmnesia,
            ai("La conclusion es que hubo fallos en ambos logs.")
    ));

    execute(script);
  }

  @Test
  @DisplayName("Las anotaciones (annotate_observation) previenen las advertencias y sobreviven a la poda")
  public void testAnnotationImmunity() throws Throwable {
    Path oldLogFile = workspaceDir.resolve("servidor_antiguo.log");
    String heavyContent = "LOG_BRUTO: " + StringUtils.repeat("Detalle log antiguo...\n", 150);
    Files.writeString(oldLogFile, heavyContent, StandardCharsets.UTF_8);

    String resourceId = "user://" + oldLogFile.toAbsolutePath().normalize().toString().replace("\\", "/");
    String fileReadArgs = "{\"path\": \"" + oldLogFile.getFileName().toString() + "\"}";
    String annotateArgs = "{\"source\": \"servidor_antiguo.log\", \"note\": \"NOTA_CRITICA_TIMEOUT_0312: Fallo critico en autenticacion\", \"resource_id\": \"" + resourceId + "\"}";

    List<SimTurn> script = new ArrayList<>();

    // Turno 0: Lectura pesada + Anotacion preventiva inmediata
    script.add(turn(
            user("Lee el archivo y anota lo relevante"),
            this::verifyNoEphemeralNotification,
            aiTool("file_read", fileReadArgs),
            aiTool("annotate_observation", annotateArgs),
            ai("He leido el archivo y registrado la nota en memoria.")
    ));

    // Turnos 1 al 7: Conversacion intermedia
    for (int i = 1; i <= 7; i++) {
      final int step = i;
      script.add(turn(
              user("Pregunta de conversacion " + step),
              this::verifyNoEphemeralNotification,
              ai("Respuesta intermedia " + step)
      ));
    }

    // Turno 8: Al llegar a 20 mensajes, como YA esta anotado, NO debe saltar aviso
    script.add(turn(
            user("Pregunta de conversacion 8"),
            this::verifyNoEphemeralNotification,
            ai("Respuesta intermedia 8")
    ));

    // Turnos 9 al 20: Relleno conversacional
    for (int i = 9; i <= 20; i++) {
      final int step = i;
      script.add(turn(
              user("Pregunta de conversacion " + step),
              this::verifyNoEphemeralNotification,
              ai("Respuesta intermedia " + step)
      ));
    }

    // Turno 21: Verificacion de que la nota sobrevivio intacta, el log se podo y no hay avisos
    script.add(turn(
            user("Que notas tecnicas tenemos guardadas del pasado?"),
            this::verifyAnnotationImmunity,
            ai("Tenemos registrada la nota NOTA_CRITICA_TIMEOUT_0312.")
    ));

    execute(script);
  }

  @Test
  @DisplayName("Debe advertir de recursos sin anotar y silenciar el aviso en cuanto el modelo anota")
  public void testDelayedAnnotationLifecycle() throws Throwable {
    Path oldLogFile = workspaceDir.resolve("servidor_antiguo.log");
    String heavyContent = "LOG_BRUTO: " + StringUtils.repeat("Detalle log antiguo...\n", 150);
    Files.writeString(oldLogFile, heavyContent, StandardCharsets.UTF_8);

    String resourceId = "user://" + oldLogFile.toAbsolutePath().normalize().toString().replace("\\", "/");
    String fileReadArgs = "{\"path\": \"" + oldLogFile.getFileName().toString() + "\"}";
    String annotateArgs = "{\"source\": \"servidor_antiguo.log\", \"note\": \"NOTA_CRITICA_TIMEOUT_0312: Fallo critico en autenticacion\", \"resource_id\": \"" + resourceId + "\"}";

    List<SimTurn> script = new ArrayList<>();

    // Turno 0: Lectura desatendida (sin anotar)
    script.add(turn(
            user("Por favor lee el log"),
            this::verifyNoEphemeralNotification,
            aiTool("file_read", fileReadArgs),
            ai("He leido el log.")
    ));

    // Turnos 1 al 7: Periodo de gracia (sin aviso)
    for (int i = 1; i <= 7; i++) {
      final int step = i;
      script.add(turn(
              user("Pregunta de conversacion " + step),
              this::verifyNoEphemeralNotification,
              ai("Respuesta intermedia " + step)
      ));
    }

    // Turno 8: El arnes inyecta el aviso en la zona de riesgo
    script.add(turn(
            user("Pregunta de conversacion 8"),
            this::verifyEphemeralNotificationTriggered,
            ai("Respuesta intermedia 8")
    ));

    // Turno 9: El modelo reacciona al aviso y ejecuta annotate_observation
    script.add(turn(
            user("Recuerdas consolidar la informacion pendiente?"),
            aiTool("annotate_observation", annotateArgs),
            ai("He anotado los datos clave del log.")
    ));

    // Turno 10 al 20: El aviso DESAPARECE de inmediato en los siguientes turnos
    for (int i = 10; i <= 20; i++) {
      final int step = i;
      script.add(turn(
              user("Pregunta de conversacion " + step),
              this::verifyNoEphemeralNotification,
              ai("Respuesta intermedia " + step)
      ));
    }

    // Turno 21: Verificacion final: el log se podo, la nota tardia permanece y no hay avisos activos
    script.add(turn(
            user("Cual es el estado final de la memoria?"),
            this::verifyAnnotationImmunity,
            ai("Todo el conocimiento esta correctamente preservado.")
    ));

    execute(script);
  }

// =========================================================================
  // PREDICADO PARA RECURSOS PEQUEÑOS
  // =========================================================================
  private boolean verifySmallResourceRetention(SimTurn turn) {
    // 1. El recurso pequeno no debe tener marca de recorte
    boolean notTrimmed = !hasTrimmedResource("config_pequeno.json");

    // 2. Debe aparecer como recurso completo en la proyeccion
    boolean isFull = hasFullResource("config_pequeno.json");

    // 3. El texto literal debe seguir presente en la memoria proyectada del turno 30
    boolean bodyInProjected = projectedContextContainsText("TEXTO_CORTO_CONFIG");

    // 4. La sesion en RAM conserva el texto original
    boolean bodyInSession = sessionContainsText("TEXTO_CORTO_CONFIG");

    // 5. No debe haber ningun aviso efimero activo
    boolean noNotification = !hasEphemeralNotification();

    return notTrimmed
            && isFull
            && bodyInProjected
            && bodyInSession
            && noNotification;
  }

  // =========================================================================
  // NUEVO CASO DE PRUEBA
  // =========================================================================
  @Test
  @DisplayName("Los recursos menores a 1024 caracteres no deben podarse ni generar avisos de anotacion")
  public void testSmallResourceNotTrimmed() throws Throwable {
    Path smallFile = workspaceDir.resolve("config_pequeno.json");
    String smallContent = "{\"clave\": \"TEXTO_CORTO_CONFIG\", \"puerto\": 8080, \"debug\": true}";
    Files.writeString(smallFile, smallContent, StandardCharsets.UTF_8);

    String smallFileArgs = "{\"path\": \"" + smallFile.getFileName().toString() + "\"}";

    List<SimTurn> script = new ArrayList<>();

    // Turno 0: Lectura de archivo pequeno (< 1024 caracteres)
    script.add(turn(
            user("Por favor lee la configuracion"),
            this::verifyNoEphemeralNotification,
            aiTool("file_read", smallFileArgs),
            ai("He leido la configuracion.")
    ));

    // Turnos 1 al 7: Conversacion intermedia (contexto < 20 mensajes)
    for (int i = 1; i <= 7; i++) {
      final int step = i;
      script.add(turn(
              user("Pregunta de conversacion " + step),
              this::verifyNoEphemeralNotification,
              ai("Respuesta intermedia " + step)
      ));
    }

    // Turno 8: Al alcanzar 20 mensajes, como es pequeno, NO debe saltar aviso
    script.add(turn(
            user("Pregunta de conversacion 8"),
            this::verifyNoEphemeralNotification,
            ai("Respuesta intermedia 8")
    ));

    // Turnos 9 al 29: Relleno conversacional para superar holgadamente la ventana de 20
    for (int i = 9; i <= 29; i++) {
      final int step = i;
      script.add(turn(
              user("Pregunta de conversacion " + step),
              this::verifyNoEphemeralNotification,
              ai("Respuesta intermedia " + step)
      ));
    }

    // Turno 30: Verificacion de que en el turno 30 el recurso sigue integro y sin podar
    script.add(turn(
            user("Cual es la configuracion que leiste al principio?"),
            this::verifySmallResourceRetention,
            ai("La configuracion tiene la clave TEXTO_CORTO_CONFIG y puerto 8080.")
    ));

    execute(script);
  }

}
