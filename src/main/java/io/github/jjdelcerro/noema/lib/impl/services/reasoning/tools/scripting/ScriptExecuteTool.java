package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.scripting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.transform.TimedInterrupt;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.AbstractPaginatedAgentTool;
import io.github.jjdelcerro.noema.lib.impl.ToolSpecificationBuilder;
import static io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.scripting.ScriptContext.CONTEXT_NAME;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

  private static final long EXECUTION_TIMEOUT_SECONDS = 30L;
  private static final int MAX_INLINE_OUTPUT_CHARS = 2048;

  private final CompilerConfiguration compilerConfig;
  private final Map<String, Map<String, Object>> subchannelStates;
  private final Gson outputGson;

  public ScriptExecuteTool(Agent agent) {
    super(agent);
    this.subchannelStates = new ConcurrentHashMap<>();
    this.outputGson = new GsonBuilder().setPrettyPrinting().create();
    this.compilerConfig = createCompilerConfiguration();
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description(StringUtils.replace("""
Executes a sandboxed Groovy script inside the Noema JVM to process, filter, or transform data programmatically.

**GLOBAL CONTEXT OBJECT (`${CONTEXT_NAME}`):**
Use the `noema` object to access streaming facades:
- `${CONTEXT_NAME}.fs.lines("file.txt")` : Returns an Iterable of lines (streaming, low memory).
- `${CONTEXT_NAME}.fs.forEachLine("file.txt") { line, num -> ... }` : Iterates line by line.
- `${CONTEXT_NAME}.fs.find("src/**/*.java")` : Lists matching relative file paths.
- `${CONTEXT_NAME}.fs.grep("regex", "path")` : Searches regex returning matches with file and line.
- `${CONTEXT_NAME}.fs.write("path", content)` : Writes text/lines with automatic RCS backup.
- `${CONTEXT_NAME}.llm.query("prompt", chunk)` : Stateless sub-query to evaluate data chunks.
- `${CONTEXT_NAME}.llm.extractJson("prompt", chunk)` : Extracts and parses structured JSON.
- `${CONTEXT_NAME}.web.lines("https://...")` : Streams clean lines of text from web/PDF/DOCX.
- `${CONTEXT_NAME}.web.search("query")` : Web search returning list of {title, url, content}.
- `${CONTEXT_NAME}.notes.add(source, note, [resource_id], [type])` : Direct knowledge registration.
- `${CONTEXT_NAME}.subagents.run(name, params)` : Runs a subagent worker recipe synchronously.
- `${CONTEXT_NAME}.state.myVar = value` : Preserves variables between scripts in the same session.
- `println ${CONTEXT_NAME}.help()` : Prints available modules.

""" + getShortPaginationInstruction(),"${CONTEXT_NAME}",CONTEXT_NAME))
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
    Map<String, Object> sessionState = getSessionState(subchannel);

    Args args;
    try {
      args = gson.fromJson(jsonArguments, Args.class);
    } catch (Exception e) {
      return formatErrorResponse("Invalid JSON arguments: " + e.getMessage());
    }

    if (args == null || StringUtils.isBlank(args.script)) {
      return formatErrorResponse("Parameter 'script' is required and cannot be empty.");
    }

    // Try-with-resources guarantees deterministic cleanup of open file iterators
    try (ScriptContext context = new ScriptContext(this.agent, subchannel, sessionState)) {
      Binding binding = new Binding();
      binding.setVariable(CONTEXT_NAME, context);
      binding.setVariable("context", context); // Convenient alias

      GroovyShell shell = new GroovyShell(binding, this.compilerConfig);
      Object rawResult = shell.evaluate(args.script);

      String formattedOutput = formatResult(rawResult);

      if (shouldPaginate(formattedOutput)) {
        return saveAndPaginateOutput(formattedOutput);
      }

      return formatDirectResponse(formattedOutput);

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

  private Map<String, Object> getSessionState(String subchannel) {
    return this.subchannelStates.computeIfAbsent(subchannel, k -> new ConcurrentHashMap<>());
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

  /**
   * Prepares the strict sandbox configuration and execution timeouts.
   */
  private CompilerConfiguration createCompilerConfiguration() {
    CompilerConfiguration config = new CompilerConfiguration();

    // 1. AST Security Customizer
    SecureASTCustomizer secureCustomizer = new SecureASTCustomizer();
    secureCustomizer.setClosuresAllowed(true);
    secureCustomizer.setMethodDefinitionAllowed(true);

    // Blacklist dangerous system and reflection classes
    secureCustomizer.setDisallowedImports(List.of(
            "java.lang.System",
            "java.lang.Runtime",
            "java.lang.ProcessBuilder",
            "java.lang.reflect.*"
    ));

    secureCustomizer.setDisallowedStarImports(List.of(
            "java.lang.reflect",
            "java.lang.invoke"
    ));

    // 2. Timed Interrupt Customizer (5s timeout to prevent infinite loops)
    Map<String, Object> timeoutParams = Collections.singletonMap("value", EXECUTION_TIMEOUT_SECONDS);
    ASTTransformationCustomizer timedInterrupt = new ASTTransformationCustomizer(timeoutParams, TimedInterrupt.class);

    config.addCompilationCustomizers(secureCustomizer, timedInterrupt);
    return config;
  }

  private static class Args {

    String script;
  }
}
