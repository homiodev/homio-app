package org.homio.addon.homekit.accessories;

import io.github.hapjava.characteristics.Characteristic;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;
import io.github.hapjava.characteristics.impl.base.BaseCharacteristic;
import io.github.hapjava.characteristics.impl.common.NameCharacteristic;
import io.github.hapjava.characteristics.impl.windowcovering.PositionStateCharacteristic;
import io.github.hapjava.services.Service;
import lombok.Getter;
import org.homio.addon.homekit.HomekitEndpointContext;
import org.homio.addon.homekit.HomekitEndpointEntity;
import org.homio.addon.homekit.enums.HomekitCharacteristicType;
import org.homio.api.ContextVar;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.State;
import org.homio.api.util.CommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.Function;

import static org.homio.addon.homekit.HomekitCharacteristicFactory.buildCharacteristics;
import static org.homio.addon.homekit.HomekitCharacteristicFactory.buildInitialCharacteristics;

public abstract class AbstractHomekitAccessory implements BaseHomekitAccessory {
    @Getter
    final ContextVar.Variable variable;
    final @NotNull HomekitEndpointContext ctx;
    final boolean inverted;
    @Getter
    private final @NotNull List<Service> services = new ArrayList<>();
    private final @NotNull Characteristics characteristics = new Characteristics();
    private final BaseCharacteristic<?> masterCharacteristic;
    @Getter
    private Map<String, ContextVar.Variable> extraVariables = new HashMap<>();

    public AbstractHomekitAccessory(@NotNull HomekitEndpointContext ctx) {
        this(ctx, (String)null, null);
    }

    public AbstractHomekitAccessory(HomekitEndpointContext ctx,
                                    Class<? extends BaseCharacteristic<?>> masterCharacteristicClass,
                                    Class<? extends Service> serviceClass) {
        this.ctx = ctx;
        this.variable = varId == null ? null : ctx.getVariable(varId);
        buildInitialCharacteristics(ctx, null, characteristics);
        buildCharacteristics(ctx, characteristics);
        masterCharacteristic = getCharacteristic(masterCharacteristicClass);
        this.inverted = ctx.endpoint().getInverted();

        if (serviceClass != null) {
            addService(CommonUtils.newInstance(serviceClass, this));
        }

        // add information service only if this accessory not in groups
        if (ctx.endpoint().getGroup().isEmpty()) {
            // make sure this is the first service
            services.addFirst(BaseHomekitAccessory.createInformationService(characteristics));
        }
    }

    /**
     * Gives an accessory an opportunity to populate additional characteristics after all optional
     * characteristics have been added.
     */
    public AbstractHomekitAccessory(@NotNull HomekitEndpointContext ctx,
                                    @Nullable String varId,
                                    @Nullable Class<? extends Service> serviceClass) {
        this.ctx = ctx;
        this.variable = varId == null ? null : ctx.getVariable(varId);
        buildInitialCharacteristics(ctx, null, characteristics);
        buildCharacteristics(ctx, characteristics);
        this.inverted = ctx.endpoint().getInverted();

        if (serviceClass != null) {
            addService(CommonUtils.newInstance(serviceClass, this));
        }

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

    boolean getVarValue(ContextVar.Variable variable) {
        boolean value = variable.getValue().boolValue();
        return inverted != value;
    }

    protected void updateVar(ContextVar.Variable variable, boolean value) {
        updateVar(variable, OnOffType.of(inverted != value));
    }

    protected void updateVar(ContextVar.Variable variable, int value) {
        updateVar(variable, new DecimalType(value));
    }

    protected void updateVar(ContextVar.Variable variable, State state) {
        variable.set(state);
        ctx.updateUI();
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

    State getVariableValue(Function<HomekitEndpointEntity, String> supplier, State defaultValue) {
        var variable = ctx.getVariable(supplier.apply(ctx.endpoint()));
        return variable == null ? defaultValue : variable.getValue();
    }

    ContextVar.Variable getVariable(Function<HomekitEndpointEntity, String> supplier) {
        return ctx.getVariable(supplier.apply(ctx.endpoint()));
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
    public Collection<Characteristic> getCharacteristics() {
        return characteristics.values();
    }

    @Override
    public <T> T getCharacteristic(Class<? extends T> klazz) {
        return characteristics.get(klazz);
    }

    public <T> Optional<T> getCharacteristicOpt(Class<? extends T> klazz) {
        return Optional.ofNullable(characteristics.get(klazz));
    }

    protected <T> Optional<T> getCharacteristic(HomekitCharacteristicType type) {
        return Optional.ofNullable(characteristics.get(type));
    }
}
