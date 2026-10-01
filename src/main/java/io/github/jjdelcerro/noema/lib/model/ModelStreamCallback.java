package io.github.jjdelcerro.noema.lib.model;

/**
 *
 * @author jjdelcerro
 */
public interface ModelStreamCallback {

  public void StreamReasoning(String s);

  public void StreamResponse(String s);

  public void streamingFinished();

  public boolean streamingUsed();

  public void setStreamingUsed(boolean used);
  
}
