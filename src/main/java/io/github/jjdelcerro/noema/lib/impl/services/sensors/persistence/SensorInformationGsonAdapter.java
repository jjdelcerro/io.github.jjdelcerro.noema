package io.github.jjdelcerro.noema.lib.impl.services.sensors.persistence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorInformation;
import java.lang.reflect.Type;

/**
 * Adaptador GSON para SensorInformation. Expone la identidad y capacidades del
 * canal sensorial al agente.
 */
public class SensorInformationGsonAdapter implements JsonSerializer<SensorInformation> {

  @Override
  public JsonElement serialize(SensorInformation src, Type typeOfSrc, JsonSerializationContext context) {
    JsonObject obj = new JsonObject();

    obj.addProperty("channel", src.getChannel());
    obj.addProperty("label", src.getLabel());
    obj.addProperty("description", src.getDescription());

    // Exponemos la naturaleza como un string legible para el LLM
    if (src.getNature() != null) {
      obj.addProperty("nature", src.getNature().name());
    }

    return obj;
  }
}
