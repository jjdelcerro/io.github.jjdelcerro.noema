package io.github.jjdelcerro.chatagent.lib.persistence;

import dev.langchain4j.data.message.ChatMessage;
import java.sql.Timestamp;

/**
 *
 * @author jjdelcerro
 */
public interface Turn {

    /**
     * Devuelve el texto concatenado que representa el contenido semántico del
     * turno. Útil para que el SourceOfTruth calcule el embedding sobre esto.
     */
    String getContentForEmbedding();

    String getContenttype();

    float[] getEmbedding();

    int getId();

    String getTextModel();

    String getTextModelThinking();

    String getTextUser();

    Timestamp getTimestamp();

    String getToolCall();

    String getToolResult();

    /**
     * Genera una línea CSV formateada y escapada para el protocolo de
     * compactación.
     */
    String toCSVLine();

    /**
     * Convierte el Turno al formato de mensaje de LangChain4j. Nota: Esta
     * conversión es básica. Si se requiere soportar ToolExecutionResultMessage,
     * se debería expandir la lógica según el 'contenttype'.
     */
    ChatMessage toChatMessage();

}
