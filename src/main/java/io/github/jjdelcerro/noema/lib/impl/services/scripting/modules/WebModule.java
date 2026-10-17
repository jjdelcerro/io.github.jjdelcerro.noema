package io.github.jjdelcerro.noema.lib.impl.services.scripting.modules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.impl.services.scripting.AutoClosingLineIterator;
import io.github.jjdelcerro.noema.lib.api.services.scripting.AbstractScriptModule;
import io.github.jjdelcerro.noema.lib.impl.services.scripting.ScriptContextImpl;
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
public class WebModule extends AbstractScriptModule {

  private static final int DEFAULT_TIMEOUT_IN_SECS = 30;
  
  final HttpClient httpClient;
  final Tika tika;

  public WebModule(ScriptContextImpl context, Agent agent) {
    super(context, agent, "web", "modulo de acceso a funciones web");
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();
    this.tika = new Tika();
  }

  @Override
  public String help() {
    return """
[agent.web API]
• lines(url): Iterable<String> (lazy stream, HTML/PDF extraído limpio vía Tika)
  -> agent.web.lines('https://example.org/doc.pdf').take(20).each { println it }
• search(query): Iterable<Map[title, url, content]> (búsqueda web)
  -> agent.web.search('langchain4j').each { println "${it.title}: ${it.url}" }

Todos los metodos aceptan un segundo parametro opcional con el timeout en segundos.
""";
  }

  public Iterable<String> lines(String url) {
    return this.lines(url, DEFAULT_TIMEOUT_IN_SECS);
  }

  /**
   * Downloads and streams lines of text extracted from a URL via Tika.
   */
  public Iterable<String> lines(String url, int timeoutsecs) {

    URI uri = URI.create(url);
    if (!agent.getAccessControl().isAccessible(uri)) {
      throw new SecurityException("Access Denied to URL: " + url);
    }
    try {
      HttpRequest request = HttpRequest.newBuilder()
              .uri(uri)
              .header("User-Agent", "Noema-Bot/1.0")
              .timeout(Duration.ofSeconds(timeoutsecs))
              .GET()
              .build();
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

  public Iterable<Map<String, String>> search(String query) {
    return this.search(query, DEFAULT_TIMEOUT_IN_SECS);
  }

  /**
   * Performs a web search using the default provider (DeepSeek).
   */
  public Iterable<Map<String, String>> search(String query, int timeoutsecs) {
    return this.search_deepseek(query, timeoutsecs);
  }

  public Iterable<Map<String, String>> search_tavily(String query) {
    return this.search_tavily(query, DEFAULT_TIMEOUT_IN_SECS);
  }

  /**
   * Performs web search against Tavily and returns an Iterable of result maps
   * (title, url, content).
   */
  public Iterable<Map<String, String>> search_tavily(String query, int timeoutsecs) {
    String apiKey = agent.getSettings().getPropertyAsString("websearch/tavily_api_key");
    if (StringUtils.isBlank(apiKey)) {
      throw new IllegalStateException("Tavily API Key is not configured.");
    }
    try {
      JsonObject body = new JsonObject();
      body.addProperty("query", query);
      body.addProperty("search_depth", "basic");
      HttpRequest request = HttpRequest.newBuilder()
              .uri(URI.create("https://api.tavily.com/search")) // FIXME: use settings
              .timeout(Duration.ofSeconds(timeoutsecs))
              .header("Content-Type", "application/json")
              .header("Authorization", "Bearer " + apiKey)
              .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
              .build();
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

  public Iterable<Map<String, String>> search_deepseek(String query) {
    return this.search_deepseek(query, DEFAULT_TIMEOUT_IN_SECS);
  }

  /**
   * Performs web search against DeepSeek and returns an Iterable of result maps
   * (title, url, content). The content field is filled with the summary
   * supplied by the API, which is the extended text intended to be read by a
   * model (the snippet is UI oriented and too short).
   */
  public Iterable<Map<String, String>> search_deepseek(String query, int timeoutsecs) {
    String apiKey = agent.getSettings().getPropertyAsString("websearch/deepseek_api_key");
    if (StringUtils.isBlank(apiKey)) {
      throw new IllegalStateException("DeepSeek API Key is not configured.");
    }
    try {
      JsonObject body = new JsonObject();
      body.addProperty("query", query);
      body.addProperty("need_content", false);
      HttpRequest request = HttpRequest.newBuilder()
              .uri(URI.create("https://api.deepseek.com/v1/tools/search"))
              .timeout(Duration.ofSeconds(timeoutsecs))
              .header("Content-Type", "application/json")
              .header("Authorization", "Bearer " + apiKey)
              .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
              .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new IOException("HTTP Error " + response.statusCode() + ": " + response.body());
      }
      JsonObject fullRes = JsonParser.parseString(response.body()).getAsJsonObject();
      List<Map<String, String>> list = new ArrayList<>();
      if (fullRes.has("results")) {
        fullRes.getAsJsonArray("results").forEach(el -> {
          JsonObject item = el.getAsJsonObject();
          Map<String, String> entry = new LinkedHashMap<>();
          entry.put("title", deepseekString(item, "title"));
          entry.put("url", deepseekString(item, "url"));
          entry.put("content", deepseekContent(item));
          list.add(entry);
        });
      }
      return list;
    } catch (Exception e) {
      LOGGER.warn("Web search (DeepSeek) failed for query: {}", query, e);
      throw new RuntimeException("DeepSeek web search failed for query: " + query + " (" + e.getMessage() + ")", e);
    }
  }

  private static String deepseekString(JsonObject item, String name) {
    if (!item.has(name) || item.get(name).isJsonNull()) {
      return "";
    }
    return item.get(name).getAsString();
  }

  private static String deepseekContent(JsonObject item) {
    String summary = deepseekString(item, "summary");
    if (StringUtils.isNotBlank(summary)) {
      return summary;
    }
    String snippet = deepseekString(item, "snippet");
    if (StringUtils.isNotBlank(snippet)) {
      return snippet;
    }
    return deepseekString(item, "content");
  }

}
