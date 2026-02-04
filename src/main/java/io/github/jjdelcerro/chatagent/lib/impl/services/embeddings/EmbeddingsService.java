package io.github.jjdelcerro.chatagent.lib.impl.services.embeddings;

import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.AgentService;
import io.github.jjdelcerro.chatagent.lib.AgentServiceFactory;
import io.github.jjdelcerro.chatagent.lib.AgentTool;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author jjdelcerro
 */
public class EmbeddingsService implements AgentService {

  public static final String NAME = "Embeddings";

  private final AgentServiceFactory factory;
  private final Agent agent;
  private boolean running;
  private AllMiniLmL6V2EmbeddingModel embeddingModel;

  public EmbeddingsService(AgentServiceFactory factory, Agent agent) {
    this.factory = factory;
    this.agent = agent;
    this.running = false;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public AgentServiceFactory getFactory() {
    return this.factory;
  }

  @Override
  public void start() {
    agent.getConsole().println(">>> Cargando motor de embeddings local...");
    this.embeddingModel = new AllMiniLmL6V2EmbeddingModel();
    this.running = true;
  }

  @Override
  public boolean canStart() {
    return true;
  }

  @Override
  public boolean isRunning() {
    return this.running;
  }

  @Override
  public Agent.ModelParameters getModelParameters(String name) {
    return null;
  }

  @Override
  public List<AgentTool> getTools() {
    return null;
  }

  public synchronized float[] embed(String text) {
    if( StringUtils.isBlank(text) ) {
      return null;
    }
    float[] vector = embeddingModel.embed(text).content().vector();
    return vector;
  }

  public synchronized byte[] embedAsBytes(String text) {
    if( StringUtils.isBlank(text) ) {
      return null;
    }
    float[] vector = embeddingModel.embed(text).content().vector();
    return toBytes(vector);
  }

  public byte[] toBytes(float[] vector) {
    if( vector == null ) {
      return null;
    }
    ByteBuffer buffer = ByteBuffer.allocate(vector.length * 4);
    buffer.asFloatBuffer().put(vector);
    return buffer.array();
  }

  public float[] fromBytes(byte[] bytes) {
    if (bytes == null) {
      return null;
    }
    FloatBuffer buffer = ByteBuffer.wrap(bytes).asFloatBuffer();
    float[] vector = new float[buffer.remaining()];
    buffer.get(vector);
    return vector;
  }

  public double cosineSimilarity(float[] vectorA, float[] vectorB) {
    if (vectorA.length != vectorB.length) {
      return 0.0;
    }
    double dotProduct = 0.0;
    double normA = 0.0;
    double normB = 0.0;
    for (int i = 0; i < vectorA.length; i++) {
      dotProduct += vectorA[i] * vectorB[i];
      normA += Math.pow(vectorA[i], 2);
      normB += Math.pow(vectorB[i], 2);
    }
    return (normA == 0 || normB == 0) ? 0.0 : dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
  }

  public EmbeddingFilter createEmbeddingFilter(String query, int limit) {
    EmbeddingFilterImpl filter = new EmbeddingFilterImpl(this, query, limit, Double.NaN);
    return filter;
  }
  
  /**
   * 
   * Interpretacion del parametro similarity:
   * - 1.0: Significa que son idénticos (o apuntan exactamente a la misma dirección semántica).
   * - 0.0: Significa que no tienen nada que ver (son ortogonales).
   * - -1.0: Significa que son opuestos (aunque en procesamiento de texto esto es raro y suele significar contextos muy diferentes).
   * Por lo tanto:
   * - Un minScore alto (ej. 0.85) hace la búsqueda muy estricta: solo te dará resultados que digan casi lo mismo que tu query.
   * - Un minScore bajo (ej. 0.60) hace la búsqueda más flexible: te dará resultados vagamente relacionados.
   * 
   * @param query
   * @param limit
   * @param similarity
   * @return 
   */
  public EmbeddingFilter createEmbeddingFilter(String query, int limit, double similarity) {
    EmbeddingFilterImpl filter = new EmbeddingFilterImpl(this, query, limit, similarity);
    return filter;
  }
  
}
