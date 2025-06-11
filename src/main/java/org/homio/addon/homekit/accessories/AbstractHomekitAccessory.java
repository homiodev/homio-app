package org.homio.addon.homekit.accessories;

import io.github.hapjava.characteristics.Characteristic;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;
import io.github.hapjava.characteristics.impl.common.NameCharacteristic;
import io.github.hapjava.services.Service;
import lombok.Getter;
import org.homio.addon.homekit.HomekitEndpointContext;
import org.homio.addon.homekit.enums.HomekitCharacteristicType;
import org.homio.api.ContextVar;
import org.homio.api.util.CommonUtils;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.homio.addon.homekit.HomekitCharacteristicFactory.buildOptionalCharacteristics;
import static org.homio.addon.homekit.HomekitCharacteristicFactory.buildRequiredCharacteristics;

public abstract class AbstractHomekitAccessory implements BaseHomekitAccessory {
    @Getter
    final ContextVar.Variable variable;
    final @NotNull HomekitEndpointContext ctx;
    final boolean inverted;
    @Getter
    private final @NotNull List<Service> services = new ArrayList<>();
    private final @NotNull Characteristics characteristics = new Characteristics();

    /**
     * Gives an accessory an opportunity to populate additional characteristics after all optional
     * characteristics have been added.
     */
    public AbstractHomekitAccessory(HomekitEndpointContext ctx,
                                    Class<? extends Service> serviceClass,
                                    String varId) {
        this.ctx = ctx;
        this.variable = ctx.getVariable(varId);
        this.characteristics.putAll(buildRequiredCharacteristics(ctx));
        this.characteristics.putAll(buildOptionalCharacteristics(ctx));
        this.inverted = ctx.endpoint().getInverted();

        addService(CommonUtils.newInstance(serviceClass, this));

        // add information service only if this accessory not in groups
        if (ctx.endpoint().getGroup().isEmpty()) {
            // make sure this is the first service
            services.addFirst(BaseHomekitAccessory.createInformationService(characteristics));
        }
    }

    @Override
    public int getId() {
        return ctx.endpoint().getId();
    }

    protected void subscribe(ContextVar.Variable variable, HomekitCharacteristicChangeCallback callback) {
        String k = ctx.owner().getEntityID() + "_" + ctx.endpoint().getId() + "_" + "_sub";
        variable.addListener(k, state -> {
            ctx.service().stateUpdated();
            callback.changed();
        });
    }

    protected void subscribe(HomekitCharacteristicChangeCallback callback) {
        subscribe(variable, callback);
    }

    protected void unsubscribe(ContextVar.Variable variable) {
        String k = ctx.owner().getEntityID() + "_" + ctx.endpoint().getId() + "_" + "_sub";
        variable.removeListener(k);
    }

    protected void unsubscribe() {
        unsubscribe(variable);
    }

    public void addService(Service service) {
        services.add(service);

        var serviceClass = service.getClass();
        characteristics.values().stream()
                .sorted(Comparator.comparing(Characteristic::getType))
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

    @Override
    public <T> T getCharacteristic(Class<? extends T> klazz) {
        return characteristics.get(klazz);
    }

    protected <T> Optional<T> getCharacteristic(HomekitCharacteristicType homekitCharacteristicType) {
        /*return characteristics.values().stream()
                .filter(c -> cgetCharacteristicType() == type)
                .findAny();*/
        return null;
    }
}
