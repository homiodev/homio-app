package org.homio.addon.homekit;

import io.github.hapjava.characteristics.impl.common.ActiveCharacteristic;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.addon.homekit.accessories.BaseHomekitAccessory;
import org.homio.addon.homekit.accessories.HomekitAccessoryFactory;
import org.homio.api.ContextVar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

@Getter
@Setter
@RequiredArgsConstructor
@Accessors(fluent = true)
public class HomekitEndpointContext {
    private final @NotNull HomekitEndpointEntity endpoint;
    private final @NotNull HomekitEntity owner;
    private final @NotNull HomekitService service;
    public @Nullable Runnable updateUI;
    private BaseHomekitAccessory accessory;
    // only for endpoints that inside some group
    private @Nullable HomekitAccessoryFactory.HomekitGroup group;
    // keep all characteristics
    private @NotNull ArrayList<CharacteristicInfo> characteristics = new ArrayList<>();

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

    public void setCharacteristic(ActiveCharacteristic characteristic, ContextVar.Variable variable, String name) {
        this.characteristics.add(new CharacteristicInfo(characteristic, variable, name));
    }

    public record CharacteristicInfo(ActiveCharacteristic characteristic, ContextVar.Variable variable, String name){}
}