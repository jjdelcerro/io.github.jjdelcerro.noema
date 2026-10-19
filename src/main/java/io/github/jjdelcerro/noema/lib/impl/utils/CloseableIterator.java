package io.github.jjdelcerro.noema.lib.impl.utils;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 *
 * @author jjdelcerro
 * @param <T>
 */
public interface CloseableIterator<T> extends Closeable, Iterator<T> {

  public static CloseableIterator empty() {
    return new CloseableIterator() {
      @Override
      public void close() throws IOException {
      }

      @Override
      public boolean hasNext() {
        return false;
      }

      @Override
      public Object next() {
        throw new NoSuchElementException();
      }
    };
  }

  public static CloseableIterator of(Object p) {
    Iterator it = Collections.singletonList(p).iterator();
    return new CloseableIterator() {
      @Override
      public void close() throws IOException {
      }

      @Override
      public boolean hasNext() {
        return it.hasNext();
      }

      @Override
      public Object next() {
        return it.next();
      }
    };
    
  }
}
