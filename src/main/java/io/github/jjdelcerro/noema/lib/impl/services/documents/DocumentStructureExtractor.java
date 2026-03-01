package io.github.jjdelcerro.noema.lib.impl.services.documents;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.impl.services.documents.DocumentStructure.DocumentStructureEntry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jjdelcerro
 */
@SuppressWarnings("UseSpecificCatch")
public class DocumentStructureExtractor {

  private static final Logger LOGGER = LoggerFactory.getLogger(DocumentStructureExtractor.class);

  private final Agent agent;

  public DocumentStructureExtractor() { // Para lanzar main de pruebas.
    this.agent = null;
  }

  public DocumentStructureExtractor(Agent agent) {
    this.agent = agent;
  }

  /**
   * Inicia el procesamiento del documento de forma asíncrona.Utiliza hilos
   * virtuales para no bloquear el flujo del agente.
   *
   * @param document
   */
  public void processDocument(Path document) {
    Thread.ofVirtual().start(() -> {
      try {
        LOGGER.info("Infiriendo la estrutura del documento '" + Objects.toString(document) + "'.");
        agent.getConsole().printSystemLog("Iniciando análisis del documento " + document.getFileName());

        doProcessDocument(document);

        // Al terminar, inyectamos un evento para que el Agente se entere proactivamente
        agent.putEvent("document", "DOCUMENT INDEXATION FINALIZED", "normal",
                "Ha terminado la indexacion del documento: `" + document.getFileName()
                + "`. Ya está disponible para búsquedas y consultas detalladas.");
        LOGGER.info("Inferencia de  la estrutura de documento '" + Objects.toString(document) + "' finalizada.");
      } catch (Exception ex) {
        LOGGER.warn("Error infiriendo la estrutura del documento '" + Objects.toString(document) + "'.", ex);
        agent.getConsole().printSystemError("Error document-strucure-extractor sobre '" + document.getFileName() + "': " + ex.getMessage());
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
      LOGGER.warn("Error infiriendo la estrutura del documento '" + Objects.toString(document) + "'.", ex);
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
    String systemPrompt = agent.getResourceAsString("prompts/documents/extract-structure.md");
    return systemPrompt;
  }

  private String getSummaryAndCategorizeSystemPrompt() {
    String systemPrompt = agent.getResourceAsString("prompts/documents/sumary-and-categorize.md");
    return systemPrompt;
  }
}
