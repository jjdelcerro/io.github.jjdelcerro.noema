package io.github.jjdelcerro.noema.lib.impl.persistence;

import io.github.jjdelcerro.noema.lib.persistence.Turn;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;

/**
 * Representa una unidad atómica de interacción (Turno) en el sistema de
 * memoria. Actúa como contenedor de datos (POJO) inmutable.
 */
public class TurnImpl implements Turn {

  private int id;
  private final LocalDateTime timestamp;
  private final String contenttype;
  private final String textUser;
  private final String textModelThinking;
  private final String textModel;
  private final String toolCall;
  private final String toolResult;
  private final float[] embedding;

  // Constructor privado. Usar métodos de factoría estáticos.
  private TurnImpl(int id, LocalDateTime timestamp, String contenttype, String textUser,
          String textModelThinking, String textModel, String toolCall,
          String toolResult, float[] embedding) {
    this.id = id;
    this.timestamp = timestamp;
    this.contenttype = contenttype;
    this.textUser = textUser;
    this.textModelThinking = textModelThinking;
    this.textModel = textModel;
    this.toolCall = toolCall;
    this.toolResult = toolResult;
    this.embedding = embedding;
  }

  /**
   * Factoría para rehidratar un Turno desde la base de datos o almacenamiento.
   */
  /*friend*/ static TurnImpl from(int id, LocalDateTime timestamp, String contenttype,
          String textUser, String textModelThinking, String textModel,
          String toolCall, String toolResult, float[] embedding) {
    return new TurnImpl(id, timestamp, contenttype, textUser, textModelThinking,
            textModel, toolCall, toolResult, embedding);
  }

  /**
   * Factoría para crear un NUEVO Turno durante la ejecución.
   */
  /*friend*/ static TurnImpl from(LocalDateTime timestamp, String contenttype,
          String textUser, String textModelThinking, String textModel,
          String toolCall, String toolResult, float[] embedding) {
    return new TurnImpl(-1, timestamp, contenttype, textUser, textModelThinking,
            textModel, toolCall, toolResult, embedding);
  }

  /**
   * Genera una línea CSV formateada y escapada para el protocolo de
   * compactación.
   */
  @Override
  public String toCSVLine() {
    // FIXME: probablemente habria que comprobar si es un turno de tipo TYPE_MEMORY y ver de generar varias lineas con los turnos recuperados de la toolResult
    return Stream.of(
            StringUtils.trim(String.valueOf(id)),
            StringUtils.trim(String.valueOf(timestamp)),
            contenttype,
            textUser,
            textModelThinking,
            textModel,
            toolCall,
            toolResult
    ).map(this::escapeCsv).collect(Collectors.joining(",")).replace("\n", "\\n");
  }

  /**
   * Devuelve el texto concatenado que representa el contenido semántico del
   * turno. Útil para que el SourceOfTruth calcule el embedding sobre esto.
   */
  @Override
  public String getContentForEmbedding() {
    return Stream.of(textUser, textModelThinking, textModel, toolCall, toolResult)
            .filter(Objects::nonNull)
            .filter(s -> !s.isBlank())
            .collect(Collectors.joining(" "));
  }

  private String escapeCsv(String val) {
    if (val == null) {
      return "";
    }
    // Escapar comillas dobles duplicándolas y rodear el campo con comillas
    String escaped = val.replace("\"", "\"\"");
    return "\"" + escaped + "\"";
  }

  @Override
  public int getId() {
    return id;
  }

  /*friend*/ void setId(int id) {
    if (this.id >= 0) {
      throw new IllegalStateException();
    }
    this.id = id;
  }

  @Override
  public LocalDateTime getTimestamp() {
    return timestamp;
  }

  @Override
  public String getContenttype() {
    return contenttype;
  }

  @Override
  public String getTextUser() {
    return textUser;
  }

  @Override
  public String getTextModelThinking() {
    return textModelThinking;
  }

  @Override
  public String getTextModel() {
    return textModel;
  }

  @Override
  public String getToolCall() {
    return toolCall;
  }

  @Override
  public String getToolResult() {
    return toolResult;
  }

  @Override
  public float[] getEmbedding() {
    return embedding;
  }

  @Override
  public String toString() {
    return "Turn{id=" + id + ", type='" + contenttype + "'}";
  }
}
