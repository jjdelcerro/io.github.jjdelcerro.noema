package io.github.jjdelcerro.chatagent.lib.impl;

import io.github.jjdelcerro.chatagent.lib.SchedulerService;
import com.google.gson.Gson;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.impl.persistence.Counter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * TODO: Falta por implementar Probablemente añadir al Agent una BBDD de
 * servicio para mantener cosas como esta separadas de la BBDD de conocimiento,
 * y dejar en el Agent un metodo "Connection getServicesDatabase()". Tambien
 * añadir al agent un metodo getSchedulerService().
 *
 * @author jjdelcerro
 */
public class SchedulerServiceImpl implements SchedulerService {

  private final Agent agent;
  private final Gson gson = new Gson();
  private final ScheduledExecutorService scheduler;
  private Counter counter;

  public SchedulerServiceImpl(Agent agent) {
    this.agent = agent;
    this.scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().factory()
    );
  }

  @Override
  public void start() {
    try {
      Connection conn = this.agent.getServicesDatabase();
      this.createTables(conn);
      this.counter = Counter.from(conn, "SCHEDULER");
      // TODO: recuperar la primera alarma mas cercana en el tiempo de la BBDD que no se ha disparado y llama a schedule_alarm.
    } catch (SQLException ex) {
      ex.printStackTrace(); // FIXME: log
    }
  }

  private void createTables(Connection conn) throws SQLException {
    try (Statement stmt = conn.createStatement()) {
      stmt.execute("""
                CREATE TABLE IF NOT EXISTS SCHEDULER (
                    id INT PRIMARY KEY,
                    timestamp TIMESTAMP,
                    alarm_time TIMESTAMP,                                                                          
                    reason VARCHAR(1024)
                )
            """);
    }
  }

  @Override
  public void schedule(LocalDateTime when, String reason) {
    // TODO: guardar en la BBDD, recuperar la primera alarma de la BBDD y llama a schedule_alarm.
  }

  private void sendEvent(LocalDateTime when, String reason) {
    String notify = this.gson.toJson(Map.of(
            "alarm_time", when.toString(),
            "reason", reason
    )
    );
    this.agent.putEvent("scheduler", "normal", notify);
  }

  private void schedule_alarm(String id, String reason, LocalDateTime alarmTime) {
    long delay = Duration.between(LocalDateTime.now(), alarmTime).toMillis();

    if (delay <= 0) {
      // FIXME: tal vez lanzar la alarma y eliminarla de la BBDD.
      agent.getConsole().printerrorln("Intento de programar alarma en el pasado: " + alarmTime);
      return;
    }

    // Programar la ejecución
    scheduler.schedule(() -> {
      sendEvent(alarmTime, reason);
      removeAlarm(id);
    }, delay, TimeUnit.MILLISECONDS);
  }

  private void removeAlarm(String id) {

  }
}
