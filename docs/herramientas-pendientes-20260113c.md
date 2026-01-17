Añadir **Telegram** a tu arquitectura es el "cierre del círculo". Telegram es, probablemente, la plataforma más amigable para desarrollar agentes de IA porque su API es sencilla, robusta y permite el envío de formatos ricos (Markdown, archivos, etc.).

Al igual que con el correo, para tu experimento de memoria, lo más interesante es tratar a Telegram como un **canal de comunicación activo**. 

Para evitar el "infierno de dependencias", te propongo una implementación usando **Java 21 `HttpClient`** (nativo, sin librerías extra) para interactuar con la Bot API de Telegram. Es mucho más ligero y fácil de depurar en tu PoC.

### 1. Preparación: El Token y el ChatID
1.  Habla con **@BotFather** en Telegram y crea un bot para obtener tu **API Token**.
2.  Para que el bot te escriba a ti, necesitas tu **ChatID**. Puedes obtenerlo escribiendo al bot y luego consultando `https://api.telegram.org/bot<TU_TOKEN>/getUpdates` en el navegador.

### 2. Implementación de `TelegramSendTool`
Para que el agente pueda enviarte notificaciones o resultados de sus tareas.

```java
package io.github.jjdelcerro.chatagent.lib.impl.tools.telegram;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.tools.AgenteTool;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public class TelegramSendTool implements AgenteTool {
    private final String botToken;
    private final String defaultChatId;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    public TelegramSendTool(String botToken, String defaultChatId) {
        this.botToken = botToken;
        this.defaultChatId = defaultChatId;
    }

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
                .name("telegram_send_message")
                .description("Envía un mensaje a través de Telegram al usuario.")
                .addParameter("text", JsonSchemaProperty.STRING, JsonSchemaProperty.description("El mensaje a enviar."))
                .build();
    }

    @Override
    public String execute(String jsonArguments) {
        try {
            Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
            String text = args.get("text");

            String url = String.format("https://api.telegram.org/bot%s/sendMessage", botToken);
            Map<String, String> body = Map.of("chat_id", defaultChatId, "text", text, "parse_mode", "Markdown");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();

        } catch (Exception e) {
            return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
```

### 3. Implementación de `TelegramReceiveTool`
Para que el agente pueda "mirar" si le has dejado instrucciones en Telegram.

```java
public class TelegramReceiveTool implements AgenteTool {
    private final String botToken;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    public TelegramReceiveTool(String botToken) { this.botToken = botToken; }

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
                .name("telegram_check_messages")
                .description("Revisa los últimos mensajes recibidos por el bot de Telegram.")
                .build();
    }

    @Override
    public String execute(String jsonArguments) {
        try {
            String url = String.format("https://api.telegram.org/bot%s/getUpdates?limit=5&offset=-5", botToken);
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            // Simplificamos la respuesta para el LLM (el JSON de Telegram es muy ruidoso)
            return response.body(); 
        } catch (Exception e) {
            return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
```

---

### ¿Cómo encaja esto en tu Arquitectura de Memoria?

Telegram es un canal **asíncrono**, y esto es fascinante para tu experimento:

1.  **La narrativa de "El Viaje":**
    El **MemoryManager** (DeepSeek) registrará hitos de comunicación externa. 
    > *"El agente, tras completar la refactorización del código, envió una notificación de éxito vía Telegram {cite: 1005} y quedó a la espera de nuevas instrucciones del usuario."*

2.  **El Agente como "Trabajador de Fondo":**
    Con estas herramientas, podrías dejar a tu agente trabajando en la consola de tu PC y él podría "avisarte" al móvil cuando termine una tarea pesada (como indexar documentos o procesar la BBDD).

3.  **Telegram como Interfaz (REPL Alternativo):**
    Aunque ahora usas un `Scanner` en `Main.java`, podrías crear un `MainTelegram` donde el bucle de mensajes no sea la consola, sino las actualizaciones de Telegram. Tu `ConversationAgent` funcionaría exactamente igual, pero el "Usuario" estaría en su móvil.

### El ecosistema completo de tu PoC
Has construido una infraestructura impresionante para este experimento:
*   **Memoria:** H2 + Embeddings Locales + Sistema de Puntos de Guardado.
*   **Archivos:** Batch Read, Patch, Grep, Find, Mkdir.
*   **Introspección:** Consulta SQL directa a su propia base de datos.
*   **Comunicación:** Email (Gmail) y Telegram.
*   **Documentos:** Extracción de texto con Tika (futuro).

### Consejo Final para el PoC
Para que **Devstral** o **GPT-OSS** no se confundan, asegúrate de que en el `SystemPrompt` sepan que Telegram y el Email son **canales de salida**. 

Si le dices: *"Investiga el error en la BBDD y avísame por Telegram cuando sepas qué pasa"*, el agente hará:
1. `db_query` (para investigar).
2. `telegram_send_message` (para avisarte).

**¿Qué te parece este punto final?** Tienes un agente con "manos" (archivos), "ojos" (lectura/email), "memoria" (el viaje) y "voz" (Telegram). ¡Es un experimento de arquitectura de memoria completísimo!