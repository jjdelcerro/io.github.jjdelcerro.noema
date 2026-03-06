package io.github.jjdelcerro.noema.lib.impl.services.sensors.persistence;

import com.google.gson.*;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.SensorInformationImpl;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorInformation;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorNature;
import java.lang.reflect.Type;

/**
 * Adaptador GSON bidireccional para SensorInformation. Serializa y deserializa
 * la identidad y capacidades de los sensores.
 */
public class SensorInformationGsonAdapter implements JsonSerializer<SensorInformation>, JsonDeserializer<SensorInformation> {

  @Override
  public JsonElement serialize(SensorInformation src, Type typeOfSrc, JsonSerializationContext context) {
    JsonObject obj = new JsonObject();
    obj.addProperty("channel", src.getChannel());
    obj.addProperty("label", src.getLabel());
    obj.addProperty("description", src.getDescription());
    obj.addProperty("silenceable", src.isSilenceable()); // Guardamos el estado de si es silenciable

    if (src.getNature() != null) {
      obj.addProperty("nature", src.getNature().name());
    }
    return obj;
  }

  @Override
  public SensorInformation deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
    JsonObject obj = json.getAsJsonObject();

    String channel = obj.get("channel").getAsString();
    String label = obj.get("label").getAsString();
    String description = obj.get("description").getAsString();
    SensorNature nature = SensorNature.valueOf(obj.get("nature").getAsString());

    // Recuperamos el flag 'silenceable', con un valor por defecto seguro (true)
    boolean silenceable = true;
    if (obj.has("silenceable")) {
      silenceable = obj.get("silenceable").getAsBoolean();
    }

    // Llamamos al constructor de la clase concreta
    return new SensorInformationImpl(channel, label, nature, description, silenceable);
  }
}
