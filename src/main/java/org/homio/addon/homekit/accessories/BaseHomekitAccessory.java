package org.homio.addon.homekit.accessories;

import io.github.hapjava.accessories.HomekitAccessory;
import io.github.hapjava.accessories.optionalcharacteristic.AccessoryWithHardwareRevision;
import io.github.hapjava.characteristics.Characteristic;
import io.github.hapjava.characteristics.impl.base.BaseCharacteristic;
import org.homio.addon.homekit.HomekitEndpointEntity;

import java.util.concurrent.CompletableFuture;

public interface BaseHomekitAccessory extends HomekitAccessory, AccessoryWithHardwareRevision {

    BaseCharacteristic<?> getMasterCharacteristic();

    <C extends Characteristic> C getCharacteristic(Class<? extends C> klazz);

    @Override
    default CompletableFuture<String> getHardwareRevision() {
        return CompletableFuture.completedFuture(getEndpoint().getHardwareRevision());
    }

    @Override
    default CompletableFuture<String> getName() {
        return CompletableFuture.completedFuture(getEndpoint().getTitle());
    }

    @Override
    default CompletableFuture<String> getManufacturer() {
        return CompletableFuture.completedFuture(getEndpoint().getManufacturer());
    }

    @Override
    default CompletableFuture<String> getModel() {
        return CompletableFuture.completedFuture(getEndpoint().getModel());
    }

    @Override
    default CompletableFuture<String> getSerialNumber() {
        return CompletableFuture.completedFuture(getEndpoint().getSerialNumber());
    }

    @Override
    default CompletableFuture<String> getFirmwareRevision() {
        return CompletableFuture.completedFuture(getEndpoint().getFirmwareRevision());
    }

    @Override
    default void identify() {
    }

    HomekitEndpointEntity getEndpoint();
}
