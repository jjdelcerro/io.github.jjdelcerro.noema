package io.github.jjdelcerro.chatagent.lib.impl.services.scheduler;

import com.google.gson.Gson;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.AgentServiceFactory;
import io.github.jjdelcerro.chatagent.lib.AgentTool;
import io.github.jjdelcerro.chatagent.lib.SchedulerService;
import io.github.jjdelcerro.chatagent.lib.impl.persistence.Counter;
import io.github.jjdelcerro.chatagent.lib.impl.services.scheduler.tools.ScheduleAlarmTool;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Servicio de planificacion de alarmas persistente.
 *
 * @author jjdelcerro
 */
public class SchedulerServiceImpl implements SchedulerService {

  private final Agent agent;
  private final Gson gson = new Gson();
  private ScheduledExecutorService scheduler;
  private Counter counter;
  private ScheduledFuture<?> currentScheduledTask;
  private boolean running;
  private final AgentServiceFactory factory;

  public SchedulerServiceImpl(AgentServiceFactory factory, Agent agent) {
    this.factory = factory;
    this.agent = agent;
    this.running = false;
  }

  @Override
  public AgentServiceFactory getFactory() {
    return factory;
  }

  @Override
  public void start() {
    this.scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().factory()
    );
    try {
      Connection conn = this.agent.getServicesDatabase();
      this.createTables(conn);
      this.counter = Counter.from(conn, "SCHEDULER");
      rescheduleNextAlarm();
      this.running = true;
    } catch (SQLException ex) {
      agent.getConsole().printerrorln("Error al iniciar SchedulerService: " + ex.getMessage());
    }
  }

  private void createTables(Connection conn) throws SQLException {
    try (Statement stmt = conn.createStatement()) {
      stmt.execute("""
                CREATE TABLE IF NOT EXISTS SCHEDULER (
                    id VARCHAR(255) PRIMARY KEY,
                    timestamp TIMESTAMP,
                    alarm_time TIMESTAMP,
                    reason VARCHAR(1024)
                )
            """);
    }
  }

  @Override
  public String schedule(LocalDateTime when, String reason) {
    String alarmId = "ALARM-" + counter.get();
    try (Connection conn = this.agent.getServicesDatabase(); PreparedStatement pstmt = conn.prepareStatement("INSERT INTO SCHEDULER (id, timestamp, alarm_time, reason) VALUES (?, ?, ?, ?)")) {
      pstmt.setString(1, alarmId);
      pstmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
      pstmt.setTimestamp(3, Timestamp.valueOf(when));
      pstmt.setString(4, reason);
      pstmt.executeUpdate();

      rescheduleNextAlarm();

    } catch (SQLException ex) {
      agent.getConsole().printerrorln("Error al guardar alarma en la BBDD: " + ex.getMessage());
      return "{\"status\": \"error\", \"message\": \"Error al guardar alarma\"}";
    }
    return alarmId;
  }

  private void sendEvent(LocalDateTime when, String reason) {
    String notify = this.gson.toJson(Map.of(
            "alarm_time", when.toString(),
            "reason", reason
    ));
    this.agent.putEvent("scheduler", "normal", notify);
  }

  private void schedule_alarm(String id, String reason, LocalDateTime alarmTime) {
    long delay = Duration.between(LocalDateTime.now(), alarmTime).toMillis();

    if (delay < 0) {
      delay = 0; // Si es en el pasado, ejecutar ahora.
    }

    currentScheduledTask = scheduler.schedule(() -> {
      sendEvent(alarmTime, reason);
      removeAlarm(id);
      rescheduleNextAlarm();
    }, delay, TimeUnit.MILLISECONDS);
  }

  private void removeAlarm(String id) {
    try (Connection conn = this.agent.getServicesDatabase(); PreparedStatement pstmt = conn.prepareStatement("DELETE FROM SCHEDULER WHERE id = ?")) {
      pstmt.setString(1, id);
      pstmt.executeUpdate();
    } catch (SQLException ex) {
      agent.getConsole().printerrorln("Error al eliminar alarma " + id + ": " + ex.getMessage());
    }
  }

  private void rescheduleNextAlarm() {
    if (currentScheduledTask != null && !currentScheduledTask.isDone()) {
      currentScheduledTask.cancel(false);
    }

    try (Connection conn = this.agent.getServicesDatabase(); PreparedStatement pstmt = conn.prepareStatement("SELECT id, reason, alarm_time FROM SCHEDULER WHERE alarm_time > ? ORDER BY alarm_time ASC LIMIT 1")) {

      pstmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
      try (ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          String id = rs.getString("id");
          String reason = rs.getString("reason");
          LocalDateTime alarmTime = rs.getTimestamp("alarm_time").toLocalDateTime();
          schedule_alarm(id, reason, alarmTime);
        }
      }
    } catch (SQLException ex) {
      agent.getConsole().printerrorln("Error al reprogramar la siguiente alarma: " + ex.getMessage());
    }
  }

  @Override
  public String getName() {
    return SchedulerService.NAME;
  }

  @Override
  public boolean isRunning() {
    return this.running;
  }

  @Override
  public boolean canStart() {
    return this.factory.canStart(agent.getSettings());
  }

  @Override
  public Agent.ModelParameters getModelParameters(String name) {
    return null;
  }

  @Override
  public List<AgentTool> getTools() {
    AgentTool[] tools = new AgentTool[]{
      new ScheduleAlarmTool(this.agent)
    };
    return Arrays.asList(tools);
  }

}
