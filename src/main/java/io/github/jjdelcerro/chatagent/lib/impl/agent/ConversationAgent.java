package io.github.jjdelcerro.chatagent.lib.impl.agent;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import io.github.jjdelcerro.chatagent.lib.impl.tools.memory.LookupTurnTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.memory.SearchFullHistoryTool;
import io.github.jjdelcerro.chatagent.lib.persistence.CheckPoint;
import io.github.jjdelcerro.chatagent.lib.persistence.SourceOfTruth;
import io.github.jjdelcerro.chatagent.lib.persistence.Turn;
import io.github.jjdelcerro.chatagent.lib.tools.AgenteTool;
import io.github.jjdelcerro.chatagent.lib.utils.ConsoleOutput;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Orquestador principal del sistema.
 * Gestiona el bucle de razonamiento, la ejecución de herramientas y la interacción con el LLM.
 */
public class ConversationAgent {
    // Umbral de compactación (Hardcoded para el ejemplo, podría ser configurable)
    private static final int COMPACTION_THRESHOLD = 20;

    private final SourceOfTruth sourceOfTruth;
    private final MemoryManager memoryManager;
    private final ChatLanguageModel model;
    private final ConsoleOutput console;

    private List<Turn> activeMemory = new ArrayList<>();
    private CheckPoint activeCheckPoint;

    // Registro de herramientas
    private final Map<String, AgenteTool> toolDispatcher = new HashMap<>();
    private final List<ToolSpecification> toolSpecifications = new ArrayList<>();

    /**
     * Constructor con inyección de dependencias y configuración.
     */
    public ConversationAgent(SourceOfTruth sourceOfTruth,
                             MemoryManager memoryManager,
                             ChatLanguageModel model,
                             ConsoleOutput console) {
        this.sourceOfTruth = sourceOfTruth;
        this.memoryManager = memoryManager;
        this.console = console;
        this.model = model;

        try {
            this.activeMemory.addAll(sourceOfTruth.getUnconsolidatedTurns());
            this.activeCheckPoint = sourceOfTruth.getLatestCheckPoint();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void addTool(AgenteTool tool) {
        this.toolDispatcher.put(tool.getName(), tool);
        this.toolSpecifications.add(tool.getSpecification());
    }

    /**
     * Procesa una entrada del usuario ejecutando el bucle de razonamiento.
     */
    public String processTurn(String textUser) {
        //FIXME: Aqui habria que hacer algo como guardarse en una lista local al metodo y no hacer el addTurn con ellos hasta que termine el turno, justo antes de la compactacion (por si petan las cosas no dejarlo a medias).
        
        // Variable "volátil" para el texto del usuario.
        // Se consumirá en el primer Turno que se genere (ya sea tool o respuesta final)
        // para no repetirlo en cada paso del bucle.
        String pendingUserText = textUser;
        StringBuilder llmResponse = new StringBuilder();
        
        try {
            // 1. CONSTRUCCIÓN DE MENSAJES (Prompt)
            List<ChatMessage> messages = buildContextMessages(this.activeCheckPoint, this.activeMemory);
            
            // Añadimos el input actual al historial de la sesión LLM
            messages.add(UserMessage.from(textUser));

            // 2. BUCLE DE INTERACCIÓN (Reasoning Loop)
            boolean turnFinished = false;

            while (!turnFinished) {
                // Llamada al Modelo
                Response<AiMessage> response = model.generate(messages, toolSpecifications);
                AiMessage aiMessage = response.content();

                // Añadimos la respuesta del modelo al contexto en memoria para la siguiente vuelta
                messages.add(aiMessage);

                if (aiMessage.hasToolExecutionRequests()) {
                    // --- MODO HERRAMIENTA ---
                    // El modelo puede pedir ejecutar una o varias herramientas
                    for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                        
                        // A. Ejecución
                        String result = executeToolLogic(request);

                        // B. Determinación del Tipo de Contenido (Para el MemoryManager)
                        // Si es una tool de memoria, usamos un tipo especial para los "Flashbacks"
                        String contentType = "tool_execution";
                        if (isMemoryTool(request.name())) {
                            contentType = "lookup_turn";
                        }

                        // C. Persistencia del Turno (Uno por herramienta ejecutada)
                        Turn toolTurn = this.sourceOfTruth.createTurn(
                                Timestamp.from(Instant.now()),
                                contentType,
                                pendingUserText, // Se asigna solo la primera vez (si aplica)
                                null, // Thinking (no soportado explícitamente por OpenAI API standard hoy)
                                null, // textModel (vacío, es una acción)
                                request.toString(), // toolCall (Intención)
                                result, // toolResult (Resultado)
                                null // Embedding (se calculará en SourceOfTruth)
                        );
                        
                        this.addTurn(toolTurn);

                        // D. Feedback al LLM
                        // Inyectamos el resultado para que el modelo lo vea en la siguiente iteración
                        messages.add(ToolExecutionResultMessage.from(request, result));

                        // Consumimos el texto del usuario si se usó
                        pendingUserText = null;
                    }
                    // El bucle continúa...

                } else {
                    // --- MODO RESPUESTA FINAL ---
                    String aiText = aiMessage.text();
                    Turn responseTurn = this.sourceOfTruth.createTurn(
                            Timestamp.from(Instant.now()),
                            "chat",
                            pendingUserText, // Si no hubo tools, aquí va el input original. Si hubo, es null.
                            null,
                            aiText,
                            null,
                            null,
                            null
                    );
                    llmResponse.append(aiText);
                    
                    this.addTurn(responseTurn);
                    turnFinished = true;
                }
            }

            // 3. GESTIÓN DE COMPACTACIÓN
            // Verificamos si el volumen de turnos no consolidados ha superado el límite
            if (this.activeMemory.size() >= COMPACTION_THRESHOLD) {
                performCompaction();
            }

        } catch (SQLException e) {
            this.console.printerrorln("Error crítico de base de datos en processTurn: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            this.console.printerrorln("Error inesperado en processTurn: " + e.getMessage());
            e.printStackTrace();
        }
        return llmResponse.toString();
    }

    private void addTurn(Turn turn) {
        this.sourceOfTruth.add(turn);
        this.activeMemory.add(turn);
    }

    private String getBaseSystemPrompt() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy, HH:mm", new Locale("es", "ES"));
        
        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("# Contexto del Sistema\n\n");
        systemPrompt.append("**Momento actual de la conversación:** ").append(LocalDateTime.now().format(formatter)).append("\n\n");
        systemPrompt.append("""
Eres el componente de razonamiento inmediato en un sistema con memoria conversacional completa. Dispones de:

## 1. Contexto disponible en este momento:
- **Relato narrativo de lo conversado anteriormente:** Un resumen estructurado que puede contener referencias como `{cite:ID-123}` a momentos específicos del diálogo.
- **Intercambios recientes:** Los últimos turnos de la conversación en curso.

## 2. Herramientas de acceso a la memoria detallada:

### `""");
        systemPrompt.append(LookupTurnTool.NAME);        
        systemPrompt.append("` - Acceso preciso por referencia");
        systemPrompt.append("""
- **Úsala cuando:** En el relato narrativo encuentres una referencia `{cite:ID-123}` y necesites recuperar exactamente lo que se dijo en ese momento, junto con su contexto inmediato (qué vino justo antes y después).
- **Ejemplo mental:** "La referencia {cite:ID-87} menciona una decisión sobre bases de datos. Necesito ver los argumentos exactos que se dieron entonces."

### `""");
        systemPrompt.append(SearchFullHistoryTool.NAME);        
        systemPrompt.append("` - Búsqueda por significado");
        systemPrompt.append("""
- **Úsala cuando:** Detectes que falta información en tu contexto para responder adecuadamente. Busca en todo el historial conversacional (desde hace minutos hasta años) por similitud semántica.
- **Ejemplo mental:** "El usuario pregunta sobre 'lanzamiento de Starship'. Tengo la sensación de que hablamos de esto antes, pero no recuerdo cuándo ni los detalles."

## 3. Interpretación de la información recuperada

Al usar estas herramientas, recibirás resultados que incluyen:
- **Identificador único** (`ID-número`)
- **Marca temporal exacta** (fecha y hora)
- **Descripción temporal relativa** ("hace X días", "hace Y meses")
- **El contenido de lo dicho en ese momento**

**Considera críticamente la antigüedad de la información:**
- **Información reciente** (horas/días): Probablemente sigue siendo aplicable al contexto actual.
- **Información antigua** (semanas/meses/años): El mundo, los proyectos o los supuestos pueden haber cambiado. Evalúa su vigencia antes de presentarla como hecho actual.

Cuando presentes información recuperada:
1. **Contextualiza temporalmente:** "En una conversación de hace unas semanas..." o "Hace aproximadamente un año mencionaste..."
2. **Añade precaución si es muy antigua:** "Esto se discutió hace més de un año, así que verifiquemos si sigue siendo válido."
3. **No confundas temporalidades:** La información de conversaciones pasadas (años atrás) pertenece a ese contexto histórico, no al diálogo actual.

## Principio rector: Memoria crítica, no automática
Tienes acceso a todo el historial, pero no toda la información es igualmente relevante o vigente. Usa tu criterio para determinar qué recuperar y cómo presentarlo.                   
                            
## Protocolo de interpretación de la intención del usuario
Antes de responder o actuar, determina el tipo de solicitud del usuario:
- **Consultas exploratorias o hipotéticas** (ej: "¿se podría...?", "¿cómo harías...?", "¿qué opinas de...?", "¿qué pasaría si...?"):
  Responde con análisis, explicaciones de conceptos, descripción de opciones y sus implicaciones. **No ejecutes herramientas de acción** (como escritura o modificación de archivos) a menos que el usuario te lo pida explícitamente después de esta fase exploratoria.
- **Solicitudes de información o explicación** (ej: "dime más sobre...", "explícame...", "describe...", "¿qué significa...?"):
  Proporciona la información, contexto o clarificación solicitada. El objetivo aquí es transmitir conocimiento, no realizar cambios.
- **Instrucciones directas y operativas** (ej: "haz...", "implementa...", "modifica...", "ejecuta...", "escribe...", "añade..."):
  Procede con la acción solicitada. Para cualquier operación que modifique el estado del sistema (escribir/editar archivos, crear directorios, etc.), **muestra brevemente tu plan o solicita confirmación** antes de ejecutar la herramienta correspondiente, a menos que la instrucción sea inequívocamente clara y el usuario haya confirmado previamente.

**Regla general: favorece el modo consultivo.** Cuando el usuario esté pensando en voz alta, explorando posibilidades o buscando comprensión, acompáñale en el análisis sin precipitarte a la acción. La transición al modo ejecutivo debe ser deliberada y basada en una indicación clara del usuario.
""");
        return systemPrompt.toString();
    }
    
    private List<ChatMessage> buildContextMessages(CheckPoint checkpoint, List<Turn> turns) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy, HH:mm", new Locale("es", "ES"));
        
        List<ChatMessage> messages = new ArrayList<>();

        // A. System Prompt Base
        StringBuilder systemPrompt = new StringBuilder();
        
        systemPrompt.append(this.getBaseSystemPrompt());

        // B. Inyección del Punto de Guardado (Memoria a Largo Plazo)
        if (checkpoint != null) {
            systemPrompt.append("\n\n## Contexto consolidado de la conversacion\n");
            systemPrompt.append("La siguiente sección contiene un resumen y relato narrativo de la conversacion");
            systemPrompt.append("consolidado y actualizado hasta ").append(checkpoint.getTimestamp().toLocalDateTime().format(formatter)).append(".\n\n");
            systemPrompt.append("--- INICIO DEL RELATO RESUMEN Y RELATO NARRATIVO DE LA CONVERSACION ---\n");
            systemPrompt.append(checkpoint.getText()).append("\n\n");            
            systemPrompt.append("--- FIN DEL RELATO RESUMEN Y RELATO NARRATIVO DE LA CONVERSACION ---\n");
        }
        messages.add(SystemMessage.from(systemPrompt.toString()));

        // C. Historial Reciente (Memoria de Trabajo)
        for (Turn t : turns) {
            messages.add(t.toChatMessage());
        }

        return messages;
    }

    private String executeToolLogic(ToolExecutionRequest request) {
        String toolName = request.name();
        String args = request.arguments();
        
        AgenteTool tool = toolDispatcher.get(toolName);
        
        if (tool != null) {
            if( tool.getMode() == AgenteTool.MODE_WRITE ) {
                boolean authorized = this.console.confirm(
                    String.format("El agente quiere ejecutar la herramienta: %s\nArgumentos: %s\n¿Autorizar?", toolName, args)
                );

                if (!authorized) {
                    this.console.println("Ejecución denegada por el usuario.");
                    return "Error: User rejected the execution of tool '" + toolName + "'.";
                }
                this.console.println("Ejecutando herramienta: " + toolName);
            } else {
                this.console.println(String.format("Ejecutando herramienta: %s\n    Argumentos: %s", toolName, args));
            }
            try {
                return tool.execute(args);
            } catch (Exception e) {
                return "Error ejecutando herramienta: " + e.getMessage();
            }
        } else {
            return "Error: Herramienta '" + toolName + "' no encontrada.";
        }
    }

    private boolean isMemoryTool(String toolName) {
        AgenteTool tool = toolDispatcher.get(toolName);
        return tool.getType() == AgenteTool.TYPE_MEMORY;
    }

    private void performCompaction() throws SQLException {
        this.console.println("Iniciando proceso de compactación de memoria...");
        
        int n = this.activeMemory.size() / 2;
        ArrayList<Turn> remainingTurns = new ArrayList<>();
        remainingTurns.addAll(this.activeMemory.subList(n, this.activeMemory.size()));
        List<Turn> compactTurns = this.activeMemory.subList(0, n);
        
        // 1. MemoryManager crea el CheckPoint (Factoría transitoria, devuelve objeto listo pero no persistido)
        CheckPoint newCheckPoint = memoryManager.compact(this.activeCheckPoint, compactTurns);
        
        // 2. SourceOfTruth persiste metadatos, asigna ID real y guarda fichero físico
        sourceOfTruth.add(newCheckPoint);
        
        this.activeMemory.clear();
        this.activeMemory.addAll(remainingTurns);
        this.activeCheckPoint = newCheckPoint;
        
        this.console.println("Memoria compactada con éxito. Nuevo CheckPoint ID: " + newCheckPoint.getId());
    }
}
