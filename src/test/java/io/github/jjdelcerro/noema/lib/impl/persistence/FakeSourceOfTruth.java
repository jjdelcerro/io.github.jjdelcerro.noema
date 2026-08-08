package io.github.jjdelcerro.noema.lib.impl.persistence;

import io.github.jjdelcerro.noema.lib.impl.persistence.TurnImpl;
import io.github.jjdelcerro.noema.lib.persistence.CheckPoint;
import io.github.jjdelcerro.noema.lib.persistence.SourceOfTruth;
import io.github.jjdelcerro.noema.lib.persistence.Turn;

import java.time.LocalDateTime;
import java.util.List;

public class FakeSourceOfTruth implements SourceOfTruth {

    @Override public void add(Turn turn) {}
    @Override public void add(CheckPoint checkpoint) {}
    @Override public CheckPoint getLatestCheckPoint(String subchannel) { return null; }
    @Override public List<Turn> getUnconsolidatedTurns(String subchannel) { return List.of(); }
    @Override public Turn getTurnById(int id) { return null; }
    @Override public List<Turn> getTurnsByIds(String subchannel, int first, int last) { return List.of(); }
    @Override public List<Turn> getTurnsByText(String subchannel, String query, int maxResults) { return List.of(); }
    @Override public CheckPoint getCheckPointById(int id) { return null; }
    @Override public CheckPoint createCheckPoint(String subchannel, int turnFirst, int turnLast, LocalDateTime timestamp, String text) { return null; }

    @Override
    public Turn createTurn(LocalDateTime timestamp, String contenttype, String subchannel, String textUser, String textModelThinking, String textModel, String toolCall, String toolResult, float[] embedding) {
        return new FakeTurn(1, contenttype, textUser, textModel);
    }    
}
