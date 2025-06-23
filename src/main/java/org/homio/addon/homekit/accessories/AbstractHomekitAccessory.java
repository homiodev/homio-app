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
import org.jetbrains.annotations.Nullable;

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
    private final @NotNull HomekitCharacteristicType type;
    private final Class<? extends Service> serviceClass;

    public AbstractHomekitAccessory(@NotNull HomekitEndpointContext ctx,
                                    @NotNull Class<T> masterCharacteristicClass) {
        this(ctx, masterCharacteristicClass, null);
    }

    public AbstractHomekitAccessory(@NotNull HomekitEndpointContext ctx,
                                    @NotNull Class<T> masterCharacteristicClass,
                                    @Nullable Class<? extends Service> serviceClass) {
        this.ctx = ctx;
        this.serviceClass = serviceClass;
        buildCharacteristics(ctx, characteristics);
        masterCharacteristic = getCharacteristic(masterCharacteristicClass);
        this.type = ctx.getCharacteristicsInfo(masterCharacteristicClass).type();
        this.inverted = ctx.endpoint().getInverted();
    }

    protected void addCharacteristic(@NotNull HomekitCharacteristicType type,
                                     @NotNull BaseCharacteristic<?> c,
                                     @NotNull ContextVar.Variable v) {
        characteristics.addIfNotNull(type, c);
        ctx.setCharacteristic(c, v, type);
        addCharacteristicToService(c);
    }

    @Override
    public @NotNull Collection<Service> getServices() {
        if (services.isEmpty()) {
            if (serviceClass != null) {
                addService(CommonUtils.newInstance(serviceClass, this), true);
            }
        }
        if (ctx.endpoint().getGroup().isEmpty()) {
            var ais = services.stream().filter(s -> s instanceof AccessoryInformationService).findAny().orElse(null);
            if (ais == null) {
                services.addFirst(new AccessoryInformationService(this));
            }
        }
        return services;
    }

    @Override
    public int getId() {
        return ctx.endpoint().getId();
    }

    State getVariableValue(@NotNull Function<HomekitEndpointEntity, String> supplier,
                           @Nullable State defaultValue) {
        var variable = ctx.getVariable(supplier.apply(ctx.endpoint()));
        return variable == null ? defaultValue : variable.getValue();
    }

    // the primary service has to be the last service
    public void addService(@NotNull Service service, boolean isPrimaryService) {
        if (isPrimaryService) {
            services.add(service);
        } else {
            int index = services.isEmpty() ? 0 : services.size() - 1;
            services.add(index, service);
        }
        if (isPrimaryService) {
            characteristics.values().stream()
                    .sorted(Comparator.comparing(Characteristic::getType))
                    .forEach(this::addCharacteristicToService);
        }
    }

    public void addCharacteristicToService(@NotNull Characteristic c) {
        Service service = null;
        for (Service s : getServices()) {
            service = s;
        }
        if (service == null) {
            throw new IllegalStateException("No service found for accessory: " + ctx.endpoint().getAccessoryType());
        }
        try {
            // if the service supports adding this characteristic as optional, add it!
            service.getClass()
                    .getMethod("addOptionalCharacteristic", c.getClass())
                    .invoke(service, c);
        } catch (Exception e) {
            log.debug("Unable to add characteristic: {} to service: {}",
                    c.getClass().getSimpleName(), service.getClass().getSimpleName());
            // the service doesn't support this optional characteristic; ignore it
        }
    }

    @Override
    public @NotNull <C extends Characteristic> C getCharacteristic(@NotNull Class<? extends C> klazz) {
        return Objects.requireNonNull(characteristics.get(klazz),
                "Unable to find characteristic: " + klazz.getName() + " for " + ctx.endpoint());
    }

    @Override
    public @NotNull <C extends Characteristic> Optional<C> getCharacteristicOpt(@NotNull Class<? extends C> klazz) {
        return Optional.ofNullable(characteristics.get(klazz));
    }

    @Override
    public @NotNull HomekitEndpointEntity getEndpoint() {
        return ctx.endpoint();
    }
}
