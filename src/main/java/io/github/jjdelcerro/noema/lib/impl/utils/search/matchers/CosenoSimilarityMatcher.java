package io.github.jjdelcerro.noema.lib.impl.utils.search.matchers;

import io.github.jjdelcerro.noema.lib.impl.services.embeddings.EmbeddingsService;
import io.github.jjdelcerro.noema.lib.impl.utils.search.SearchMatcher;

/**
 *
 * @author jjdelcerro
 */
public class CosenoSimilarityMatcher extends AbstractSearchMatcher implements SearchMatcher {
  
  final EmbeddingsService service;
  final EmbeddingsService.Embedding query_embedding;

  public CosenoSimilarityMatcher(String type, EmbeddingsService service, String query) {
    super(type);
    this.service = service;
    this.query_embedding = new EmbeddingsService.Embedding(query, service.getDefaultModel());
  }

  @Override
  public int matchScore(String text) {
    EmbeddingsService.Embedding text_embedding = new EmbeddingsService.Embedding(text, service.getDefaultModel());
    double n = this.query_embedding.cosineSimilarity(text_embedding);
    return (int) (n * 100);
  }
  
}
