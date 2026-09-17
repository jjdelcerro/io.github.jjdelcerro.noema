package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.scripting;

import io.github.jjdelcerro.noema.lib.impl.scripting.ScriptContext;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.transform.TimedInterrupt;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.AbstractPaginatedAgentTool;
import io.github.jjdelcerro.noema.lib.impl.ToolSpecificationBuilder;
import static io.github.jjdelcerro.noema.lib.impl.scripting.ScriptContext.CONTEXT_NAME;
import io.github.jjdelcerro.noema.lib.impl.scripting.ScriptEngine;
import java.io.BufferedWriter;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tool that executes Groovy scripts in an embedded and sandboxed JVM
 * environment. Automatically paginates long outputs and disposes resources
 * deterministically.
 */
public class ScriptExecuteTool extends AbstractPaginatedAgentTool {

  private static final Logger LOGGER = LoggerFactory.getLogger(ScriptExecuteTool.class);
  public static final String TOOL_NAME = "execute_script";

  private static final int MAX_INLINE_OUTPUT_CHARS = 2048;

  private final Gson outputGson;

  public ScriptExecuteTool(Agent agent) {
    super(agent);
    this.outputGson = new GsonBuilder().setPrettyPrinting().create();
  }

@Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description(StringUtils.replace("""
Ejecuta scripts Groovy/Java en la JVM para procesar, filtrar, transformar o agregar datos localmente.
Herramienta OBLIGATORIA para cálculos, medias, sumas, tareas por lotes, agregaciones o análisis masivo sobre colecciones de archivos sin saturar la conversación.

REGLAS DE EJECUCIÓN Y SEGURIDAD:
• Código estrictamente Groovy/Java para la JVM (no Python). Usa 'def', closures { item -> ... } y colecciones estándar.
• Sandbox activo: clases de E/S directa como 'java.io.File', 'System' o 'ProcessBuilder' están bloqueadas. Toda interacción con el entorno debe realizarse exclusivamente a través del objeto '${CONTEXT_NAME}'.

DESCUBRIMIENTO Y AYUDA (Bajo demanda):
• println ${CONTEXT_NAME}.help() : Lista el catálogo de módulos disponibles (fs, llm, web, annotation, state, subagents).
• println ${CONTEXT_NAME}.<modulo>.help() : Muestra las firmas completas, parámetros y objetos devueltos de un módulo (ej: println ${CONTEXT_NAME}.fs.help()).
Si requieres operaciones no listadas abajo o tienes dudas sobre una firma, ejecuta help() antes de inventar métodos.

MUESTRA DE MÉTODOS PRINCIPALES (Catálogo parcial; consulta help() para funciones avanzadas):
- ${CONTEXT_NAME}.fs.lines("ruta") : Iterable<String> (streaming bajo en memoria; procesa transparentemente texto, PDF, DOCX vía Tika)
- ${CONTEXT_NAME}.fs.find("glob") : Iterable<String> con rutas relativas coincidentes (ej: "**/*.java")
- ${CONTEXT_NAME}.fs.grep(regex, "ruta") : Iterable con coincidencias [.file, .line, .content]
- ${CONTEXT_NAME}.fs.fuzzygrep(query, "ruta", [glob]) : Búsqueda semántica Top-K con [.file, .startLine, .endLine, .content]
- ${CONTEXT_NAME}.fs.write("ruta", content) : Escribe texto o líneas con auto-mkdir y copia de seguridad en JavaRCS
- ${CONTEXT_NAME}.llm.query(prompt, texto) : Consulta semántica (DISYUNTOR: máx. 25/script; prohibido en bucles masivos sin pre-filtrar)
- ${CONTEXT_NAME}.llm.sml_query(prompt) : Consulta al modelo local ONNX Qwen3.5 en memoria (rápido, sin coste API ni red)
- ${CONTEXT_NAME}.llm.extractJson(prompt, texto) : Extrae datos estructurados como Map o List de Groovy
- ${CONTEXT_NAME}.web.lines("url") : Streaming de líneas de texto limpio extraído de URLs o PDFs web
- ${CONTEXT_NAME}.web.search("query") : Búsqueda web (retorna Map con [.title, .url, .content])
- ${CONTEXT_NAME}.annotation.add(origen, nota, [resId], [tipo]) : Guarda conocimiento o directivas directamente en memoria episódica
- ${CONTEXT_NAME}.state.miVariable = valor : Almacena variables volátiles entre scripts de la misma sesión
- ${CONTEXT_NAME}.subagents.run("receta", params) : Ejecuta un trabajador especializado de forma síncrona en su propio sandbox

""" + getShortPaginationInstruction(), "${CONTEXT_NAME}", CONTEXT_NAME))
            .addStringParameter("script", "The Groovy code to execute.");
  }  
  
  @Override
  public int getMode() {
    return AgentTool.MODE_SCRIPTING;
  }

  @Override
  public int getType() {
    return AgentTool.TYPE_OPERATIONAL;
  }

  @Override
  public String execute(String jsonArguments) {  // TODO: Habria que lanzar la ejecucion en un hilo aparte y enviar una notificacion al terminar, de forma similar a como hace subagent.
    String subchannel = this.agent.getCurrentSubchannel();

    Args args;
    try {
      args = gson.fromJson(jsonArguments, Args.class);
    } catch (Exception e) {
      return formatErrorResponse("Invalid JSON arguments: " + e.getMessage());
    }

    if (args == null || StringUtils.isBlank(args.script)) {
      return formatErrorResponse("Parameter 'script' is required and cannot be empty.");
    }

    try (ScriptEngine engine = ScriptEngine.of(agent)) {
      ScriptEngine.ScriptResult result = engine.evaluate(args.script);
      if( result.result()!=null ) {
        BufferedWriter writer = Files.newBufferedWriter(
                result.stdout(),
                StandardCharsets.UTF_8, 
                StandardOpenOption.APPEND
        );
        writer.append(formatResult(result.result()));
        writer.flush();
        writer.close();
      }
      String resultResource = this.getIdFromPath(result.stdout());
      return servePaginatedResource(resultResource);

    } catch (SecurityException se) {
      LOGGER.warn("Security violation during script execution: {}", se.getMessage());
      return formatErrorResponse("Security Policy Error: " + se.getMessage());
    } catch (org.codehaus.groovy.control.MultipleCompilationErrorsException mce) {
      LOGGER.warn("Script compilation failed:\n{}", mce.getMessage());
      return formatErrorResponse("Compilation Error in script:\n" + mce.getMessage());
    } catch (Exception e) {
      LOGGER.warn("Runtime error executing script:\n{}", args.script, e);
      String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      return formatErrorResponse("Script Execution Error: " + message);
    }
  }

  private boolean shouldPaginate(String output) {
    if (output == null) {
      return false;
    }
    return output.length() > MAX_INLINE_OUTPUT_CHARS || output.lines().count() > 60;
  }

  private String formatDirectResponse(String content) {
    StringBuilder sb = new StringBuilder();
    sb.append("STATUS: OK\n");
    sb.append("EMPTY: ").append(content.isEmpty()).append("\n");
    sb.append("---\n");
    sb.append(content);
    return sb.toString();
  }

  private String saveAndPaginateOutput(String fullContent) {
    String executionId = "script_" + UUID.randomUUID().toString().substring(0, 8);
    Path outputFile = agent.getPaths().getTempFolder().resolve(executionId + ".out");

    try {
      Files.createDirectories(agent.getPaths().getTempFolder());
      Files.writeString(outputFile, fullContent, StandardCharsets.UTF_8);

      String resourceId = getIdFromPath(outputFile);
      if (resourceId == null) {
        return formatErrorResponse("Error generating resource_id for script output.");
      }

      return servePaginatedResource(resourceId);

    } catch (IOException e) {
      LOGGER.error("Failed persisting large script output", e);
      return formatErrorResponse("Failed to paginate output: " + e.getMessage());
    }
  }

  private String formatResult(Object result) { // FIXME: hacer que devuelba un iterable<String>, que si es un Iterable lo devuelva, y para cualquier otro caso Collections.singletonList(XXX)
    if (result == null) {
      return "";
    }
    if (result instanceof String str) {
      return str;
    }
    if (result instanceof Number || result instanceof Boolean || result instanceof Character) {
      return result.toString();
    }
    if (result instanceof Iterable<?> iterable) {
      List<String> items = new ArrayList<>();
      for (Object item : iterable) {
        items.add(Objects.toString(item, ""));
      }
      return String.join("\n", items);
    }
    if (result instanceof Map<?, ?> || result instanceof Collection<?>) {
      return outputGson.toJson(result);
    }
    return Objects.toString(result, "");
  }

  private static class Args {

    String script;
  }
}
