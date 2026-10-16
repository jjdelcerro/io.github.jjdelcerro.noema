package io.github.jjdelcerro.noema.ui.webclient.handlers;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.memory.episodic.Turn;
import io.github.jjdelcerro.noema.ui.webclient.NoemaWebServer;
import io.javalin.http.Context;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author jjdelcerro
 */
public class HandlerGetHistory extends AbstractHandler {
  
  public static class FlatMessage {

    public String type;
    public String content;
    public long timestamp;

    public FlatMessage(String type, String content, long timestamp) {
      this.type = type;
      this.content = content;
      this.timestamp = timestamp;
    }
  }
  
  public HandlerGetHistory(Agent agent) {
    super(agent);
  }

  @Override
  public void handle(Context ctx) throws Exception {
    String terminalId = ctx.pathParam("terminalId");
    List<Turn> turns = this.agent.getEpisodicMemory().getUnconsolidatedTurns(terminalId);
    List<FlatMessage> flatHistory = new ArrayList<>();

    for (Turn turn : turns) {
      long ts = Timestamp.valueOf(turn.getTimestamp()).getTime();

      if (StringUtils.isNotBlank(turn.getTextUser())) {
        flatHistory.add(new FlatMessage("user-message", turn.getTextUser(), ts));
      }
      if (StringUtils.isNotBlank(turn.getTextModelThinking())) {
        flatHistory.add(new FlatMessage("thinking", turn.getTextModelThinking(), ts));
      }
      if (turn.getToolCall() != null && !turn.getToolCall().trim().isEmpty()) {
        flatHistory.add(new FlatMessage("log", turn.getToolCall(), ts));
      }
      if ("error".equals(turn.getContenttype()) && turn.getToolResult() != null) {
        flatHistory.add(new FlatMessage("error", turn.getToolResult(), ts));
      }
      if (StringUtils.isNotBlank(turn.getTextModel())) {
        flatHistory.add(new FlatMessage("response", turn.getTextModel(), ts));
      }
    }

    ctx.json(flatHistory);
  }
  
}
