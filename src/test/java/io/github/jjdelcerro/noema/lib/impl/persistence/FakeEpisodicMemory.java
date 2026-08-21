package io.github.jjdelcerro.noema.lib.impl.persistence;

import io.github.jjdelcerro.noema.lib.persistence.Turn;

import java.time.LocalDateTime;
import java.util.List;
import io.github.jjdelcerro.noema.lib.persistence.EpisodicMemory;
import io.github.jjdelcerro.noema.lib.persistence.CompactedMemory;

public class FakeEpisodicMemory implements EpisodicMemory {

  @Override
  public void add(Turn turn) {
  }

  @Override
  public void add(CompactedMemory checkpoint) {
  }

  @Override
  public CompactedMemory getLatestCompactedMemory(String subchannel) {
    return null;
  }

  @Override
  public List<Turn> getUnconsolidatedTurns(String subchannel) {
    return List.of();
  }

  @Override
  public Turn getTurnById(int id) {
    return null;
  }

  @Override
  public List<Turn> getTurnsByIds(String subchannel, int first, int last) {
    return List.of();
  }

  @Override
  public List<Turn> getTurnsByText(String subchannel, String query, int maxResults) {
    return List.of();
  }

  @Override
  public CompactedMemory getCheckPointById(int id) {
    return null;
  }

  @Override
  public CompactedMemory createCompactedMemory(String subchannel, int turnFirst, int turnLast, LocalDateTime timestamp, String text) {
    return null;
  }

  @Override
  public Turn createTurn(LocalDateTime timestamp, String contenttype, String subchannel, String textUser, String textModelThinking, String textModel, String toolCall, String toolResult, float[] embedding) {
    return new FakeTurn(1, contenttype, textUser, textModel);
  }
}
