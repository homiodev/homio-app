package org.homio.addon.homekit;

import io.github.hapjava.characteristics.impl.base.BaseCharacteristic;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.homekit.accessories.BaseHomekitAccessory;
import org.homio.addon.homekit.accessories.HomekitAccessoryFactory;
import org.homio.addon.homekit.enums.HomekitCharacteristicType;
import org.homio.api.ContextVar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Optional;

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
    @Getter
    private String error;

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

    public void setCharacteristic(BaseCharacteristic<?> characteristic,
                                  ContextVar.Variable variable,
                                  HomekitCharacteristicType type) {
        this.characteristics.add(new CharacteristicInfo(characteristic, variable, type, StringUtils.uncapitalize(type.name())));
        variable.addListener(endpoint.getEntityID(), state -> updateUI.run());
    }

    public void setUpdateUI(@NotNull Runnable runnable) {
        this.updateUI = runnable;
    }

    public void destroy() {
        for (CharacteristicInfo characteristic : characteristics) {
            characteristic.variable.removeListener(endpoint.getEntityID());
        }
    }

    public Optional<CharacteristicInfo> characteristicsInfo(String type) {
        for (HomekitEndpointContext.CharacteristicInfo characteristic : characteristics) {
            if (characteristic.characteristic().getType().equals(type)) {
                return Optional.of(characteristic);
            }
        }
        return Optional.empty();
    }

    public <T extends BaseCharacteristic<?>> CharacteristicInfo getCharacteristicsInfo(T characteristic) {
        return getCharacteristicsInfo(characteristic.getClass());
    }

    public <T extends BaseCharacteristic<?>> CharacteristicInfo getCharacteristicsInfo(Class<T> aClass) {
        for (HomekitEndpointContext.CharacteristicInfo characteristic : characteristics) {
            if (characteristic.characteristic.getClass().equals(aClass)) {
                return characteristic;
            }
        }
        return null;
    }

    public record CharacteristicInfo(BaseCharacteristic<?> characteristic,
                                     ContextVar.Variable variable,
                                     HomekitCharacteristicType type,
                                     String name) {
    }
}