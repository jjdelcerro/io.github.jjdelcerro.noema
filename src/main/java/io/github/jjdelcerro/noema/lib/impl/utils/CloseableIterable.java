package io.github.jjdelcerro.noema.lib.impl.utils;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author jjdelcerro
 */
public interface CloseableIterable<T> extends Closeable, Iterable<T>{
  
  public static CloseableIterable empty() {
    return new CloseableIterable() {
      @Override
      public void close() throws IOException {
      }

      @Override
      public Iterator iterator() {
        return Collections.emptyIterator();
      }
    };
  }
    
  public static CloseableIterable of(CloseableIterator it) {
    return new CloseableIterable() {
      @Override
      public void close() throws IOException {
        IOUtils.closeQuietly(it);
      }

      @Override
      public Iterator iterator() {
        return it;
      }
    };
  }
}
