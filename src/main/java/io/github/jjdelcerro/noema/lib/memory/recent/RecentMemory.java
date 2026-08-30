package io.github.jjdelcerro.noema.lib.memory.recent;

import dev.langchain4j.data.message.ChatMessage;
import io.github.jjdelcerro.noema.lib.memory.episodic.Turn;
import java.util.List;

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

    RecentMemoryMark getConsolidateMark();

    List<ChatMessage> getMessages();

    RecentMemoryMark getNewestMark();

    RecentMemoryMark getOldestMark();

    String getSubchannel();

    int getTurnsCount();
    
    /**
     * Evalúa si es necesario consolidar el conocimiento acumulado en una nueva ConsolidateMemory.
     * Se dispara preventivamente por volumen de turnos para actuar antes de que comience 
     * la degradación de la atención del LLM, preservando la fidelidad del razonamiento.
     *
     * @return true si el numero de turnos unicos consolidados supera el umbral.
     */    
    boolean needConsolidation();

    void remove(RecentMemoryMark mark1, RecentMemoryMark mark2);

    void save();

    long getLastTurnId();
}
