package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class TavilyWebSearchTool extends AbstractAgentTool {

  public static final String TAVILY_API_KEY = "websearch/tavily_api_key";

  public TavilyWebSearchTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name("web_search")
            .description("Busca información actualizada en internet usando Tavily. Ideal para búsquedas complejas y análisis de datos web.")
            .addParameter("query", JsonSchemaProperty.STRING, JsonSchemaProperty.description("La consulta de búsqueda."))
            .build();
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_READ;
  }

  @Override
  public String execute(String jsonArguments) {
    // Recuperamos la clave dinámicamente en cada ejecución
    String apiKey = agent.getSettings().getPropertyAsString(TAVILY_API_KEY);
    if (apiKey == null || apiKey.isBlank()) {
      return error("No se ha configurado una API Key para Tavily.");
    }

    try {
      Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
      String query = args.get("query");

      if (query == null || query.isBlank()) {
        return error("Query vacía.");
      }

      HttpClient httpClient = HttpClient.newBuilder()
              .connectTimeout(Duration.ofSeconds(10))
              .build();

      // Preparar el cuerpo de la petición
      JsonObject body = new JsonObject();
      body.addProperty("query", query);
      body.addProperty("search_depth", "advanced");

      HttpRequest request = HttpRequest.newBuilder()
              .uri(URI.create("https://api.tavily.com/search"))
              .header("Content-Type", "application/json")
              .header("Authorization", "Bearer " + apiKey)
              .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
              .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        return error("HTTP " + response.statusCode() + ": " + response.body());
      }

      return parseTavilyResponse(response.body());

    } catch (Exception e) {
      LOGGER.error("Error en TavilyWebSearchTool", e);
      return error("Fallo en ejecución: " + e.getMessage());
    }
  }

  private String parseTavilyResponse(String rawJson) {
    JsonObject fullRes = gson.fromJson(rawJson, JsonObject.class);
    JsonArray results = fullRes.getAsJsonArray("results");

    JsonArray cleanResults = new JsonArray();
    for (JsonElement el : results) {
      JsonObject item = el.getAsJsonObject();
      JsonObject cleanItem = new JsonObject();
      cleanItem.addProperty("title", item.has("title") ? item.get("title").getAsString() : "");
      cleanItem.addProperty("content", item.has("content") ? item.get("content").getAsString() : "");
      cleanItem.addProperty("url", item.has("url") ? item.get("url").getAsString() : "");
      cleanResults.add(cleanItem);
    }

    JsonObject output = new JsonObject();
    output.addProperty("status", "success");
    output.add("results", cleanResults);
    return gson.toJson(output);
  }
}
