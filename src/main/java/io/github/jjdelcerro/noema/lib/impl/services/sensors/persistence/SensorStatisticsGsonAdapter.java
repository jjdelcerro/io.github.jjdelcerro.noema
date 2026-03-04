package io.github.jjdelcerro.noema.lib.impl.services.sensors.persistence;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.SensorStatisticsImpl;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorStatistics;
import java.lang.reflect.Type;
import java.sql.Timestamp;

public class SensorStatisticsGsonAdapter implements JsonSerializer<SensorStatistics>, JsonDeserializer<SensorStatistics> {

  @Override
  public JsonElement serialize(SensorStatistics src, Type typeOfSrc, JsonSerializationContext context) {
    JsonObject obj = new JsonObject();
    obj.addProperty("total_active", src.getTotalEventsActive());
    obj.addProperty("total_silenced", src.getTotalEventsSilenced());
    obj.addProperty("last_event",  Timestamp.valueOf(src.getLastEventTimestamp()).toString());
    obj.addProperty("last_delivery",  Timestamp.valueOf(src.getLastDeliveryTimestamp()).toString());
    obj.addProperty("is_silenced", src.isSilenced());
    return obj;
  }

  @Override
  public SensorStatistics deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
    JsonObject obj = json.getAsJsonObject();
    SensorStatisticsImpl stats = new SensorStatisticsImpl();

    // Usamos reflexión o métodos setter para rehidratar el objeto interno
    stats.rehydrate(
            obj.get("total_active").getAsLong(),
            obj.get("total_silenced").getAsLong(),
            Timestamp.valueOf(obj.get("last_event").getAsString()).toLocalDateTime(),
            Timestamp.valueOf(obj.get("last_delivery").getAsString()).toLocalDateTime(),
            obj.get("is_silenced").getAsBoolean()
    );
    return stats;
  }
}
