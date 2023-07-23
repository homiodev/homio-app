package org.homio.addon.tuya.internal.cloud.dto;

import java.util.List;

public record CommandRequest(List<Command<?>> commands) {

    public record Command<T>(String code, T value) {

    }
}
