package org.homio.addon.tuya.internal.cloud;

import org.jetbrains.annotations.Nullable;

/**
 * Thrown if a connection problem caused the request to fail
 */
public class ConnectionException extends Exception {
    static final long serialVersionUID = 1L;

    public ConnectionException(@Nullable String message) {
        super(message);
    }
}
