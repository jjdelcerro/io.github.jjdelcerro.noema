package io.github.jjdelcerro.noema.lib.impl.services.scripting.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.AbstractPaginatedAgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.ToolSpecificationBuilder;
import static io.github.jjdelcerro.noema.lib.impl.services.scripting.ScriptContextImpl.CONTEXT_NAME;
import io.github.jjdelcerro.noema.lib.impl.services.scripting.ScriptEngineImpl;
import io.github.jjdelcerro.noema.lib.api.services.scripting.ScriptEngine;
import io.github.jjdelcerro.noema.lib.api.services.scripting.ScriptingService;
import java.io.BufferedWriter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Tool that executes Groovy scripts in an embedded and sandboxed JVM
 * environment. The script can be provided inline or as a .groovy file read
 * from the agent workspace. Variables can be injected into the engine binding
 * before evaluation. Automatically paginates long outputs and disposes
 * resources deterministically.
 */
public class ScriptExecuteTool extends AbstractPaginatedAgentTool {

  private static final Logger LOGGER = LoggerFactory.getLogger(ScriptExecuteTool.class);
  public static final String TOOL_NAME = "execute_script";

  private static final int MAX_INLINE_OUTPUT_CHARS = 2048;
  private static final long MAX_SCRIPT_FILE_BYTES = 512L * 1024L;
  private static final List<String> RESERVED_VARIABLES = List.of(CONTEXT_NAME, "context", "out");

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

FUENTE DEL SCRIPT (excluyente, exactamente una de las dos):
• 'script': código Groovy inline.
• 'scriptFile': ruta absoluta a un fichero '.groovy'.
  Úsalo para scripts reutilizables: evita reescribir el mismo código en cada llamada.

PARÁMETROS DEL SCRIPT:
• 'variables': objeto JSON opcional con variables que se inyectan en el engine antes de evaluar.
  Ejemplo: {"file": "/ruta/a.md", "titles": ["Sección A"], "limit": 40}
  Las claves deben ser identificadores Groovy válidos. Los valores pueden ser string, número,
  booleano, array u objeto. Nombres reservados: agent, context, out.

REGLAS DE EJECUCIÓN Y SEGURIDAD:
• Código estrictamente Groovy/Java para la JVM (no Python). Usa 'def', closures { item -> ... } y colecciones estándar.
• Sandbox activo: clases de E/S directa como 'java.io.File', 'System' o 'ProcessBuilder' están bloqueadas. Toda interacción con el entorno debe realizarse exclusivamente a través del objeto '${CONTEXT_NAME}'.

DESCUBRIMIENTO Y AYUDA (Bajo demanda):
• println ${CONTEXT_NAME}.help() : Lista el catálogo de módulos disponibles (fs, llm, web, annotation, state, subagents).
• println ${CONTEXT_NAME}.<modulo>.help() : Muestra las firmas completas, parámetros y objetos devueltos de un módulo (ej: println ${CONTEXT_NAME}.fs.help()).
Si requieres operaciones no listadas abajo o tienes dudas sobre una firma, ejecuta help() antes de inventar métodos.

MUESTRA DE MÉTODOS PRINCIPALES (Catálogo parcial; consulta help() para funciones avanzadas):
- ${CONTEXT_NAME}.fs.lines("ruta") : Iterable<String> (streaming bajo en memoria; procesa transparentemente texto, PDF, DOCX vía Tika)
- ${CONTEXT_NAME}.fs.find("glob") : Iterable<String> con rutas absolutas coincidentes (ej: "**/*.java")
- ${CONTEXT_NAME}.fs.grep(regex, "ruta") : Iterable con coincidencias [.file, .line, .content]
- ${CONTEXT_NAME}.fs.fuzzygrep(query, "ruta", [glob]) : Búsqueda semántica Top-K con [.file, .startLine, .endLine, .similarity, .content]
- ${CONTEXT_NAME}.fs.write("ruta", content) : Escribe texto o líneas con auto-mkdir y copia de seguridad en JavaRCS
- ${CONTEXT_NAME}.fs.searchAndReplace(path, oldText, newText): SearchAndReplaceResult(status, path, strategy, message, preview, startLine, replacedLineCount)
- ${CONTEXT_NAME}.llm.query(prompt) : Consulta usando un LLM (el mismo que el razonador).
- ${CONTEXT_NAME}.llm.sml_query(prompt) : Consulta al modelo local ONNX Qwen3.5 en memoria (rápido, sin coste API ni red)
- ${CONTEXT_NAME}.llm.extractJson(prompt, texto) : Extrae datos estructurados como Map o List de Groovy
- ${CONTEXT_NAME}.web.lines("url") : Streaming de líneas de texto limpio extraído de URLs o PDFs web
- ${CONTEXT_NAME}.web.search("query") : Búsqueda web (retorna Map con [.title, .url, .content])
- ${CONTEXT_NAME}.annotation.add(origen, nota, [resId], [tipo]) : Guarda conocimiento o directivas directamente en memoria episódica
- ${CONTEXT_NAME}.state.miVariable = valor : Almacena variables volátiles entre scripts de la misma sesión
- ${CONTEXT_NAME}.subagents.run("receta", params) : Ejecuta un trabajador especializado de forma síncrona en su propio sandbox

Todas las funciones, mientras no indiquen lo contrario, aceptan solo rutas absolutas.
""" + getShortPaginationInstruction(), "${CONTEXT_NAME}", CONTEXT_NAME))
            .addStringParameter("script", "Groovy code to execute. Mutually exclusive with 'scriptFile'.")
            .addStringParameter("scriptFile", "Absolute path to a '.groovy' file to execute instead of 'script'. Mutually exclusive with 'script'.")
            .addStringParameter("variables", "Optional JSON object with variables injected into the engine binding before execution. Keys must be valid Groovy identifiers. Reserved names: agent, context, out.");
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
  public ToolResult execute(String jsonArguments) {  // TODO: Habria que lanzar la ejecucion en un hilo aparte y enviar una notificacion al terminar, de forma similar a como hace subagent.
    String subchannel = this.agent.getCurrentSubchannel();
    ScriptingService service = (ScriptingService) this.agent.getService(ScriptingService.NAME);

    Args args;
    try {
      args = gson.fromJson(jsonArguments, Args.class);
    } catch (Exception e) {
      return formatErrorResponse("Invalid JSON arguments: " + e.getMessage());
    }

    if (args == null) {
      return formatErrorResponse("Invalid JSON arguments.");
    }

    String source;
    Map<String, Object> variables;
    try {
      source = this.resolveScriptSource(args);
      variables = this.parseVariables(args.variables);
    } catch (IllegalArgumentException e) {
      return formatErrorResponse(e.getMessage());
    }

    try (ScriptEngine engine = service.createScriptEngine()) {
      this.bindVariables(engine, variables);
      ScriptEngineImpl.ScriptResult result = engine.evaluate(source);
      if (result.result() != null) {
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
      String scriptReference = args.script != null ? args.script : args.scriptFile;
      LOGGER.warn("Runtime error executing script:\n{}", scriptReference, e);
      String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      return formatErrorResponse("Script Execution Error: " + message);
    }
  }

  /**
   * Resolves the Groovy source to execute: either the inline parameter or the
   * contents of a .groovy file validated against the agent access policy.
   *
   * @param args the tool arguments
   * @return the Groovy source to evaluate
   * @throws IllegalArgumentException when the parameters are missing, conflicting or the file is not usable
   */
  private String resolveScriptSource(Args args) {
    boolean hasInline = StringUtils.isNotBlank(args.script);
    boolean hasFile = StringUtils.isNotBlank(args.scriptFile);
    if (hasInline && hasFile) {
      throw new IllegalArgumentException(
              "Parameters 'script' and 'scriptFile' are mutually exclusive. Provide only one of them.");
    }
    if (!hasInline && !hasFile) {
      throw new IllegalArgumentException("One of 'script' or 'scriptFile' is required.");
    }
    if (hasInline) {
      return args.script;
    }
    return this.readScriptFile(args.scriptFile);
  }

  /**
   * Reads a Groovy script from disk after validating that the path belongs to
   * the agent sandbox, that the extension is .groovy and that the size is
   * reasonable.
   *
   * @param rawPath the path received from the tool arguments
   * @return the file contents
   * @throws IllegalArgumentException when the file is not accessible or not usable
   */
  private String readScriptFile(String rawPath) {
    Path path = this.resolvePathOrNull(rawPath);
    if (path == null) {
      throw new IllegalArgumentException(
              "Script file is not accessible: " + rawPath);
    }
    if (!Files.isRegularFile(path)) {
      throw new IllegalArgumentException(
              "Script file does not exist or is not a regular file: " + rawPath);
    }
    String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
    if (!fileName.endsWith(".groovy")) {
      throw new IllegalArgumentException("Script file must have a .groovy extension: " + rawPath);
    }
    try {
      long size = Files.size(path);
      if (size > MAX_SCRIPT_FILE_BYTES) {
        throw new IllegalArgumentException(
                "Script file exceeds the " + MAX_SCRIPT_FILE_BYTES + " bytes limit: " + rawPath);
      }
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot read script file '" + rawPath + "': " + e.getMessage(), e);
    }
  }

  /**
   * Parses the optional JSON object of script variables, validates the names
   * and normalizes the values before they are injected into the engine.
   *
   * @param variablesJson the raw JSON parameter, may be null or blank
   * @return the validated variables, empty when no parameter was provided
   * @throws IllegalArgumentException when the JSON is invalid, a name is reserved or a name is not a valid identifier
   */
  private Map<String, Object> parseVariables(String variablesJson) {
    Map<String, Object> variables = new LinkedHashMap<>();
    if (StringUtils.isBlank(variablesJson)) {
      return variables;
    }
    Type type = new TypeToken<Map<String, Object>>() { }.getType();
    Map<String, Object> raw;
    try {
      raw = gson.fromJson(variablesJson, type);
    } catch (Exception e) {
      throw new IllegalArgumentException("Parameter 'variables' is not valid JSON: " + e.getMessage(), e);
    }
    if (raw == null) {
      return variables;
    }
    for (Map.Entry<String, Object> entry : raw.entrySet()) {
      String name = entry.getKey();
      if (StringUtils.isBlank(name)) {
        throw new IllegalArgumentException("Variable names cannot be empty.");
      }
      if (RESERVED_VARIABLES.contains(name)) {
        throw new IllegalArgumentException(
                "Variable name '" + name + "' is reserved by the scripting engine.");
      }
      if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
        throw new IllegalArgumentException(
                "Variable name '" + name + "' is not a valid Groovy identifier.");
      }
      variables.put(name, this.normalizeValue(entry.getValue()));
    }
    return variables;
  }

  /**
   * Injects the validated variables into the engine binding. Must run before
   * evaluate(), because evaluate() rebinds the context variables afterwards
   * and would silently overwrite them.
   *
   * @param engine the script engine of the current execution
   * @param variables the variables to inject, already validated and normalized
   */
  private void bindVariables(ScriptEngine engine, Map<String, Object> variables) {
    for (Map.Entry<String, Object> entry : variables.entrySet()) {
      engine.setVariable(entry.getKey(), entry.getValue());
    }
  }

  /**
   * Gson deserializes every JSON number as Double. Groovy methods such as
   * take() or drop() expect Integer or Long, so whole doubles are narrowed
   * before injection.
   *
   * @param value the raw value deserialized by Gson
   * @return the normalized value
   */
  private Object normalizeValue(Object value) {
    if (value instanceof Double number) {
      double asDouble = number;
      if (!Double.isInfinite(asDouble) && asDouble == Math.floor(asDouble)) {
        if (asDouble >= Integer.MIN_VALUE && asDouble <= Integer.MAX_VALUE) {
          return (int) asDouble;
        }
        if (asDouble >= Long.MIN_VALUE && asDouble <= Long.MAX_VALUE) {
          return (long) asDouble;
        }
      }
      return number;
    }
    if (value instanceof List<?> list) {
      List<Object> normalized = new ArrayList<>(list.size());
      for (Object item : list) {
        normalized.add(this.normalizeValue(item));
      }
      return normalized;
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> normalized = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        normalized.put(String.valueOf(entry.getKey()), this.normalizeValue(entry.getValue()));
      }
      return normalized;
    }
    return value;
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
    String scriptFile;
    String variables;
  }
}
