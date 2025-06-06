package org.homio.addon.homekit;

import io.github.hapjava.accessories.*;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;
import io.github.hapjava.characteristics.impl.contactsensor.ContactStateEnum;
import io.github.hapjava.characteristics.impl.leaksensor.LeakDetectedStateEnum;
import io.github.hapjava.characteristics.impl.smokesensor.SmokeDetectedStateEnum;
import io.github.hapjava.services.impl.ContactSensorService;
import io.github.hapjava.services.impl.LeakSensorService;
import io.github.hapjava.services.impl.SmokeSensorService;
import io.github.hapjava.services.impl.SwitchService;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.homekit.accessories.AbstractHomekitAccessory;
import org.homio.addon.homekit.enums.HomekitAccessoryType;
import org.homio.api.state.OnOffType;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.homio.addon.homekit.HomekitCharacteristicFactory.createMapping;
import static org.homio.addon.homekit.HomekitCharacteristicFactory.getKeyFromMapping;
import static org.homio.addon.homekit.enums.HomekitAccessoryType.*;

@Log4j2
public class HomekitAccessoryFactory {

    private static final Map<HomekitAccessoryType, Function<HomekitEndpointEntity, HomekitAccessory>> ACCESSORY_BUILDERS = new HashMap<>() {
        {
            put(SWITCH, HomekitSwitch::new);
            put(LEAK_SENSOR, HomekitLeakSensor::new);
            put(CONTACT_SENSOR, HomekitContactSensor::new);
            put(SMOKE_SENSOR, HomekitSmokeSensor::new);

            // Common Positional
            //put(CURRENT_POSITION, HomekitCharacteristicFactory::createCurrentPositionCharacteristic);
            // put(TARGET_POSITION, HomekitCharacteristicFactory::createTargetPositionCharacteristic);
            // put(POSITION_STATE, HomekitCharacteristicFactory::createPositionStateCharacteristic);
        }
    };

    public static HomekitAccessory create(HomekitAccessoryType accessoryType, HomekitEndpointEntity endpoint) {
        return ACCESSORY_BUILDERS.get(accessoryType).apply(endpoint);
    }

    private static class HomekitSwitch extends AbstractHomekitAccessory implements SwitchAccessory {

        public HomekitSwitch(HomekitEndpointEntity e) {
            super(e, SwitchService.class, e.getOnState());
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

    private static class HomekitSmokeSensor extends AbstractHomekitAccessory implements SmokeSensorAccessory {

        private final Map<SmokeDetectedStateEnum, Object> mapping;

        public HomekitSmokeSensor(HomekitEndpointEntity e) {
            super(e, SmokeSensorService.class, e.getDetectedState());
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

        private final Map<ContactStateEnum, Object> mapping;

        public HomekitContactSensor(HomekitEndpointEntity e) {
            super(e, ContactSensorService.class, e.getDetectedState());
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

        private final Map<LeakDetectedStateEnum, Object> mapping;

        public HomekitLeakSensor(HomekitEndpointEntity e) {
            super(e, LeakSensorService.class, e.getDetectedState());
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
}