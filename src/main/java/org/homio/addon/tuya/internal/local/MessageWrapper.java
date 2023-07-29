package org.homio.addon.tuya.internal.local;

/**
 * Wraps command type and message content
 */
public record MessageWrapper<T>(CommandType commandType, T content) {

}
