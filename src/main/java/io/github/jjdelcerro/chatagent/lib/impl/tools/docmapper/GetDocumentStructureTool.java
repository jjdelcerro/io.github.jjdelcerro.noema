package io.github.jjdelcerro.chatagent.lib.impl.tools.docmapper;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.impl.AgentImpl;
import io.github.jjdelcerro.chatagent.lib.tools.AgenteTool;
import java.util.Map;

public class GetDocumentStructureTool implements AgenteTool {

  private final AgentImpl agent;
  private final Gson gson = new Gson();

  public GetDocumentStructureTool(Agent agent) {
    this.agent = (AgentImpl) agent;
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name("get_document_structure")
            .description("Recupera el esquema jerárquico (índice) de un documento en formato XML. Incluye títulos, niveles y resúmenes de secciones para que decidas qué leer.")
            .addParameter("docId", JsonSchemaProperty.STRING, JsonSchemaProperty.description("El ID único del documento (ej: 'DOCUMENT-10')."))
            .build();
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
      String docId = args.get("docId");
      return agent.getDocumentServices().getDocumentStructureXML(docId);
    } catch (Exception e) {
      return "<error>" + e.getMessage() + "</error>";
    }
  }
}
