package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.web;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.AbstractPaginatedAgentTool;
import org.apache.commons.io.IOUtils;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

public class WebGetTikaTool extends AbstractPaginatedAgentTool {

  public static final String TOOL_NAME = "web_get_content";

  private final HttpClient httpClient;
  private final Tika tika = new Tika();
  private static final int CONNECTION_TIMEOUT_SECONDS = 15;

  public WebGetTikaTool(Agent agent) {
    super(agent);
    this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECTION_TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name(TOOL_NAME)
            .description("Descarga y extrae texto de URLs (HTML, PDF, DOCX, etc).\n" +
                    "\n" +
                    getShortPaginationInstruction())
            .addParameter("url", JsonSchemaProperty.STRING, 
                    JsonSchemaProperty.description("URL completa del recurso web a descargar y procesar."))
            .build();
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_READ;
  }

  @Override
  @SuppressWarnings("UseSpecificCatch")
  public String execute(String jsonArguments) {
    String urlForLog = "unknown";
    try {
      ReadArgs args = gson.fromJson(jsonArguments, ReadArgs.class);

      if (args.url == null || args.url.trim().isEmpty()) {
        return formatErrorResponse("El parámetro 'url' es obligatorio.");
      }

      String url = args.url.trim();
      urlForLog = url;

      if (!agent.getAccessControl().isAccessible(URI.create(url))) {
        return formatErrorResponse("URL no accesible: " + url + ". Verifica las políticas de access control.");
      }

      String fileName = "web_" + UUID.randomUUID().toString().substring(0, 8) + ".txt";
      Path tempFile = agent.getPaths().getTempFolder().resolve(fileName);

      downloadAndExtract(url, tempFile);

      String resourceId = getIdFromPath(tempFile);
      if (resourceId == null) {
        return formatErrorResponse("Error generando resource_id para el contenido web descargado.");
      }

      return servePaginatedResource(resourceId);

    } catch (HttpResponseException e) {
      return formatHttpResponseError(e.getStatusCode(), e.getMessage(), urlForLog);
    } catch (IOException e) {
      return formatNetworkError(e, urlForLog);
    } catch (Exception e) {
      LOGGER.warn("Error descargando contenido web, url=" + urlForLog, e);
      return formatErrorResponse("Error procesando URL: " + e.getMessage());
    }
  }

  private void downloadAndExtract(String url, Path tempFile) throws IOException {
    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "Noema-Bot/1.0")
            .GET()
            .build();

    HttpResponse<byte[]> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Thread interrumpido durante la descarga", e);
    }

    if (response.statusCode() != 200) {
      throw new HttpResponseException(
              "HTTP " + response.statusCode(), 
              getHttpStatusMessage(response.statusCode()),
              response.statusCode()
      );
    }

    String contentType = getContentType(response);

    try (InputStream input = new ByteArrayInputStream(response.body());
         Reader reader = prepareReader(input, contentType);
         Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
      
      IOUtils.copy(reader, writer);
    }
  }

  private Reader prepareReader(InputStream input, String contentType) throws IOException {
    if (shouldParseWithTika(contentType)) {
      Metadata metadata = new Metadata();
      metadata.set(Metadata.CONTENT_TYPE, contentType);
      return tika.parse(input, metadata);
    } else {
      return new InputStreamReader(input, StandardCharsets.UTF_8);
    }
  }

  private boolean shouldParseWithTika(String contentType) {
    return !(contentType.contains("json") 
          || contentType.contains("xml") 
          || contentType.contains("text/plain"));
  }

  private String getContentType(HttpResponse<byte[]> response) {
    return response.headers()
            .firstValue("Content-Type")
            .orElse("text/plain")
            .toLowerCase();
  }

  private String formatHttpResponseError(int statusCode, String message, String url) {
    String errorDescription = getHttpStatusMessage(statusCode);
    return formatErrorResponse("HTTP Error " + statusCode + ": " + errorDescription + " (" + url + ")");
  }

  private String formatNetworkError(IOException e, String url) {
    String message = e.getMessage().toLowerCase();
    
    if (message.contains("timeout") || message.contains("timed out")) {
      return formatErrorResponse("Connection timeout: No se pudo conectar a " + url + " después de " + CONNECTION_TIMEOUT_SECONDS + " segundos. El servidor puede estar sobrecargado o inaccesible.");
    } else if (message.contains("connection refused") || message.contains("refused")) {
      return formatErrorResponse("Connection refused: El servidor en " + url + " rechazó la conexión. Posiblemente el servicio no está ejecutándose.");
    } else if (message.contains("unknown host") || message.contains("dns")) {
      return formatErrorResponse("DNS resolution failed: No se puede resolver el host de " + url + ". Verifica que la URL es correcta y hay conectividad de red.");
    } else if (message.contains("ssl") || message.contains("tls") || message.contains("certificate")) {
      return formatErrorResponse("SSL/TLS error: Error en la conexión segura con " + url + ". El certificado SSL del servidor puede no ser válido.");
    } else {
      return formatErrorResponse("Network error: " + e.getMessage() + " (" + url + ")");
    }
  }

  private String getHttpStatusMessage(int statusCode) {
    return switch (statusCode) {
      case 400 -> "Bad Request - La solicitud es inválida";
      case 401 -> "Unauthorized - Se requiere autenticación";
      case 403 -> "Forbidden - Acceso denegado";
      case 404 -> "Not Found - El recurso no existe";
      case 408 -> "Request Timeout - El servidor tardó demasiado en responder";
      case 429 -> "Too Many Requests - Rate limit excedido";
      case 500 -> "Internal Server Error - Error en el servidor";
      case 502 -> "Bad Gateway - Error en el servidor proxy";
      case 503 -> "Service Unavailable - El servicio no está disponible temporalmente";
      case 504 -> "Gateway Timeout - Timeout en el servidor proxy";
      default -> "HTTP Error " + statusCode;
    };
  }

  private static class HttpResponseException extends IOException {
    private final int statusCode;

    public HttpResponseException(String message, int statusCode) {
      super(message);
      this.statusCode = statusCode;
    }

    public HttpResponseException(String message, String statusText, int statusCode) {
      super(message + " - " + statusText);
      this.statusCode = statusCode;
    }

    public int getStatusCode() {
      return statusCode;
    }
  }

  private static class ReadArgs {
    String url;
  }
}
