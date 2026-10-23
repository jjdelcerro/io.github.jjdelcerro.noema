package io.github.jjdelcerro.noema.ui.webclient.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.ui.webclient.state.UIState;
import io.javalin.http.Context;
import java.util.Map;

/**
 * Recibe el estado parcial de la UI (push desde el cliente).
 *
 * <p>El payload agrupa los componentes por nombre, de modo que cada uno viaja
 * por su cuenta y solo se aplica el que venga:</p>
 * <pre>
 * {
 *   "terminalId": "default",
 *   "components": {
 *     "editor": {
 *       "openFiles": [ { "path": "/ruta/A.java", "dirty": true } ],
 *       "activeFile": "/ruta/A.java",
 *       "cursorLine": 44,
 *       "selectionStartLine": 44,
 *       "selectionEndLine": 57,
 *       "selectionText": "texto"
 *     },
 *     "notes": {
 *       "title": "Guia rapida",
 *       "path": "/ruta/notas/20260101-120000.xml",
 *       "cursorLine": 0,
 *       "selectionStartLine": null,
 *       "selectionEndLine": null
 *     }
 *   }
 * }
 * </pre>
 *
 * <p>Un componente ausente conserva su valor anterior; un componente presente
 * se sustituye entero. {@code notes: null} significa que no hay ninguna nota
 * abierta.</p>
 *
 * <p>Las lineas llegan 0-based (Ace) y se almacenan 1-based: la conversion se
 * hace aqui, en el unico borde de entrada, para que el modelo y las
 * herramientas hablen siempre en el mismo sistema que el gutter.</p>
 */
@SuppressWarnings("UseSpecificCatch")
public class HandlerPostUIState extends AbstractHandler {

  private static final Gson GSON = new Gson();

  public HandlerPostUIState(Agent agent) {
    super(agent);
  }

  @Override
  public void handle(Context ctx) throws Exception {
    JsonObject body;
    try {
      body = JsonParser.parseString(ctx.body()).getAsJsonObject();
    } catch (Exception e) {
      ctx.status(400).json(Map.of("error", "JSON invalido en el cuerpo de la peticion"));
      return;
    }

    String terminalId = jsonStringOrDefault(body, "terminalId", "default");

    UIState state = this.getWebServer().getUIStates().computeIfAbsent(terminalId, id -> {
      UIState s = new UIState();
      s.setSubchannel(id);
      return s;
    });

    JsonElement componentsElement = body.get("components");
    if (componentsElement != null && componentsElement.isJsonObject()) {
      applyComponents(componentsElement.getAsJsonObject(), state);
    }

    ctx.json(Map.of());
  }

  /**
   * Aplica los componentes presentes en el payload. Los ausentes se dejan
   * como estaban.
   *
   * @param components objeto JSON con los componentes
   * @param state estado destino
   */
  private static void applyComponents(JsonObject components, UIState state) {
    if (components.has("editor")) {
      UIState.Editor editor = readComponent(components.get("editor"), UIState.Editor.class);
      if (editor == null) {
        editor = new UIState.Editor();
      }
      editor.setCursorLine(toOneBased(editor.getCursorLine()));
      editor.setSelectionStartLine(toOneBased(editor.getSelectionStartLine()));
      editor.setSelectionEndLine(toOneBased(editor.getSelectionEndLine()));
      state.setEditor(editor);
    }
    if (components.has("notes")) {
      UIState.Notes notes = readComponent(components.get("notes"), UIState.Notes.class);
      if (notes != null) {
        notes.setCursorLine(toOneBased(notes.getCursorLine()));
        notes.setSelectionStartLine(toOneBased(notes.getSelectionStartLine()));
        notes.setSelectionEndLine(toOneBased(notes.getSelectionEndLine()));
      }
      state.setNotes(notes);
    }
  }

  /**
   * Deserializa un componente. Devuelve null si el valor es JSON null.
   *
   * @param <T> tipo del componente
   * @param element valor JSON
   * @param type clase destino
   * @return instancia o null
   */
  private static <T> T readComponent(JsonElement element, Class<T> type) {
    if (element == null || element.isJsonNull()) {
      return null;
    }
    return GSON.fromJson(element, type);
  }

  /**
   * Convierte un numero de linea 0-based de Ace a 1-based. Null se queda null.
   *
   * @param line numero de linea de Ace
   * @return numero de linea 1-based
   */
  private static Integer toOneBased(Integer line) {
    return (line == null) ? null : (line + 1);
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

}
