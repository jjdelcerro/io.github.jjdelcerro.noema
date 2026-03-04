package io.github.jjdelcerro.noema.lib.impl.services.sensors.persistence;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.AbstractSensorEvent;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.SensorsServiceImpl;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.nature.aggregate.SensorEventAggregateImpl;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.nature.discrete.SensorEventDiscreteImpl;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.nature.mergeable.SensorEventMergeableImpl;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.nature.state.SensorEventStateImpl;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorEvent;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorEventAggregate;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorInformation;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorNature;
import java.lang.reflect.Type;
import java.sql.Timestamp;
import java.time.LocalDateTime;

public class SensorEventGsonAdapter implements JsonSerializer<SensorEvent>, JsonDeserializer<SensorEvent> {
    private final SensorsServiceImpl service;

    public SensorEventGsonAdapter(SensorsServiceImpl service) {
        this.service = service;
    }
    
    @Override
    public JsonElement serialize(SensorEvent src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject obj = new JsonObject();
        obj.addProperty("channel", src.getChannel());
        obj.addProperty("priority", src.getPriority());
        obj.addProperty("status", src.getStatus());
        obj.addProperty("contents", src.getContents());
        obj.addProperty("startTimestamp",  Timestamp.valueOf(src.getStartTimestamp()).toString());
        obj.addProperty("endTimestamp", Timestamp.valueOf(src.getEndTimestamp()).toString());
        
        // Discriminador para la deserialización
        SensorNature nature = ((AbstractSensorEvent)src).getSensor().getNature();
        obj.addProperty("nature", nature.name());

        if (src instanceof SensorEventAggregate) {
            obj.addProperty("count", ((SensorEventAggregate) src).getCount());
        }
        return obj;
    }

    @Override
    public SensorEvent deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();
        String natureStr = obj.get("nature").getAsString();
        SensorNature nature = SensorNature.valueOf(natureStr);

        // Rehidratamos los datos comunes
        String channel = obj.get("channel").getAsString();
        String priority = obj.get("priority").getAsString();
        String status = obj.get("status").getAsString();
        String contents = obj.get("contents").getAsString();
        LocalDateTime start = Timestamp.valueOf(obj.get("startTimestamp").getAsString()).toLocalDateTime();
        LocalDateTime end = Timestamp.valueOf(obj.get("endTimestamp").getAsString()).toLocalDateTime();

        SensorInformation info = service.getOrPlaceholderInfo(channel, nature);

        // Instanciamos según la naturaleza
        // Nota: El 'text' original ya no es necesario porque el evento está "sellado" con sus 'contents'
        switch (nature) {
            case DISCRETE:
                return new SensorEventDiscreteImpl(info, contents, priority, status, start, null);
            case AGGREGATABLE:
                long count = obj.get("count").getAsLong();
                return new SensorEventAggregateImpl(info, contents, priority, status, start, null, count);
            case MERGEABLE:
                return new SensorEventMergeableImpl(info, contents, priority, status, start, null, end);
            case STATE:
                return new SensorEventStateImpl(info, contents, priority, status, start, null);
            default:
                throw new JsonParseException("Naturaleza desconocida: " + natureStr);
        }
    }
}
