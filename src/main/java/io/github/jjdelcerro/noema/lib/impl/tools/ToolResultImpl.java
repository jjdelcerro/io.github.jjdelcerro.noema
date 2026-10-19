package io.github.jjdelcerro.noema.lib.impl.tools;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.github.jjdelcerro.noema.lib.api.AgentTool.ToolResult;
import io.github.jjdelcerro.noema.lib.api.AgentTool.ToolResultType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tika.Tika;

/**
 *
 * @author jjdelcerro
 */
public class ToolResultImpl implements ToolResult {
  
  private final ToolResultType type;
  private final String text;
  private final Path path;

  public ToolResultImpl(String text) {
    this.type = ToolResultType.Text;
    this.text = text == null ? "" : text;
    this.path = null;
  }

  public ToolResultImpl(Path path, ToolResultType type) {
    this.type = type;
    this.text = null;
    this.path = path;
  }

  @Override
  public ToolResultType getType() {
    return this.type;
  }

  @Override
  public String text() {
    return this.text;
  }

  @Override
  public Path path() {
    return this.path;
  }

  /**
   * Textual representation of the result, used by ReasoningServiceImpl to
   * persist the tool output in the episodic memory (Turn.toolResult).
   * For image results it returns the file path, which is the reference
   * required to reach the resource later.
   */
  @Override
  public String toString() {
    if (this.type == ToolResultType.Image) {
      return this.path == null ? "" : this.path.toString();
    }
    return this.text == null ? "" : this.text;
  }
  
  public static ToolExecutionResultMessage toToolExecutionResultMessage(ToolExecutionRequest request, ToolResult toolResult) {
    return toToolExecutionResultMessage(request.id(), request.name(), toolResult);
  }
  
  public static ToolExecutionResultMessage toToolExecutionResultMessage(String id, String toolsName, ToolResult toolResult) {
    switch(toolResult.getType()) {
      case ToolResultType.Image:
        return toImageMessage(id, toolsName, toolResult.path());
      case ToolResultType.Text:
      default:
        return ToolExecutionResultMessage.from(id, toolsName, toolResult.text());
    }
  }

  /**
   * Builds a multimodal tool result message carrying an image. This method
   * never throws: any problem degrades to a plain text message so the model
   * can read the failure. It is invoked outside the tool execution try/catch,
   * so an exception here would abort the whole turn.
   */
  private static ToolExecutionResultMessage toImageMessage(String id, String toolsName, Path path) {
    if (path == null) {
      return ToolExecutionResultMessage.from(id, toolsName, "ERROR: image result without path.");
    }
    if (!Files.isRegularFile(path)) {
      return ToolExecutionResultMessage.from(id, toolsName, "ERROR: image file not found: " + path);
    }
    String mimeType;
    try {
      mimeType = new Tika().detect(path);
    } catch (IOException ex) {
      return ToolExecutionResultMessage.from(id, toolsName, "ERROR: cannot detect mime type of " + path + ": " + ex.getMessage());
    }
    if (mimeType == null || !mimeType.startsWith("image/")) {
      return ToolExecutionResultMessage.from(id, toolsName, "ERROR: file is not an image (" + mimeType + "): " + path);
    }
    try {
      return ToolExecutionResultMessage.builder()
          .id(id)
          .toolName(toolsName)
          .contents(new Content[] { ImageContent.from(path, mimeType) })
          .build();
    } catch (RuntimeException ex) {
      return ToolExecutionResultMessage.from(id, toolsName, "ERROR: cannot read image " + path + ": " + ex.getMessage());
    }
  }
  
}
