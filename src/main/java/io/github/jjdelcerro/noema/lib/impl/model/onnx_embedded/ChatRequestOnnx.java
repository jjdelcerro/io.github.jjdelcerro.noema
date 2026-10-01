package io.github.jjdelcerro.noema.lib.impl.model.onnx_embedded;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChatRequestOnnx {

    private static final ObjectMapper MAPPER = new ObjectMapper();
  private List<ChatMessage> messages;
  private List<ToolSpecification> toolsSpecification;

    public ChatRequestOnnx() {
      
    }
    
    public ChatRequestOnnx setMessages(List<ChatMessage> messages) {
      this.messages = messages;
      return this;
    }
    
    public ChatRequestOnnx setToolsSpecification(List<ToolSpecification> tools) {
      this.toolsSpecification = tools;
      return this;
    }
    
    public String rawprompt() {
      // Ahora mismo solo soporta el formato ChatML
      return this.toChatML(messages, toolsSpecification);
    }
    
    private String toChatML(List<ChatMessage> messages, List<ToolSpecification> tools) {
        StringBuilder prompt = new StringBuilder();

        // 1. Extraer o inicializar el System Message
        String systemContent = extractSystemContent(messages);
        
        // 2. Si hay herramientas, inyectarlas en el bloque system (formato oficial Qwen)
        prompt.append("<|im_start|>system\n");
        if (!systemContent.isEmpty()) {
            prompt.append(systemContent).append("\n\n");
        } else {
            prompt.append("You are Qwen, created by Alibaba Cloud. You are a helpful assistant.\n\n");
        }

        if (tools != null && !tools.isEmpty()) {
            prompt.append("# Tools\n\n")
                  .append("You may call one or more functions to assist with the user query.\n\n")
                  .append("You are provided with function signatures within <tools></tools> XML tags:\n")
                  .append("<tools>\n");
            
            for (ToolSpecification tool : tools) {
                prompt.append(serializeTool(tool)).append("\n");
            }
            
            prompt.append("</tools>\n\n")
                  .append("For each function call, return a json object with function name and arguments within <tool_call></tool_call> XML tags:\n")
                  .append("<tool_call>\n")
                  .append("{\"name\": <function-name>, \"arguments\": <args-json-object>}\n")
                  .append("</tool_call>");
        }
        prompt.append("<|im_end|>\n");

        // 3. Procesar el historial de mensajes restantes
        for (ChatMessage message : messages) {
            if (message instanceof SystemMessage) {
                continue; // Ya se procesó arriba
            } else if (message instanceof UserMessage um) {
                prompt.append("<|im_start|>user\n")
                      .append(um.singleText())
                      .append("<|im_end|>\n");

            } else if (message instanceof AiMessage am) {
                prompt.append("<|im_start|>assistant\n");
                if (am.text() != null && !am.text().isBlank()) {
                    prompt.append(am.text()).append("\n");
                }
                // Si el modelo invocó herramientas en este turno
                if (am.hasToolExecutionRequests()) {
                    for (ToolExecutionRequest req : am.toolExecutionRequests()) {
                        prompt.append("<tool_call>\n")
                              .append("{\"name\": \"").append(req.name()).append("\", ")
                              .append("\"arguments\": ").append(req.arguments()).append("}\n")
                              .append("</tool_call>\n");
                    }
                }
                prompt.append("<|im_end|>\n");

            } else if (message instanceof ToolExecutionResultMessage tm) {
                // Qwen espera las respuestas de herramientas como un turno de usuario con <tool_response>
                prompt.append("<|im_start|>user\n<tool_response>\n")
                      .append(tm.text())
                      .append("\n</tool_response><|im_end|>\n");
            }
        }

        // 4. Token final para ceder el turno al asistente
        prompt.append("<|im_start|>assistant\n");

        return prompt.toString();
    }

    private static String extractSystemContent(List<ChatMessage> messages) {
        return messages.stream()
                .filter(m -> m instanceof SystemMessage)
                .map(m -> ((SystemMessage) m).text())
                .findFirst()
                .orElse("");
    }

    private static String serializeTool(ToolSpecification tool) {
        try {
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", tool.name());
            if (tool.description() != null) {
                fn.put("description", tool.description());
            }
            if (tool.parameters() != null) {
                fn.put("parameters", tool.parameters());
            }

            Map<String, Object> wrapper = Map.of(
                    "type", "function",
                    "function", fn
            );
            return MAPPER.writeValueAsString(wrapper);
        } catch (Exception e) {
            throw new RuntimeException("Error serializando ToolSpecification a JSON", e);
        }
    }
}
