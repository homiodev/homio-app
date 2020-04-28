package org.touchhome.bundle.cloud.impl;

import lombok.Getter;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import java.util.Arrays;

public class ServletOutputStreamAdapter extends ServletOutputStream {

    @Getter
    private byte[] array;

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {

    }

    @Override
    public void write(byte[] b) {
        this.array = b;
    }

    @Override
    public void write(byte[] b, int off, int len) {
        this.array = Arrays.copyOfRange(b, 0, b.length);
    }

    @Override
    public void write(int b) {
        throw new RuntimeException("Not implemented exception");
    }
}
