package io.github.jjdelcerro.noema.lib.impl.services.documents.tools;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.impl.services.documents.DocumentsServiceImpl;
import java.util.List;
import java.util.Map;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;

public class GetPartialDocumentTool extends AbstractAgentTool {
  public static final String TOOL_NAME = "get_partial_document";

  public GetPartialDocumentTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name(TOOL_NAME)
            .description("Recupera la estructura completa de un documento inyectando el texto íntegro en las secciones solicitadas. Úsalo para leer el detalle tras haber explorado el índice.")
            .addParameter("docId", JsonSchemaProperty.STRING, JsonSchemaProperty.description("El ID único del documento."))
            .addParameter("sectionIds", JsonSchemaProperty.type("array"),
                    JsonSchemaProperty.items(JsonSchemaProperty.STRING),
                    JsonSchemaProperty.description("Lista de IDs de secciones a leer (ej: ['SECTION-45', 'SECTION-120']).")
            )
            .build();
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      DocumentsServiceImpl service = (DocumentsServiceImpl) this.agent.getService(DocumentsServiceImpl.NAME);

      Map<String, Object> args = gson.fromJson(jsonArguments, Map.class);
      String docId = (String) args.get("docId");
      List<String> sectionIds = (List<String>) args.get("sectionIds");

      return service.getPartialDocumentXML(docId, sectionIds);
    } catch (Exception e) {
      return "<error>" + e.getMessage() + "</error>";
    }
  }
}
