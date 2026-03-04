package io.github.jjdelcerro.noema.lib.impl.services.sensors.nature.aggregate;

import io.github.jjdelcerro.noema.lib.impl.services.sensors.AbstractSensorData;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorEvent;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorInformation;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorStatistics;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService.SensorEventCallback;
import java.time.LocalDateTime;

public class AggregateSensorData extends AbstractSensorData {

    public AggregateSensorData(SensorInformation info, SensorStatistics stats) {
      super(info, stats);
    }
    
    public SensorEventAggregateImpl getCurrentEvent() {
      return (SensorEventAggregateImpl) this.getCurrentEvent();
    }
    
    @Override
    public SensorEventAggregateImpl createSensorEvent(String text, String priority, String status, LocalDateTime timestamp, SensorEventCallback callback) {
      return new SensorEventAggregateImpl(info, text, priority, status, timestamp, callback);
    }

    @Override
    public void process(String text, String priority, String status, LocalDateTime timestamp, SensorEventCallback callback) {
        if (this.getCurrentEvent() == null) {
            this.setCurrentEvent(this.createSensorEvent(text, priority, status, timestamp, callback));
        } else {
            this.getCurrentEvent().increment();
            this.getCurrentEvent().updateEndTimestamp(timestamp);
        }
    }

}
