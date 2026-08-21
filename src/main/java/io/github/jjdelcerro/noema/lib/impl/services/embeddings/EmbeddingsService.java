package io.github.jjdelcerro.noema.lib.impl.services.embeddings;

import dev.langchain4j.model.embedding.onnx.AbstractInProcessEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.OnnxEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.PoolingMode;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentService;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.AgentTool;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author jjdelcerro
 */
public class EmbeddingsService implements AgentService {

    public static class H2VectorUtils {

        /*
    Notas sobre H2.
    
    ```sql
    CREATE ALIAS COSINE_DISTANCE FOR "cio.github.jjdelcerro.noema.lib.impl.services.embeddings.H2VectorUtils.cosineDistance";
    ```
    
    Para definir una tabla con un campo embedding seria:
    ```
    CREATE TABLE DATOS (
        ID BIGINT AUTO_INCREMENT PRIMARY KEY,
        EMBEDDING ARRAY -- En H2, ARRAY es el tipo que mejor mapea a double[] de Java
    );
    ```
    
    Para insertar elementos en la tabla:
    ```
    INSERT INTO DATOS (EMBEDDING) VALUES (?);
    ```
    Y desde java pasarle un float[].
    
    Y usar consultas del tipo:
    ```
    SELECT * FROM DATOS 
    WHERE COSINE_DISTANCE(EMBEDDING, ?) < 0.3
    ORDER BY COSINE_DISTANCE(EMBEDDING, ?)
    LIMIT 10;
    ```
    Y igual que en la insercion pasarle float[].
    
    
    Ojo, que 0.3 seria la distancia coseno entre el EMBEDDING del campo de la tabla
    y el valor query, no la similutud. "(1-simulitud) = distancia".
    
         */
        public static double cosineDistance(Object v1, Object v2) {
            // H2 pasa los arrays como float[] si eso es lo que insertaste
            Embedding embeddingA = new Embedding((float[]) v1);
            Embedding embeddingB = new Embedding((float[]) v2);

            return embeddingA.cosineDistance(embeddingB);
        }
    }

    public static final String NAME = "Embeddings";

    private final AgentServiceFactory factory;
    private final Agent agent;
    private boolean running;
    
    private EmbeddingModel[] embeddingModels;
    private EmbeddingModel embeddingModel;

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
        agent.getCurrentConsole().printSystemLog("Cargando motor de embeddings local...");
        
        String[] resources = new String[]{
          "var/models/embeddings/paraphrase-multilingual-MiniLM-L12-v2/model_quantized.onnx",
          "var/models/embeddings/paraphrase-multilingual-MiniLM-L12-v2/tokenizer.json"
        };
        for (String resPath : resources) {
          this.agent.installResource(resPath);
        }        
        
        this.embeddingModels = new EmbeddingModel[]{
          new EmbeddingModel(
                  0, 
                  384, 
                  agent.getPaths().getAgentPath(resources[0]),
                  agent.getPaths().getAgentPath(resources[1])
          ),
          new EmbeddingModel(1, 384, AllMiniLmL6V2QuantizedEmbeddingModel.class),
          new EmbeddingModel(2, 384, BgeSmallEnV15QuantizedEmbeddingModel.class)
        };
        
        this.embeddingModel = embeddingModels[0];
        this.embeddingModel.getModel(); // Fuerza que se carge el modelo de embedding.
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
        if (StringUtils.isBlank(text)) {
            return null;
        }
        Embedding embedding = new Embedding(text, embeddingModel);
        return embedding.getFloats();
    }

    public synchronized byte[] embedAsBytes(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        return toBytes(embed(text));
    }

    public byte[] toBytes(float[] vector) {
        if (vector == null) {
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
        Embedding embeddingA = new Embedding(vectorA);
        Embedding embeddingB = new Embedding(vectorB);

        // 1.0 - distancia = similitud real (donde 1.0 es coincidencia exacta)
        return 1.0 - embeddingA.cosineDistance(embeddingB);
    }

    public double cosineDistance(float[] vectorA, float[] vectorB) {
        Embedding embeddingA = new Embedding(vectorA);
        Embedding embeddingB = new Embedding(vectorB);

        return embeddingA.cosineDistance(embeddingB);
    }    

    public EmbeddingFilter createEmbeddingFilter(String query, int limit) {
        EmbeddingFilterImpl filter = new EmbeddingFilterImpl(this, query, limit, Double.NaN);
        return filter;
    }

    /**
     *
     * Interpretacion del parametro similarity: - 1.0: Significa que son
     * idénticos (o apuntan exactamente a la misma dirección semántica). - 0.0:
     * Significa que no tienen nada que ver (son ortogonales). - -1.0: Significa
     * que son opuestos (aunque en procesamiento de texto esto es raro y suele
     * significar contextos muy diferentes). Por lo tanto: - Un minScore alto
     * (ej. 0.85) hace la búsqueda muy estricta: solo te dará resultados que
     * digan casi lo mismo que tu query. - Un minScore bajo (ej. 0.60) hace la
     * búsqueda más flexible: te dará resultados vagamente relacionados.
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

    @Override
    public void stop() {
        this.running = false;
    }

    @SuppressWarnings("UseSpecificCatch")
    public static class EmbeddingModel {

        private final int modelId;
        private final int dimensions;
        private final Class modelClass;
        private final Path modelPath;
        private final Path  tokenizerPath;
        private AbstractInProcessEmbeddingModel model;

        public EmbeddingModel(int modelId, int dimensions, Path modelPath, Path  tokenizerPath) {
            this.modelId = modelId;
            this.modelClass = null;
            this.dimensions = dimensions;
            this.modelPath = modelPath;
            this.tokenizerPath = tokenizerPath;
        }
        
        public EmbeddingModel(int modelId, int dimensions, Class modelClass) {
            this.modelId = modelId;
            this.modelClass = modelClass;
            this.dimensions = dimensions;
            this.modelPath = null;
            this.tokenizerPath = null;
        }

        protected AbstractInProcessEmbeddingModel getModel() {
            if (this.model == null) {
              if( this.modelClass != null ) {
                try {
                    this.model = (AbstractInProcessEmbeddingModel) this.modelClass.getDeclaredConstructor().newInstance();
                } catch (Exception ex) {
                    throw new RuntimeException("Can't create embedding model", ex);
                }
              } else {
                  this.model = new OnnxEmbeddingModel(
                      modelPath.toString(),
                      tokenizerPath.toString(),
                          PoolingMode.MEAN
                  );
              }
            }
            return this.model;
        }

        public float[] embed(String text) {
            return this.getModel().embed(text).content().vector();
        }

        public int dimensions() {
            return this.dimensions;
        }

        public int modelId() {
            return this.modelId;
        }
    }

    /**
     * Representa un conjunto de embeddings generados a partir de un texto.
     *
     * <p>
     * En lugar de delegar en la fragmentación interna del modelo (que aplica un
     * promedio ponderado por número de tokens), esta clase realiza su propia
     * fragmentación en chunks de texto para permitir una estrategia de búsqueda
     * basada en <strong>MaxP</strong>
     * (Maximum Passage Retrieval).</p>
     *
     * <p>
     * Esta decisión está motivada por el caso de uso de Noema: la búsqueda
     * semántica en el historial de conversaciones. Un turno largo puede
     * contener múltiples ideas o temas; la aproximación por promedio ponderado
     * diluye la señal de fragmentos específicos. En cambio, MaxP compara la
     * consulta con cada chunk del turno y se queda con la similitud máxima, lo
     * que permite encontrar coincidencias precisas aunque el texto contenga
     * información irrelevante.</p>
     *
     * <p>
     * El formato de almacenamiento incluye el identificador del modelo y la
     * dimensión de los embeddings, lo que permite detectar incompatibilidades
     * al comparar objetos de distintos modelos.</p>
     *
     * @see #cosineDistance(Embedding)
     * @see EmbeddingsService#embed(String)
     */
    public static class Embedding {

        private static final int TEXT_CHUNK_SIZE = 1024;

        private float[] data; // contiene todos los floats concatenados + metadatos al final
        private int dimension;
        private int modelId;

        public Embedding(float[] data) {
            this.data = data;
            this.dimension = (int) data[data.length - 1];
            this.modelId = (int) data[data.length - 2];
            // numChunks se calcula: (data.length - 2) / dimension
        }

        public Embedding(String text, EmbeddingModel embedding) {
            List<String> chunks = computeTextChunks(text, TEXT_CHUNK_SIZE);
            int numChunks = chunks.size();
            int dimension = embedding.dimensions();
            int modelId = embedding.modelId();
            float[] data = new float[numChunks * dimension + 2];

            for (int i = 0; i < numChunks; i++) {
                float[] emb = embedding.embed(chunks.get(i));
                // Asumimos que emb.length == dimension
                System.arraycopy(emb, 0, data, i * dimension, dimension);
            }

            data[data.length - 2] = (float) modelId;
            data[data.length - 1] = (float) dimension;

            // Delegar en el constructor que interpreta el array
            this(data);
        }

        private static List<String> computeTextChunks(String text, int maxChunkSize) {
            List<String> chunks = new ArrayList<>();
            int length = text.length();
            int start = 0;

            while (start < length) {
                int end = Math.min(start + maxChunkSize, length);

                // Si no es el final del texto, intentamos ajustar el corte
                if (end < length) {
                    // Buscar hacia atrás un signo de puntuación
                    int punctuation = findLastPunctuation(text, start, end);
                    if (punctuation > start) {
                        end = punctuation + 1; // Incluir el signo
                    } else {
                        // Si no hay puntuación, buscar el último espacio
                        int space = text.lastIndexOf(' ', end - 1);
                        if (space > start) {
                            end = space + 1; // Incluir el espacio para no cortar palabra
                        }
                        // Si no hay espacio, forzar corte en end (original)
                    }
                }

                chunks.add(text.substring(start, end));
                start = end;
            }

            return chunks;
        }

        private static int findLastPunctuation(String text, int start, int end) {
            // Buscar hacia atrás desde end-1 hasta start
            for (int i = end - 1; i > start; i--) {
                char c = text.charAt(i);
                if (c == '.' || c == '?' || c == '!') { // || c == ';' || c == ':') {
                    return i;
                }
            }
            return -1;
        }

        private double cosineSimilarity(float[] vectorA, int offsetA, float[] vectorB, int offsetB) {
            double dotProduct = 0.0;
            double normA = 0.0;
            double normB = 0.0;

            for (int i = 0; i < this.dimension; i++) {
                float a = vectorA[offsetA + i];
                float b = vectorB[offsetB + i];
                dotProduct += a * b;
                normA += a * a;
                normB += b * b;
            }

            if (normA == 0.0 || normB == 0.0) {
                return 0.0;
            }
            return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
        }

        public double cosineDistance(Embedding other) {
            // 1. Validación de compatibilidad
            if (this.modelId != other.modelId) {
                throw new IllegalArgumentException(
                        "Cannot compare embeddings from different models: "
                        + this.modelId + " vs " + other.modelId
                );
            }
            if (this.dimension != other.dimension) {
                throw new IllegalArgumentException(
                        "Cannot compare embeddings with different dimensions: "
                        + this.dimension + " vs " + other.dimension
                );
            }

            // 2. Calcular número de chunks de cada objeto
            int numChunksThis = (this.data.length - 2) / this.dimension;
            int numChunksOther = (other.data.length - 2) / other.dimension;

            // 3. Caso especial: ambos tienen 1 chunk (comparación directa)
            if (numChunksThis == 1 && numChunksOther == 1) {
                double similarity = cosineSimilarity(this.data, 0, other.data, 0);
                return 1.0 - similarity;
            }

            // 4. Identificar consulta (1 chunk) y target (múltiples chunks)
            float[] queryData;
            int queryOffset;
            Embedding target;
            int targetNumChunks;

            if (numChunksThis == 1 && numChunksOther > 1) {
                // Este objeto es la consulta, el otro es el target
                queryData = this.data;
                queryOffset = 0;
                target = other;
                targetNumChunks = numChunksOther;
            } else if (numChunksOther == 1 && numChunksThis > 1) {
                // El otro objeto es la consulta, este es el target
                queryData = other.data;
                queryOffset = 0;
                target = this;
                targetNumChunks = numChunksThis;
            } else {
                // Ambos tienen más de 1 chunk: MaxP sobre todos los pares
                // Esto lo he dejado aqui por que creo que no se debe dar pero 
                // en caso de que se de no quiero que falle con un error, aunque
                // es muy ineficiente.
                double maxSimilarity = -1.0;
                for (int i = 0; i < numChunksThis; i++) {
                    int offsetThis = i * this.dimension;
                    for (int j = 0; j < numChunksOther; j++) {
                        int offsetOther = j * other.dimension;
                        double sim = cosineSimilarity(this.data, offsetThis, other.data, offsetOther);
                        if (sim > maxSimilarity) {
                            maxSimilarity = sim;
                        }
                    }
                }
                // Si por alguna razón no se calculó ninguna similitud (no debería ocurrir)
                if (maxSimilarity == -1.0) {
                    return Double.NaN;
                }
                return 1.0 - maxSimilarity;
            }

            // 5. Calcular similitud máxima (MaxP)
            double maxSimilarity = -1.0;
            for (int i = 0; i < targetNumChunks; i++) {
                int targetOffset = i * this.dimension; // o target.dimension, es el mismo
                double similarity = cosineSimilarity(queryData, queryOffset, target.data, targetOffset);
                if (similarity > maxSimilarity) {
                    maxSimilarity = similarity;
                }
            }

            // 6. Devolver distancia coseno
            return 1.0 - maxSimilarity;
        }

        public float[] getFloats() {
            return this.data;
        }
    }

}
