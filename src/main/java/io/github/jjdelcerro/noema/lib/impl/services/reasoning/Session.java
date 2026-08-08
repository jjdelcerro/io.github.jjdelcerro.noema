/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package io.github.jjdelcerro.noema.lib.impl.services.reasoning;

import dev.langchain4j.data.message.ChatMessage;
import io.github.jjdelcerro.noema.lib.persistence.CheckPoint;
import io.github.jjdelcerro.noema.lib.persistence.Turn;
import java.time.LocalDateTime;
import java.util.List;

/**
 *
 * @author jjdelcerro
 */
public interface Session {

    /**
     * Interfaz publica para marcas de compactacion.
     */
    public interface SessionMark {

        int getTurnId();

        ChatMessage getMessage();
    }

    void add(ChatMessage message);

    void clear();

    void consolideTurn(Turn turn);
    
    public boolean isEmpty();

    SessionMark getCompactMark();

    List<ChatMessage> getContextMessages(CheckPoint checkpoint, String systemPrompt);

    LocalDateTime getLastInteractionTime();

    List<ChatMessage> getMessages();

    SessionMark getNewestMark();

    SessionMark getOldestMark();

    String getSubchannel();

    int getTurnsCount();

    /**
     * Indica si la sesion ha acumulado suficientes turnos para requerir
     * compactacion.
     *
     * @return true si el numero de turnos unicos consolidados supera el umbral.
     */
    boolean needCompaction();

    void remove(SessionMark mark1, SessionMark mark2);

    void save();

    void setLastInteractionTime(LocalDateTime lastInteractionTime);

}
