package org.homio.addon.homekit;

import io.github.hapjava.characteristics.impl.base.BaseCharacteristic;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.homekit.accessories.BaseHomekitAccessory;
import org.homio.addon.homekit.accessories.HomekitAccessoryFactory;
import org.homio.api.ContextVar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

@Setter
@RequiredArgsConstructor
@Accessors(fluent = true)
public class HomekitEndpointContext {
    @Getter
    private final @NotNull HomekitEndpointEntity endpoint;
    @Getter
    private final @NotNull HomekitEntity owner;
    @Getter
    private final @NotNull HomekitService service;
    private @NotNull Runnable updateUI = () -> {
    };
    @Getter
    private BaseHomekitAccessory accessory;
    // only for endpoints that inside some group
    @Getter
    private @Nullable HomekitAccessoryFactory.HomekitGroup group;
    // keep all characteristics
    @Getter
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

    public void setCharacteristic(BaseCharacteristic characteristic, ContextVar.Variable variable, String name) {
        this.characteristics.add(new CharacteristicInfo(characteristic, variable, StringUtils.uncapitalize(name)));
    }

    public void setUpdateUI(@NotNull Runnable runnable) {
        this.updateUI = runnable;
    }

    public void updateUI() {
        updateUI.run();
    }

    public record CharacteristicInfo(BaseCharacteristic characteristic, ContextVar.Variable variable, String name) {
    }
}