package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.scripting.modules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.scripting.AbstractScriptingModule;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.scripting.ScriptContext;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;

/**
 *
 * @author jjdelcerro
 */
public class WebModule extends AbstractScriptingModule {
  
  final HttpClient httpClient;
  final Tika tika;

  public WebModule(ScriptContext context, Agent agent) {
    super(context, agent, "web", "modulo de acceso a funciones web");
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();
    this.tika = new Tika();
  }

  /**
   * Downloads and streams lines of text extracted from a URL via Tika.
   */
  public Iterable<String> lines(String url) {
    URI uri = URI.create(url);
    if (!agent.getAccessControl().isAccessible(uri)) {
      throw new SecurityException("Access Denied to URL: " + url);
    }
    try {
      HttpRequest request = HttpRequest.newBuilder().uri(uri).header("User-Agent", "Noema-Bot/1.0").GET().build();
      HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() != 200) {
        throw new IOException("HTTP Error " + response.statusCode());
      }
      String contentType = response.headers().firstValue("Content-Type").orElse("text/plain");
      InputStream input = new ByteArrayInputStream(response.body());
      Reader reader = contentType.contains("text/plain") ? new InputStreamReader(input, StandardCharsets.UTF_8) : tika.parse(input, new Metadata());
      BufferedReader bufferedReader = new BufferedReader(reader);
      this.context.registerResource(bufferedReader);
      return () -> new AutoClosingLineIterator(bufferedReader);
    } catch (Exception e) {
      throw new RuntimeException("Error fetching URL: " + url + " (" + e.getMessage() + ")", e);
    }
  }

  /**
   * Performs web search and returns an Iterable of result maps (title, url,
   * content).
   */
  public Iterable<Map<String, String>> search(String query) {
    String apiKey = agent.getSettings().getPropertyAsString("websearch/tavily_api_key");
    if (StringUtils.isBlank(apiKey)) {
      throw new IllegalStateException("Tavily API Key is not configured.");
    }
    try {
      JsonObject body = new JsonObject();
      body.addProperty("query", query);
      body.addProperty("search_depth", "basic");
      HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://api.tavily.com/search")).header("Content-Type", "application/json").header("Authorization", "Bearer " + apiKey).POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        return Collections.emptyList();
      }
      JsonObject fullRes = JsonParser.parseString(response.body()).getAsJsonObject();
      List<Map<String, String>> list = new ArrayList<>();
      if (fullRes.has("results")) {
        fullRes.getAsJsonArray("results").forEach(el -> {
          JsonObject item = el.getAsJsonObject();
          Map<String, String> entry = new LinkedHashMap<>();
          entry.put("title", item.has("title") ? item.get("title").getAsString() : "");
          entry.put("url", item.has("url") ? item.get("url").getAsString() : "");
          entry.put("content", item.has("content") ? item.get("content").getAsString() : "");
          list.add(entry);
        });
      }
      return list;
    } catch (Exception e) {
      LOGGER.warn("Web search failed for query: {}", query, e);
      return Collections.emptyList();
    }
  }
  
}
