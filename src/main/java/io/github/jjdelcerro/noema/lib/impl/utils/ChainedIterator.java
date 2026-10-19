package io.github.jjdelcerro.noema.lib.impl.utils;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.apache.commons.io.IOUtils;

public class ChainedIterator<T> implements CloseableIterator<T> {

    private Iterator<T>[] iterators;
    private int current;

    public ChainedIterator(Iterator<T>... iterators) {
        this.iterators = iterators;
        this.current = 0;
    }

    @Override
    public boolean hasNext() {
        if( this.iterators == null ) {
            return false;
        }
        while (this.current < this.iterators.length) {
            Iterator<T> it = this.iterators[this.current];
            if( it!=null ) {
                if (it.hasNext()) {
                    return true;
                }
            }
            this.current++;
        }
        return false;
    }

    @Override
    public T next() {
        if( this.iterators == null ) {
            return null;
        }
        Iterator<T> it = this.iterators[this.current];
        if( it==null ) {
          throw new NoSuchElementException();
        }
        return it.next();
    }

    @Override
    public void remove() {
        Iterator<T> it = this.iterators[this.current];
        if( it==null ) {
            return;
        }
        it.remove();
    }

    @Override
    public void close() throws IOException {
        if( this.iterators == null ) {
            return;
        }
        for (Iterator<T> iterator : iterators) {
          if( iterator instanceof Closeable closeable) {
            IOUtils.closeQuietly(closeable);
          }
        }
        this.iterators = null;
        this.current = -1;
    }
    
    public int getCurrent() {
        return current;
    }

    
}
