package io.github.jjdelcerro.chatagent.lib.impl.tools.web;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.tools.AgenteTool;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;

public class WebGetTool implements AgenteTool {

    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private static final int MAX_CHARS = 10000; // Límite razonable para no saturar el contexto

    public WebGetTool() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
                .name("web_get_content")
                .description("Extrae el texto de una URL específica. Úsala cuando tengas un enlace directo (por ejemplo, de un resultado de búsqueda) y necesites leer su contenido detallado.")
                .addParameter("url", JsonSchemaProperty.STRING, JsonSchemaProperty.description("La URL completa a leer."))
                .build();
    }

    @Override
    public String execute(String jsonArguments) {
        try {
            Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
            String url = args.get("url");

            // SEGURIDAD BÁSICA: Evitar acceso a red local (Opcional pero recomendado)
            if (isInternalAddress(url)) {
                return "{\"status\": \"error\", \"message\": \"Acceso denegado a direcciones internas.\"}";
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "ChatAgent-Bot/1.0 (Pragmatic Architecture Experiment)")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "{\"status\": \"error\", \"code\": " + response.statusCode() + "}";
            }

            String cleanText = cleanHtml(response.body());
            
            // Aplicar política de recorte
            boolean truncated = false;
            if (cleanText.length() > MAX_CHARS) {
                cleanText = cleanText.substring(0, MAX_CHARS);
                truncated = true;
            }

            return gson.toJson(Map.of(
                    "status", "success",
                    "content", cleanText,
                    "truncated", truncated
            ));

        } catch (Exception e) {
            return "{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }

    private boolean isInternalAddress(String url) {
        String lower = url.toLowerCase();
        return lower.contains("localhost") || lower.contains("127.0.0.1") || lower.contains("192.168.");
    }

    /**
     * Limpieza ruda pero efectiva para un prototipo sin dependencias externas.
     * En un sistema de producción, aquí usaríamos Jsoup.
     */
    private String cleanHtml(String html) {
        String text = html;
        // Eliminar scripts y estilos
        text = Pattern.compile("<script.*?>.*?</script>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(text).replaceAll("");
        text = Pattern.compile("<style.*?>.*?</style>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(text).replaceAll("");
        // Eliminar todas las etiquetas HTML
        text = text.replaceAll("<[^>]*>", " ");
        // Normalizar espacios y saltos de línea
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }
}
