package io.github.jjdelcerro.noema.lib.impl.utils.search.chunkers;

import io.github.jjdelcerro.noema.lib.impl.utils.search.SearchChunker;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author jjdelcerro
 */
public class ChunkerLines extends AbstractSearchChunker implements SearchChunker {
  
  private BufferedReader reader;
  private int line;
  

  public ChunkerLines(String type) {
    super(type);
    this.reader = null;
  }

  @Override
  public SearchChunker open(Reader reader) {
    this.reader = (reader instanceof BufferedReader)? (BufferedReader)reader :new BufferedReader(reader); 
    this.line = 0;
    return this;
  }

  @Override
  public String read() throws IOException {
    this.line++;
    return this.reader.readLine();
  }

  @Override
  public void close() throws Exception {
    IOUtils.close(reader);
    this.reader = null;
    this.line = 0;
  }

  @Override
  public SearchChunkerLocation getLocation() {
    return new SearchChunkerLocationBase(line, line+1);
  }
  
}
