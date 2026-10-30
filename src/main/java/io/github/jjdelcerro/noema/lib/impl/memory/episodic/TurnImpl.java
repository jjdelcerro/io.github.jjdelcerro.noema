package io.github.jjdelcerro.noema.lib.impl.memory.episodic;

import io.github.jjdelcerro.noema.lib.impl.services.memory.tools.AnnotateObservationTool;
import io.github.jjdelcerro.noema.lib.api.memory.episodic.Turn;

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
  
  public String toXML() {  
    String xml = String.format(
"""
    <turn id="%s" timestamp="%s" contenttype="%s" subchannel="%s">
      <textUser>CDATA[[%s]]</textUser>
      <textModelThinking>CDATA[[%s]]</textModelThinking>
      <textModel>CDATA[[%s]]</textModel>
      <toolCall>CDATA[[%s]]</toolCall>
      <toolResult>CDATA[[%s]]</toolResult>
    </turn>
""",
            StringUtils.trim(String.valueOf(id)),
            StringUtils.trim(String.valueOf(timestamp)),
            contenttype,
            subchannel,
            textUser,
            getCDataSafe(textModelThinking),
            getCDataSafe(textModel),
            getCDataSafe(toolCall),
            getCDataSafe(toolResult)
    );
    return xml;
  }
  private String getCDataSafe(String text)  { // FIXME: mover getCDataSafe a un modulo reutilizable
    if (StringUtils.isBlank(text) ) {
      return "";
    }
    String sanitized = sanitizeXmlText(text);
    if (sanitized.contains("]]>")) {
      // Split into safe chunks
      String safe = sanitized.replace("]]>", "]]]]><![CDATA[>");
      return safe;
    } else {
      return sanitized;
    }
  }

  private String sanitizeXmlText(String text) {  // FIXME: mover sanitizeXmlText a un modulo reutilizable
    if (text == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder(text.length());
    int i = 0;
    while (i < text.length()) {
      int cp = text.codePointAt(i);
      boolean legal = (cp == 0x9 || cp == 0xA || cp == 0xD)
              || (cp >= 0x20 && cp <= 0xD7FF)
              || (cp >= 0xE000 && cp <= 0xFFFD)
              || (cp >= 0x10000 && cp <= 0x10FFFF);
      if (legal) {
        sb.appendCodePoint(cp);
      } else {
        sb.append('\uFFFD');
      }
      i += Character.charCount(cp);
    }
    return sb.toString();
  }

  @Override
  public String getCSVHeader() {
    return "code,timestamp,contenttype,subchannel,text_user,text_model_thinking,text_model,tool_call,tool_result";
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

  /**
   * Copia el turno sustituyendo el toolCall. Se conserva el identificador y
   * el annotationType del original: la sustitucion del contenido de una nota
   * no altera su classification.
   */
  @Override
  public Turn withToolCall(String toolCall) {
    return new TurnImpl(this.id, this.timestamp, this.contenttype, this.subchannel,
            this.textUser, this.textModelThinking, this.textModel, toolCall,
            this.toolResult, this.annotationType, this.embedding);
  }

  /**
   * Copia el turno sustituyendo el toolResult. Se conserva el identificador y
   * el resto de campos del original.
   */
  @Override
  public Turn withToolResult(String toolResult) {
    return new TurnImpl(this.id, this.timestamp, this.contenttype, this.subchannel,
            this.textUser, this.textModelThinking, this.textModel, this.toolCall,
            toolResult, this.annotationType, this.embedding);
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
