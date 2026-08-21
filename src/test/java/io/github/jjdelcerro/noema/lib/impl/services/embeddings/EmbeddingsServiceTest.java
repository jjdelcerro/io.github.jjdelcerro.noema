package io.github.jjdelcerro.noema.lib.impl.services.embeddings;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentPaths;
import io.github.jjdelcerro.noema.lib.FakeConsole;
import io.github.jjdelcerro.noema.lib.impl.AgentImpl;
import io.github.jjdelcerro.noema.lib.impl.AgentPathsImpl;
import io.github.jjdelcerro.noema.lib.impl.persistence.FakeEpisodicMemory;
import io.github.jjdelcerro.noema.lib.impl.settings.AgentSettingsImpl;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EmbeddingsServiceTest {

  @TempDir
  Path tempDir;

  private EmbeddingsService embeddingsService;

  @BeforeEach
  public void setUp() {
    AgentPaths paths = new AgentPathsImpl(tempDir);
    paths.setupHierarchy();
    AgentSettings settings = new AgentSettingsImpl(paths);

    Agent agent = new AgentImpl(null, null, settings, new FakeConsole(), new FakeEpisodicMemory(), null);
    EmbeddingsServiceFactory factory = new EmbeddingsServiceFactory();

    embeddingsService = new EmbeddingsService(factory, agent);
    embeddingsService.start();
  }

  @Test
  @DisplayName("El servicio debe arrancar e inicializar el modelo ONNX local en memoria")
  public void testLifecycle() {
    assertTrue(embeddingsService.canStart());
    assertTrue(embeddingsService.isRunning());
    assertEquals(EmbeddingsService.NAME, embeddingsService.getName());
  }

  @Test
  @DisplayName("embed() y embedAsBytes() con entradas nulas o vacias deben retornar null")
  public void testEmbedNullAndEmpty() {
    assertNull(embeddingsService.embed(null));
    assertNull(embeddingsService.embed(""));
    assertNull(embeddingsService.embed("   \n\t  "));

    assertNull(embeddingsService.embedAsBytes(null));
    assertNull(embeddingsService.embedAsBytes(""));
  }

  @Test
  @DisplayName("Un texto corto debe generar un vector con dimension 384 y metadatos validos al final")
  public void testEmbedNormalTextAndMetadata() {
    String text = "Prueba de generacion de embeddings en Noema";
    float[] vector = embeddingsService.embed(text);

    assertNotNull(vector);

    int dimension = 384;
    int expectedChunks = 1;
    int expectedLength = (expectedChunks * dimension) + 2;

    assertEquals(expectedLength, vector.length);

    // Metadatos al final del array: [..., modelId, dimension]
    int modelId = (int) vector[vector.length - 2];
    int dim = (int) vector[vector.length - 1];

    assertEquals(0, modelId, "El modelId por defecto debe ser 0");
    assertEquals(384, dim, "La dimension por defecto debe ser 384");
  }

  @Test
  @DisplayName("Serializacion binaria: toBytes y fromBytes deben ser bidireccionales y exactos")
  public void testSerializationRoundTrip() {
    assertNull(embeddingsService.toBytes(null));
    assertNull(embeddingsService.fromBytes(null));

    float[] originalVector = embeddingsService.embed("Verificacion de serializacion binaria");
    assertNotNull(originalVector);

    byte[] bytes = embeddingsService.toBytes(originalVector);
    assertNotNull(bytes);
    assertEquals(originalVector.length * 4, bytes.length);

    float[] deserializedVector = embeddingsService.fromBytes(bytes);
    assertNotNull(deserializedVector);
    assertArrayEquals(originalVector, deserializedVector, 0.00001f);
  }

  @Test
  @DisplayName("Distancia coseno sobre textos identicos debe ser 0.0")
  public void testCosineDistanceIdenticalText() {
    String text = "Arquitectura de memoria continua para agentes inteligentes";
    float[] vecA = embeddingsService.embed(text);
    float[] vecB = embeddingsService.embed(text);

    double distance = embeddingsService.cosineSimilarity(vecA, vecB);

    assertEquals(1.0, distance, 0.001, "La distancia coseno entre vectores identicos debe ser 0.0");
  }

  @Test
  @DisplayName("Textos de mas de 1024 caracteres deben dividirse en chunks y evaluarse con MaxP")
  public void testChunkingAndMaxP() {
    String phraseTarget = "El fallo critico ocurrio por saturacion en el pool de conexiones de base de datos.";
    String filler = StringUtils.repeat("Este es un texto largo de relleno para forzar la particion en chunks. ", 30);

    // Texto largo (> 2000 caracteres) que contiene la frase clave
    String longDocument = filler + "\n" + phraseTarget + "\n" + filler;
    assertTrue(longDocument.length() > 1024);

    float[] longDocVector = embeddingsService.embed(longDocument);
    assertNotNull(longDocVector);

    int dimension = 384;
    int numChunks = (longDocVector.length - 2) / dimension;
    assertTrue(numChunks > 1, "El documento largo debe haberse dividido en al menos 2 chunks");

    // Vector de consulta de 1 chunk
    float[] queryVector = embeddingsService.embed("saturacion pool conexiones");
    assertNotNull(queryVector);

    // Distancia MaxP
    double distanceToMatchingQuery = embeddingsService.cosineSimilarity(queryVector, longDocVector);

    // Consulta no relacionada
    float[] unrelatedQuery = embeddingsService.embed("receta para cocinar pastel de manzana");
    double distanceToUnrelatedQuery = embeddingsService.cosineSimilarity(unrelatedQuery, longDocVector);

    // A menor distancia, mayor relevancia
    assertTrue(distanceToMatchingQuery > distanceToUnrelatedQuery,
            "La consulta clave debe tener menor distancia coseno (mas similar) que la consulta irrelevante");
  }

  @Test
  @DisplayName("Comparar vectores con dimensiones o modelos incompatibles debe lanzar IllegalArgumentException")
  public void testIncompatibleVectors() {
    float[] vecA = new float[]{0.1f, 0.2f, 0.0f, 384.0f}; // modelId 0, dim 384 (datos dummy)
    float[] vecDifferentDim = new float[]{0.1f, 0.2f, 0.0f, 512.0f}; // modelId 0, dim 512
    float[] vecDifferentModel = new float[]{0.1f, 0.2f, 1.0f, 384.0f}; // modelId 1, dim 384

    assertThrows(IllegalArgumentException.class, () -> {
      embeddingsService.cosineSimilarity(vecA, vecDifferentDim);
    });

    assertThrows(IllegalArgumentException.class, () -> {
      embeddingsService.cosineSimilarity(vecA, vecDifferentModel);
    });
  }
  
  @Test
  @DisplayName("EmbeddingFilter debe retornar los elementos mas relevantes ordenados")
  public void testEmbeddingFilterRanking() {
    class Doc {
      private final float[] vect;
      private final String text;
      private final String id;
      Doc(String id, String text) {
        this.id = id;
        this.text = text;
        this.vect = embeddingsService.embed(text);
      }
    }
    
    List<Doc> docs = new ArrayList<>();
    docs.add(new Doc("DOC_RED","Configuracion segura del puerto HTTP y servidor web"));
    docs.add(new Doc("DOC_SEGURIDAD","Politica de seguridad y control de acceso a archivos"));
    docs.add(new Doc("DOC_COCINA","Receta tradicional de paella valenciana"));
    docs.add(new Doc("DOC_ARCHIVOS","Modificacion de archivos y creacion de directorios"));

    String query = "sandbox permisos escritura";
    EmbeddingFilter<String> filter = embeddingsService.createEmbeddingFilter(query, 2, 0.2);
    for (Doc doc : docs) {
      filter.add(doc.vect, doc.id);
    }

    List<String> results = filter.get();

    assertNotNull(results);
    assertEquals(2, results.size());
    assertEquals(docs.get(1).id, results.get(0), "El primer resultado debe ser "+docs.get(1).id);
    assertEquals(docs.get(3).id, results.get(1), "El segundo resultado debe ser "+docs.get(3).id);    
  }
}
