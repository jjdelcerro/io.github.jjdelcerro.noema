package io.github.jjdelcerro.chatagent.lib.impl.docmapper;

import java.io.IOException;
import java.io.InputStream;

public class BoundedInputStream extends InputStream {
    private final InputStream in;
    private long remaining;

    public BoundedInputStream(InputStream in, long limit) {
        this.in = in;
        this.remaining = limit;
    }

    @Override
    public int read() throws IOException {
        if (remaining <= 0) {
            return -1; // EOF simulado
        }
        int result = in.read();
        if (result != -1) {
            remaining--;
        }
        return result;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (remaining <= 0) {
            return -1;
        }
        // No leer más de lo que queda disponible
        int maxToRead = (int) Math.min(len, remaining);
        int bytesRead = in.read(b, off, maxToRead);
        if (bytesRead != -1) {
            remaining -= bytesRead;
        }
        return bytesRead;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
