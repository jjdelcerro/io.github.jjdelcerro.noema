package io.github.jjdelcerro.noema.lib.impl.services.sensors.nature.discrete;

import io.github.jjdelcerro.noema.lib.impl.services.sensors.AbstractSensorEvent;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorEventDiscrete;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorInformation;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService;
import java.time.LocalDateTime;

public class SensorEventDiscreteImpl extends AbstractSensorEvent implements SensorEventDiscrete {

    public SensorEventDiscreteImpl(SensorInformation sensor, String text, String priority, String status, LocalDateTime timestamp, SensorsService.SensorEventCallback callback) {
        super(sensor, text, priority, status, timestamp, callback);
    }

    @Override
    public String getContents() {
        return this.getText();
    }
}
