package io.github.jjdelcerro.noema.lib.impl.services.conversation.tools.web;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import io.github.jjdelcerro.noema.lib.AgentTool;

public class WebSearchTool implements AgentTool {

  private final String apiKey;
  private final HttpClient httpClient;
  private final Gson gson = new Gson();

  private final Agent agent;

  public WebSearchTool(Agent agent) {
    this.agent = agent;
    this.apiKey = agent.getSettings().getProperty("BRAVE_SEARCH_API_KEY");
    this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name("web_search")
            .description("Busca información actualizada en internet. Úsala cuando necesites datos que no están en tu memoria local o para verificar hechos actuales.")
            .addParameter("query", JsonSchemaProperty.STRING, JsonSchemaProperty.description("La consulta de búsqueda en lenguaje natural."))
            .build();
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_READ; // Es una consulta de información
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
      String query = args.get("query");

      if (query == null || query.isBlank()) {
        return "{\"status\": \"error\", \"message\": \"Query vacía.\"}";
      }

      // Construir la URL (Brave usa el parámetro 'q')
      // El tier gratuito permite hasta 20 resultados, pero con 5 suele sobrar para un LLM.
      String url = "https://api.search.brave.com/res/v1/web/search?q="
              + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8)
              + "&count=5";

      HttpRequest request = HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("Accept", "application/json")
              .header("X-Subscription-Token", apiKey)
              .GET()
              .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        return "{\"status\": \"error\", \"code\": " + response.statusCode() + "}";
      }

      return parseBraveResponse(response.body());

    } catch (Exception e) {
      return "{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}";
    }
  }

  /**
   * Limpia la respuesta de Brave para enviar solo lo relevante al modelo.
   */
  private String parseBraveResponse(String rawJson) {
    JsonObject fullRes = gson.fromJson(rawJson, JsonObject.class);
    JsonObject web = fullRes.getAsJsonObject("web");

    if (web == null || !web.has("results")) {
      return "{\"status\": \"success\", \"results\": []}";
    }

    JsonArray results = web.getAsJsonArray("results");
    JsonArray cleanResults = new JsonArray();

    for (JsonElement el : results) {
      JsonObject item = el.getAsJsonObject();
      JsonObject cleanItem = new JsonObject();
      cleanItem.addProperty("title", item.get("title").getAsString());
      cleanItem.addProperty("description", item.get("description").getAsString());
      cleanItem.addProperty("url", item.get("url").getAsString());
      cleanResults.add(cleanItem);
    }

    JsonObject output = new JsonObject();
    output.addProperty("status", "success");
    output.add("results", cleanResults);

    return gson.toJson(output);
  }
}
