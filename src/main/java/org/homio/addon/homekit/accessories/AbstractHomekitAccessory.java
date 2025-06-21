package org.homio.addon.homekit.accessories;

import io.github.hapjava.characteristics.Characteristic;
import io.github.hapjava.characteristics.impl.base.BaseCharacteristic;
import io.github.hapjava.services.Service;
import io.github.hapjava.services.impl.AccessoryInformationService;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.homekit.HomekitEndpointContext;
import org.homio.addon.homekit.HomekitEndpointEntity;
import org.homio.addon.homekit.enums.HomekitCharacteristicType;
import org.homio.api.ContextVar;
import org.homio.api.state.State;
import org.homio.api.util.CommonUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

import static org.homio.addon.homekit.HomekitCharacteristicFactory.buildCharacteristics;

@Log4j2
public abstract class AbstractHomekitAccessory<T extends BaseCharacteristic<?>> implements BaseHomekitAccessory {
    final @NotNull HomekitEndpointContext ctx;
    final boolean inverted;
    @Getter
    final @NotNull T masterCharacteristic;
    private final @NotNull List<Service> services = new ArrayList<>();
    private final @NotNull Characteristics characteristics = new Characteristics();
    @Getter
    private final HomekitCharacteristicType type;

    public AbstractHomekitAccessory(@NotNull HomekitEndpointContext ctx, @NotNull Class<T> masterCharacteristicClass) {
        this(ctx, masterCharacteristicClass, null);
    }

    public AbstractHomekitAccessory(@NotNull HomekitEndpointContext ctx,
                                    @NotNull Class<T> masterCharacteristicClass,
                                    Class<? extends Service> serviceClass) {
        this.ctx = ctx;
        buildCharacteristics(ctx, characteristics);
        masterCharacteristic = getCharacteristic(masterCharacteristicClass);
        this.type = ctx.getCharacteristicsInfo(masterCharacteristicClass).type();
        this.inverted = ctx.endpoint().getInverted();

        if (ctx.endpoint().getGroup().isEmpty()) {
            services.add(new AccessoryInformationService(this));
        }
        if (serviceClass != null) {
            addService(CommonUtils.newInstance(serviceClass, this));
        }
    }

    protected void addCharacteristic(HomekitCharacteristicType type, BaseCharacteristic c, ContextVar.Variable v) {
        characteristics.addIfNotNull(type, c);
        ctx.setCharacteristic(c, v, type);
    }

    @Override
    public Collection<Service> getServices() {
        return services;
    }

    @Override
    public int getId() {
        return ctx.endpoint().getId();
    }

    State getVariableValue(Function<HomekitEndpointEntity, String> supplier, State defaultValue) {
        var variable = ctx.getVariable(supplier.apply(ctx.endpoint()));
        return variable == null ? defaultValue : variable.getValue();
    }

    public void addService(Service service) {
        services.add(service);

        var serviceClass = service.getClass();
        characteristics.values().stream()
                .sorted(Comparator.comparing(Characteristic::getType))
                .forEach(c -> {
                    try {
                        // if the service supports adding this characteristic as optional, add it!
                        serviceClass
                                .getMethod("addOptionalCharacteristic", c.getClass())
                                .invoke(service, c);
                    } catch (Exception e) {
                        log.debug("Unable to add characteristic: {} to service: {}",
                                c.getClass().getSimpleName(), serviceClass.getSimpleName());
                        // the service doesn't support this optional characteristic; ignore it
                    }
                });
    }

    @Override
    public @NotNull <C extends Characteristic> C getCharacteristic(Class<? extends C> klazz) {
        return Objects.requireNonNull(characteristics.get(klazz),
                "Unable to find characteristic: " + klazz.getName() + " for " + ctx.endpoint());
    }

    @Override
    public @NotNull <C extends Characteristic> Optional<C> getCharacteristicOpt(Class<? extends C> klazz) {
        return Optional.ofNullable(characteristics.get(klazz));
    }

    @Override
    public HomekitEndpointEntity getEndpoint() {
        return ctx.endpoint();
    }
}
