package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.devel;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.ToolSpecificationBuilder;
import io.github.jjdelcerro.noema.lib.memory.projected.ProjectedMemory;
import io.github.jjdelcerro.noema.lib.memory.projected.operations.PinnedTurnsOperation;
import io.github.jjdelcerro.noema.lib.services.reasoning.ReasoningService;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.jjdelcerro.noema.lib.AgentAccessControl.AccessMode.PATH_ACCESS_READ;

/**
 * Herramienta para cargar el mapa de un proyecto (project-map.md). Emite una
 * cabecera con metadatos y el contenido integro sin paginacion. Fija el turno
 * en la memoria proyectada (pinned turn) para que sea inmune a la poda y
 * sobreviva a las consolidaciones.
 */
public class LoadProjectMapTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "load_project_map";
  private static final String POM_FILE = "pom.xml";
  private static final String PROJECT_MAP_FILE = "project-map.md";

  public LoadProjectMapTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("""
Carga el contenido integro del documento 'project-map.md' de un proyecto Java/Maven y 
fija su arquitectura en tu memoria de trabajo de forma permanente.
Exige que en el directorio indicado existan tanto 'pom.xml' como 'project-map.md'.

Solo puedes tener un mapa de proyecto cargado. Si vuelves a cargarlo de nuevo o
cargas el de otro proyecto, se descargara al anterior para cargar el nuevo.
                         
Usa esta herramienta cuando tengas que empezar a realizar tareas de desarrollo de 
software sobre un proyecto para asimilar sus reglas, patrones y estilo sin 
requerir la lectura repetida de archivos.                         

**FORMATO DE SALIDA:**
El resultado consta de dos secciones separadas estrictamente por el delimitador `---`:
1. Cabecera (arriba de `---`):
   STATUS: OK
   PATHNAME: [ruta absoluta y normalizada al archivo project-map.md cargado]
2. Contenido (debajo de `---`):
   El texto integro y completo del mapa de arquitectura sin paginar.

**CICLO DE VIDA:**
Las directivas del mapa permaneceran fijadas en tu contexto proyectado hasta 
que finalices la tarea sobre el proyecto y decidas liberarlo invocando
'unload_project_map'.
""")
            .addStringParameter("path", false, "Ruta al directorio raiz del proyecto (relativa o absoluta).");
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_READ;
  }

  @Override
  public int getType() {
    return AgentTool.TYPE_OPERATIONAL;
  }

  @Override
  public boolean shouldPin() {
    return true;
  }

  @Override
  public String getPinnedNotificationMessage(ToolExecutionRequest request, ToolExecutionResultMessage result) {
    String pathName = "desconocido";
    if (result != null && result.text() != null) {
      for (String line : result.text().split("\n")) {
        if (line.startsWith("PATHNAME:")) {
          pathName = line.substring("PATHNAME:".length()).trim();
          break;
        }
      }
    }
    return String.format(
            "[MAPA DE PROYECTO ACTIVO: %s]\n"
            + "Las reglas, patrones y estilo de este proyecto permanecen fijados en tu contexto. "
            + "Si concluyes el trabajo o cambias de proyecto, invoca 'unload_project_map' para liberarlo.",
            pathName
    );
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      Args args = gson.fromJson(jsonArguments, Args.class);
      if (args == null || StringUtils.isBlank(args.path)) {
        return formatError("El parametro 'path' es obligatorio.");
      }

      // 1. Validar resolucion y politicas de sandbox
      Path resolvedDir = resolvePathOrNull(args.path.trim(), PATH_ACCESS_READ);
      if (resolvedDir == null) {
        return formatError("Acceso denegado o ruta fuera del sandbox: " + args.path);
      }

      if (!Files.exists(resolvedDir) || !Files.isDirectory(resolvedDir)) {
        return formatError("La ruta especificada no existe o no es un directorio: " + args.path);
      }

      // 2. Comprobar presencia de pom.xml
      Path pomFile = resolvedDir.resolve(POM_FILE);
      if (!Files.exists(pomFile) || !Files.isRegularFile(pomFile)) {
        return formatError("No se encontro el archivo obligatorio '" + POM_FILE + "' en: " + args.path);
      }

      // 3. Comprobar presencia y legibilidad de project-map.md
      Path mapFile = resolvedDir.resolve(PROJECT_MAP_FILE);
      if (!Files.exists(mapFile) || !Files.isRegularFile(mapFile)) {
        return formatError("No se encontro el archivo obligatorio '" + PROJECT_MAP_FILE + "' en: " + args.path);
      }

      if (!Files.isReadable(mapFile)) {
        return formatError("El archivo '" + PROJECT_MAP_FILE + "' no tiene permisos de lectura: " + mapFile);
      }

      // 4. Desanclar cualquier mapa cargado previamente (solo uno activo a la vez)
      unpinPreviousProjectMaps();

      // 5. Lectura integra y ensamblado de cabecera
      Path canonicalMapPath = mapFile.toAbsolutePath().normalize();
      String content = Files.readString(canonicalMapPath, StandardCharsets.UTF_8);

      LOGGER.info("Cargando y fijando mapa de proyecto desde: {}", canonicalMapPath);

      StringBuilder sb = new StringBuilder();
      sb.append("STATUS: OK\n");
      sb.append("PATHNAME: ").append(canonicalMapPath.toString().replace('\\', '/')).append("\n");
      sb.append("---\n");
      sb.append(content);

      return sb.toString();

    } catch (IOException e) {
      LOGGER.warn("Error de E/S leyendo project-map.md: {}", e.getMessage(), e);
      return formatError("Error de lectura en archivo: " + e.getMessage());
    } catch (Exception e) {
      LOGGER.error("Error inesperado en {}: {}", TOOL_NAME, e.getMessage(), e);
      return formatError("Fallo inesperado: " + e.getMessage());
    }
  }

  private String formatError(String message) {
    return "STATUS: ERROR\nERROR: " + message + "\n---\n";
  }

  /**
   * Elimina cualquier project-map fijado con anterioridad en este subcanal para
   * asegurar que solo exista una instancia activa en la memoria proyectada.
   */
  private void unpinPreviousProjectMaps() {
    ReasoningService reasoning = (ReasoningService) agent.getService(ReasoningService.NAME);
    if (reasoning == null) {
      return;
    }

    ProjectedMemory projectedMemory = reasoning.getProjectedMemory(agent.getCurrentSubchannel());
    if (projectedMemory == null) {
      return;
    }

    PinnedTurnsOperation op = (PinnedTurnsOperation) projectedMemory.getOperation(PinnedTurnsOperation.OPERATION_NAME);
    if (op != null) {
      op.removePinnedTurn(state -> {
        ToolExecutionResultMessage res = state.getResultMessage();
        return res != null && TOOL_NAME.equals(res.toolName());
      });
    }
  }

  private static class Args {

    String path;
  }
}
