package org.homio.addon.homekit.accessories;

import io.github.hapjava.accessories.*;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;
import io.github.hapjava.characteristics.impl.airquality.AirQualityEnum;
import io.github.hapjava.characteristics.impl.common.ActiveEnum;
import io.github.hapjava.characteristics.impl.common.InUseEnum;
import io.github.hapjava.characteristics.impl.contactsensor.ContactStateEnum;
import io.github.hapjava.characteristics.impl.leaksensor.LeakDetectedStateEnum;
import io.github.hapjava.characteristics.impl.smokesensor.SmokeDetectedStateEnum;
import io.github.hapjava.characteristics.impl.valve.RemainingDurationCharacteristic;
import io.github.hapjava.characteristics.impl.valve.ValveTypeEnum;
import io.github.hapjava.services.Service;
import io.github.hapjava.services.impl.*;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.homekit.HomekitCharacteristicFactory;
import org.homio.addon.homekit.HomekitEndpointContext;
import org.homio.addon.homekit.enums.HomekitAccessoryType;
import org.homio.api.ContextVar;
import org.homio.api.state.OnOffType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            put(Switch, HomekitSwitch::new);
            put(LeakSensor, HomekitLeakSensor::new);
            put(ContactSensor, HomekitContactSensor::new);
            put(SmokeSensor, HomekitSmokeSensor::new);
            put(Valve, HomekitValve::new);
            put(Window, HomekitWindow::new);
            put(WindowCovering, HomekitWindowCovering::new);

            // Common Positional
            // put(CURRENT_POSITION, HomekitCharacteristicFactory::createCurrentPositionCharacteristic);
            // put(TARGET_POSITION, HomekitCharacteristicFactory::createTargetPositionCharacteristic);
            // put(POSITION_STATE, HomekitCharacteristicFactory::createPositionStateCharacteristic);
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
            this.characteristics.putAll(HomekitCharacteristicFactory.buildInitialCharacteristics(c, groupName));
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
            super(c, SwitchService.class, c.endpoint().getOnState());
        }

        @Override
        public CompletableFuture<Boolean> getSwitchState() {
            return CompletableFuture.completedFuture(variable.getValue().boolValue());
        }

        @Override
        public CompletableFuture<Void> setSwitchState(boolean state) {
            variable.set(OnOffType.of(state));
            return CompletableFuture.completedFuture(null);
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

        @Override
        public CompletableFuture<Void> setTargetPosition(Integer integer) throws Exception {
            return null;
        }
    }

    private static class HomekitSmokeSensor extends AbstractHomekitAccessory implements SmokeSensorAccessory {

        private final @NotNull Map<SmokeDetectedStateEnum, Object> mapping;

        public HomekitSmokeSensor(@NotNull HomekitEndpointContext c) {
            super(c, SmokeSensorService.class, c.endpoint().getDetectedState());
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
            super(c, ContactSensorService.class, c.endpoint().getDetectedState());
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
            super(c, LeakSensorService.class, c.endpoint().getDetectedState());
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
        public HomekitWindowCovering(HomekitEndpointContext ctx) {
            super(ctx, WindowCoveringService.class);
        }
    }

    @Log4j2
    private static class HomekitValve extends AbstractHomekitAccessory implements ValveAccessory {
        private static final String CONFIG_VALVE_TYPE = "ValveType";
        private static final String CONFIG_VALVE_TYPE_DEPRECATED = "homekitValveType";
        public static final String CONFIG_DEFAULT_DURATION = "homekitDefaultDuration";
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
        private ScheduledFuture<?> valveTimer;
        private final boolean homekitTimer;
        private ValveTypeEnum valveType;


        public HomekitValve(HomekitEndpointContext ctx) {
            super(ctx, ValveService.class, ctx.endpoint().getActiveState());
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
                return CompletableFuture.completedFuture(remainingTime);
            }, getSubscriber(remainingDurationVar, ctx, RemainingDuration),
                    getUnsubscriber(remainingDurationVar, ctx, RemainingDuration)));
        }

        @Override
        public CompletableFuture<ActiveEnum> getValveActive() {
            return CompletableFuture
                    .completedFuture(variable.getValue().boolValue() ? ActiveEnum.ACTIVE : ActiveEnum.INACTIVE);
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
            return CompletableFuture.completedFuture(null);
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
            return CompletableFuture.completedFuture(inUseStatus.getValue().boolValue() ? InUseEnum.IN_USE : InUseEnum.NOT_IN_USE);
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
            return CompletableFuture.completedFuture(valveType);
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

        public HomekitAirQualitySensor(HomekitEndpointContext ctx) {
            super(ctx, AirQualityService.class, ctx.endpoint().getAirQuality());
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
        public HomekitBasicFan(HomekitEndpointContext ctx) {
            super(ctx, BasicFanService.class, ctx.endpoint().getOnState());
        }

        @Override
        public CompletableFuture<Boolean> isOn() {
            return null;
        }

        @Override
        public CompletableFuture<Void> setOn(boolean b) throws Exception {
            return null;
        }

        @Override
        public void subscribeOn(HomekitCharacteristicChangeCallback homekitCharacteristicChangeCallback) {

        }

        @Override
        public void unsubscribeOn() {

        }
    }
}