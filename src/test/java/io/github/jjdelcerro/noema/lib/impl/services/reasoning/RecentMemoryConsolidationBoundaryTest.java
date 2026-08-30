package io.github.jjdelcerro.noema.lib.impl.services.reasoning;

import io.github.jjdelcerro.noema.lib.memory.recent.RecentMemory;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.impl.persistence.FakeRecentMemory;
import io.github.jjdelcerro.noema.lib.impl.persistence.FakeTurn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RecentMemoryConsolidationBoundaryTest {

    private FakeRecentMemory recentMemory;

    @BeforeEach
    public void setUp() {
        recentMemory = new FakeRecentMemory(Agent.DEFAULT_SUBCHANNEL);
        recentMemory.setNeedConsolidation(false);
    }

    // =========================================================================
    // HELPERS PARA CONSTRUIR TURNOS EN MEMORIA
    // =========================================================================

    private void addChatTurn(int turnId, String userText, String aiText) {
        recentMemory.add(UserMessage.from(userText));
        recentMemory.add(AiMessage.from(aiText));
        recentMemory.consolideTurn(new FakeTurn(turnId, "chat", userText, aiText));
    }

    private void addMultiToolTurn(int startTurnId, String userText, String... toolNames) {
        // 1. Mensaje de usuario
        recentMemory.add(UserMessage.from(userText));

        // 2. AiMessage con multiples llamadas a herramientas en paralelo
        List<ToolExecutionRequest> requests = new ArrayList<>();
        for (String toolName : toolNames) {
            requests.add(ToolExecutionRequest.builder()
                    .id("call_" + UUID.randomUUID().toString().substring(0, 8))
                    .name(toolName)
                    .arguments("{}")
                    .build());
        }
        AiMessage aiMessage = AiMessage.from(requests);
        recentMemory.add(aiMessage);

        // 3. Resultados de cada herramienta con consolidacion progresiva
        int currentTurnId = startTurnId;
        for (ToolExecutionRequest req : requests) {
            ToolExecutionResultMessage resultMsg = ToolExecutionResultMessage.from(req, "{\"status\":\"ok\"}");
            recentMemory.add(resultMsg);
            recentMemory.consolideTurn(new FakeTurn(currentTurnId++, "tool_execution", null, null));
        }
    }

    // =========================================================================
    // CASOS DE PRUEBA DE FRONTERA
    // =========================================================================

    @Test
    @DisplayName("Caso 1: Corte limpio sobre un dialogo simple alineado en el punto medio")
    public void testCleanTurnBoundaryCut() {
        // 10 turnos de chat = 20 mensajes en total (indices 0 al 19)
        for (int i = 1; i <= 10; i++) {
            addChatTurn(i, "Usuario " + i, "Modelo " + i);
        }

        assertEquals(20, recentMemory.getMessages().size());

        // mid = 20 / 2 = 10 (UserMessage del turno 6).
        // getCompactMark() debe avanzar hasta el indice 11 (AiMessage del turno 6).
        RecentMemory.RecentMemoryMark compactMark = recentMemory.getConsolidateMark();
        assertNotNull(compactMark);
        assertEquals(6, compactMark.getTurnId());
        assertTrue(compactMark.getMessage() instanceof AiMessage);

        // Ejecutamos el recorte
        RecentMemory.RecentMemoryMark oldestMark = recentMemory.getOldestMark();
        recentMemory.remove(oldestMark, compactMark);

        // Verificamos el estado de la sesion restante:
        // Deben quedar 8 mensajes (turnos 7 al 10) y el primero debe ser el UserMessage del turno 7
        List<ChatMessage> remaining = recentMemory.getMessages();
        assertEquals(8, remaining.size());
        assertTrue(remaining.get(0) instanceof UserMessage);
        assertEquals("Usuario 7", ((UserMessage) remaining.get(0)).singleText());
    }

    @Test
    @DisplayName("Caso 2: Corte en mitad de un turno conversacional (User -> Ai)")
    public void testCutInsideUserAiDialogTurn() {
        // 9 turnos de chat = 18 mensajes (indices 0 al 17)
        for (int i = 1; i <= 9; i++) {
            addChatTurn(i, "Usuario " + i, "Modelo " + i);
        }

        // mid = 18 / 2 = 9 (AiMessage del turno 5).
        // Debe cerrar exactamente en el indice 9 (turno 5 completo).
        RecentMemory.RecentMemoryMark compactMark = recentMemory.getConsolidateMark();
        assertNotNull(compactMark);
        assertEquals(5, compactMark.getTurnId());

        recentMemory.remove(recentMemory.getOldestMark(), compactMark);

        // La sesion restante debe empezar limpiamente con el UserMessage del turno 6
        List<ChatMessage> remaining = recentMemory.getMessages();
        assertEquals(8, remaining.size());
        assertTrue(remaining.get(0) instanceof UserMessage);
        assertEquals("Usuario 6", ((UserMessage) remaining.get(0)).singleText());
    }

    @Test
    @DisplayName("Caso 3: Corte en mitad de llamadas multiples a herramientas (Caso del fallo E2E)")
    public void testCutInsideMultiToolCall() {
        // Turnos 1 al 3: Chat normal (indices 0 al 5)
        for (int i = 1; i <= 3; i++) {
            addChatTurn(i, "Usuario " + i, "Modelo " + i);
        }

        // Turno 4: Prompt de usuario + AiMessage(ToolA, ToolB) + ResultA (Turn 4) + ResultB (Turn 5)
        // Indices: 6 (User), 7 (Ai con 2 tools), 8 (ResultA - Turn 4), 9 (ResultB - Turn 5)
        addMultiToolTurn(4, "Ejecuta dos herramientas", "tool_a", "tool_b");

        // Turnos 6 al 10: Chat normal posterior (indices 10 al 19)
        for (int i = 6; i <= 10; i++) {
            addChatTurn(i, "Usuario " + i, "Modelo " + i);
        }

        assertEquals(20, recentMemory.getMessages().size());

        // mid = 20 / 2 = 10 (UserMessage del turno 6).
        // En el bug anterior, si mid caia en 8 (ResultA - Turn 4), cortaba dejando ResultB (indice 9) huerfano.
        // Con la correccion, getCompactMark() DEBE avanzar obligatoriamente hasta consumir ResultB si corta el bloque.
        RecentMemory.RecentMemoryMark compactMark = recentMemory.getConsolidateMark();
        assertNotNull(compactMark);

        recentMemory.remove(recentMemory.getOldestMark(), compactMark);

        // Verificamos que la sesion restante NUNCA empieza con un ToolExecutionResultMessage
        List<ChatMessage> remaining = recentMemory.getMessages();
        assertFalse(remaining.isEmpty());
        assertFalse(remaining.get(0) instanceof ToolExecutionResultMessage,
                "El primer mensaje de la sesion restante no puede ser un ToolExecutionResultMessage huerfano.");
        assertTrue(remaining.get(0) instanceof UserMessage,
                "La sesion restante debe comenzar limpiamente con un UserMessage.");
    }

    @Test
    @DisplayName("Caso 4: Prueba de escala con 40 turnos (Umbral estandar de produccion)")
    public void testScaleWith40Turns() {
        // 40 turnos de chat = 80 mensajes (indices 0 al 79)
        for (int i = 1; i <= 40; i++) {
            addChatTurn(i, "Usuario " + i, "Modelo " + i);
        }

        assertEquals(80, recentMemory.getMessages().size());

        // mid = 80 / 2 = 40 (UserMessage del turno 21).
        // Debe avanzar al indice 41 (AiMessage del turno 21).
        RecentMemory.RecentMemoryMark compactMark = recentMemory.getConsolidateMark();
        assertNotNull(compactMark);
        assertEquals(21, compactMark.getTurnId());

        recentMemory.remove(recentMemory.getOldestMark(), compactMark);

        // Quedan 38 mensajes (turnos 22 al 40)
        List<ChatMessage> remaining = recentMemory.getMessages();
        assertEquals(38, remaining.size());
        assertTrue(remaining.get(0) instanceof UserMessage);
        assertEquals("Usuario 22", ((UserMessage) remaining.get(0)).singleText());
    }
}
