package io.github.jjdelcerro.chatagent.lib.impl.services.docmapper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.jjdelcerro.chatagent.lib.impl.AgentImpl;
import io.github.jjdelcerro.chatagent.lib.impl.services.docmapper.DocumentStructure.DocumentStructureEntry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * @author jjdelcerro
 */
public class DocumentMapper {

  private final AgentImpl agent;

  public DocumentMapper() { // Para lanzar main de pruebas.
    this.agent = null;
  }

  public DocumentMapper(AgentImpl agent) {
    this.agent = agent;
  }

  /**
   * Inicia el procesamiento del documento de forma asíncrona.Utiliza hilos
   * virtuales para no bloquear el flujo del agente.
   * @param document
   */
  public void processDocument(Path document) {
    // Usamos la factoría de hilos virtuales de Java 21
    Thread.ofVirtual().start(() -> {
      try {
        agent.getConsole().println("DocMapper: Iniciando análisis de " + document.getFileName());

        doProcessDocument(document);

        // Al terminar, inyectamos un evento para que el Agente se entere proactivamente
        agent.putEvent("DocMapper", "normal",
                "He terminado de procesar el documento: " + document.getFileName()
                + ". Ya está disponible para búsquedas y consultas detalladas.");

      } catch (Exception ex) {
        agent.getConsole().printerrorln("Error crítico en DocMapper para " + document.getFileName() + ": " + ex.getMessage());
        ex.printStackTrace(); // Log para depuración
      }
    });
  }

  /**
   * Convierte un JsonArray de GSON en una lista de Strings. Útil para recuperar
   * las categorías devueltas por el LLM.
   */
  private List<String> toListOfStrings(JsonArray array) {
    if (array == null || array.isJsonNull()) {
      return new ArrayList<>();
    }

    List<String> list = new ArrayList<>();
    for (JsonElement element : array) {
      // Verificamos que sea un primitivo (String) antes de añadirlo
      if (element.isJsonPrimitive()) {
        list.add(element.getAsString());
      }
    }
    return list;
  }

  public void doProcessDocument(Path document) {
    try {
      DocumentsServiceImpl service = (DocumentsServiceImpl) this.agent.getService(DocumentsServiceImpl.NAME);

      DocumentStructure structure = this.doExtractStructure(document);
      service.insertOrReplace(structure, document);

    } catch (Exception ex) {
      // TODO: log and user message.
    }
  }

  private DocumentStructure doExtractStructure(Path document) throws IOException {
    TextContent lines = new TextContent();
    lines.load(document, StandardCharsets.UTF_8);
    DocumentStructure structure = DocumentStructure.from(document);
    if (structure == null) {
      String doc_csv = lines.toCSV();
      String struct_csv = this.agent.callChatModel(
              "DOCMAPPER_REASONING_LLM",
              this.getExtractStructureSystemPrompt(),
              doc_csv
      );
      structure = DocumentStructure.from(struct_csv);
      structure.save(document);
    }
    for (DocumentStructureEntry entry : structure) {
      if (entry.isEmpty() || StringUtils.isNotBlank(entry.getSummary())) {
        continue;
      }
      String contents = entry.getContents(document);
      if (contents == null) {
        continue;
      }
      JsonObject response = this.agent.callChatModelAsJson(
              "DOCMAPPER_BASIC_LLM",
              this.getSummaryAndCategorizeSystemPrompt(),
              contents
      );
      entry.setSummary(response.get("summary").getAsString());
      entry.setCategories(toListOfStrings(response.get("categories").getAsJsonArray()));
      structure.save(document);
    }
    if (StringUtils.isBlank(structure.getSummary())) {
      String text = structure.joinAll();
      JsonObject response = this.agent.callChatModelAsJson(
              "DOCMAPPER_BASIC_LLM",
              this.getSummaryAndCategorizeSystemPrompt(),
              text
      );
      structure.setSummary(response.get("summary").getAsString());
      structure.setCategories(toListOfStrings(response.get("categories").getAsJsonArray()));
      structure.save(document);
    }
    structure.consolide(document);
    if (structure.isDirty()) {
      structure.save(document);
    }
    return structure;
  }

  private String getExtractStructureSystemPrompt() {
    String systemPrompt = agent.getResourceAsString("prompts/docmapper/prompt-extract-structure.md");
    return systemPrompt;
  }

  private String getSummaryAndCategorizeSystemPrompt() {
    String systemPrompt = agent.getResourceAsString("prompts/docmapper/prompt-sumary-and-categorize.md");
    return systemPrompt;
  }
}
