package io.github.jjdelcerro.noema.lib.impl.persistence;

import io.github.jjdelcerro.noema.lib.memory.episodic.Turn;
import java.time.LocalDateTime;

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
}
