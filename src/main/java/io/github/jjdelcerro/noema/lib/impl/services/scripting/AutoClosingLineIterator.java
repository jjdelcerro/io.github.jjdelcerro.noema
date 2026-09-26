package io.github.jjdelcerro.noema.lib.impl.services.scripting;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author jjdelcerro
 */
public class AutoClosingLineIterator implements Iterator<String> {

  private final BufferedReader reader;
  private String nextLine = null;
  private boolean finished = false;

  public AutoClosingLineIterator(BufferedReader reader) {
    this.reader = Objects.requireNonNull(reader, "BufferedReader cannot be null");
    advance();
  }

  private void advance() {
    if (finished) {
      return;
    }
    try {
      nextLine = reader.readLine();
      if (nextLine == null) {
        close();
      }
    } catch (IOException e) {
      close();
      throw new RuntimeException("Error reading line: " + e.getMessage(), e);
    }
  }

  private void close() {
    finished = true;
    nextLine = null;
    IOUtils.closeQuietly(reader);
  }

  @Override
  public boolean hasNext() {
    return nextLine != null;
  }

  @Override
  public String next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    String current = nextLine;
    advance();
    return current;
  }
}
