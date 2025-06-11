package org.homio.addon.homekit;

import io.github.hapjava.accessories.HomekitAccessory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.addon.homekit.accessories.BaseHomekitAccessory;
import org.homio.addon.homekit.accessories.HomekitAccessoryFactory;
import org.homio.api.ContextVar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
@RequiredArgsConstructor
@Accessors(fluent = true)
public class HomekitEndpointContext {
    private final @NotNull HomekitEndpointEntity endpoint;
    private final @NotNull HomekitEntity owner;
    private final @NotNull HomekitService service;
    private BaseHomekitAccessory accessory;
    // only for endpoints that inside some group
    private @Nullable HomekitAccessoryFactory.HomekitGroup group;

    public ContextVar.Variable getVariable(String variableId) {
        if (variableId == null || variableId.isEmpty()) {
            return null;
        }
        try {
            return service.getContext().var().getVariable(variableId);
        } catch (Exception ignored) {
            return null;
        }
    }
}