package io.github.jjdelcerro.noema.lib.impl.settings;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.github.jjdelcerro.noema.lib.settings.AgentSettingsCheckedList;
import io.github.jjdelcerro.noema.lib.settings.AgentSettingsGroup;
import io.github.jjdelcerro.noema.lib.settings.AgentSettingsItem;
import io.github.jjdelcerro.noema.lib.settings.AgentSettingsPaths;
import io.github.jjdelcerro.noema.lib.settings.AgentSettingsString;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deserializador personalizado para reconstruir la jerarquía de
 * AgentSettingsItem. Decide el tipo de nodo basándose en la estructura del
 * elemento JSON.
 */
public class AgentSettingsItemDeserializer
        implements JsonDeserializer<AgentSettingsItem>, JsonSerializer<AgentSettingsItem> {

  @Override
  public JsonElement serialize(AgentSettingsItem src, Type typeOfSrc, JsonSerializationContext context) {
    if (src instanceof AgentSettingsString s) {
      return new com.google.gson.JsonPrimitive(s.getValue()); // Devuelve "valor" en lugar de {"value":"valor"}
    }
    if (src instanceof AgentSettingsPaths p) {
      // Devuelve ["ruta1", "ruta2"]
      return context.serialize(p.getValues().stream().map(Object::toString).toList());
    }
    if (src instanceof AgentSettingsCheckedList cl) {
      return context.serialize(((AgentSettingsCheckedListImpl) cl).getInternalItems());
    }
    if (src instanceof AgentSettingsGroup g) {
      JsonObject obj = new JsonObject();
      Map<String, AgentSettingsItem> map = ((AgentSettingsGroupImpl) g).getItems();
      for (Map.Entry<String, AgentSettingsItem> entry : map.entrySet()) {
        obj.add(entry.getKey(), context.serialize(entry.getValue()));
      }
      return obj;
    }
    return null;
  }

  @Override
  public AgentSettingsItem deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {

    // 1. Si es un valor simple (Primitivo) -> AgentSettingsString
    if (json.isJsonPrimitive()) {
      return new AgentSettingsStringImpl(json.getAsString());
    }

    // 2. Si es una lista (Array) -> Puede ser AgentSettingsPaths o AgentSettingsCheckedList
    if (json.isJsonArray()) {
      JsonArray array = json.getAsJsonArray();

      // Lógica del Anexo I: Detectar si es una lista de elementos marcables
      if (isLikelyCheckedList(array)) {
        List<AgentSettingsCheckedListImpl.CheckedItemImpl> items = new ArrayList<>();
        for (JsonElement element : array) {
          // Delegamos a Gson para deserializar el objeto interno {checked, value}
          items.add(context.deserialize(element, AgentSettingsCheckedListImpl.CheckedItemImpl.class));
        }
        return new AgentSettingsCheckedListImpl(items);
      }

      // Por defecto, tratamos los arrays como listas de Strings (Rutas)
      List<String> paths = new ArrayList<>();
      for (JsonElement element : array) {
        if (element.isJsonPrimitive()) {
          paths.add(element.getAsString());
        }
      }
      return new AgentSettingsPathsImpl(paths);
    }

    // 3. Si es un objeto -> AgentSettingsGroup (Recursivo)
    if (json.isJsonObject()) {
      AgentSettingsGroupImpl group = new AgentSettingsGroupImpl();
      JsonObject obj = json.getAsJsonObject();

      for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
        // Llamada recursiva para procesar cada hijo del grupo
        AgentSettingsItem child = context.deserialize(entry.getValue(), AgentSettingsItem.class);
        if (child != null) {
          group.getItems().put(entry.getKey(), child);
        }
      }
      return group;
    }

    throw new JsonParseException("Tipo de JSON no soportado para AgentSettingsItem: " + json.getClass().getName());
  }

  /**
   * Determina si un array JSON representa una CheckedList. Criterio: El primer
   * elemento es un objeto que contiene la propiedad "checked".
   */
  private boolean isLikelyCheckedList(JsonArray array) {
    if (array.size() == 0) {
      return false;
    }
    JsonElement first = array.get(0);
    if (first.isJsonObject()) {
      JsonObject obj = first.getAsJsonObject();
      return obj.has("checked");
    }
    return false;
  }
}
