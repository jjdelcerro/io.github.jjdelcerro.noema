package io.github.jjdelcerro.noema.lib.impl.model.onnx_embedded;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

public class ChatResponseOnnx {

  private static final Pattern TOOL_PATTERN = Pattern.compile("<tool_call>\\s*(\\{.*?\\})\\s*</tool_call>", Pattern.DOTALL);
  private List<ToolSpecification> toolsSpecification;
  private String rawResponse;

  public ChatResponseOnnx() {

  }

  public ChatResponseOnnx setToolsSpecification(List<ToolSpecification> tools) {
    this.toolsSpecification = tools;
    return this;
  }

  public ChatResponseOnnx setRawResponse(String rawResponse) {
    this.rawResponse = rawResponse;
    return this;
  }

  public AiMessage aiMessage() {
    return this.parseResponse(toolsSpecification, rawResponse);
  }

  private AiMessage parseResponse(List<ToolSpecification> toolSpecifications, String rawOutput) {
    List<ToolExecutionRequest> toolRequests = new ArrayList<>();
    String cleanText = rawOutput;

    if (!CollectionUtils.isEmpty(toolSpecifications)) {
      Matcher matcher = TOOL_PATTERN.matcher(rawOutput);

      while (matcher.find()) {
        String jsonPayload = matcher.group(1);
        try {
          // Jackson JsonNode
          var root = new ObjectMapper().readTree(jsonPayload);
          String name = root.get("name").asText();
          String args = root.get("arguments").toString();

          if (isTool(toolSpecifications, name)) {
            if (!isInTextBlock(rawOutput, matcher)) {
              toolRequests.add(ToolExecutionRequest.builder()
                      .name(name)
                      .arguments(args)
                      .build());
            }
            // FIXME: en lugar de un replaceAll habria que extraer solo el texto del matcher de start a end.
            cleanText = rawOutput.replaceAll("<tool_call>.*?</tool_call>", "").trim();
          }
        } catch (Exception ignored) {
          // Do nothing
        }
      }
    }
    if (!toolRequests.isEmpty()) {
      if (StringUtils.isNotBlank(cleanText)) {
        return AiMessage.from(cleanText, toolRequests);
      } else {
        return AiMessage.from(toolRequests);
      }
    } else {
      return AiMessage.from(cleanText);
    }
  }

  private static boolean isTool(List<ToolSpecification> toolSpecifications, String name) {
    if (CollectionUtils.isEmpty(toolSpecifications)) {
      return false;
    }
    // FIXME: falta por implementar el comprobar que el nombre se corresponde con una tool
    return true;
  }

  private static boolean isInTextBlock(String rawOutput, Matcher matcher) {
    // FIXME: 
    return false;
  }
}
