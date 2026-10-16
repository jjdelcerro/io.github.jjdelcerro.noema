package io.github.jjdelcerro.noema.ui.webclient.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.ui.webclient.workspace.editor.EditorState;
import io.javalin.http.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author jjdelcerro
 */
@SuppressWarnings("UseSpecificCatch")
public class HandlerPostEditorState extends AbstractHandler {

  public HandlerPostEditorState(Agent agent) {
    super(agent);
  }

  @Override
  /**
   * Recibe el estado del editor (push desde el cliente). El estado se mantiene
   * por terminalId en una unica instancia que se va mutando en cada llamada:
   * los campos presentes en el JSON se aplican, los ausentes quedan a null (o
   * lista vacia en el caso de openFiles).
   *
   * Contrato del payload (lo genera editor-state.js en el cliente):
   * <pre>
   * {
   *  "terminalId": "default",
   *  "openFiles": [
   *      {
   *        "path": "/ruta/absoluta/A.java",
   *        "dirty": true
   *      },
   *      ...
   *  ],
   *  "activeFile": "/ruta/absoluta/A.java",
   *  "cursorLine": 45,
   *  "selectionStartLine": 45,
   *  "selectionEndLine": 58,
   *  "selectionText": "texto seleccionado"
   * }
   * </pre> Devuelve 200 {} siempre que el JSON sea valido.
   */
  public void handle(Context ctx) throws Exception {
    JsonObject body;
    try {
      body = JsonParser.parseString(ctx.body()).getAsJsonObject();
    } catch (Exception e) {
      ctx.status(400).json(Map.of("error", "JSON invalido en el cuerpo de la peticion"));
      return;
    }

    String terminalId = jsonStringOrDefault(body, "terminalId", "default");

    EditorState state = this.getWebServer().getEditorStates().computeIfAbsent(terminalId, id -> {
      EditorState s = new EditorState();
      s.setSubchannel(id);
      return s;
    });

    // Actualizacion completa: la instancia se reutiliza, todos los campos
    // se sustituyen por lo que venga (o por null si no viene).
    state.setActiveFile(jsonStringOrNull(body, "activeFile"));
    state.setCursorLine(jsonIntegerOrNull(body, "cursorLine"));
    state.setSelectionStartLine(jsonIntegerOrNull(body, "selectionStartLine"));
    state.setSelectionEndLine(jsonIntegerOrNull(body, "selectionEndLine"));
    state.setSelectionText(jsonStringOrNull(body, "selectionText"));
    state.setOpenFiles(parseOpenFiles(body));

    ctx.json(Map.of());
  }

  /**
   * Construye la lista de ficheros abiertos a partir del JSON. Devuelve lista
   * vacia si el campo no existe, no es un array, o el array esta vacio.
   */
  private static List<EditorState.OpenFile> parseOpenFiles(JsonObject body) {
    List<EditorState.OpenFile> openFiles = new ArrayList<>();
    JsonElement arr = body.get("openFiles");
    if (arr == null || !arr.isJsonArray()) {
      return openFiles;
    }
    for (JsonElement el : arr.getAsJsonArray()) {
      if (!el.isJsonObject()) {
        continue;
      }
      JsonObject o = el.getAsJsonObject();
      EditorState.OpenFile of = new EditorState.OpenFile();
      of.setPath(jsonStringOrNull(o, "path"));
      JsonElement dirty = o.get("dirty");
      of.setDirty(dirty != null && !dirty.isJsonNull() && dirty.getAsBoolean());
      openFiles.add(of);
    }
    return openFiles;
  }

  /**
   * Devuelve el valor String de una clave del JsonObject, o null si el valor
   * esta ausente o es JSON null.
   */
  private static String jsonStringOrNull(JsonObject obj, String key) {
    JsonElement el = obj.get(key);
    return (el == null || el.isJsonNull()) ? null : el.getAsString();
  }

  /**
   * Devuelve el valor String de una clave del JsonObject, o el valor por
   * defecto si el valor esta ausente, es JSON null o esta en blanco.
   */
  private static String jsonStringOrDefault(JsonObject obj, String key, String defaultValue) {
    String value = jsonStringOrNull(obj, key);
    return (value == null || value.isBlank()) ? defaultValue : value;
  }

  /**
   * Devuelve el valor Integer de una clave del JsonObject, o null si el valor
   * esta ausente o es JSON null.
   */
  private static Integer jsonIntegerOrNull(JsonObject obj, String key) {
    JsonElement el = obj.get(key);
    return (el == null || el.isJsonNull()) ? null : el.getAsInt();
  }

}
