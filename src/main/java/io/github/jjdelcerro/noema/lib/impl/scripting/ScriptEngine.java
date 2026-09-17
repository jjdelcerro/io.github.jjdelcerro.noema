package io.github.jjdelcerro.noema.lib.impl.scripting;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.transform.TimedInterrupt;
import io.github.jjdelcerro.noema.lib.Agent;
import static io.github.jjdelcerro.noema.lib.impl.scripting.ScriptContext.CONTEXT_NAME;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.io.IOUtils;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;

/**
 *
 * @author jjdelcerro
 */
public class ScriptEngine implements AutoCloseable {

  public record ScriptResult(Path stdout, Object result) {

  }

  private static final long EXECUTION_TIMEOUT_SECONDS = 30L;

  private CompilerConfiguration compilerConfig;
  private final Agent agent;
  private ScriptContext context;
  private Binding binding;

  protected ScriptEngine(Agent agent) {
    this.agent = agent;
    this.compilerConfig = null;
    this.context = null;
  }

  public void setContext(ScriptContext context) {
    this.context = context;
  }

  public ScriptContext getContext() {
    if (this.context == null) {
      this.context = ScriptContext.of(agent);
    }
    return context;
  }

  private Binding getBinding() {
    if (this.binding == null) {
      this.binding = new Binding();
    }
    return binding;
  }

  public static ScriptEngine of(Agent agent) {
    return new ScriptEngine(agent);
  }

  public void setVariable(String name, Object value) {
    this.getBinding().setVariable(name, value);
  }

  public ScriptResult evaluate(String script) {
    BufferedWriter stdoutWriter = null;
    try {
      String executionId = "script-stdout-" + UUID.randomUUID().toString().substring(0, 8);
      Path outputFile = agent.getPaths().getTempFolder().resolve(executionId + ".txt");
      stdoutWriter = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8);
      this.getBinding().setProperty("out", stdoutWriter);
      this.setVariable(CONTEXT_NAME, this.getContext());
      this.setVariable("context", this.getContext());
      GroovyShell shell = new GroovyShell(
              this.getBinding(),
              this.getCompilerConfiguration()
      );
      Object rawResult = shell.evaluate(script);

      return new ScriptResult(outputFile, rawResult);
    } catch (IOException ex) {
      throw new RuntimeException(ex.getLocalizedMessage(), ex);
    } finally {
      IOUtils.closeQuietly(stdoutWriter);
    }
  }

  /**
   * Prepares the strict sandbox configuration and execution timeouts.
   */
  private CompilerConfiguration getCompilerConfiguration() {
    if (this.compilerConfig == null) {
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

      ImportCustomizer importCustomizer = new ImportCustomizer();
      importCustomizer.addImports(
              "io.github.jjdelcerro.noema.lib.impl.scripting.modules.FsModule.GrepMatch",
              "io.github.jjdelcerro.noema.lib.impl.FileFuzzySearchUtils.FuzzyMatch"
      );
      config.addCompilationCustomizers(secureCustomizer, timedInterrupt, importCustomizer);

      this.compilerConfig = config;
    }
    return this.compilerConfig;
  }

  @Override
  public void close() throws Exception {
    if (this.context != null) {
      this.context.close();
      this.context = null;
    }
    this.binding = null;
  }

}
