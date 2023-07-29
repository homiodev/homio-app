package org.homio.addon.tuya.internal.local;

import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * The {@link MessageWrapper} wraps command type and message content
 */
@ToString
@RequiredArgsConstructor
public final class MessageWrapper<T> {

    public final CommandType commandType;
    public final T content;
}
