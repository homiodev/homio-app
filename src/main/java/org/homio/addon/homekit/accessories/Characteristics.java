package org.homio.addon.homekit.accessories;

import io.github.hapjava.characteristics.Characteristic;
import org.homio.addon.homekit.enums.HomekitCharacteristicType;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class Characteristics {
    private final Map<Class<? extends Characteristic>, Characteristic> rawCharacteristics = new HashMap<>();
    private final Map<HomekitCharacteristicType, Characteristic> rawByTpeCharacteristics = new HashMap<>();

    public <T> T get(Class<? extends T> klazz) {
        return (T) rawCharacteristics.get(klazz);
    }

    public <T> T get(HomekitCharacteristicType type) {
        return (T) rawByTpeCharacteristics.get(type);
    }

    public <T> Optional<T> getOpt(Class<? extends T> klazz) {
        return Optional.ofNullable((T) rawCharacteristics.get(klazz));
    }

    public Collection<Characteristic> values() {
        return rawCharacteristics.values();
    }

    public void addIfNotNull(HomekitCharacteristicType characteristicType, Characteristic characteristic) {
        if (characteristic != null) {
            rawByTpeCharacteristics.put(characteristicType, characteristic);
            rawCharacteristics.put(characteristic.getClass(), characteristic);
        }
    }

    public boolean has(HomekitCharacteristicType characteristicType) {
        return rawByTpeCharacteristics.containsKey(characteristicType);
    }
}