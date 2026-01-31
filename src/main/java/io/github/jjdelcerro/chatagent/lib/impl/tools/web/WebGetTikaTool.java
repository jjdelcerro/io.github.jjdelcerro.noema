package io.github.jjdelcerro.chatagent.lib.impl.tools.web;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.tools.AgenteTool;
import org.apache.tika.Tika;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class WebGetTikaTool implements AgenteTool { // FIXME: alguna forma de paginar documentos largos.

    private final HttpClient httpClient;
    private final Tika tika = new Tika();
    private final Gson gson = new Gson();
    private static final int MAX_CHARS = 10000; // Tu política de recorte

    private final Agent agent;
    
    public WebGetTikaTool(Agent agent) {
        this.agent = agent;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
                .name("web_get_content")
                .description("Extrae el texto legible de una URL (HTML, PDF, etc). Úsala para leer el contenido detallado de un sitio web.")
                .addParameter("url", JsonSchemaProperty.STRING, JsonSchemaProperty.description("La URL completa a procesar."))
                .build();
    }

    @Override
    public String execute(String jsonArguments) {
        try {
            Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
            String url = args.get("url");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "ChatAgent-Bot/1.0")
                    .GET()
                    .build();

            // 1. Obtenemos la respuesta como String primero para analizar el tipo
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "{\"status\": \"error\", \"code\": " + response.statusCode() + "}";
            }

            // 2. Analizar el Content-Type
            String contentType = response.headers().firstValue("Content-Type").orElse("text/plain").toLowerCase();
            String finalContent;

            if (contentType.contains("json") || contentType.contains("xml") || contentType.contains("text/plain")) {
                // Es un formato estructurado que el LLM entiende nativamente. No tocamos nada.
                finalContent = response.body();
            } else {
                // Es HTML, PDF, DOCX... aquí sí entra Tika para limpiar la "mugre"
                finalContent = tika.parseToString(new java.io.ByteArrayInputStream(response.body().getBytes()));
                finalContent = finalContent.replaceAll("\\s+", " ").trim();
            }

            // 3. Aplicar política de recorte
            boolean truncated = false;
            if (finalContent.length() > MAX_CHARS) {
                finalContent = finalContent.substring(0, MAX_CHARS);
                truncated = true;
            }

            return gson.toJson(Map.of(
                    "status", "success",
                    "mime_type", contentType,
                    "content", finalContent,
                    "truncated", truncated
            ));

        } catch (Exception e) {
            return "{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }

}
