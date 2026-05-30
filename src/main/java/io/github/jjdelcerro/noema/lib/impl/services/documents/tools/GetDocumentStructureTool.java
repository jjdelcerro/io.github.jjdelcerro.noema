package io.github.jjdelcerro.noema.lib.impl.services.documents.tools;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.impl.services.documents.DocumentsServiceImpl;
import java.util.Map;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.ToolSpecificationBuilder;

public class GetDocumentStructureTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "get_document_structure";

  public GetDocumentStructureTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("Recupera el esquema jerárquico (índice) de un documento en formato XML. Incluye títulos, niveles y resúmenes de secciones para que decidas qué leer.")
            .addStringParameter("docId", false, "El ID único del documento (ej: 'DOCUMENT-10').");
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      DocumentsServiceImpl service = (DocumentsServiceImpl) this.agent.getService(DocumentsServiceImpl.NAME);

      Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
      String docId = args.get("docId");
      return service.getDocumentStructureXML(docId);
    } catch (Exception e) {
      return "<error>" + e.getMessage() + "</error>";
    }
  }
}
