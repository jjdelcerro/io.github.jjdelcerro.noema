package io.github.jjdelcerro.noema.lib.impl.memory.episodic;

import io.github.jjdelcerro.noema.lib.impl.services.memory.tools.AnnotateObservationTool;
import io.github.jjdelcerro.noema.lib.memory.episodic.Turn;

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
  private final String subchannel;
  private final float[] embedding;

  private final String annotationType;

  private TurnImpl(int id, LocalDateTime timestamp, String contenttype, String subchannel,
          String textUser, String textModelThinking, String textModel, String toolCall,
          String toolResult, String annotationType, float[] embedding) {
    this.id = id;
    this.timestamp = timestamp;
    this.contenttype = contenttype;
    this.textUser = textUser;
    this.textModelThinking = textModelThinking;
    this.textModel = textModel;
    this.toolCall = toolCall;
    this.toolResult = toolResult;
    this.annotationType = annotationType;
    this.embedding = embedding;
    this.subchannel = subchannel;
  }

  /**
   * Factoria para rehidratar un Turno desde la base de datos o almacenamiento.
   */
  /*friend*/ static TurnImpl from(int id, LocalDateTime timestamp, String contenttype, String subchannel,
          String textUser, String textModelThinking, String textModel,
          String toolCall, String toolResult, String annotationType, float[] embedding) {
    return new TurnImpl(id, timestamp, contenttype, subchannel, textUser, textModelThinking,
            textModel, toolCall, toolResult, annotationType, embedding);
  }

  /**
   * Factoria para crear un NUEVO Turno durante la ejecucion. 
   * 
   * Si contenttype es "annotation", extrae automaticamente el annotationType desde toolCall.
   */
  /*friend*/ static TurnImpl from(LocalDateTime timestamp, String contenttype, String subchannel,
          String textUser, String textModelThinking, String textModel,
          String toolCall, String toolResult, float[] embedding) {
    String annotationType = null;
    if ( StringUtils.equalsIgnoreCase("annotation",contenttype) ) {
      annotationType = AnnotateObservationTool.getAnnotationTypeFromToolCall(toolCall);
    }
    return new TurnImpl(-1, timestamp, contenttype, subchannel, textUser, textModelThinking,
            textModel, toolCall, toolResult, annotationType, embedding);
  }

  @Override
  public String getAnnotationType() {
    return annotationType;
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
            subchannel,
            textUser,
            textModelThinking,
            textModel,
            toolCall,
            toolResult
    ).map(this::escapeCsv).collect(Collectors.joining(",")).replace("\n", "\\n");
  }

  /**
   * Devuelve el texto concatenado que representa el contenido semántico del
   * turno. Útil para que el EpisodicMemory calcule el embedding sobre esto.
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
  public String getSubchannel() {
    return subchannel;
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
