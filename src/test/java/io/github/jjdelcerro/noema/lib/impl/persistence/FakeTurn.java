package io.github.jjdelcerro.noema.lib.impl.persistence;

import io.github.jjdelcerro.noema.lib.api.memory.episodic.Turn;
import java.time.LocalDateTime;
import org.apache.commons.lang3.StringUtils;

public class FakeTurn implements Turn {

    private int id;
    private LocalDateTime timestamp;
    private String contenttype;
    private String subchannel;
    private String textUser;
    private String textModelThinking;
    private String textModel;
    private String toolCall;
    private String toolResult;
    private float[] embedding;

    public FakeTurn() {
        this.id = 1;
        this.timestamp = LocalDateTime.now();
        this.contenttype = "chat";
        this.subchannel = "default";
        this.textUser = "Mensaje de prueba de usuario";
        this.textModelThinking = null;
        this.textModel = "Respuesta de prueba del modelo";
        this.toolCall = null;
        this.toolResult = null;
        this.embedding = null;
    }

    public FakeTurn(int id, String contenttype, String textUser, String textModel) {
        this();
        this.id = id;
        this.contenttype = contenttype;
        this.textUser = textUser;
        this.textModel = textModel;
    }

    @Override public int getId() { return id; }
    @Override public LocalDateTime getTimestamp() { return timestamp; }
    @Override public String getContenttype() { return contenttype; }
    @Override public String getSubchannel() { return subchannel; }
    @Override public String getTextUser() { return textUser; }
    @Override public String getTextModelThinking() { return textModelThinking; }
    @Override public String getTextModel() { return textModel; }
    @Override public String getToolCall() { return toolCall; }
    @Override public String getToolResult() { return toolResult; }
    @Override public float[] getEmbedding() { return embedding; }
    @Override public String getAnnotationType() { return null; }

    @Override
    public String getContentForEmbedding() {
        StringBuilder sb = new StringBuilder();
        if (textUser != null) sb.append(textUser).append(" ");
        if (textModel != null) sb.append(textModel).append(" ");
        if (toolCall != null) sb.append(toolCall).append(" ");
        if (toolResult != null) sb.append(toolResult);
        return sb.toString().trim();
    }

    @Override
    public String toCSVLine() {
        return String.format("%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"",
                id,
                timestamp,
                contenttype != null ? contenttype : "",
                subchannel != null ? subchannel : "",
                textUser != null ? textUser.replace("\"", "\"\"") : "",
                textModelThinking != null ? textModelThinking.replace("\"", "\"\"") : "",
                textModel != null ? textModel.replace("\"", "\"\"") : "",
                toolCall != null ? toolCall.replace("\"", "\"\"") : "",
                toolResult != null ? toolResult.replace("\"", "\"\"") : ""
        );
    }
    
  public String toXML() {  // FIXME: escapar los CDATA
    String xml = String.format(
"""
    <turn id="%s" timestamp="%s" contenttype="%s" subchannel="%s">
      <textUser>CDATA[[%s]]</textUser>
      <textModelThinking>CDATA[[%s]]</textModelThinking>
      <textModel>CDATA[[%s]]</textModel>
      <toolCall>CDATA[[%s]]</toolCall>
      <toolResult>CDATA[[%s]]</toolResult>
      <textModel>CDATA[[%s]]</textModel>
    </turn>
""",
            StringUtils.trim(String.valueOf(id)),
            StringUtils.trim(String.valueOf(timestamp)),
            contenttype,
            subchannel,
            textUser,
            textModelThinking,
            textModel,
            toolCall,
            toolResult            
    );
    return xml;
  }

    @Override
    public String getCSVHeader() {
      return "code,timestamp,contenttype,subchannel,text_user,text_model_thinking,text_model,tool_call,tool_result";
    }

    @Override
    public Turn withToolCall(String toolCall) {
        FakeTurn copy = copy();
        copy.toolCall = toolCall;
        return copy;
    }

    @Override
    public Turn withToolResult(String toolResult) {
        FakeTurn copy = copy();
        copy.toolResult = toolResult;
        return copy;
    }

    private FakeTurn copy() {
        FakeTurn copy = new FakeTurn();
        copy.id = this.id;
        copy.timestamp = this.timestamp;
        copy.contenttype = this.contenttype;
        copy.subchannel = this.subchannel;
        copy.textUser = this.textUser;
        copy.textModelThinking = this.textModelThinking;
        copy.textModel = this.textModel;
        copy.toolCall = this.toolCall;
        copy.toolResult = this.toolResult;
        copy.embedding = this.embedding;
        return copy;
    }
}
