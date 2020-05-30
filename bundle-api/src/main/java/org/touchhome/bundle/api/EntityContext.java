package org.touchhome.bundle.api;

import io.swagger.annotations.ApiParam;
import org.apache.commons.lang3.SystemUtils;
import org.touchhome.bundle.api.json.NotificationEntityJSON;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.model.PureEntity;
import org.touchhome.bundle.api.notification.NotificationType;
import org.touchhome.bundle.api.repository.AbstractRepository;
import org.touchhome.bundle.api.ui.PublicJsMethod;

import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface EntityContext {

    static boolean isTestEnvironment() {
        return "true".equals(System.getProperty("development"));
    }

    static boolean isDockerEnvironment() {
        return "true".equals(System.getProperty("docker"));
    }

    static boolean isLinuxEnvironment() {
        return SystemUtils.IS_OS_LINUX && !isDockerEnvironment() && !isTestEnvironment();
    }

    static boolean isLinuxOrDockerEnvironment() {
        return SystemUtils.IS_OS_LINUX && !isTestEnvironment();
    }

    @PublicJsMethod
    void sendNotification(String destination, Object param);

    @PublicJsMethod
    default void sendNotification(NotificationEntityJSON notificationEntityJSON) {
        if (notificationEntityJSON != null) {
            sendNotification("-notification", notificationEntityJSON);
        }
    }

    @PublicJsMethod
    void showAlwaysOnViewNotification(NotificationEntityJSON notificationEntityJSON, @ApiParam("color (in secs.)") int duration, @ApiParam("color") String color);

    @PublicJsMethod
    void hideAlwaysOnViewNotification(NotificationEntityJSON notificationEntityJSON);

    @PublicJsMethod
    default void sendNotification(String name, String description, NotificationType notificationType) {
        sendNotification(new NotificationEntityJSON("random-" + System.currentTimeMillis())
                .setName(name)
                .setDescription(description)
                .setNotificationType(notificationType));
    }

    @PublicJsMethod
    <T> T getSettingValue(Class<? extends BundleSettingPlugin<T>> bundleSettingPluginClazz);

    default <T> void listenSettingValueAsync(Class<? extends BundleSettingPlugin<T>> bundleSettingPluginClazz, Consumer<T> listener) {
        listenSettingValue(bundleSettingPluginClazz, value ->
                new Thread(() -> listener.accept(value), "run-listen-value-async-" + bundleSettingPluginClazz.getSimpleName()).start());
    }

    default <T> void listenSettingValueAsync(Class<? extends BundleSettingPlugin<T>> bundleSettingPluginClazz, Runnable listener) {
        listenSettingValueAsync(bundleSettingPluginClazz, t -> listener.run());
    }

    default <T> void listenSettingValue(Class<? extends BundleSettingPlugin<T>> bundleSettingPluginClazz, Runnable listener) {
        listenSettingValue(bundleSettingPluginClazz, p -> listener.run());
    }

    <T> void listenSettingValue(Class<? extends BundleSettingPlugin<T>> bundleSettingPluginClazz, Consumer<T> listener);

    @PublicJsMethod
    <T> void setSettingValueRaw(Class<? extends BundleSettingPlugin<T>> bundleSettingPluginClazz, @NotNull String value);

    @PublicJsMethod
    <T> void setSettingValue(Class<? extends BundleSettingPlugin<T>> bundleSettingPluginClazz, T value);

    @PublicJsMethod
    <T> void setSettingValueSilence(Class<? extends BundleSettingPlugin<T>> settingPluginClazz, @NotNull T value);

    @PublicJsMethod
    <T> void setSettingValueSilenceRaw(Class<? extends BundleSettingPlugin<T>> settingPluginClazz, @NotNull String value);

    @PublicJsMethod
    <T extends BaseEntity> T getEntity(String entityID);

    @PublicJsMethod
    <T extends BaseEntity> T getEntityOrDefault(String entityID, T defEntity);

    @PublicJsMethod
    <T> T getEntity(String entityID, Class<T> clazz);

    @PublicJsMethod
    <T extends BaseEntity> T getEntity(String entityID, boolean useCache);

    @PublicJsMethod
    default Optional<AbstractRepository> getRepository(BaseEntity baseEntity) {
        return getRepository(baseEntity.getEntityID());
    }

    @PublicJsMethod
    Optional<AbstractRepository> getRepository(String entityID);

    @PublicJsMethod
    AbstractRepository getRepository(Class<? extends BaseEntity> entityClass);

    @PublicJsMethod
    <T extends BaseEntity> T getEntity(T entity);

    @PublicJsMethod
    <T extends PureEntity> void saveDelayed(T entity);

    @PublicJsMethod
    <T extends BaseEntity> void saveDelayed(T entity);

    @PublicJsMethod
    <T extends PureEntity> void save(T entity);

    @PublicJsMethod
    <T extends BaseEntity> T save(T entity);

    @PublicJsMethod
    BaseEntity<? extends BaseEntity> delete(BaseEntity baseEntity);

    @PublicJsMethod
    void sendInfoMessage(String message);

    @PublicJsMethod
    void sendErrorMessage(String message, Exception ex);

    @PublicJsMethod
    <T extends BaseEntity> List<T> findAll(Class<T> clazz);

    @PublicJsMethod
    default <T extends BaseEntity> List<T> findAll(T baseEntity) {
        return (List<T>) findAll(baseEntity.getClass());
    }

    @PublicJsMethod
    BaseEntity<? extends BaseEntity> delete(String entityId);

    @PublicJsMethod
    AbstractRepository<? extends BaseEntity> getRepositoryByPrefix(String repositoryPrefix);

    @PublicJsMethod
    AbstractRepository<BaseEntity> getRepositoryByClass(String className);

    @PublicJsMethod
    <T extends BaseEntity> T getEntityByName(String name, Class<T> entityClass);

    <T extends BaseEntity> void addEntityUpdateListener(String entityID, Consumer<T> listener);

    <T extends BaseEntity> void addEntityUpdateListener(String entityID, BiConsumer<T, T> listener);

    Map<DeviceFeature, Boolean> getDeviceFeatures();

    <T extends BaseEntity> void addEntityUpdateListener(Class<T> entityClass, Consumer<T> listener);

    <T extends BaseEntity> void addEntityUpdateListener(Class<T> entityClass, BiConsumer<T, T> listener);

    <T extends BaseEntity> void removeEntityUpdateListener(String entityID, BiConsumer<T, T> listener);

    void disableFeature(DeviceFeature deviceFeature);

    <T> T getBean(String beanName, Class<T> clazz);

    <T> Collection<T> getBeansOfType(Class<T> clazz);

    enum DeviceFeature {
        Bluetooth, HotSpot, GPIO
    }
}
