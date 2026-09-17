package io.github.jjdelcerro.noema.lib.impl;

import ai.onnxruntime.genai.GenAIException;
import ai.onnxruntime.genai.GeneratorParams;
import ai.onnxruntime.genai.SimpleGenAI;
import io.github.jjdelcerro.noema.lib.Agent;
import java.nio.file.Path;

/**
 *
 * @author jjdelcerro
 */
public class SLMUtils {

  private static final String SLM_NAME = "Qwen3.5-0.8B";

  private static SimpleGenAI model;

  public static void start(Agent agent) {
    String[] resources = new String[]{
      "onnx/decoder_model_merged_q4.onnx",
      "onnx/decoder_model_merged_q4.onnx_data",
      "onnx/embed_tokens_q4.onnx",
      "onnx/embed_tokens_q4.onnx_data",
      "README.md",
      "chat_template.jinja",
      "config.json",
      "generation_config.json",
      "preprocessor_config.json",
      "processor_config.json",
      "tokenizer.json",
      "tokenizer_config.json"
    };
    for (String resPath : resources) {
      agent.installResource("var/models/" + SLM_NAME + "/" + resPath);
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
