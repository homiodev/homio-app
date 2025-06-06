/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.homio.addon.homekit.accessories;

import io.github.hapjava.accessories.HomekitAccessory;
import io.github.hapjava.characteristics.Characteristic;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;
import io.github.hapjava.characteristics.impl.accessoryinformation.*;
import io.github.hapjava.characteristics.impl.common.NameCharacteristic;
import io.github.hapjava.services.Service;
import io.github.hapjava.services.impl.AccessoryInformationService;
import lombok.Getter;
import lombok.Setter;
import org.homio.addon.homekit.HomekitCharacteristicFactory;
import org.homio.addon.homekit.HomekitEndpointEntity;
import org.homio.api.ContextVar;
import org.homio.api.util.CommonUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public abstract class AbstractHomekitAccessory implements HomekitAccessory {
    protected final ContextVar.Variable variable;
    private final List<Service> services = new ArrayList<>();
    private final Map<Class<? extends Characteristic>, Characteristic> rawCharacteristics = new HashMap<>();
    protected HomekitEndpointEntity endpoint;
    @Getter
    @Setter
    private boolean isLinkedService = false;

    /**
     * Gives an accessory an opportunity to populate additional characteristics after all optional
     * charactericteristics have been added.
     */
    public AbstractHomekitAccessory(HomekitEndpointEntity endpoint,
                                    Class<? extends Service> serviceClass,
                                    String varId) {
        this.endpoint = endpoint;
        this.variable = endpoint.getVariable(varId);
        this.rawCharacteristics.putAll(HomekitCharacteristicFactory.buildRequiredCharacteristics(endpoint)
                .stream().collect(Collectors.toMap(Characteristic::getClass, e -> e)));

        this.rawCharacteristics.putAll(HomekitCharacteristicFactory.buildOptionalCharacteristics(endpoint)
                .stream().collect(Collectors.toMap(Characteristic::getClass, e -> e)));

        addService(CommonUtils.newInstance(serviceClass, this));

        if (!isLinkedService()) {
            var service =
                    new AccessoryInformationService(
                            getCharacteristic(IdentifyCharacteristic.class).get(),
                            getCharacteristic(ManufacturerCharacteristic.class).get(),
                            getCharacteristic(ModelCharacteristic.class).get(),
                            getCharacteristic(NameCharacteristic.class).get(),
                            getCharacteristic(SerialNumberCharacteristic.class).get(),
                            getCharacteristic(FirmwareRevisionCharacteristic.class).get());

            getCharacteristic(HardwareRevisionCharacteristic.class).ifPresent(service::addOptionalCharacteristic);

            // make sure this is the first service
            services.addFirst(service);
        }
    }

    /**
     * @param parentAccessory The primary service to link to.
     * @return If this accessory should be nested as a linked service below a primary service, rather
     * than as a sibling.
     */
    public boolean isLinkable(HomekitAccessory parentAccessory) {
        return false;
    }

    /**
     * @return If this accessory is only valid as a linked service, not as a standalone accessory.
     */
    public boolean isLinkedServiceOnly() {
        return false;
    }

    @Override
    public int getId() {
        return endpoint.getId();
    }

    protected void subscribe(HomekitCharacteristicChangeCallback callback) {
        String k = endpoint.getEntityID() + "_sub";
        variable.addListener(k, state -> callback.changed());
    }

    protected void unsubscribe() {
        String k = endpoint.getEntityID() + "_sub";
        variable.removeListener(k);
    }

    @Override
    public CompletableFuture<String> getName() {
        return getCharacteristic(NameCharacteristic.class).get().getValue();
    }

    @Override
    public CompletableFuture<String> getManufacturer() {
        return getCharacteristic(ManufacturerCharacteristic.class).get().getValue();
    }

    @Override
    public CompletableFuture<String> getModel() {
        return getCharacteristic(ModelCharacteristic.class).get().getValue();
    }

    @Override
    public CompletableFuture<String> getSerialNumber() {
        return getCharacteristic(SerialNumberCharacteristic.class).get().getValue();
    }

    @Override
    public CompletableFuture<String> getFirmwareRevision() {
        return getCharacteristic(FirmwareRevisionCharacteristic.class).get().getValue();
    }

    @Override
    public void identify() {
        try {
            getCharacteristic(IdentifyCharacteristic.class).get().setValue(true);
        } catch (Exception e) {
            // ignore
        }
    }

  /*public HomekitTaggedItem getRootAccessory() {
    return accessory;
  }*/

    @Override
    public Collection<Service> getServices() {
        return this.services;
    }

    public void addService(Service service) {
        services.add(service);

        var serviceClass = service.getClass();
        rawCharacteristics.values().stream()
                .sorted((lhs, rhs) -> lhs.getType().compareTo(rhs.getType()))
                .forEach(
                        characteristic -> {
                            // belongs on the accessory information service
                            if (characteristic.getClass() == NameCharacteristic.class) {
                                return;
                            }
                            try {
                                // if the service supports adding this characteristic as optional, add it!
                                serviceClass
                                        .getMethod("addOptionalCharacteristic", characteristic.getClass())
                                        .invoke(service, characteristic);
                            } catch (NoSuchMethodException
                                     | IllegalAccessException
                                     | InvocationTargetException e) {
                                // the service doesn't support this optional characteristic; ignore it
                            }
                        });
    }

    public <T> Optional<T> getCharacteristic(Class<? extends T> klazz) {
        return Optional.ofNullable((T) rawCharacteristics.get(klazz));
    }
}
