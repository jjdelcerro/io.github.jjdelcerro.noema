package io.github.jjdelcerro.noema.lib.impl.services.documents.tools;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.impl.services.documents.DocumentsServiceImpl;
import java.util.List;
import java.util.Map;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.ToolSpecificationBuilder;

public class GetPartialDocumentTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "get_partial_document";

  public GetPartialDocumentTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("Recupera la estructura completa de un documento inyectando el texto íntegro en las secciones solicitadas. Úsalo para leer el detalle tras haber explorado el índice.")
            .addStringParameter("docId", false, "El ID único del documento.")
            .addStringArrayParameter("sectionIds", false, "Lista de IDs de secciones a leer (ej: ['SECTION-45', 'SECTION-120']).");
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
