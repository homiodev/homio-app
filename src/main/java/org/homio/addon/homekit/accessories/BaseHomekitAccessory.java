package org.homio.addon.homekit.accessories;

import io.github.hapjava.accessories.HomekitAccessory;
import io.github.hapjava.characteristics.Characteristic;
import io.github.hapjava.characteristics.impl.accessoryinformation.*;
import io.github.hapjava.characteristics.impl.common.NameCharacteristic;
import io.github.hapjava.services.Service;
import io.github.hapjava.services.impl.AccessoryInformationService;
import org.homio.api.ContextVar;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface BaseHomekitAccessory extends HomekitAccessory {

    static @NotNull Service createInformationService(@NotNull Characteristics characteristics) {
        var service = new AccessoryInformationService(
                characteristics.get(IdentifyCharacteristic.class),
                characteristics.get(ManufacturerCharacteristic.class),
                characteristics.get(ModelCharacteristic.class),
                characteristics.get(NameCharacteristic.class),
                characteristics.get(SerialNumberCharacteristic.class),
                characteristics.get(FirmwareRevisionCharacteristic.class));

        characteristics.getOpt(HardwareRevisionCharacteristic.class)
                .ifPresent(service::addOptionalCharacteristic);
        return service;
    }

    Collection<Characteristic> getCharacteristics();

    <T> T getCharacteristic(Class<? extends T> klazz);

    @Override
    default CompletableFuture<String> getName() {
        return getCharacteristic(NameCharacteristic.class).getValue();
    }

    @Override
    default CompletableFuture<String> getManufacturer() {
        return getCharacteristic(ManufacturerCharacteristic.class).getValue();
    }

    @Override
    default CompletableFuture<String> getModel() {
        return getCharacteristic(ModelCharacteristic.class).getValue();
    }

    @Override
    default CompletableFuture<String> getSerialNumber() {
        return getCharacteristic(SerialNumberCharacteristic.class).getValue();
    }

    @Override
    default CompletableFuture<String> getFirmwareRevision() {
        return getCharacteristic(FirmwareRevisionCharacteristic.class).getValue();
    }

    @Override
    default void identify() {
        try {
            getCharacteristic(IdentifyCharacteristic.class).setValue(true);
        } catch (Exception e) {
            // ignore
        }
    }

    ContextVar.Variable getVariable();

    Map<String, ContextVar.Variable> getExtraVariables();
}
