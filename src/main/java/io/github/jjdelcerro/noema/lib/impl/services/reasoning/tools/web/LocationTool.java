package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.web;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import io.github.jjdelcerro.noema.lib.AgentTool;

/**
 * Herramienta para determinar la ubicación geográfica aproximada basada en la
 * IP pública. Utiliza ip-api.com (gratuito para desarrollo).
 */
public class LocationTool implements AgentTool {

  private final HttpClient httpClient;
  private final Gson gson = new Gson();

  public LocationTool(Agent agent) {
    this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name("get_current_location")
            .description("Obtiene la ubicacion geografica actual del sistema basada en la IP publica. "
                    + "Devuelve ciudad, pais y coordenadas (latitud/longitud).")
            .build();
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_READ;
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      // El servicio por defecto usa la IP desde la que recibe la petición
      HttpRequest request = HttpRequest.newBuilder()
              .uri(URI.create("http://ip-api.com/json"))
              .GET()
              .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        return "{\"status\": \"error\", \"code\": " + response.statusCode() + "}";
      }

      // Validar que el servicio devolvió éxito (ip-api devuelve 200 pero con status: "fail" si hay error)
      Map<String, Object> res = gson.fromJson(response.body(), Map.class);
      if ("fail".equals(res.get("status"))) {
        return "{\"status\": \"error\", \"message\": \"" + res.get("message") + "\"}";
      }

      return response.body();

    } catch (Exception e) {
      return "{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}";
    }
  }
}
