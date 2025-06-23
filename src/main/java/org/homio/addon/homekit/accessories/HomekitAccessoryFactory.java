package org.homio.addon.homekit.accessories;

import io.github.hapjava.accessories.*;
import io.github.hapjava.characteristics.Characteristic;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;
import io.github.hapjava.characteristics.impl.airquality.AirQualityCharacteristic;
import io.github.hapjava.characteristics.impl.airquality.AirQualityEnum;
import io.github.hapjava.characteristics.impl.audio.MuteCharacteristic;
import io.github.hapjava.characteristics.impl.base.BaseCharacteristic;
import io.github.hapjava.characteristics.impl.base.EnumCharacteristic;
import io.github.hapjava.characteristics.impl.battery.*;
import io.github.hapjava.characteristics.impl.carbondioxidesensor.CarbonDioxideDetectedCharacteristic;
import io.github.hapjava.characteristics.impl.carbondioxidesensor.CarbonDioxideDetectedEnum;
import io.github.hapjava.characteristics.impl.carbonmonoxidesensor.CarbonMonoxideDetectedCharacteristic;
import io.github.hapjava.characteristics.impl.carbonmonoxidesensor.CarbonMonoxideDetectedEnum;
import io.github.hapjava.characteristics.impl.common.*;
import io.github.hapjava.characteristics.impl.contactsensor.ContactSensorStateCharacteristic;
import io.github.hapjava.characteristics.impl.contactsensor.ContactStateEnum;
import io.github.hapjava.characteristics.impl.filtermaintenance.FilterChangeIndicationCharacteristic;
import io.github.hapjava.characteristics.impl.filtermaintenance.FilterChangeIndicationEnum;
import io.github.hapjava.characteristics.impl.garagedoor.CurrentDoorStateCharacteristic;
import io.github.hapjava.characteristics.impl.garagedoor.TargetDoorStateCharacteristic;
import io.github.hapjava.characteristics.impl.heatercooler.CurrentHeaterCoolerStateCharacteristic;
import io.github.hapjava.characteristics.impl.heatercooler.CurrentHeaterCoolerStateEnum;
import io.github.hapjava.characteristics.impl.heatercooler.TargetHeaterCoolerStateCharacteristic;
import io.github.hapjava.characteristics.impl.heatercooler.TargetHeaterCoolerStateEnum;
import io.github.hapjava.characteristics.impl.humiditysensor.CurrentRelativeHumidityCharacteristic;
import io.github.hapjava.characteristics.impl.leaksensor.LeakDetectedStateCharacteristic;
import io.github.hapjava.characteristics.impl.leaksensor.LeakDetectedStateEnum;
import io.github.hapjava.characteristics.impl.lightsensor.CurrentAmbientLightLevelCharacteristic;
import io.github.hapjava.characteristics.impl.lock.LockCurrentStateCharacteristic;
import io.github.hapjava.characteristics.impl.lock.LockCurrentStateEnum;
import io.github.hapjava.characteristics.impl.lock.LockTargetStateCharacteristic;
import io.github.hapjava.characteristics.impl.lock.LockTargetStateEnum;
import io.github.hapjava.characteristics.impl.motionsensor.MotionDetectedCharacteristic;
import io.github.hapjava.characteristics.impl.occupancysensor.OccupancyDetectedCharacteristic;
import io.github.hapjava.characteristics.impl.occupancysensor.OccupancyDetectedEnum;
import io.github.hapjava.characteristics.impl.outlet.OutletInUseCharacteristic;
import io.github.hapjava.characteristics.impl.securitysystem.CurrentSecuritySystemStateCharacteristic;
import io.github.hapjava.characteristics.impl.securitysystem.CurrentSecuritySystemStateEnum;
import io.github.hapjava.characteristics.impl.securitysystem.TargetSecuritySystemStateCharacteristic;
import io.github.hapjava.characteristics.impl.securitysystem.TargetSecuritySystemStateEnum;
import io.github.hapjava.characteristics.impl.slat.CurrentSlatStateCharacteristic;
import io.github.hapjava.characteristics.impl.slat.CurrentSlatStateEnum;
import io.github.hapjava.characteristics.impl.slat.SlatTypeCharacteristic;
import io.github.hapjava.characteristics.impl.slat.SlatTypeEnum;
import io.github.hapjava.characteristics.impl.smokesensor.SmokeDetectedCharacteristic;
import io.github.hapjava.characteristics.impl.smokesensor.SmokeDetectedStateEnum;
import io.github.hapjava.characteristics.impl.television.CurrentMediaStateCharacteristic;
import io.github.hapjava.characteristics.impl.television.CurrentMediaStateEnum;
import io.github.hapjava.characteristics.impl.television.TargetMediaStateCharacteristic;
import io.github.hapjava.characteristics.impl.television.TargetMediaStateEnum;
import io.github.hapjava.characteristics.impl.thermostat.CurrentTemperatureCharacteristic;
import io.github.hapjava.characteristics.impl.thermostat.TemperatureDisplayUnitCharacteristic;
import io.github.hapjava.characteristics.impl.valve.RemainingDurationCharacteristic;
import io.github.hapjava.characteristics.impl.valve.SetDurationCharacteristic;
import io.github.hapjava.characteristics.impl.valve.ValveTypeEnum;
import io.github.hapjava.services.Service;
import io.github.hapjava.services.impl.*;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.homekit.HomekitEndpointContext;
import org.homio.addon.homekit.HomekitEndpointEntity;
import org.homio.addon.homekit.enums.HomekitAccessoryType;
import org.homio.api.state.DecimalType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.homio.addon.homekit.enums.HomekitAccessoryType.*;

@Log4j2
public class HomekitAccessoryFactory {

    private static final Map<HomekitAccessoryType, Function<HomekitEndpointContext, BaseHomekitAccessory>> ACCESSORY_BUILDERS = new HashMap<>() {
        {
            put(AirQualitySensor, HomekitAirQualitySensor::new);
            put(BasicFan, HomekitBasicFan::new);
            put(Battery, HomekitBattery::new);
            put(CarbonDioxideSensor, HomekitCarbonDioxideSensor::new);
            put(CarbonMonoxideSensor, HomekitCarbonMonoxideSensor::new);
            put(ContactSensor, HomekitContactSensor::new);
            put(Door, HomekitDoor::new);
            put(Doorbell, HomekitDoorbell::new);
            put(Fan, HomekitFan::new);
            put(Faucet, HomekitFaucet::new);
            put(Filter, HomekitFilter::new);
            put(GarageDoorOpener, HomekitGarageDoorOpener::new);
            put(HeaterCooler, HomekitHeaterCooler::new);
            put(HumiditySensor, HomekitHumiditySensor::new);
            // put(InputSource, HomekitInputSource::new);
            put(LeakSensor, HomekitLeakSensor::new);
            put(LightSensor, HomekitLightSensor::new);
            put(LightBulb, HomekitLightBulb::new);
            put(Lock, HomekitLock::new);
            put(Microphone, HomekitMicrophone::new);
            put(MotionSensor, HomekitMotionSensor::new);
            put(OccupancySensor, HomekitOccupancySensor::new);
            put(Outlet, HomekitOutlet::new);
            put(SecuritySystem, HomekitSecuritySystem::new);
            put(Slat, HomekitSlat::new);
            put(SmartSpeaker, HomekitSmartSpeaker::new);
            put(SmokeSensor, HomekitSmokeSensor::new);
            put(Speaker, HomekitSpeaker::new);
            put(StatelessProgrammableSwitch, HomekitStatelessProgrammableSwitch::new);
            put(Switch, HomekitSwitch::new);
            // put(Television, HomekitTelevisionImpl.class);
            // put(TelevisionSpeaker, HomekitTelevisionSpeakerImpl.class);
            put(TemperatureSensor, HomekitTemperatureSensor::new);
            put(Thermostat, HomekitThermostat::new);
            put(Valve, HomekitValve::new);
            put(Window, HomekitWindow::new);
            put(WindowCovering, HomekitWindowCovering::new);
        }
    };

    public static @NotNull BaseHomekitAccessory create(@NotNull HomekitEndpointContext c) {
        log.debug("[{}] Creating accessory for endpoint: {}, type: {}", c.owner().getEntityID(), c.endpoint().getName(), c.endpoint().getAccessoryType());
        var accessory = ACCESSORY_BUILDERS.get(c.endpoint().getAccessoryType()).apply(c);
        c.accessory(accessory);
        log.info("[{}] Created accessory: {} for endpoint: {}", c.owner().getEntityID(), accessory.getClass().getSimpleName(), c.endpoint().getName());
        return accessory;
    }

    public static class HomekitGroup implements BaseHomekitAccessory {
        @Getter
        private final @NotNull String groupName;
        @Getter
        private final @NotNull List<Service> services = new ArrayList<>();
        private final @NotNull Characteristics characteristics = new Characteristics();
        private final @NotNull HomekitEndpointContext ctx;

        public HomekitGroup(@NotNull HomekitEndpointContext ctx) {
            this.ctx = ctx;
            this.groupName = ctx.endpoint().getGroup();
            log.info("[{}] [Group: {}] Creating HomekitGroup", ctx.owner().getEntityID(), groupName);
            services.add(new AccessoryInformationService(this));
        }

        @Override
        public CompletableFuture<String> getName() {
            return CompletableFuture.completedFuture(groupName);
        }

        @Override
        public HomekitEndpointEntity getEndpoint() {
            return ctx.endpoint();
        }

        public void addService(@NotNull HomekitEndpointContext ctx) {
            log.info("[{}] [Group: {}] Adding service for endpoint: {}", this.ctx.owner().getEntityID(), groupName, ctx.endpoint().getName());
            var accessory = HomekitAccessoryFactory.create(ctx);
            ctx.accessory(accessory);
            ctx.group(this);
            services.addAll(accessory.getServices());
            log.debug("[{}] [Group: {}] Added service for endpoint: {}", this.ctx.owner().getEntityID(), groupName, ctx.endpoint().getName());
        }

        @Override
        public BaseCharacteristic<?> getMasterCharacteristic() {
            return null;
        }

        @Override
        public @NotNull <C extends Characteristic> Optional<C> getCharacteristicOpt(Class<? extends C> klazz) {
            return Optional.ofNullable(characteristics.get(klazz));
        }

        @Override
        public <C extends Characteristic> @NotNull C getCharacteristic(@NotNull Class<? extends C> klazz) {
            return Objects.requireNonNull(characteristics.get(klazz),
                    "Unable to find characteristic: " + klazz.getName() + " for " + ctx.endpoint());
        }

        @Override
        public int getId() {
            return groupName.hashCode();
        }
    }

    private static class HomekitSwitch extends AbstractHomekitAccessory<OnCharacteristic> implements SwitchAccessory {

        public HomekitSwitch(@NotNull HomekitEndpointContext c) {
            super(c, OnCharacteristic.class, SwitchService.class);
            log.info("[{}]: {} Created HomekitSwitch accessory", c.owner().getEntityID(), c.endpoint().getAccessoryType());
        }

        @Override
        public CompletableFuture<Boolean> getSwitchState() {
            return masterCharacteristic.getValue();
        }

        @SneakyThrows
        @Override
        public CompletableFuture<Void> setSwitchState(boolean value) {
            log.info("[{}]: {} Setting switch state to: {}", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType(), value);
            masterCharacteristic.setValue(value);
            return completedFuture(null);
        }

        @Override
        public void subscribeSwitchState(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to switch state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.subscribe(callback);
        }

        @Override
        public void unsubscribeSwitchState() {
            log.info("[{}]: {} Unsubscribing from switch state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.unsubscribe();
        }
    }

    public static class HomekitWindow extends AbstractHomekitPositionAccessory implements WindowAccessory {

        public HomekitWindow(@NotNull HomekitEndpointContext c) {
            super(c, WindowCoveringService.class);
            log.info("[{}]: {} Created HomekitWindow accessory", c.owner().getEntityID(), c.endpoint().getAccessoryType());
        }
    }

    private static class HomekitSmokeSensor extends AbstractHomekitAccessory<SmokeDetectedCharacteristic> implements SmokeSensorAccessory {

        public HomekitSmokeSensor(@NotNull HomekitEndpointContext c) {
            super(c, SmokeDetectedCharacteristic.class, SmokeSensorService.class);
            log.info("[{}]: {} Created HomekitSmokeSensor accessory", c.owner().getEntityID(), c.endpoint().getAccessoryType());
        }

        @Override
        public CompletableFuture<SmokeDetectedStateEnum> getSmokeDetectedState() {
            return masterCharacteristic.getEnumValue();
        }

        @Override
        public void subscribeSmokeDetectedState(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to smoke detected state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.subscribe(callback);
        }

        @Override
        public void unsubscribeSmokeDetectedState() {
            log.info("[{}]: {} Unsubscribing from smoke detected state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.unsubscribe();
        }
    }

    private static class HomekitContactSensor extends AbstractHomekitAccessory<ContactSensorStateCharacteristic> implements ContactSensorAccessory {

        public HomekitContactSensor(@NotNull HomekitEndpointContext c) {
            super(c, ContactSensorStateCharacteristic.class, ContactSensorService.class);
            log.info("[{}]: {} Created HomekitContactSensor accessory", c.owner().getEntityID(), c.endpoint().getAccessoryType());
        }

        @Override
        public CompletableFuture<ContactStateEnum> getCurrentState() {
            return masterCharacteristic.getEnumValue();
        }

        @Override
        public void subscribeContactState(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to contact state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.subscribe(callback);
        }

        @Override
        public void unsubscribeContactState() {
            log.info("[{}]: {} Unsubscribing from contact state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.unsubscribe();
        }
    }

    private static class HomekitLeakSensor extends AbstractHomekitAccessory<LeakDetectedStateCharacteristic> implements LeakSensorAccessory {

        public HomekitLeakSensor(@NotNull HomekitEndpointContext c) {
            super(c, LeakDetectedStateCharacteristic.class, LeakSensorService.class);
            log.info("[{}]: {} Created HomekitLeakSensor accessory", c.owner().getEntityID(), c.endpoint().getAccessoryType());
        }

        @Override
        public CompletableFuture<LeakDetectedStateEnum> getLeakDetected() {
            return masterCharacteristic.getEnumValue();
        }

        @Override
        public void subscribeLeakDetected(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to leak detected state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.subscribe(callback);
        }

        @Override
        public void unsubscribeLeakDetected() {
            log.info("[{}]: {} Unsubscribing from leak detected state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.unsubscribe();
        }
    }

    private static class HomekitWindowCovering extends AbstractHomekitPositionAccessory implements WindowCoveringAccessory {
        public HomekitWindowCovering(@NotNull HomekitEndpointContext ctx) {
            super(ctx, WindowCoveringService.class);
            log.info("[{}]: {} Created HomekitWindowCovering accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
        }
    }

    @Log4j2
    private static class HomekitValve extends AbstractHomekitAccessory<ActiveCharacteristic> implements ValveAccessory {

        private final ScheduledExecutorService timerService = Executors.newSingleThreadScheduledExecutor();
        private final boolean homekitTimer;
        private final InUseCharacteristic inUseStatusCharacteristic;
        private final ValveTypeEnum valveType;
        private ScheduledFuture<?> valveTimer;

        public HomekitValve(@NotNull HomekitEndpointContext ctx) {
            super(ctx, ActiveCharacteristic.class, ValveService.class);
            log.info("[{}]: {} Created HomekitValve accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            inUseStatusCharacteristic = getCharacteristic(InUseCharacteristic.class);
            homekitTimer = ctx.endpoint().isConfigurationTimer();

            valveType = ctx.endpoint().getValveType();
            var remainingDurationCharacteristic = getCharacteristicOpt(RemainingDurationCharacteristic.class);
            if (homekitTimer && remainingDurationCharacteristic.isEmpty()) {
                addRemainingDurationCharacteristic();
            }
            log.debug("[{}]: {} HomekitValve configured with type: {}, timer enabled: {}", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType(), this.valveType, homekitTimer);
        }

        private void addRemainingDurationCharacteristic() {
            addCharacteristicToService(new RemainingDurationCharacteristic(() -> {
                int remainingTime = 0;
                ScheduledFuture<?> future = valveTimer;
                if (future != null && !future.isDone()) {
                    remainingTime = Math.toIntExact(future.getDelay(TimeUnit.SECONDS));
                }
                return CompletableFuture.completedFuture(remainingTime);
            }, callback -> {
            }, () -> {
            }));
        }

        @SneakyThrows
        @Override
        public CompletableFuture<ActiveEnum> getValveActive() {
            return masterCharacteristic.getEnumValue();
        }

        @SneakyThrows
        @Override
        public CompletableFuture<Void> setValveActive(ActiveEnum value) {
            masterCharacteristic.setValue(value);
            log.info("[{}]: {} Setting valve active state to: {}", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType(), value);
            if (homekitTimer) {
                if (value == ActiveEnum.ACTIVE) {
                    startTimer();
                } else {
                    stopTimer();
                }
            }
            return completedFuture(null);
        }

        @SneakyThrows
        private void startTimer() {
            int duration = getCharacteristic(SetDurationCharacteristic.class).getValue().get();
            if (duration > 0) {
                stopTimer();
                log.info("[{}]: Starting timer for: {}", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
                valveTimer = timerService.schedule(() -> {
                    try {
                        log.info("[{}]: Turn off valve for: {}", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
                        getCharacteristic(ActiveCharacteristic.class).setValue(ActiveEnum.INACTIVE);
                    } catch (Exception ignore) {
                    }
                }, duration, TimeUnit.SECONDS);
            }
        }

        private void stopTimer() {
            ScheduledFuture<?> future = valveTimer;
            if (future != null && !future.isDone()) {
                log.debug("[{}]: {} Stopping valve timer", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
                future.cancel(true);
            }
        }

        @Override
        public void subscribeValveActive(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to valve active state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.subscribe(callback);
        }

        @Override
        public void unsubscribeValveActive() {
            log.info("[{}]: {} Unsubscribing from valve active state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.unsubscribe();
        }

        @Override
        public CompletableFuture<InUseEnum> getValveInUse() {
            return inUseStatusCharacteristic.getEnumValue();
        }

        @Override
        public void subscribeValveInUse(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to valve in-use state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            inUseStatusCharacteristic.subscribe(callback);
        }

        @Override
        public void unsubscribeValveInUse() {
            log.info("[{}]: {} Unsubscribing from valve in-use state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            inUseStatusCharacteristic.unsubscribe();
        }

        @Override
        public CompletableFuture<ValveTypeEnum> getValveType() {
            return completedFuture(valveType);
        }

        @Override
        public void subscribeValveType(HomekitCharacteristicChangeCallback homekitCharacteristicChangeCallback) {
            log.info("[{}]: {} Subscribing to valve type changes (no-op)", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
        }

        @Override
        public void unsubscribeValveType() {
            log.info("[{}]: {} Unsubscribing from valve type changes (no-op)", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
        }
    }

    private static class HomekitAirQualitySensor extends AbstractHomekitAccessory<AirQualityCharacteristic> implements AirQualityAccessory {

        public HomekitAirQualitySensor(@NotNull HomekitEndpointContext ctx) {
            super(ctx, AirQualityCharacteristic.class, AirQualityService.class);
            log.info("[{}]: {} Created HomekitAirQualitySensor accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
        }

        @Override
        public CompletableFuture<AirQualityEnum> getAirQuality() {
            return masterCharacteristic.getEnumValue();
        }

        @Override
        public void subscribeAirQuality(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to air quality state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.subscribe(callback);
        }

        @Override
        public void unsubscribeAirQuality() {
            log.info("[{}]: {} Unsubscribing from air quality state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.unsubscribe();
        }
    }

    private static class HomekitBasicFan extends AbstractHomekitAccessory<OnCharacteristic> implements BasicFanAccessory {
        public HomekitBasicFan(@NotNull HomekitEndpointContext ctx) {
            super(ctx, OnCharacteristic.class, BasicFanService.class);
            log.info("[{}]: {} Created HomekitBasicFan accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
        }

        @Override
        public CompletableFuture<Boolean> isOn() {
            return masterCharacteristic.getValue();
        }

        @SneakyThrows
        @Override
        public CompletableFuture<Void> setOn(boolean value) {
            log.info("[{}]: {} Setting fan on state to: {}", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType(), value);
            masterCharacteristic.setValue(value);
            return completedFuture(null);
        }

        @Override
        public void subscribeOn(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to fan on state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.subscribe(callback);
        }

        @Override
        public void unsubscribeOn() {
            log.info("[{}]: {} Unsubscribing from fan on state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.unsubscribe();
        }
    }

    private static class HomekitBattery extends AbstractHomekitAccessory<BatteryLevelCharacteristic> implements BatteryAccessory {

        private final Optional<StatusLowBatteryCharacteristic> statusLowBatteryCharacteristic;
        private final Optional<ChargingStateCharacteristic> chargingBatteryCharacteristic;

        public HomekitBattery(@NotNull HomekitEndpointContext ctx) {
            super(ctx, BatteryLevelCharacteristic.class, BatteryService.class);
            log.info("[{}]: {} Created HomekitBattery accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            var lowThreshold = getVariableValue(HomekitEndpointEntity::getBatteryLowThreshold, new DecimalType(20)).intValue();
            statusLowBatteryCharacteristic = getCharacteristicOpt(StatusLowBatteryCharacteristic.class);
            chargingBatteryCharacteristic = getCharacteristicOpt(ChargingStateCharacteristic.class);
            log.debug("[{}]: {} HomekitBattery configured with lowThreshold: {}", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType(), lowThreshold);
        }

        @Override
        public CompletableFuture<Integer> getBatteryLevel() {
            return masterCharacteristic.getValue();
        }

        @Override
        public CompletableFuture<StatusLowBatteryEnum> getLowBatteryState() {
            return statusLowBatteryCharacteristic.map(EnumCharacteristic::getEnumValue).orElse(completedFuture(StatusLowBatteryEnum.NORMAL));
        }

        @Override
        public CompletableFuture<ChargingStateEnum> getChargingState() {
            return chargingBatteryCharacteristic.map(EnumCharacteristic::getEnumValue).orElse(completedFuture(ChargingStateEnum.NOT_CHARABLE));
        }

        @Override
        public void subscribeBatteryLevel(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to battery level changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.subscribe(callback);
        }

        @Override
        public void subscribeLowBatteryState(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to low battery state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            statusLowBatteryCharacteristic.ifPresent(c -> c.subscribe(callback));
        }

        @Override
        public void subscribeBatteryChargingState(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to battery charging state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            chargingBatteryCharacteristic.ifPresent(c -> c.subscribe(callback));
        }

        @Override
        public void unsubscribeBatteryLevel() {
            log.info("[{}]: {} Unsubscribing from battery level changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.unsubscribe();
        }

        @Override
        public void unsubscribeLowBatteryState() {
            log.info("[{}]: {} Unsubscribing from low battery state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            statusLowBatteryCharacteristic.ifPresent(BaseCharacteristic::unsubscribe);
        }

        @Override
        public void unsubscribeBatteryChargingState() {
            log.info("[{}]: {} Unsubscribing from battery charging state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            chargingBatteryCharacteristic.ifPresent(BaseCharacteristic::unsubscribe);
        }
    }

    private static class HomekitStatelessProgrammableSwitch extends AbstractHomekitAccessory<ProgrammableSwitchEventCharacteristic>
            implements StatelessProgrammableSwitchAccessory {
        public HomekitStatelessProgrammableSwitch(@NotNull HomekitEndpointContext ctx) {
            super(ctx, ProgrammableSwitchEventCharacteristic.class, null);
            addService(new StatelessProgrammableSwitchService(
                    getCharacteristic(ProgrammableSwitchEventCharacteristic.class)));
            log.info("[{}]: {} Created HomekitStatelessProgrammableSwitch accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
        }

        @Override
        public CompletableFuture<ProgrammableSwitchEnum> getSwitchEvent() {
            return completedFuture(ProgrammableSwitchEnum.DOUBLE_PRESS);
        }

        @Override
        public void subscribeSwitchEvent(HomekitCharacteristicChangeCallback callback) {
            System.out.println();
        }

        @Override
        public void unsubscribeSwitchEvent() {
            System.out.println();
        }
    }

    private static class HomekitDoor extends AbstractHomekitPositionAccessory implements DoorAccessory {

        public HomekitDoor(@NotNull HomekitEndpointContext ctx) {
            super(ctx, DoorService.class);
            log.info("[{}]: {} Created HomekitDoor accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
        }
    }

    private static class HomekitDoorbell extends AbstractHomekitAccessory<ProgrammableSwitchEventCharacteristic> {
        public HomekitDoorbell(@NotNull HomekitEndpointContext ctx) {
            super(ctx, ProgrammableSwitchEventCharacteristic.class);
            addService(new DoorbellService(getCharacteristic(ProgrammableSwitchEventCharacteristic.class)));
            log.info("[{}]: {} Created HomekitDoorbell accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
        }
    }

    private static class HomekitFaucet extends AbstractActiveHomekitAccessory implements FaucetAccessory {
        public HomekitFaucet(@NotNull HomekitEndpointContext ctx) {
            super(ctx, FaucetService.class);
            log.info("[{}]: {} Created HomekitFaucet accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
        }
    }

    private static class HomekitFilter extends AbstractHomekitAccessory<FilterChangeIndicationCharacteristic> implements FilterMaintenanceAccessory {

        public HomekitFilter(@NotNull HomekitEndpointContext ctx) {
            super(ctx, FilterChangeIndicationCharacteristic.class, FilterMaintenanceService.class);
            log.info("[{}]: {} Created HomekitFilter accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
        }

        @Override
        public CompletableFuture<FilterChangeIndicationEnum> getFilterChangeIndication() {
            return masterCharacteristic.getEnumValue();
        }

        @Override
        public void subscribeFilterChangeIndication(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to filter change indication changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.subscribe(callback);
        }

        @Override
        public void unsubscribeFilterChangeIndication() {
            log.info("[{}]: {} Unsubscribing from filter change indication changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.unsubscribe();
        }
    }

    private static class HomekitFan extends AbstractActiveHomekitAccessory implements FanAccessory {
        public HomekitFan(@NotNull HomekitEndpointContext ctx) {
            super(ctx, FanService.class);
            log.info("[{}]: {} Created HomekitFan accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
        }
    }

    private static class AbstractActiveHomekitAccessory extends AbstractHomekitAccessory<StatusActiveCharacteristic> {
        public AbstractActiveHomekitAccessory(@NotNull HomekitEndpointContext ctx, @Nullable Class<? extends Service> serviceClass) {
            super(ctx, StatusActiveCharacteristic.class, serviceClass);
        }

        public CompletableFuture<Boolean> isActive() {
            log.debug("[{}]: {} Getting active state", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            return masterCharacteristic.getValue();
        }

        @SneakyThrows
        public CompletableFuture<Void> setActive(boolean value) {
            log.info("[{}]: {} Setting active state to: {}", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType(), value);
            masterCharacteristic.setValue(value);
            return completedFuture(null);
        }

        public void subscribeActive(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to active state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.subscribe(callback);
        }

        public void unsubscribeActive() {
            log.info("[{}]: {} Unsubscribing from active state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.unsubscribe();
        }
    }

    private static class HomekitSpeaker extends BaseHomekitMuteAccessory implements SpeakerAccessory {
        public HomekitSpeaker(@NotNull HomekitEndpointContext ctx) {
            super(ctx, SpeakerService.class);
            log.info("[{}]: {} Created HomekitSpeaker accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
        }
    }

    private static class HomekitOccupancySensor extends AbstractHomekitAccessory<OccupancyDetectedCharacteristic> implements OccupancySensorAccessory {

        public HomekitOccupancySensor(@NotNull HomekitEndpointContext ctx) {
            super(ctx, OccupancyDetectedCharacteristic.class, OccupancySensorService.class);
            log.info("[{}]: {} Created HomekitOccupancySensor accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
        }

        @Override
        public CompletableFuture<OccupancyDetectedEnum> getOccupancyDetected() {
            return masterCharacteristic.getEnumValue();
        }

        @Override
        public void subscribeOccupancyDetected(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to occupancy detected state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.subscribe(callback);
        }

        @Override
        public void unsubscribeOccupancyDetected() {
            log.info("[{}]: {} Unsubscribing from occupancy detected state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.unsubscribe();
        }
    }

    private static class HomekitSlat extends AbstractHomekitAccessory<CurrentSlatStateCharacteristic> implements SlatAccessory {

        private final Optional<SlatTypeCharacteristic> slatTypeCh;

        public HomekitSlat(@NotNull HomekitEndpointContext ctx) {
            super(ctx, CurrentSlatStateCharacteristic.class, SlatService.class);
            log.info("[{}]: {} Created HomekitSlat accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            slatTypeCh = getCharacteristicOpt(SlatTypeCharacteristic.class);
        }

        @Override
        public CompletableFuture<CurrentSlatStateEnum> getSlatState() {
            return masterCharacteristic.getEnumValue();
        }

        @Override
        public void subscribeSlatState(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to slat state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.subscribe(callback);
        }

        @Override
        public void unsubscribeSlatState() {
            log.info("[{}]: {} Unsubscribing from slat state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.unsubscribe();
        }

        @Override
        public CompletableFuture<SlatTypeEnum> getSlatType() {
            return slatTypeCh.map(EnumCharacteristic::getEnumValue).orElse(completedFuture(SlatTypeEnum.HORIZONTAL));
        }
    }

    private static class HomekitMotionSensor extends AbstractHomekitAccessory<MotionDetectedCharacteristic> implements MotionSensorAccessory {
        public HomekitMotionSensor(@NotNull HomekitEndpointContext ctx) {
            super(ctx, MotionDetectedCharacteristic.class, MotionSensorService.class);
            log.info("[{}]: {} Created HomekitMotionSensor accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
        }

        @Override
        public CompletableFuture<Boolean> getMotionDetected() {
            return masterCharacteristic.getValue();
        }

        @Override
        public void subscribeMotionDetected(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to motion detected state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.subscribe(callback);
        }

        @Override
        public void unsubscribeMotionDetected() {
            log.info("[{}]: {} Unsubscribing from motion detected state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.unsubscribe();
        }
    }

    private static class HomekitHumiditySensor extends AbstractHomekitAccessory<CurrentRelativeHumidityCharacteristic> implements HumiditySensorAccessory {
        public HomekitHumiditySensor(@NotNull HomekitEndpointContext ctx) {
            super(ctx, CurrentRelativeHumidityCharacteristic.class, HumiditySensorService.class);
            log.info("[{}]: {} Created HomekitHumiditySensor accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
        }

        @Override
        public CompletableFuture<Double> getCurrentRelativeHumidity() {
            return masterCharacteristic.getValue();
        }

        @Override
        public void subscribeCurrentRelativeHumidity(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to current relative humidity changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.subscribe(callback);
        }

        @Override
        public void unsubscribeCurrentRelativeHumidity() {
            log.info("[{}]: {} Unsubscribing from current relative humidity changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.unsubscribe();
        }
    }

    private static class HomekitGarageDoorOpener extends AbstractHomekitAccessory<CurrentDoorStateCharacteristic> {
        public HomekitGarageDoorOpener(@NotNull HomekitEndpointContext ctx) {
            super(ctx, CurrentDoorStateCharacteristic.class);
            log.info("[{}]: {} Created HomekitGarageDoorOpener accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            var obstructionDetectedCharacteristic = getCharacteristicOpt(ObstructionDetectedCharacteristic.class).orElseGet(
                    () -> new ObstructionDetectedCharacteristic(() -> completedFuture(false), (cb) -> {
                    }, () -> {
                    }));
            addService(new GarageDoorOpenerService(
                    getCharacteristic(CurrentDoorStateCharacteristic.class),
                    getCharacteristic(TargetDoorStateCharacteristic.class), obstructionDetectedCharacteristic));
            log.debug("[{}]: {} HomekitGarageDoorOpener service added", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
        }
    }

    private static class HomekitCarbonDioxideSensor extends AbstractHomekitAccessory<CarbonDioxideDetectedCharacteristic> implements CarbonDioxideSensorAccessory {

        public HomekitCarbonDioxideSensor(@NotNull HomekitEndpointContext ctx) {
            super(ctx, CarbonDioxideDetectedCharacteristic.class, CarbonDioxideSensorService.class);
            log.info("[{}]: {} Created HomekitCarbonDioxideSensor accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
        }

        @Override
        public CompletableFuture<CarbonDioxideDetectedEnum> getCarbonDioxideDetectedState() {
            return masterCharacteristic.getEnumValue();
        }

        @Override
        public void subscribeCarbonDioxideDetectedState(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to carbon dioxide detected state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.subscribe(callback);
        }

        @Override
        public void unsubscribeCarbonDioxideDetectedState() {
            log.info("[{}]: {} Unsubscribing from carbon dioxide detected state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.unsubscribe();
        }
    }

    private static class HomekitCarbonMonoxideSensor extends AbstractHomekitAccessory<CarbonMonoxideDetectedCharacteristic> implements CarbonMonoxideSensorAccessory {

        public HomekitCarbonMonoxideSensor(@NotNull HomekitEndpointContext ctx) {
            super(ctx, CarbonMonoxideDetectedCharacteristic.class, CarbonMonoxideSensorService.class);
            log.info("[{}]: {} Created HomekitCarbonMonoxideSensor accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
        }

        @Override
        public CompletableFuture<CarbonMonoxideDetectedEnum> getCarbonMonoxideDetectedState() {
            return masterCharacteristic.getEnumValue();
        }

        @Override
        public void subscribeCarbonMonoxideDetectedState(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to carbon monoxide detected state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.subscribe(callback);
        }

        @Override
        public void unsubscribeCarbonMonoxideDetectedState() {
            log.info("[{}]: {} Unsubscribing from carbon monoxide detected state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.unsubscribe();
        }
    }

    private static class HomekitHeaterCooler extends AbstractActiveHomekitAccessory implements HeaterCoolerAccessory {
        private final List<CurrentHeaterCoolerStateEnum> customCurrentStateList = new ArrayList<>();
        private final List<TargetHeaterCoolerStateEnum> customTargetStateList = new ArrayList<>();

        private final CurrentTemperatureCharacteristic currentTemperatureCh;
        private final CurrentHeaterCoolerStateCharacteristic currentHeaterCoolerStateCh;
        private final TargetHeaterCoolerStateCharacteristic targetHeaterCoolerStateCh;

        public HomekitHeaterCooler(@NotNull HomekitEndpointContext ctx) {
            super(ctx, HeaterCoolerService.class);
            log.info("[{}]: {} Created HomekitHeaterCooler accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            currentTemperatureCh = getCharacteristic(CurrentTemperatureCharacteristic.class);
            currentHeaterCoolerStateCh = getCharacteristic(CurrentHeaterCoolerStateCharacteristic.class);
            targetHeaterCoolerStateCh = getCharacteristic(TargetHeaterCoolerStateCharacteristic.class);

            var displayUnitCharacteristic = new TemperatureDisplayUnitCharacteristic(
                    () -> completedFuture(ctx.endpoint().getTemperatureUnit()),
                    value -> log.error("TemperatureDisplayUnit changed to: {}", value), callback -> {
            }, () -> {
            });
            addCharacteristicToService(displayUnitCharacteristic);
            log.debug("[{}]: {} HomekitHeaterCooler service added/configured", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
        }

        @Override
        public CurrentHeaterCoolerStateEnum[] getCurrentHeaterCoolerStateValidValues() {
            log.debug("[{}]: {} Getting current heater/cooler state valid values", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            return currentHeaterCoolerStateCh.getValidValues();
        }

        @Override
        public TargetHeaterCoolerStateEnum[] getTargetHeaterCoolerStateValidValues() {
            log.debug("[{}]: {} Getting target heater/cooler state valid values", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            return targetHeaterCoolerStateCh.getValidValues();
        }

        @Override
        public CompletableFuture<Double> getCurrentTemperature() {
            return currentTemperatureCh.getValue();
        }

        @Override
        public CompletableFuture<CurrentHeaterCoolerStateEnum> getCurrentHeaterCoolerState() {
            return currentHeaterCoolerStateCh.getEnumValue();
        }

        @Override
        public CompletableFuture<TargetHeaterCoolerStateEnum> getTargetHeaterCoolerState() {
            return targetHeaterCoolerStateCh.getEnumValue();
        }

        @SneakyThrows
        @Override
        public CompletableFuture<Void> setTargetHeaterCoolerState(TargetHeaterCoolerStateEnum state) {
            log.info("[{}]: {} Setting target heater/cooler state to: {}", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType(), state);
            targetHeaterCoolerStateCh.setValue(state);
            return completedFuture(null);
        }

        @Override
        public void subscribeCurrentHeaterCoolerState(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to current heater/cooler state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            currentHeaterCoolerStateCh.subscribe(callback);
        }

        @Override
        public void unsubscribeCurrentHeaterCoolerState() {
            log.info("[{}]: {} Unsubscribing from current heater/cooler state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            currentHeaterCoolerStateCh.unsubscribe();
        }

        @Override
        public void subscribeTargetHeaterCoolerState(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to target heater/cooler state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            targetHeaterCoolerStateCh.subscribe(callback);
        }

        @Override
        public void unsubscribeTargetHeaterCoolerState() {
            log.info("[{}]: {} Unsubscribing from target heater/cooler state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            targetHeaterCoolerStateCh.unsubscribe();
        }

        @Override
        public void subscribeCurrentTemperature(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to current temperature changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            currentTemperatureCh.subscribe(callback);
        }

        @Override
        public void unsubscribeCurrentTemperature() {
            log.info("[{}]: {} Unsubscribing from current temperature changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            currentTemperatureCh.unsubscribe();
        }
    }

    private static class HomekitLightSensor extends AbstractHomekitAccessory<CurrentAmbientLightLevelCharacteristic> implements LightSensorAccessory {
        public HomekitLightSensor(@NotNull HomekitEndpointContext ctx) {
            super(ctx, CurrentAmbientLightLevelCharacteristic.class, LightSensorService.class);
            log.info("[{}]: {} Created HomekitLightSensor accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
        }

        @Override
        public CompletableFuture<Double> getCurrentAmbientLightLevel() {
            return masterCharacteristic.getValue();
        }

        @Override
        public void subscribeCurrentAmbientLightLevel(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to current ambient light level changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.subscribe(callback);
        }

        @Override
        public void unsubscribeCurrentAmbientLightLevel() {
            log.info("[{}]: {} Unsubscribing from current ambient light level changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.unsubscribe();
        }

        @Override
        public double getMinCurrentAmbientLightLevel() {
            double minValue = masterCharacteristic.getMinValue();
            log.debug("[{}]: {} Getting min current ambient light level: {}", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType(), minValue);
            return minValue;
        }

        @Override
        public double getMaxCurrentAmbientLightLevel() {
            double maxValue = masterCharacteristic.getMaxValue();
            log.debug("[{}]: {} Getting max current ambient light level: {}", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType(), maxValue);
            return maxValue;
        }
    }

    private static class HomekitLock extends AbstractHomekitAccessory<LockCurrentStateCharacteristic> implements LockMechanismAccessory {
        private final LockTargetStateCharacteristic lockTargetStateCh;

        public HomekitLock(@NotNull HomekitEndpointContext ctx) {
            super(ctx, LockCurrentStateCharacteristic.class, LockMechanismService.class);
            log.info("[{}]: {} Created HomekitLock accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            lockTargetStateCh = getCharacteristic(LockTargetStateCharacteristic.class);
        }

        @Override
        public CompletableFuture<LockCurrentStateEnum> getLockCurrentState() {
            return masterCharacteristic.getEnumValue();
        }

        @Override
        public CompletableFuture<LockTargetStateEnum> getLockTargetState() {
            return lockTargetStateCh.getEnumValue();
        }

        @SneakyThrows
        @Override
        public CompletableFuture<Void> setLockTargetState(LockTargetStateEnum state) {
            log.info("[{}]: {} Setting lock target state to: {}", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType(), state);
            lockTargetStateCh.setValue(state);
            return completedFuture(null);
        }

        @Override
        public void subscribeLockCurrentState(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to lock current state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.subscribe(callback);
        }

        @Override
        public void unsubscribeLockCurrentState() {
            log.info("[{}]: {} Unsubscribing from lock current state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.unsubscribe();
        }

        @Override
        public void subscribeLockTargetState(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to lock target state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            lockTargetStateCh.subscribe(callback);
        }

        @Override
        public void unsubscribeLockTargetState() {
            log.info("[{}]: {} Unsubscribing from lock target state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            lockTargetStateCh.unsubscribe();
        }
    }

    private static class HomekitMicrophone extends BaseHomekitMuteAccessory implements MicrophoneAccessory {
        public HomekitMicrophone(@NotNull HomekitEndpointContext ctx) {
            super(ctx, MicrophoneService.class);
            log.info("[{}]: {} Created HomekitMicrophone accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
        }
    }

    private static abstract class BaseHomekitMuteAccessory extends AbstractHomekitAccessory<MuteCharacteristic> {
        public BaseHomekitMuteAccessory(@NotNull HomekitEndpointContext ctx, @Nullable Class<? extends Service> serviceClass) {
            super(ctx, MuteCharacteristic.class, serviceClass);
            log.info("[{}]: {} Created BaseHomekitMuteAccessory (or subclass)", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
        }

        public CompletableFuture<Boolean> isMuted() {
            return masterCharacteristic.getValue();
        }

        @SneakyThrows
        public CompletableFuture<Void> setMute(boolean value) {
            log.info("[{}]: {} Setting mute state to: {}", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType(), value);
            masterCharacteristic.setValue(value);
            return completedFuture(null);
        }

        public void subscribeMuteState(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to mute state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.subscribe(callback);
        }

        public void unsubscribeMuteState() {
            log.info("[{}]: {} Unsubscribing from mute state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.unsubscribe();
        }
    }

    private static class HomekitOutlet extends AbstractHomekitAccessory<OutletInUseCharacteristic> implements OutletAccessory {
        private final OutletInUseCharacteristic outletInUseCharacteristic;

        public HomekitOutlet(@NotNull HomekitEndpointContext ctx) {
            super(ctx, OutletInUseCharacteristic.class, OutletService.class);
            log.info("[{}]: {} Created HomekitOutlet accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            outletInUseCharacteristic = getCharacteristic(OutletInUseCharacteristic.class);
        }

        @Override
        public CompletableFuture<Boolean> getPowerState() {
            return masterCharacteristic.getValue();
        }

        @Override
        @SneakyThrows
        public CompletableFuture<Boolean> getOutletInUse() {
            return outletInUseCharacteristic.getValue();
        }

        @SneakyThrows
        @Override
        public CompletableFuture<Void> setPowerState(boolean value) {
            log.info("[{}]: {} Setting outlet power state to: {}", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType(), value);
            masterCharacteristic.setValue(value);
            return completedFuture(null);
        }

        @Override
        public void subscribePowerState(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to outlet power state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.subscribe(callback);
        }

        @Override
        public void subscribeOutletInUse(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to outlet in-use state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            outletInUseCharacteristic.subscribe(callback);
        }

        @Override
        public void unsubscribePowerState() {
            log.info("[{}]: {} Unsubscribing from outlet power state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.unsubscribe();
        }

        @Override
        public void unsubscribeOutletInUse() {
            log.info("[{}]: {} Unsubscribing from outlet in-use state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            outletInUseCharacteristic.unsubscribe();
        }
    }

    private static class HomekitTemperatureSensor extends AbstractHomekitAccessory<CurrentTemperatureCharacteristic> {
        public HomekitTemperatureSensor(@NotNull HomekitEndpointContext ctx) {
            super(ctx, CurrentTemperatureCharacteristic.class, null);
            log.info("[{}]: {} Created HomekitTemperatureSensor accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            addService(new TemperatureSensorService(getCharacteristic(CurrentTemperatureCharacteristic.class)));
            log.debug("[{}]: {} HomekitTemperatureSensor service added", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
        }
    }

    private static class HomekitSmartSpeaker extends AbstractHomekitAccessory<CurrentMediaStateCharacteristic> implements SmartSpeakerAccessory {
        private final TargetMediaStateCharacteristic targetMediaStateCh;

        public HomekitSmartSpeaker(@NotNull HomekitEndpointContext ctx) {
            super(ctx, CurrentMediaStateCharacteristic.class, SmartSpeakerService.class);
            log.info("[{}]: {} Created HomekitSmartSpeaker accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            targetMediaStateCh = getCharacteristic(TargetMediaStateCharacteristic.class);
        }

        @Override
        public CompletableFuture<CurrentMediaStateEnum> getCurrentMediaState() {
            return masterCharacteristic.getEnumValue();
        }

        @Override
        public void subscribeCurrentMediaState(final HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to current media state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.subscribe(callback);
        }

        @Override
        public void unsubscribeCurrentMediaState() {
            log.info("[{}]: {} Unsubscribing from current media state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.unsubscribe();
        }

        @Override
        public CompletableFuture<TargetMediaStateEnum> getTargetMediaState() {
            return targetMediaStateCh.getEnumValue();
        }

        @SneakyThrows
        @Override
        public CompletableFuture<Void> setTargetMediaState(final TargetMediaStateEnum targetState) {
            log.info("[{}]: {} Setting target media state to: {}", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType(), targetState);
            targetMediaStateCh.setValue(targetState);
            return completedFuture(null);
        }

        @Override
        public void subscribeTargetMediaState(final HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to target media state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            targetMediaStateCh.subscribe(callback);
        }

        @Override
        public void unsubscribeTargetMediaState() {
            log.info("[{}]: {} Unsubscribing from target media state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            targetMediaStateCh.unsubscribe();
        }
    }

    private static class HomekitSecuritySystem extends AbstractHomekitAccessory<CurrentSecuritySystemStateCharacteristic> implements SecuritySystemAccessory {
        private final List<TargetSecuritySystemStateEnum> customTargetStateList = new ArrayList<>();
        private final TargetSecuritySystemStateCharacteristic targetSecuritySystemStateCharacteristic;

        public HomekitSecuritySystem(@NotNull HomekitEndpointContext ctx) {
            super(ctx, CurrentSecuritySystemStateCharacteristic.class, SecuritySystemService.class);
            log.info("[{}]: {} Created HomekitSecuritySystem accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            targetSecuritySystemStateCharacteristic = getCharacteristic(TargetSecuritySystemStateCharacteristic.class);
        }

        @Override
        public CurrentSecuritySystemStateEnum[] getCurrentSecuritySystemStateValidValues() {
            log.debug("[{}]: {} Getting current security system state valid values", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            return masterCharacteristic.getValidValues();
        }

        @Override
        public TargetSecuritySystemStateEnum[] getTargetSecuritySystemStateValidValues() {
            log.debug("[{}]: {} Getting target security system state valid values", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            return targetSecuritySystemStateCharacteristic.getValidValues();
        }

        @Override
        public CompletableFuture<CurrentSecuritySystemStateEnum> getCurrentSecuritySystemState() {
            return masterCharacteristic.getEnumValue();
        }

        @Override
        public CompletableFuture<TargetSecuritySystemStateEnum> getTargetSecuritySystemState() {
            return targetSecuritySystemStateCharacteristic.getEnumValue();
        }

        @SneakyThrows
        @Override
        public void setTargetSecuritySystemState(TargetSecuritySystemStateEnum state) {
            log.info("[{}]: {} Setting target security system state to: {}", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType(), state);
            targetSecuritySystemStateCharacteristic.setValue(state);
        }

        @Override
        public void subscribeCurrentSecuritySystemState(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to current security system state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.subscribe(callback);
        }

        @Override
        public void unsubscribeCurrentSecuritySystemState() {
            log.info("[{}]: {} Unsubscribing from current security system state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            masterCharacteristic.unsubscribe();
        }

        @Override
        public void subscribeTargetSecuritySystemState(HomekitCharacteristicChangeCallback callback) {
            log.info("[{}]: {} Subscribing to target security system state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            targetSecuritySystemStateCharacteristic.subscribe(callback);
        }

        @Override
        public void unsubscribeTargetSecuritySystemState() {
            log.info("[{}]: {} Unsubscribing from target security system state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
            targetSecuritySystemStateCharacteristic.unsubscribe();
        }
    }
}