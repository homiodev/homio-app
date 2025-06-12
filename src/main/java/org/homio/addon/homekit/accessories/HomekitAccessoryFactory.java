package org.homio.addon.homekit.accessories;

import io.github.hapjava.accessories.*;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;
import io.github.hapjava.characteristics.impl.airquality.AirQualityEnum;
import io.github.hapjava.characteristics.impl.battery.ChargingStateEnum;
import io.github.hapjava.characteristics.impl.battery.StatusLowBatteryEnum;
import io.github.hapjava.characteristics.impl.carbondioxidesensor.CarbonDioxideDetectedEnum;
import io.github.hapjava.characteristics.impl.carbonmonoxidesensor.CarbonMonoxideDetectedEnum;
import io.github.hapjava.characteristics.impl.common.ActiveEnum;
import io.github.hapjava.characteristics.impl.common.InUseEnum;
import io.github.hapjava.characteristics.impl.common.ObstructionDetectedCharacteristic;
import io.github.hapjava.characteristics.impl.contactsensor.ContactStateEnum;
import io.github.hapjava.characteristics.impl.filtermaintenance.FilterChangeIndicationEnum;
import io.github.hapjava.characteristics.impl.garagedoor.CurrentDoorStateCharacteristic;
import io.github.hapjava.characteristics.impl.garagedoor.TargetDoorStateCharacteristic;
import io.github.hapjava.characteristics.impl.heatercooler.CurrentHeaterCoolerStateEnum;
import io.github.hapjava.characteristics.impl.heatercooler.TargetHeaterCoolerStateEnum;
import io.github.hapjava.characteristics.impl.leaksensor.LeakDetectedStateEnum;
import io.github.hapjava.characteristics.impl.lock.LockCurrentStateEnum;
import io.github.hapjava.characteristics.impl.lock.LockTargetStateEnum;
import io.github.hapjava.characteristics.impl.occupancysensor.OccupancyDetectedEnum;
import io.github.hapjava.characteristics.impl.slat.CurrentSlatStateEnum;
import io.github.hapjava.characteristics.impl.slat.SlatTypeEnum;
import io.github.hapjava.characteristics.impl.smokesensor.SmokeDetectedStateEnum;
import io.github.hapjava.characteristics.impl.thermostat.*;
import io.github.hapjava.characteristics.impl.valve.RemainingDurationCharacteristic;
import io.github.hapjava.characteristics.impl.valve.ValveTypeEnum;
import io.github.hapjava.services.Service;
import io.github.hapjava.services.impl.*;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.homekit.HomekitCharacteristicFactory;
import org.homio.addon.homekit.HomekitEndpointContext;
import org.homio.addon.homekit.HomekitEndpointEntity;
import org.homio.addon.homekit.enums.HomekitAccessoryType;
import org.homio.api.ContextVar;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.StringType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.homio.addon.homekit.HomekitCharacteristicFactory.*;
import static org.homio.addon.homekit.enums.HomekitAccessoryType.*;
import static org.homio.addon.homekit.enums.HomekitCharacteristicType.RemainingDuration;

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
            //put(SecuritySystem, HomekitSecuritySystem::new);
            put(Slat, HomekitSlat::new);
            // put(SmartSpeaker, HomekitSmartSpeaker::new);
            put(SmokeSensor, HomekitSmokeSensor::new);
            put(Speaker, HomekitSpeaker::new);
            put(StatelessProgrammableSwitch, HomekitStatelessProgrammableSwitch::new);
            put(Switch, HomekitSwitch::new);
            // put(TELEVISION, HomekitTelevisionImpl.class);
            // put(TELEVISION_SPEAKER, HomekitTelevisionSpeakerImpl.class);
            put(TemperatureSensor, HomekitTemperatureSensor::new);
            put(Thermostat, HomekitThermostat::new);
            put(Valve, HomekitValve::new);
            put(Window, HomekitWindow::new);
            put(WindowCovering, HomekitWindowCovering::new);
        }
    };

    public static @NotNull BaseHomekitAccessory create(@NotNull HomekitEndpointContext c) {
        var accessory = ACCESSORY_BUILDERS.get(c.endpoint().getAccessoryType()).apply(c);
        c.accessory(accessory);
        return accessory;
    }

    public static class HomekitGroup implements BaseHomekitAccessory {
        @Getter
        private final @NotNull String groupName;
        @Getter
        private final @NotNull List<Service> services = new ArrayList<>();
        private final @NotNull Characteristics characteristics = new Characteristics();

        public HomekitGroup(@NotNull HomekitEndpointContext c) {
            this.groupName = c.endpoint().getGroup();
            HomekitCharacteristicFactory.buildInitialCharacteristics(c, groupName, characteristics);
            services.addFirst(BaseHomekitAccessory.createInformationService(characteristics));
            /* getCharacteristic(ServiceLabelNamespaceCharacteristic.class)
                    .ifPresent(ch -> getServices().add(new ServiceLabelService(ch))); */
        }

        public void addService(@NotNull HomekitEndpointContext ctx) {
            var accessory = HomekitAccessoryFactory.create(ctx);
            ctx.accessory(accessory);
            ctx.group(this);
            services.addAll(accessory.getServices());
        }

        @Override
        public <T> T getCharacteristic(@NotNull Class<? extends T> klazz) {
            return characteristics.get(klazz);
        }

        @Override
        public ContextVar.Variable getVariable() {
            return null;
        }

        @Override
        public int getId() {
            return groupName.hashCode();
        }
    }

    private static class HomekitSwitch extends AbstractHomekitAccessory implements SwitchAccessory {

        public HomekitSwitch(@NotNull HomekitEndpointContext c) {
            super(c, c.endpoint().getOnState(), SwitchService.class);
        }

        @Override
        public CompletableFuture<Boolean> getSwitchState() {
            return completedFuture(variable.getValue().boolValue());
        }

        @Override
        public CompletableFuture<Void> setSwitchState(boolean value) {
            updateVar(variable, value);
            return completedFuture(null);
        }

        @Override
        public void subscribeSwitchState(HomekitCharacteristicChangeCallback callback) {
            subscribe(callback);
        }

        @Override
        public void unsubscribeSwitchState() {
            unsubscribe();
        }
    }

    public static class HomekitWindow extends AbstractHomekitPositionAccessory implements WindowAccessory {

        public HomekitWindow(@NotNull HomekitEndpointContext c) {
            super(c, WindowCoveringService.class);
        }
    }

    private static class HomekitSmokeSensor extends AbstractHomekitAccessory implements SmokeSensorAccessory {

        private final @NotNull Map<SmokeDetectedStateEnum, Object> mapping;

        public HomekitSmokeSensor(@NotNull HomekitEndpointContext c) {
            super(c, c.endpoint().getDetectedState(), SmokeSensorService.class);
            mapping = createMapping(variable, SmokeDetectedStateEnum.class);
        }

        @Override
        public CompletableFuture<SmokeDetectedStateEnum> getSmokeDetectedState() {
            return completedFuture(getKeyFromMapping(variable, mapping, SmokeDetectedStateEnum.NOT_DETECTED));
        }

        @Override
        public void subscribeSmokeDetectedState(HomekitCharacteristicChangeCallback callback) {
            subscribe(callback);
        }

        @Override
        public void unsubscribeSmokeDetectedState() {
            unsubscribe();
        }
    }

    private static class HomekitContactSensor extends AbstractHomekitAccessory implements ContactSensorAccessory {

        private final @NotNull Map<ContactStateEnum, Object> mapping;

        public HomekitContactSensor(@NotNull HomekitEndpointContext c) {
            super(c, c.endpoint().getDetectedState(), ContactSensorService.class);
            mapping = createMapping(variable, ContactStateEnum.class);
        }

        @Override
        public CompletableFuture<ContactStateEnum> getCurrentState() {
            return completedFuture(getKeyFromMapping(variable, mapping, ContactStateEnum.DETECTED));
        }

        @Override
        public void subscribeContactState(HomekitCharacteristicChangeCallback callback) {
            subscribe(callback);
        }

        @Override
        public void unsubscribeContactState() {
            unsubscribe();
        }
    }

    private static class HomekitLeakSensor extends AbstractHomekitAccessory implements LeakSensorAccessory {

        private final @NotNull Map<LeakDetectedStateEnum, Object> mapping;

        public HomekitLeakSensor(@NotNull HomekitEndpointContext c) {
            super(c, c.endpoint().getDetectedState(), LeakSensorService.class);
            mapping = createMapping(variable, LeakDetectedStateEnum.class);
        }

        @Override
        public CompletableFuture<LeakDetectedStateEnum> getLeakDetected() {
            return completedFuture(
                    getKeyFromMapping(variable, mapping, LeakDetectedStateEnum.LEAK_NOT_DETECTED));
        }

        @Override
        public void subscribeLeakDetected(HomekitCharacteristicChangeCallback callback) {
            subscribe(callback);
        }

        @Override
        public void unsubscribeLeakDetected() {
            unsubscribe();
        }
    }

    private static class HomekitWindowCovering extends AbstractHomekitPositionAccessory implements WindowCoveringAccessory {
        public HomekitWindowCovering(@NotNull HomekitEndpointContext ctx) {
            super(ctx, WindowCoveringService.class);
        }
    }

    @Log4j2
    private static class HomekitValve extends AbstractHomekitAccessory implements ValveAccessory {
        public static final String CONFIG_DEFAULT_DURATION = "homekitDefaultDuration";
        private static final String CONFIG_VALVE_TYPE = "ValveType";
        private static final String CONFIG_VALVE_TYPE_DEPRECATED = "homekitValveType";
        private static final String CONFIG_TIMER = "homekitTimer";

        private static final Map<String, ValveTypeEnum> CONFIG_VALVE_TYPE_MAPPING = new HashMap<>() {
            {
                put("GENERIC", ValveTypeEnum.GENERIC);
                put("IRRIGATION", ValveTypeEnum.IRRIGATION);
                put("SHOWER", ValveTypeEnum.SHOWER);
                put("FAUCET", ValveTypeEnum.WATER_FAUCET);
            }
        };
        private final ContextVar.Variable inUseStatus;
        private final ScheduledExecutorService timerService = Executors.newSingleThreadScheduledExecutor();
        private final ContextVar.Variable remainingDurationVar;
        private final boolean homekitTimer;
        private ScheduledFuture<?> valveTimer;
        private ValveTypeEnum valveType;

        public HomekitValve(@NotNull HomekitEndpointContext ctx) {
            super(ctx, ctx.endpoint().getActiveState(), ValveService.class);
            inUseStatus = ctx.getVariable(ctx.endpoint().getInuseStatus());
            homekitTimer = false; // getAccessoryConfigurationAsBoolean(CONFIG_TIMER, false);

            var remainingDurationCharacteristic = getCharacteristic(RemainingDurationCharacteristic.class);

            /*if (homekitTimer && remainingDurationCharacteristic == null) {
                addRemainingDurationCharacteristic(getRootAccessory(), getUpdater(), service);
            }*/
            String valveTypeConfig = "GENERIC"; // getAccessoryConfiguration(CONFIG_VALVE_TYPE, "GENERIC");
            valveTypeConfig = valveTypeConfig; // getAccessoryConfiguration(CONFIG_VALVE_TYPE_DEPRECATED, valveTypeConfig);
            var valveType = CONFIG_VALVE_TYPE_MAPPING.get(valveTypeConfig.toUpperCase());
            this.valveType = valveType != null ? valveType : ValveTypeEnum.GENERIC;
            this.remainingDurationVar = ctx.getVariable(ctx.endpoint().getRemainingDuration());
        }

        private void addRemainingDurationCharacteristic(ValveService service) {
            service.addOptionalCharacteristic(new RemainingDurationCharacteristic(() -> {
                int remainingTime = 0;
                ScheduledFuture<?> future = valveTimer;
                if (future != null && !future.isDone()) {
                    remainingTime = Math.toIntExact(future.getDelay(TimeUnit.SECONDS));
                }
                return completedFuture(remainingTime);
            }, getSubscriber(remainingDurationVar, ctx, RemainingDuration),
                    getUnsubscriber(remainingDurationVar, ctx, RemainingDuration)));
        }

        @Override
        public CompletableFuture<ActiveEnum> getValveActive() {
            return completedFuture(variable.getValue().boolValue() ? ActiveEnum.ACTIVE : ActiveEnum.INACTIVE);
        }

        @Override
        public CompletableFuture<Void> setValveActive(ActiveEnum activeEnum) throws Exception {
            /*getItem(ACTIVE_STATUS, SwitchItem.class).ifPresent(item -> {
                item.send(OnOffType.from(state == ActiveEnum.ACTIVE));
                if (homekitTimer) {
                    if ((state == ActiveEnum.ACTIVE)) {
                        startTimer();
                    } else {
                        stopTimer();
                    }
                    // let home app refresh the remaining duration
                    ((GenericItem) getRootAccessory().getItem()).send(RefreshType.REFRESH);
                }
            });*/
            return completedFuture(null);
        }

       /* private void startTimer() {
            int duration = getDuration();
            if (duration > 0) {
                stopTimer();
                valveTimer = timerService.schedule(() -> {
                    switchOffValve();
                    // let home app refresh the remaining duration, which is 0
                    ((GenericItem) getRootAccessory().getItem()).send(RefreshType.REFRESH);
                }, duration, TimeUnit.SECONDS);
            }
        }*/

        private void stopTimer() {
            ScheduledFuture<?> future = valveTimer;
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
        }

        @Override
        public void subscribeValveActive(HomekitCharacteristicChangeCallback callback) {
            subscribe(variable, callback);
        }

        @Override
        public void unsubscribeValveActive() {
            unsubscribe(variable);
        }

        @Override
        public CompletableFuture<InUseEnum> getValveInUse() {
            return completedFuture(inUseStatus.getValue().boolValue() ? InUseEnum.IN_USE : InUseEnum.NOT_IN_USE);
        }

        @Override
        public void subscribeValveInUse(HomekitCharacteristicChangeCallback callback) {
            subscribe(inUseStatus, callback);
        }

        @Override
        public void unsubscribeValveInUse() {
            unsubscribe(inUseStatus);
        }

        @Override
        public CompletableFuture<ValveTypeEnum> getValveType() {
            return completedFuture(valveType);
        }

        @Override
        public void subscribeValveType(HomekitCharacteristicChangeCallback homekitCharacteristicChangeCallback) {
            // nothing changes here
        }

        @Override
        public void unsubscribeValveType() {
            // nothing changes here
        }
    }

    private static class HomekitAirQualitySensor extends AbstractHomekitAccessory implements AirQualityAccessory {
        private final Map<AirQualityEnum, Object> qualityStateMapping;

        public HomekitAirQualitySensor(@NotNull HomekitEndpointContext ctx) {
            super(ctx, ctx.endpoint().getAirQuality(), AirQualityService.class);
            qualityStateMapping = createMapping(variable, AirQualityEnum.class);
        }

        @Override
        public CompletableFuture<AirQualityEnum> getAirQuality() {
            return completedFuture(getKeyFromMapping(variable, qualityStateMapping, AirQualityEnum.UNKNOWN));
        }

        @Override
        public void subscribeAirQuality(HomekitCharacteristicChangeCallback callback) {
            subscribe(callback);
        }

        @Override
        public void unsubscribeAirQuality() {
            unsubscribe();
        }
    }

    private static class HomekitBasicFan extends AbstractHomekitAccessory implements BasicFanAccessory {
        public HomekitBasicFan(@NotNull HomekitEndpointContext ctx) {
            super(ctx, ctx.endpoint().getOnState(), BasicFanService.class);
        }

        @Override
        public CompletableFuture<Boolean> isOn() {
            return completedFuture(variable.getValue().boolValue());
        }

        @Override
        public CompletableFuture<Void> setOn(boolean value) {
            updateVar(variable, value);
            return completedFuture(null);
        }

        @Override
        public void subscribeOn(HomekitCharacteristicChangeCallback callback) {
            subscribe(callback);
        }

        @Override
        public void unsubscribeOn() {
            unsubscribe();
        }
    }

    private static class HomekitBattery extends AbstractHomekitAccessory implements BatteryAccessory {

        private final ContextVar.Variable lowBatteryVar;
        private final ContextVar.Variable chargingBatteryVar;
        private final int lowThreshold;

        public HomekitBattery(@NotNull HomekitEndpointContext ctx) {
            super(ctx, ctx.endpoint().getBatteryLevel(), BatteryService.class);
            lowThreshold = getVariableValue(HomekitEndpointEntity::getBatteryLowThreshold, new DecimalType(20)).intValue();
            lowBatteryVar = getVariable(HomekitEndpointEntity::getStatusLowBattery);
            chargingBatteryVar = getVariable(HomekitEndpointEntity::getBatteryChargingState);

        }

        @Override
        public CompletableFuture<Integer> getBatteryLevel() {
            return completedFuture(variable.getValue().intValue(0));
        }

        @Override
        public CompletableFuture<StatusLowBatteryEnum> getLowBatteryState() {
            return completedFuture(lowBatteryVar.getValue().boolValue(lowThreshold) ? StatusLowBatteryEnum.LOW : StatusLowBatteryEnum.NORMAL);
        }

        @Override
        public CompletableFuture<ChargingStateEnum> getChargingState() {
            return completedFuture(chargingBatteryVar != null
                    ? chargingBatteryVar.getValue().boolValue() ? ChargingStateEnum.CHARGING : ChargingStateEnum.NOT_CHARGING
                    : ChargingStateEnum.NOT_CHARABLE);
        }

        @Override
        public void subscribeBatteryLevel(HomekitCharacteristicChangeCallback callback) {
            subscribe(callback);
        }

        @Override
        public void subscribeLowBatteryState(HomekitCharacteristicChangeCallback callback) {
            subscribe(lowBatteryVar, callback);
        }

        @Override
        public void subscribeBatteryChargingState(HomekitCharacteristicChangeCallback callback) {
            subscribe(chargingBatteryVar, callback);
        }

        @Override
        public void unsubscribeBatteryLevel() {
            unsubscribe();
        }

        @Override
        public void unsubscribeLowBatteryState() {
            unsubscribe(lowBatteryVar);
        }

        @Override
        public void unsubscribeBatteryChargingState() {
            unsubscribe(chargingBatteryVar);
        }
    }

    private static class HomekitStatelessProgrammableSwitch extends AbstractHomekitAccessory {
        public HomekitStatelessProgrammableSwitch(@NotNull HomekitEndpointContext ctx) {
            super(ctx);
        }
    }

    private static class HomekitDoor extends AbstractHomekitPositionAccessory implements DoorAccessory {

        public HomekitDoor(@NotNull HomekitEndpointContext ctx) {
            super(ctx, DoorService.class);
        }
    }

    private static class HomekitDoorbell extends AbstractHomekitAccessory {
        public HomekitDoorbell(@NotNull HomekitEndpointContext ctx) {
            super(ctx, ctx.endpoint().getStatelessProgrammableSwitch(), DoorbellService.class);
        }
    }

    private static class HomekitFaucet extends AbstractActiveHomekitAccessory implements FaucetAccessory {
        public HomekitFaucet(@NotNull HomekitEndpointContext ctx) {
            super(ctx, FaucetService.class);
        }
    }

    private static class HomekitFilter extends AbstractHomekitAccessory implements FilterMaintenanceAccessory {
        private final Map<FilterChangeIndicationEnum, Object> mapping;

        public HomekitFilter(@NotNull HomekitEndpointContext ctx) {
            super(ctx, ctx.endpoint().getFilterChangeIndication(), FilterMaintenanceService.class);
            mapping = createMapping(variable, FilterChangeIndicationEnum.class);
        }

        @Override
        public CompletableFuture<FilterChangeIndicationEnum> getFilterChangeIndication() {
            return CompletableFuture.completedFuture(
                    getKeyFromMapping(variable, mapping, FilterChangeIndicationEnum.NO_CHANGE_NEEDED));
        }

        @Override
        public void subscribeFilterChangeIndication(HomekitCharacteristicChangeCallback callback) {
            subscribe(callback);
        }

        @Override
        public void unsubscribeFilterChangeIndication() {
            unsubscribe();
        }
    }

    private static class HomekitFan extends AbstractActiveHomekitAccessory implements FanAccessory {
        public HomekitFan(@NotNull HomekitEndpointContext ctx) {
            super(ctx, FanService.class);
        }
    }

    private static class AbstractActiveHomekitAccessory extends AbstractHomekitAccessory {
        public AbstractActiveHomekitAccessory(@NotNull HomekitEndpointContext ctx, @Nullable Class<? extends Service> serviceClass) {
            super(ctx, ctx.endpoint().getActiveState(), serviceClass);
        }

        public CompletableFuture<Boolean> isActive() {
            return completedFuture(variable.getValue().boolValue());
        }

        public CompletableFuture<Void> setActive(boolean value) {
            updateVar(variable, value);
            return completedFuture(null);
        }

        public void subscribeActive(HomekitCharacteristicChangeCallback callback) {
            subscribe(callback);
        }

        public void unsubscribeActive() {
            unsubscribe();
        }
    }

    private static class HomekitSpeaker extends BaseHomekitMuteAccessory implements SpeakerAccessory {
        public HomekitSpeaker(@NotNull HomekitEndpointContext ctx) {
            super(ctx, SpeakerService.class);
        }
    }

    private static class HomekitOccupancySensor extends AbstractHomekitAccessory implements OccupancySensorAccessory {
        private final Map<OccupancyDetectedEnum, Object> mapping;

        public HomekitOccupancySensor(@NotNull HomekitEndpointContext ctx) {
            super(ctx, ctx.endpoint().getDetectedState(), OccupancySensorService.class);
            mapping = createMapping(variable, OccupancyDetectedEnum.class);
        }

        @Override
        public CompletableFuture<OccupancyDetectedEnum> getOccupancyDetected() {
            return completedFuture(getKeyFromMapping(variable, mapping, OccupancyDetectedEnum.NOT_DETECTED));
        }

        @Override
        public void subscribeOccupancyDetected(HomekitCharacteristicChangeCallback callback) {
            subscribe(callback);
        }

        @Override
        public void unsubscribeOccupancyDetected() {
            unsubscribe();
        }
    }

    private static class HomekitSlat extends AbstractHomekitAccessory implements SlatAccessory {

        private final SlatTypeEnum slatType;
        private final Map<CurrentSlatStateEnum, Object> currentSlatStateMapping;

        public HomekitSlat(@NotNull HomekitEndpointContext ctx) {
            super(ctx, ctx.endpoint().getCurrentSlatState(), SlatService.class);
            slatType = getVariableValue(endpoint -> ctx.endpoint().getSlatType(), OnOffType.OFF).boolValue() ? SlatTypeEnum.VERTICAL : SlatTypeEnum.HORIZONTAL;
            currentSlatStateMapping = createMapping(variable, CurrentSlatStateEnum.class);
        }

        @Override
        public CompletableFuture<CurrentSlatStateEnum> getSlatState() {
            return CompletableFuture.completedFuture(
                    getKeyFromMapping(variable, currentSlatStateMapping, CurrentSlatStateEnum.FIXED));
        }

        @Override
        public void subscribeSlatState(HomekitCharacteristicChangeCallback callback) {
            subscribe(callback);
        }

        @Override
        public void unsubscribeSlatState() {
            unsubscribe();
        }

        @Override
        public CompletableFuture<SlatTypeEnum> getSlatType() {
            return CompletableFuture.completedFuture(slatType);
        }
    }

    private static class HomekitMotionSensor extends AbstractHomekitAccessory implements MotionSensorAccessory {
        public HomekitMotionSensor(@NotNull HomekitEndpointContext ctx) {
            super(ctx, ctx.endpoint().getDetectedState(), MotionSensorService.class);
        }

        @Override
        public CompletableFuture<Boolean> getMotionDetected() {
            return CompletableFuture.completedFuture(variable.getValue().boolValue());
        }

        @Override
        public void subscribeMotionDetected(HomekitCharacteristicChangeCallback callback) {
            subscribe(callback);
        }

        @Override
        public void unsubscribeMotionDetected() {
            unsubscribe();
        }
    }

    private static class HomekitHumiditySensor extends AbstractHomekitAccessory implements HumiditySensorAccessory {
        public HomekitHumiditySensor(@NotNull HomekitEndpointContext ctx) {
            super(ctx, ctx.endpoint().getRelativeHumidity(), HumiditySensorService.class);
        }

        @Override
        public CompletableFuture<Double> getCurrentRelativeHumidity() {
            return CompletableFuture.completedFuture(variable.getValue().doubleValue());
        }

        @Override
        public void subscribeCurrentRelativeHumidity(HomekitCharacteristicChangeCallback callback) {
            subscribe(callback);
        }

        @Override
        public void unsubscribeCurrentRelativeHumidity() {
            unsubscribe();
        }
    }

    private static class HomekitGarageDoorOpener extends AbstractHomekitAccessory {
        public HomekitGarageDoorOpener(@NotNull HomekitEndpointContext ctx) {
            super(ctx);
            var obstructionDetectedCharacteristic = getCharacteristicOpt(ObstructionDetectedCharacteristic.class).orElseGet(
                    () -> new ObstructionDetectedCharacteristic(() -> CompletableFuture.completedFuture(false), (cb) -> {
                    }, () -> {
                    }));
            addService(new GarageDoorOpenerService(getCharacteristic(CurrentDoorStateCharacteristic.class),
                    getCharacteristic(TargetDoorStateCharacteristic.class), obstructionDetectedCharacteristic));
        }
    }

    private static class HomekitCarbonDioxideSensor extends AbstractHomekitAccessory implements CarbonDioxideSensorAccessory {
        private final Map<CarbonDioxideDetectedEnum, Object> mapping;

        public HomekitCarbonDioxideSensor(@NotNull HomekitEndpointContext ctx) {
            super(ctx, ctx.endpoint().getDetectedState(), CarbonDioxideSensorService.class);
            mapping = createMapping(variable, CarbonDioxideDetectedEnum.class);
        }

        @Override
        public CompletableFuture<CarbonDioxideDetectedEnum> getCarbonDioxideDetectedState() {
            return CompletableFuture.completedFuture(
                    getKeyFromMapping(variable, mapping, CarbonDioxideDetectedEnum.NORMAL));
        }

        @Override
        public void subscribeCarbonDioxideDetectedState(HomekitCharacteristicChangeCallback callback) {
            subscribe(callback);
        }

        @Override
        public void unsubscribeCarbonDioxideDetectedState() {
            unsubscribe();
        }
    }

    private static class HomekitCarbonMonoxideSensor extends AbstractHomekitAccessory implements CarbonMonoxideSensorAccessory {
        private final Map<CarbonMonoxideDetectedEnum, Object> mapping;

        public HomekitCarbonMonoxideSensor(@NotNull HomekitEndpointContext ctx) {
            super(ctx, ctx.endpoint().getDetectedState(), CarbonMonoxideSensorService.class);
            mapping = createMapping(variable, CarbonMonoxideDetectedEnum.class);
        }

        @Override
        public CompletableFuture<CarbonMonoxideDetectedEnum> getCarbonMonoxideDetectedState() {
            return CompletableFuture.completedFuture(
                    getKeyFromMapping(variable, mapping, CarbonMonoxideDetectedEnum.NORMAL));
        }

        @Override
        public void subscribeCarbonMonoxideDetectedState(HomekitCharacteristicChangeCallback callback) {
            subscribe(callback);
        }

        @Override
        public void unsubscribeCarbonMonoxideDetectedState() {
            unsubscribe();
        }
    }

    private static class HomekitHeaterCooler extends AbstractActiveHomekitAccessory implements HeaterCoolerAccessory {
        private final List<CurrentHeaterCoolerStateEnum> customCurrentStateList = new ArrayList<>();
        private final List<TargetHeaterCoolerStateEnum> customTargetStateList = new ArrayList<>();

        private final ContextVar.Variable currentTemperatureVar;
        private final ContextVar.Variable targetHeatingCoolStateVar;
        private final Map<TargetHeaterCoolerStateEnum, Object> targetStateMapping;
        private final ContextVar.Variable currentHeaterCoolerStateVar;
        private final Map<CurrentHeaterCoolerStateEnum, Object> currentStateMapping;

        public HomekitHeaterCooler(@NotNull HomekitEndpointContext ctx) {
            super(ctx, null);
            currentTemperatureVar = getVariable(HomekitEndpointEntity::getCurrentTemperature);
            currentHeaterCoolerStateVar = getVariable(HomekitEndpointEntity::getCurrentHeaterCoolerState);
            targetHeatingCoolStateVar = getVariable(HomekitEndpointEntity::getTargetHeatingCoolingState);
            targetStateMapping = createMapping(targetHeatingCoolStateVar, TargetHeaterCoolerStateEnum.class,
                    customTargetStateList);
            currentStateMapping = createMapping(currentHeaterCoolerStateVar, CurrentHeaterCoolerStateEnum.class,
                    customCurrentStateList);

            var service = new HeaterCoolerService(this);

            var temperatureDisplayUnit = getCharacteristic(TemperatureDisplayUnitCharacteristic.class);
            if (temperatureDisplayUnit == null) {
                service.addOptionalCharacteristic(
                        HomekitCharacteristicFactory.createSystemTemperatureDisplayUnitCharacteristic());
            }

            addService(service);
        }

        @Override
        public CurrentHeaterCoolerStateEnum[] getCurrentHeaterCoolerStateValidValues() {
            return customCurrentStateList.isEmpty()
                    ? currentStateMapping.keySet().toArray(new CurrentHeaterCoolerStateEnum[0])
                    : customCurrentStateList.toArray(new CurrentHeaterCoolerStateEnum[0]);
        }

        @Override
        public TargetHeaterCoolerStateEnum[] getTargetHeaterCoolerStateValidValues() {
            return customTargetStateList.isEmpty() ? targetStateMapping.keySet().toArray(new TargetHeaterCoolerStateEnum[0])
                    : customTargetStateList.toArray(new TargetHeaterCoolerStateEnum[0]);
        }

        @Override
        public CompletableFuture<Double> getCurrentTemperature() {
            return CompletableFuture.completedFuture(currentTemperatureVar.getValue().doubleValue(currentTemperatureVar.getMinValue(0)));
        }

        @Override
        public CompletableFuture<CurrentHeaterCoolerStateEnum> getCurrentHeaterCoolerState() {
            return CompletableFuture.completedFuture(getKeyFromMapping(currentHeaterCoolerStateVar, currentStateMapping,
                    CurrentHeaterCoolerStateEnum.INACTIVE));
        }

        @Override
        public CompletableFuture<TargetHeaterCoolerStateEnum> getTargetHeaterCoolerState() {
            return CompletableFuture.completedFuture(
                    getKeyFromMapping(targetHeatingCoolStateVar, targetStateMapping, TargetHeaterCoolerStateEnum.AUTO));
        }

        @Override
        public CompletableFuture<Void> setTargetHeaterCoolerState(TargetHeaterCoolerStateEnum state) {
            updateVar(targetHeatingCoolStateVar, new StringType((String) targetStateMapping.get(state)));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void subscribeCurrentHeaterCoolerState(HomekitCharacteristicChangeCallback callback) {
            subscribe(currentHeaterCoolerStateVar, callback);
        }

        @Override
        public void unsubscribeCurrentHeaterCoolerState() {
            unsubscribe(currentHeaterCoolerStateVar);
        }

        @Override
        public void subscribeTargetHeaterCoolerState(HomekitCharacteristicChangeCallback callback) {
            subscribe(targetHeatingCoolStateVar, callback);
        }

        @Override
        public void unsubscribeTargetHeaterCoolerState() {
            unsubscribe(targetHeatingCoolStateVar);
        }

        @Override
        public void subscribeCurrentTemperature(HomekitCharacteristicChangeCallback callback) {
            subscribe(currentTemperatureVar, callback);

        }

        @Override
        public void unsubscribeCurrentTemperature() {
            unsubscribe(currentTemperatureVar);
        }
    }

    private static class HomekitLightSensor extends AbstractHomekitAccessory implements LightSensorAccessory {
        public HomekitLightSensor(@NotNull HomekitEndpointContext ctx) {
            super(ctx, ctx.endpoint().getLightLevel(), LightSensorService.class);
        }

        @Override
        public CompletableFuture<Double> getCurrentAmbientLightLevel() {
            return CompletableFuture
                    .completedFuture(variable.getValue().doubleValue(getMinCurrentAmbientLightLevel()));
        }

        @Override
        public void subscribeCurrentAmbientLightLevel(HomekitCharacteristicChangeCallback callback) {
            subscribe(callback);
        }

        @Override
        public void unsubscribeCurrentAmbientLightLevel() {
            unsubscribe();
        }

        @Override
        public double getMinCurrentAmbientLightLevel() {
            return variable.getMinValue(0);
        }

        @Override
        public double getMaxCurrentAmbientLightLevel() {
            return variable.getMaxValue(120000);
        }
    }

    private static class HomekitLightBulb extends AbstractHomekitAccessory implements LightbulbAccessory {
        public HomekitLightBulb(@NotNull HomekitEndpointContext ctx) {
            super(ctx, ctx.endpoint().getOnState(), LightbulbService.class);
        }

        @Override
        public CompletableFuture<Boolean> getLightbulbPowerState() {
            return CompletableFuture.completedFuture(variable.getValue().boolValue());
        }

        @Override
        public CompletableFuture<Void> setLightbulbPowerState(boolean value) {
            updateVar(variable, value);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void subscribeLightbulbPowerState(HomekitCharacteristicChangeCallback homekitCharacteristicChangeCallback) {

        }

        @Override
        public void unsubscribeLightbulbPowerState() {

        }
    }

    private static class HomekitLock extends AbstractHomekitAccessory implements LockMechanismAccessory {
        private final ContextVar.Variable lockTargetStateVar;
        private final Map<LockCurrentStateEnum, Object> currentStateMapping;
        private final Map<LockTargetStateEnum, Object> targetStateMapping;

        public HomekitLock(@NotNull HomekitEndpointContext ctx) {
            super(ctx, ctx.endpoint().getLockCurrentState(), LockMechanismService.class);
            lockTargetStateVar = getVariable(HomekitEndpointEntity::getLockTargetState);
            currentStateMapping = createMapping(variable, LockCurrentStateEnum.class);
            targetStateMapping = createMapping(lockTargetStateVar, LockTargetStateEnum.class);
        }

        @Override
        public CompletableFuture<LockCurrentStateEnum> getLockCurrentState() {
            return CompletableFuture.completedFuture(getKeyFromMapping(variable,
                    currentStateMapping, LockCurrentStateEnum.UNKNOWN));
        }

        @Override
        public CompletableFuture<LockTargetStateEnum> getLockTargetState() {
            return CompletableFuture.completedFuture(getKeyFromMapping(lockTargetStateVar,
                    targetStateMapping, LockTargetStateEnum.UNSECURED));
        }

        @Override
        public CompletableFuture<Void> setLockTargetState(LockTargetStateEnum state) {
            updateVar(lockTargetStateVar, new StringType((String) targetStateMapping.get(state)));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void subscribeLockCurrentState(HomekitCharacteristicChangeCallback callback) {
            subscribe(callback);
        }

        @Override
        public void unsubscribeLockCurrentState() {
            unsubscribe();
        }

        @Override
        public void subscribeLockTargetState(HomekitCharacteristicChangeCallback callback) {
            subscribe(lockTargetStateVar, callback);
        }

        @Override
        public void unsubscribeLockTargetState() {
            unsubscribe(lockTargetStateVar);
        }
    }

    private static class HomekitMicrophone extends BaseHomekitMuteAccessory implements MicrophoneAccessory {
        public HomekitMicrophone(@NotNull HomekitEndpointContext ctx) {
            super(ctx, MicrophoneService.class);
        }
    }

    private static abstract class BaseHomekitMuteAccessory extends AbstractHomekitAccessory {
        public BaseHomekitMuteAccessory(@NotNull HomekitEndpointContext ctx, @Nullable Class<? extends Service> serviceClass) {
            super(ctx, ctx.endpoint().getMute(), serviceClass);
        }

        public CompletableFuture<Boolean> isMuted() {
            return completedFuture(variable.getValue().boolValue());
        }

        public CompletableFuture<Void> setMute(boolean value) {
            updateVar(variable, value);
            return completedFuture(null);
        }

        public void subscribeMuteState(HomekitCharacteristicChangeCallback callback) {
            subscribe(callback);
        }

        public void unsubscribeMuteState() {
            unsubscribe();
        }
    }

    private static class HomekitOutlet extends AbstractHomekitAccessory implements OutletAccessory {
        private final ContextVar.Variable inUseStatus;

        public HomekitOutlet(@NotNull HomekitEndpointContext ctx) {
            super(ctx, ctx.endpoint().getOnState(), OutletService.class);
            inUseStatus = ctx.getVariable(ctx.endpoint().getInuseStatus());
        }

        @Override
        public CompletableFuture<Boolean> getPowerState() {
            return CompletableFuture.completedFuture(variable.getValue().boolValue());
        }

        @Override
        public CompletableFuture<Boolean> getOutletInUse() {
            return CompletableFuture.completedFuture(inUseStatus.getValue().boolValue());
        }

        @Override
        public CompletableFuture<Void> setPowerState(boolean value) {
            updateVar(variable, value);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void subscribePowerState(HomekitCharacteristicChangeCallback callback) {
            subscribe(callback);
        }

        @Override
        public void subscribeOutletInUse(HomekitCharacteristicChangeCallback callback) {
            subscribe(inUseStatus, callback);
        }

        @Override
        public void unsubscribePowerState() {
            unsubscribe();
        }

        @Override
        public void unsubscribeOutletInUse() {
            unsubscribe(inUseStatus);
        }
    }

    private static class HomekitTemperatureSensor extends AbstractHomekitAccessory {
        public HomekitTemperatureSensor(@NotNull HomekitEndpointContext ctx) {
            super(ctx, ctx.endpoint().getCurrentTemperature(), null);
            var currentTemperatureCharacteristic = HomekitCharacteristicFactory.createCurrentTemperatureCharacteristic(ctx);
            addService(new TemperatureSensorService(currentTemperatureCharacteristic));
        }
    }

    private static class HomekitThermostat extends AbstractHomekitAccessory {
        private @Nullable HomekitCharacteristicChangeCallback targetTemperatureCallback = null;

        public HomekitThermostat(@NotNull HomekitEndpointContext ctx) {
            super(ctx);
            var coolingThresholdTemperatureCharacteristic = getCharacteristic(
                    CoolingThresholdTemperatureCharacteristic.class);
            var heatingThresholdTemperatureCharacteristic = getCharacteristic(
                    HeatingThresholdTemperatureCharacteristic.class);
            Optional<TargetTemperatureCharacteristic> targetTemperatureCharacteristic = getCharacteristicOpt(TargetTemperatureCharacteristic.class);

            if (coolingThresholdTemperatureCharacteristic == null
                && heatingThresholdTemperatureCharacteristic == null
                && targetTemperatureCharacteristic.isEmpty()) {
                throw new RuntimeException(
                        "Unable to create thermostat; at least one of TargetTemperature, CoolingThresholdTemperature, or HeatingThresholdTemperature is required.");
            }

            TargetHeatingCoolingStateCharacteristic targetHeatingCoolingStateCharacteristic = getCharacteristic(TargetHeatingCoolingStateCharacteristic.class);

            // TargetTemperature not provided; simulate by forwarding to HeatingThresholdTemperature and
            // CoolingThresholdTemperature
            // as appropriate
            if (targetTemperatureCharacteristic.isEmpty()) {
                if (Arrays.stream(targetHeatingCoolingStateCharacteristic.getValidValues())
                            .anyMatch(v -> v.equals(TargetHeatingCoolingStateEnum.HEAT))
                    && heatingThresholdTemperatureCharacteristic == null) {
                    throw new RuntimeException(
                            "HeatingThresholdTemperature must be provided if HEAT mode is allowed and TargetTemperature is not provided.");
                }
                if (Arrays.asList(targetHeatingCoolingStateCharacteristic.getValidValues()).contains(TargetHeatingCoolingStateEnum.COOL)
                    && coolingThresholdTemperatureCharacteristic == null) {
                    throw new RuntimeException(
                            "CoolingThresholdTemperature must be provided if COOL mode is allowed and TargetTemperature is not provided.");
                }

                double minValue, maxValue, minStep;
                if (coolingThresholdTemperatureCharacteristic != null
                    && heatingThresholdTemperatureCharacteristic != null) {
                    minValue = Math.min(coolingThresholdTemperatureCharacteristic.getMinValue(),
                            heatingThresholdTemperatureCharacteristic.getMinValue());
                    maxValue = Math.max(coolingThresholdTemperatureCharacteristic.getMaxValue(),
                            heatingThresholdTemperatureCharacteristic.getMaxValue());
                    minStep = Math.min(coolingThresholdTemperatureCharacteristic.getMinStep(),
                            heatingThresholdTemperatureCharacteristic.getMinStep());
                } else if (coolingThresholdTemperatureCharacteristic != null) {
                    minValue = coolingThresholdTemperatureCharacteristic.getMinValue();
                    maxValue = coolingThresholdTemperatureCharacteristic.getMaxValue();
                    minStep = coolingThresholdTemperatureCharacteristic.getMinStep();
                } else {
                    minValue = heatingThresholdTemperatureCharacteristic.getMinValue();
                    maxValue = heatingThresholdTemperatureCharacteristic.getMaxValue();
                    minStep = heatingThresholdTemperatureCharacteristic.getMinStep();
                }
                targetTemperatureCharacteristic = Optional
                        .of(new TargetTemperatureCharacteristic(minValue, maxValue, minStep, () -> {
                            // return the value from the characteristic corresponding to the current mode
                            try {
                                switch (targetHeatingCoolingStateCharacteristic.getEnumValue().get()) {
                                    case HEAT:
                                        return heatingThresholdTemperatureCharacteristic.getValue();
                                    case COOL:
                                        return coolingThresholdTemperatureCharacteristic.getValue();
                                    default:
                                        return CompletableFuture.completedFuture(
                                                (heatingThresholdTemperatureCharacteristic.getValue().get()
                                                 + coolingThresholdTemperatureCharacteristic.getValue().get())
                                                / 2);
                                }
                            } catch (InterruptedException | ExecutionException e) {
                                return null;
                            }
                        }, value -> {
                            try {
                                // set the charactestic corresponding to the current mode
                                switch (targetHeatingCoolingStateCharacteristic.getEnumValue().get()) {
                                    case HEAT:
                                        heatingThresholdTemperatureCharacteristic.setValue(value);
                                        break;
                                    case COOL:
                                        coolingThresholdTemperatureCharacteristic.setValue(value);
                                        break;
                                    default:
                                        // ignore
                                }
                            } catch (InterruptedException | ExecutionException e) {
                                // can't happen, since the futures are synchronous
                            }
                        }, cb -> {
                            targetTemperatureCallback = cb;
                            if (heatingThresholdTemperatureCharacteristic != null) {
                                getCharacteristic(HeatingThresholdTemperatureCharacteristic.class);
                                /*getUpdater().subscribe(
                                        (GenericItem) getCharacteristic(HEATING_THRESHOLD_TEMPERATURE).getItem(),
                                        TARGET_TEMPERATURE.getTag(), this::thresholdTemperatureChanged);*/
                            }
                            if (coolingThresholdTemperatureCharacteristic != null) {
                                /*getUpdater().subscribe(
                                        (GenericItem) getCharacteristic(COOLING_THRESHOLD_TEMPERATURE).getItem(),
                                        TARGET_TEMPERATURE.getTag(), this::thresholdTemperatureChanged);*/
                            }
                            /*getUpdater().subscribe(
                                    (GenericItem) getCharacteristic(TARGET_HEATING_COOLING_STATE).getItem(),
                                    TARGET_TEMPERATURE.getTag(), this::thresholdTemperatureChanged);*/
                        }, () -> {
                            if (heatingThresholdTemperatureCharacteristic != null) {
                                /*getUpdater().unsubscribe(
                                        (GenericItem) getCharacteristic(HEATING_THRESHOLD_TEMPERATURE).getItem(),
                                        TARGET_TEMPERATURE.getTag());*/
                            }
                            if (coolingThresholdTemperatureCharacteristic != null) {
                                /*getUpdater().unsubscribe(
                                        (GenericItem) getCharacteristic(COOLING_THRESHOLD_TEMPERATURE).getItem(),
                                        TARGET_TEMPERATURE.getTag());*/
                            }
                            /*getUpdater().unsubscribe(
                                    (GenericItem) getCharacteristic(TARGET_HEATING_COOLING_STATE).getItem(),
                                    TARGET_TEMPERATURE.getTag());*/
                            targetTemperatureCallback = null;
                        }));
            }

            // These characteristics are technically mandatory, but we provide defaults if they're not provided
            var currentHeatingCoolingStateCharacteristic = getCharacteristicOpt(CurrentHeatingCoolingStateCharacteristic.class)
                    .orElseGet(() -> new CurrentHeatingCoolingStateCharacteristic(
                                    new CurrentHeatingCoolingStateEnum[]{CurrentHeatingCoolingStateEnum.OFF},
                                    () -> CompletableFuture.completedFuture(CurrentHeatingCoolingStateEnum.OFF), (cb) -> {
                            }, () -> {
                            })

                    );
            var displayUnitCharacteristic = getCharacteristicOpt(TemperatureDisplayUnitCharacteristic.class)
                    .orElseGet(HomekitCharacteristicFactory::createSystemTemperatureDisplayUnitCharacteristic);

            addService(
                    new ThermostatService(currentHeatingCoolingStateCharacteristic, targetHeatingCoolingStateCharacteristic,
                            getCharacteristic(CurrentTemperatureCharacteristic.class),
                            targetTemperatureCharacteristic.get(), displayUnitCharacteristic));
        }

        private void thresholdTemperatureChanged() {
            targetTemperatureCallback.changed();
        }
    }
}