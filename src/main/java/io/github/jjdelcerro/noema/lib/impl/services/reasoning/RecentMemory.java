package io.github.jjdelcerro.noema.lib.impl.services.reasoning;

import dev.langchain4j.data.message.ChatMessage;
import io.github.jjdelcerro.noema.lib.persistence.Turn;
import java.time.LocalDateTime;
import java.util.List;
import io.github.jjdelcerro.noema.lib.persistence.CompactedMemory;

/**
 *
 * TODO: Antes Session, habria que actualizar la documentacion con este cambio
 * 
 * @author jjdelcerro
 */
public interface RecentMemory {

    /**
     * Interfaz publica para marcas de compactacion.
     */
    public interface RecentMemoryMark {

        int getTurnId();

        ChatMessage getMessage();
    }

    void add(ChatMessage message);

    void clear();

    void consolideTurn(Turn turn);
    
    public boolean isEmpty();

    RecentMemoryMark getCompactMark();

    List<ChatMessage> getContextMessages(CompactedMemory checkpoint, String systemPrompt);

    LocalDateTime getLastInteractionTime();

    List<ChatMessage> getMessages();

    RecentMemoryMark getNewestMark();

    RecentMemoryMark getOldestMark();

    String getSubchannel();

    int getTurnsCount();

    /**
     * Indica si la sesion ha acumulado suficientes turnos para requerir
     * compactacion.
     *
     * @return true si el numero de turnos unicos consolidados supera el umbral.
     */
    boolean needCompaction();

    void remove(RecentMemoryMark mark1, RecentMemoryMark mark2);

    void save();

    void setLastInteractionTime(LocalDateTime lastInteractionTime);

}
