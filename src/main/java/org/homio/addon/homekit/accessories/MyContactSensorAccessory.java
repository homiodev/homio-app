package org.homio.addon.homekit.accessories;

import io.github.hapjava.accessories.ContactSensorAccessory;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;
import io.github.hapjava.characteristics.impl.contactsensor.ContactStateEnum;
import io.github.hapjava.services.Service;
import io.github.hapjava.services.impl.AccessoryInformationService;
import io.github.hapjava.services.impl.ContactSensorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class MyContactSensorAccessory implements ContactSensorAccessory {

    private static final Logger logger = LoggerFactory.getLogger(MyContactSensorAccessory.class);
    private final int id;
    private final String accessoryName; // Имя аксессуара, используемое для getName()
    // true = контакт ОБНАРУЖЕН (дверь/окно закрыто)
    // false = контакт НЕ ОБНАРУЖЕН (дверь/окно открыто)
    private final AtomicBoolean contactActuallyDetected = new AtomicBoolean(false);
    private final AtomicReference<HomekitCharacteristicChangeCallback> contactStateNotification = new AtomicReference<>();

    public MyContactSensorAccessory(int id, String name) {
        this.id = id;
        this.accessoryName = name;
    }

    @Override
    public int getId() {
        return id;
    }

    // Метод для предоставления имени аксессуара сервису информации
    @Override
    public CompletableFuture<String> getName() {
        return CompletableFuture.completedFuture(this.accessoryName);
    }

    @Override
    public void identify() {
        logger.info("Идентификация датчика контакта: {}", this.accessoryName);
    }

    @Override
    public CompletableFuture<String> getSerialNumber() {
        return CompletableFuture.completedFuture("CS-" + id);
    }

    @Override
    public CompletableFuture<String> getModel() {
        return CompletableFuture.completedFuture("MyHAPContactSensor-V4");
    }

    @Override
    public CompletableFuture<String> getManufacturer() {
        return CompletableFuture.completedFuture("Java HomeKit Example");
    }

    @Override
    public CompletableFuture<String> getFirmwareRevision() {
        return CompletableFuture.completedFuture("1.0.3");
    }

    @Override
    public Collection<Service> getServices() {
        // Сервис информации об аксессуаре (имя, производитель и т.д.)
        AccessoryInformationService infoService = new AccessoryInformationService(this);
        // Сервис датчика контакта
        ContactSensorService contactService = new ContactSensorService(this);
        return List.of(infoService, contactService);
    }

    // --- Методы, специфичные для ContactSensorAccessory ---

    @Override
    public CompletableFuture<ContactStateEnum> getCurrentState() { // <--- ИСПРАВЛЕН ТИП
        /*boolean isDetected = this.contactActuallyDetected.get();
        // Используем значения из ContactStateEnum
        ContactStateEnum currentState = isDetected ? ContactStateEnum.DETECTED : ContactStateEnum.NOT_DETECTED;
        logger.debug("Датчик контакта '{}' запросил getCurrentState: {} (isDetected: {})",
                this.accessoryName, currentState, isDetected);*/
        return CompletableFuture.completedFuture(ContactStateEnum.NOT_DETECTED);
    }

    @Override
    public void subscribeContactState(HomekitCharacteristicChangeCallback callback) {
        /*this.contactStateNotification.set(callback);
        logger.info("HomeKit подписался на изменения состояния контакта для '{}'", this.accessoryName);*/
    }

    @Override
    public void unsubscribeContactState() {
        /*this.contactStateNotification.set(null);
        logger.info("HomeKit отписался от изменений состояния контакта для '{}'", this.accessoryName);*/
    }
}