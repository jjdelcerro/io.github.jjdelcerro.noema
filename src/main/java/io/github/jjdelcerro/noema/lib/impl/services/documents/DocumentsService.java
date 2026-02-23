package io.github.jjdelcerro.noema.lib.impl.services.documents;

import io.github.jjdelcerro.noema.lib.AgentService;
import java.nio.file.Path;
import java.util.List;

/**
 *
 * @author jjdelcerro
 */
public interface DocumentsService extends AgentService {
  
  public static final String DOCMAPPER_REASONING_PROVIDER_URL = "DOCMAPPER_REASONING_PROVIDER_URL";
  public static final String DOCMAPPER_REASONING_PROVIDER_API_KEY = "DOCMAPPER_REASONING_PROVIDER_API_KEY";
  public static final String DOCMAPPER_REASONING_MODEL_ID = "DOCMAPPER_REASONING_MODEL_ID";
  public static final String DOCMAPPER_BASIC_PROVIDER_URL = "DOCMAPPER_BASIC_PROVIDER_URL";
  public static final String DOCMAPPER_BASIC_PROVIDER_API_KEY = "DOCMAPPER_BASIC_PROVIDER_API_KEY";
  public static final String DOCMAPPER_BASIC_MODEL_ID = "DOCMAPPER_BASIC_MODEL_ID";

  public String NAME = "Document";

  public interface DocumentResult {

    public String getDocumentId();

    public String getTitle();

    public String getSumary();

    public String getPath();

    public List<String> getCategories();

    public double getScore();
  }

  String getDocumentStructureXML(String docIdStr);

  String getPartialDocumentXML(String docIdStr, List<String> sectionIds);

  /**
   * Registra o actualiza un documento en el sistema.
   * @param structure
   * @param docPath
   */
  void insertOrReplace(DocumentStructure structure, Path docPath);

  /**
   * Búsqueda híbrida: Filtro por categorías (SQL) + Ranking por resumen
   * (Vectorial).
   * @param categories
   * @param query
   * @param maxResults
   * @return 
   */
  List<DocumentResult> search(List<String> categories, String query, int maxResults);

  List<DocumentResult> searchByCategories(List<String> categories, int maxResults);

  List<DocumentResult> searchBySummaries(String query, int maxResults);

  public void indexDocument(Path docPath);


}
