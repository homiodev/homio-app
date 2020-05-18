package org.touchhome.bundle.nrf24i01.rf24.options;

public enum RetryCount {
    RETRY_15(15),
    RETRY_25(25),
    RETRY_50(50),
    RETRY_100(100);

    public short count;

    RetryCount(int count) {
        this.count = (short) count;
    }
}
