package io.github.jjdelcerro.chatagent.lib.impl.tools.web;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.tools.AgenteTool;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Herramienta para consultar el clima actual utilizando Open-Meteo. No requiere
 * API Key, cumpliendo con la filosofía de minimizar fricción en el prototipo.
 */
public class WeatherTool implements AgenteTool {

  private final HttpClient httpClient;
  private final Gson gson = new Gson();

  public WeatherTool(Agent agent) {
    this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name("get_weather")
            .description("Consulta el clima actual y la prevision. "
                    + "ESTRATEGIA: Si el usuario no especifica una ciudad o ubicacion, utiliza primero "
                    + "la herramienta 'get_current_location' para obtener las coordenadas actuales "
                    + "y luego invoca esta herramienta pasando los datos obtenidos.")
            .addParameter("location", JsonSchemaProperty.STRING,
                    JsonSchemaProperty.description("Nombre de la ciudad (opcional si se usan coordenadas)."))
            .addParameter("lat", JsonSchemaProperty.NUMBER,
                    JsonSchemaProperty.description("Latitud de la ubicacion (opcional)."))
            .addParameter("lon", JsonSchemaProperty.NUMBER,
                    JsonSchemaProperty.description("Longitud de la ubicacion (opcional)."))
            .build();
  }

  @Override
  public int getMode() {
    return AgenteTool.MODE_READ;
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
      String location = args.get("location");

      if (location == null || location.isBlank()) {
        return "{\"status\": \"error\", \"message\": \"Ubicación no proporcionada.\"}";
      }

      // 1. Geocodificación: Ciudad -> Lat/Lon
      JsonObject geo = geocode(location);
      if (geo == null) {
        return "{\"status\": \"error\", \"message\": \"No se pudo encontrar la ubicación.\"}";
      }

      double lat = geo.get("latitude").getAsDouble();
      double lon = geo.get("longitude").getAsDouble();
      String name = geo.get("name").getAsString();

      // 2. Consulta de clima
      String url = String.format(
              "https://api.open-meteo.com/v1/forecast?latitude=%f&longitude=%f&current_weather=true",
              lat, lon);

      HttpRequest request = HttpRequest.newBuilder()
              .uri(URI.create(url))
              .GET()
              .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        return "{\"status\": \"error\", \"code\": " + response.statusCode() + "}";
      }

      JsonObject weatherRes = gson.fromJson(response.body(), JsonObject.class);
      weatherRes.addProperty("location_name", name);

      return gson.toJson(weatherRes);

    } catch (Exception e) {
      return "{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}";
    }
  }

  private JsonObject geocode(String city) throws Exception {
    String url = "https://geocoding-api.open-meteo.com/v1/search?name="
            + java.net.URLEncoder.encode(city, "UTF-8") + "&count=1";

    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    JsonObject res = gson.fromJson(response.body(), JsonObject.class);
    if (res.has("results")) {
      return res.getAsJsonArray("results").get(0).getAsJsonObject();
    }
    return null;
  }
}
