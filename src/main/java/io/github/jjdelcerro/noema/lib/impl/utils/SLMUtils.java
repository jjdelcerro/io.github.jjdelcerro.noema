package io.github.jjdelcerro.noema.lib.impl.utils;

import ai.onnxruntime.genai.GenAIException;
import ai.onnxruntime.genai.GeneratorParams;
import ai.onnxruntime.genai.SimpleGenAI;
import io.github.jjdelcerro.noema.lib.api.Agent;
import java.nio.file.Path;
import java.util.List;

/**
 *
 * @author jjdelcerro
 */
public class SLMUtils {

  private static final String SLM_NAME = "Qwen3-0.6B";

  private static SimpleGenAI model;

  public static void start(Agent agent) {
    List<String> resources = AgentUtils.readLinesResourceFile("var/models/"+SLM_NAME+"/files.txt");
    for (String resname : resources) {
      agent.installResource("var/models/" + SLM_NAME + "/" + resname);
    }
  }

  public static SimpleGenAI getModel(Agent agent) throws GenAIException {
    if (model == null) {
      Path modelpath = agent.getPaths().getAgentPath("var/models/" + SLM_NAME).toAbsolutePath();
      model = new SimpleGenAI(modelpath.toString());
    }
    return model;
  }

  public static String generate(Agent agent, String prompt) {
    return generate(agent, prompt, 8 * 1024, 0.5);
  }

  public static String generate(Agent agent, String prompt, int maxTokens, double temperature) {
    try {
      GeneratorParams params = getModel(agent).createGeneratorParams();
      params.setSearchOption("max_length", maxTokens);
      params.setSearchOption("temperature", temperature);

      return getModel(agent).generate(params, prompt, null);
    } catch (GenAIException ex) {
      throw new RuntimeException("Can't generate response from SLM", ex);
    }
  }
}
