package io.github.jjdelcerro.noema.lib.impl.memory.projected.operations;

import com.google.gson.JsonObject;
import dev.langchain4j.data.message.ChatMessage;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.impl.DateUtils;
import io.github.jjdelcerro.noema.lib.memory.episodic.EpisodicMemory;
import io.github.jjdelcerro.noema.lib.memory.episodic.EpisodicMemory.SubchannelActivity;
import io.github.jjdelcerro.noema.lib.memory.projected.ProjectedMemory;
import io.github.jjdelcerro.noema.lib.memory.projected.ProjectedMemoryOperation;
import org.apache.commons.lang3.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import io.github.jjdelcerro.noema.lib.memory.consolidate.ConsolidateMemory;

public class PeripheralAwarenessOperation implements ProjectedMemoryOperation {

  public static final String OPERATION_NAME = "peripheral_awareness";
  private static final int PRIORITY = 1000;
  private static final long HOT_HOURS_THRESHOLD = 24L;
  private static final long COLD_DAYS_THRESHOLD = 7L;

  @Override
  public String getName() {
    return OPERATION_NAME;
  }

  @Override
  public int getPriority() {
    return PRIORITY;
  }

  @Override
  public void process(ProjectedMemory memory, List<ChatMessage> projectedMessages, List<String> notifications) {
    if (memory == null || notifications == null) {
      return;
    }

    Agent agent = memory.getAgent();
    if (agent == null) {
      return;
    }

    EpisodicMemory episodicMemory = agent.getEpisodicMemory();
    if (episodicMemory == null) {
      return;
    }

    String currentSubchannel = memory.getSubchannel();
    LocalDateTime now = LocalDateTime.now();
    Timestamp oldestLimit = Timestamp.valueOf(now.minusDays(COLD_DAYS_THRESHOLD));
    LocalDateTime hotBoundary = now.minusHours(HOT_HOURS_THRESHOLD);

    List<SubchannelActivity> activities = episodicMemory.getSubchannelsActivity(oldestLimit);
    if (activities == null || activities.isEmpty()) {
      return;
    }

    StringBuilder sb = new StringBuilder();
    boolean hasPeripheralChannels = false;

    /*
    FIXME: Si en el futuro pruebas Noema con 5 o 10 canales activos simultáneamente, 
    podrías inyectar 10 resúmenes completos en cada turno, lo que acabará deborando la ventana de contexto.
    Sugerencia: Añadir un tope estricto. Por ejemplo, detallar solo los 3 canales más recientes, 
    y agrupar el resto bajo un mensaje genérico: "Tienes otros 5 canales con actividad reciente, 
    pero sus detalles han sido omitidos por eficiencia".    
    */
    
    
    for (SubchannelActivity activity : activities) {
      if (activity == null || StringUtils.isBlank(activity.getSubchannel())) {
        continue;
      }

      String channel = activity.getSubchannel();
      if (channel.equalsIgnoreCase(currentSubchannel)) {
        continue;
      }

      if (!hasPeripheralChannels) {
        sb.append("Mantienes otras sesiones activas de forma paralela (consciencia periferica). ");
        sb.append("No respondas a estos usuarios desde aqui, pero utiliza este contexto si el usuario actual hace referencia a ellos:\n\n");
        hasPeripheralChannels = true;
      }

      Timestamp lastTimestamp = activity.getLastActivity();
      LocalDateTime lastTime = (lastTimestamp != null) ? lastTimestamp.toLocalDateTime() : null;
      String timeAgo = (lastTime != null) ? DateUtils.timeAgo(lastTime) : "recientemente";

      boolean isHot = lastTime != null && lastTime.isAfter(hotBoundary);

      if (isHot) {
        ConsolidateMemory cp = episodicMemory.getLatestConsolidateMemory(channel);
        String summary = (cp != null) ? cp.getSummary() : null;

        if (StringUtils.isNotBlank(summary)) {
          sb.append("- Subcanal '").append(channel).append("' (Ultima actividad: ").append(timeAgo).append("):\n");
          sb.append("  [Resumen]: ").append(summary.trim()).append("\n");
        } else {
          sb.append("- Subcanal '").append(channel).append("' (Ultima actividad: ").append(timeAgo).append(", conversacion activa en curso).\n");
        }
      } else {
        sb.append("- Subcanal '").append(channel).append("' (Inactivo recientemente, ultima actividad: ").append(timeAgo).append(").\n");
      }
    }

    if (hasPeripheralChannels) {
      sb.append("\nSi requieres mas detalles sobre alguna de estas conversaciones, utiliza las herramientas de busqueda en el historial.");
      notifications.add(sb.toString().trim());
    }
  }

  @Override
  public JsonObject getState() {
    return null;
  }

  @Override
  public void restoreState(JsonObject state) {
    // Stateless operation
  }
}
