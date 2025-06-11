package org.homio.addon.homekit.accessories;

import io.github.hapjava.characteristics.Characteristic;

import java.util.*;
import java.util.stream.Collectors;

public class Characteristics {
    private final Map<Class<? extends Characteristic>, Characteristic> rawCharacteristics = new HashMap<>();

    public <T> T get(Class<? extends T> klazz) {
        return (T) rawCharacteristics.get(klazz);
    }

    public <T> Optional<T> getOpt(Class<? extends T> klazz) {
        return Optional.ofNullable((T) rawCharacteristics.get(klazz));
    }

    public Collection<Characteristic> values() {
        return rawCharacteristics.values();
    }

    public void putAll(List<Characteristic> characteristics) {
        rawCharacteristics.putAll(characteristics
                .stream().collect(Collectors.toMap(Characteristic::getClass, e -> e)));
    }
}