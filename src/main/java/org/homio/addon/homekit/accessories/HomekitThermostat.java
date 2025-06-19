package org.homio.addon.homekit.accessories;

import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;
import io.github.hapjava.characteristics.impl.base.BaseCharacteristic;
import io.github.hapjava.characteristics.impl.thermostat.*;
import io.github.hapjava.services.impl.ThermostatService;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.homekit.HomekitEndpointContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static java.util.concurrent.CompletableFuture.completedFuture;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Log4j2
public class HomekitThermostat extends AbstractHomekitAccessory<CurrentTemperatureCharacteristic> {
    private @Nullable HomekitCharacteristicChangeCallback targetTemperatureCallback = null;

    public HomekitThermostat(@NotNull HomekitEndpointContext ctx) {
        super(ctx, CurrentTemperatureCharacteristic.class, null);
        log.info("[{}]: {} Created HomekitThermostat accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
        var coolingThresholdTemperatureCharacteristic = getCharacteristicOpt(
                CoolingThresholdTemperatureCharacteristic.class);
        var heatingThresholdTemperatureCharacteristic = getCharacteristicOpt(
                HeatingThresholdTemperatureCharacteristic.class);
        /*var currentRelativeHumidityCharacteristic = getCharacteristic(
                CurrentRelativeHumidityCharacteristic.class);
        var targetRelativeHumidity = getCharacteristic(
                TargetRelativeHumidityCharacteristic.class);*/
        var displayUnitCharacteristic = getCharacteristic(
                TemperatureDisplayUnitCharacteristic.class);
        var targetHeatingCoolingStateCharacteristic = getCharacteristic(
                TargetHeatingCoolingStateCharacteristic.class);
        var targetTemperatureCharacteristic = getCharacteristicOpt(
                TargetTemperatureCharacteristic.class);
        var currentHeatingCoolingStateCharacteristic = getCharacteristicOpt(CurrentHeatingCoolingStateCharacteristic.class)
                .orElseGet(() -> new CurrentHeatingCoolingStateCharacteristic(
                                new CurrentHeatingCoolingStateEnum[]{CurrentHeatingCoolingStateEnum.OFF},
                                () -> completedFuture(CurrentHeatingCoolingStateEnum.OFF), (cb) -> {
                        }, () -> {
                        })

                );

        if (coolingThresholdTemperatureCharacteristic.isEmpty()
            && heatingThresholdTemperatureCharacteristic.isEmpty()
            && targetTemperatureCharacteristic.isEmpty()) {
            log.error("[{}]: {} Unable to create thermostat; at least one of TargetTemperature, CoolingThresholdTemperature, or HeatingThresholdTemperature is required.", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            throw new RuntimeException(
                    "Unable to create thermostat; at least one of TargetTemperature, CoolingThresholdTemperature, or HeatingThresholdTemperature is required.");
        }

        if (targetTemperatureCharacteristic.isEmpty()) {
            log.debug("[{}]: {} TargetTemperatureCharacteristic not provided, simulating.", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            if (Arrays.asList(targetHeatingCoolingStateCharacteristic.getValidValues()).contains(TargetHeatingCoolingStateEnum.HEAT)
                && heatingThresholdTemperatureCharacteristic.isEmpty()) {
                log.error("[{}]: {} HeatingThresholdTemperature must be provided if HEAT mode is allowed and TargetTemperature is not provided.", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
                throw new RuntimeException(
                        "HeatingThresholdTemperature must be provided if HEAT mode is allowed and TargetTemperature is not provided.");
            }
            if (Arrays.asList(targetHeatingCoolingStateCharacteristic.getValidValues()).contains(TargetHeatingCoolingStateEnum.COOL)
                && coolingThresholdTemperatureCharacteristic.isEmpty()) {
                log.error("[{}]: {} CoolingThresholdTemperature must be provided if COOL mode is allowed and TargetTemperature is not provided.", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
                throw new RuntimeException(
                        "CoolingThresholdTemperature must be provided if COOL mode is allowed and TargetTemperature is not provided.");
            }

            boolean coolingPresent = coolingThresholdTemperatureCharacteristic.isPresent();
            boolean heatingPresent = heatingThresholdTemperatureCharacteristic.isPresent();

            double minValue, maxValue, minStep;
            if (coolingPresent && heatingPresent) {
                minValue = Math.min(coolingThresholdTemperatureCharacteristic.get().getMinValue(),
                        heatingThresholdTemperatureCharacteristic.get().getMinValue());
                maxValue = Math.max(coolingThresholdTemperatureCharacteristic.get().getMaxValue(),
                        heatingThresholdTemperatureCharacteristic.get().getMaxValue());
                minStep = Math.min(coolingThresholdTemperatureCharacteristic.get().getMinStep(),
                        heatingThresholdTemperatureCharacteristic.get().getMinStep());
            } else if (coolingPresent) {
                minValue = coolingThresholdTemperatureCharacteristic.get().getMinValue();
                maxValue = coolingThresholdTemperatureCharacteristic.get().getMaxValue();
                minStep = coolingThresholdTemperatureCharacteristic.get().getMinStep();
            } else {
                minValue = heatingThresholdTemperatureCharacteristic.get().getMinValue();
                maxValue = heatingThresholdTemperatureCharacteristic.get().getMaxValue();
                minStep = heatingThresholdTemperatureCharacteristic.get().getMinStep();
            }
            targetTemperatureCharacteristic = createTargetTemperatureCharacteristic(ctx, minValue, maxValue, minStep,
                    targetHeatingCoolingStateCharacteristic,
                    heatingThresholdTemperatureCharacteristic,
                    coolingThresholdTemperatureCharacteristic);
        }

        addService(
                new ThermostatService(currentHeatingCoolingStateCharacteristic, targetHeatingCoolingStateCharacteristic,
                        getCharacteristic(CurrentTemperatureCharacteristic.class),
                        targetTemperatureCharacteristic.get(), displayUnitCharacteristic));
        log.debug("[{}]: {} HomekitThermostat service added/configured", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
    }

    private @NotNull Optional<TargetTemperatureCharacteristic> createTargetTemperatureCharacteristic(
            @NotNull HomekitEndpointContext ctx, double minValue, double maxValue, double minStep,
            TargetHeatingCoolingStateCharacteristic targetHeatingCoolingStateCharacteristic,
            Optional<HeatingThresholdTemperatureCharacteristic> heatingThresholdTemperatureCharacteristic,
            Optional<CoolingThresholdTemperatureCharacteristic> coolingThresholdTemperatureCharacteristic) {
        Optional<TargetTemperatureCharacteristic> targetTemperatureCharacteristic;
        targetTemperatureCharacteristic = Optional
                .of(new TargetTemperatureCharacteristic(minValue, maxValue, minStep, () -> {
                    try {
                        return switch (targetHeatingCoolingStateCharacteristic.getEnumValue().get()) {
                            case HEAT -> heatingThresholdTemperatureCharacteristic.get().getValue();
                            case COOL -> coolingThresholdTemperatureCharacteristic.get().getValue();
                            default -> completedFuture(
                                    (heatingThresholdTemperatureCharacteristic.get().getValue().get()
                                     + coolingThresholdTemperatureCharacteristic.get().getValue().get())
                                    / 2);
                        };
                    } catch (InterruptedException | ExecutionException e) {
                        log.error("[{}]: {} Error getting simulated target temperature", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType(), e);
                        return null;
                    }
                }, value -> {
                    try {
                        log.debug("[{}]: {} Setting simulated target temperature to: {}", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType(), value);
                        switch (targetHeatingCoolingStateCharacteristic.getEnumValue().get()) {
                            case HEAT:
                                heatingThresholdTemperatureCharacteristic.get().setValue(value);
                                break;
                            case COOL:
                                coolingThresholdTemperatureCharacteristic.get().setValue(value);
                                break;
                            default:
                                // ignore
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        log.error("[{}]: {} Error setting simulated target temperature", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType(), e);
                    }
                }, cb -> {
                    log.debug("[{}]: {} Subscribing to simulated target temperature", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
                    targetTemperatureCallback = cb;
                    subscribeCharacteristic(heatingThresholdTemperatureCharacteristic);
                    subscribeCharacteristic(coolingThresholdTemperatureCharacteristic);
                    subscribeCharacteristic(Optional.of(targetHeatingCoolingStateCharacteristic));
                }, () -> {
                    log.debug("[{}]: {} Unsubscribing from simulated target temperature", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
                    targetTemperatureCallback = null;
                    unSubscribeCharacteristic(heatingThresholdTemperatureCharacteristic);
                    unSubscribeCharacteristic(coolingThresholdTemperatureCharacteristic);
                    unSubscribeCharacteristic(Optional.of(targetHeatingCoolingStateCharacteristic));
                }));
        return targetTemperatureCharacteristic;
    }

    private <T extends BaseCharacteristic<?>> void subscribeCharacteristic(@NotNull Optional<T> characteristic) {
        characteristic.ifPresent(t -> ctx.getCharacteristicsInfo(t)
                .variable().addListener("target-" + t.getType() + "-" + ctx.endpoint().getEntityID(), state -> thresholdTemperatureChanged()));
    }

    private <T extends BaseCharacteristic<?>> void unSubscribeCharacteristic(@NotNull Optional<T> characteristic) {
        characteristic.ifPresent(t -> ctx.getCharacteristicsInfo(t)
                .variable().removeListener("target-" + t.getType() + "-" + ctx.endpoint().getEntityID()));
    }

    private void thresholdTemperatureChanged() {
        if (targetTemperatureCallback != null) {
            log.debug("[{}]: {} Threshold temperature changed, invoking callback", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            targetTemperatureCallback.changed();
        }
    }
}
