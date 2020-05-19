package org.touchhome.app.manager.common;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang.StringUtils;
import org.hibernate.event.internal.PostDeleteEventListenerStandardImpl;
import org.hibernate.event.internal.PostInsertEventListenerStandardImpl;
import org.hibernate.event.internal.PostUpdateEventListenerStandardImpl;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.internal.SessionFactoryImpl;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.touchhome.app.LogService;
import org.touchhome.app.config.WebSocketConfig;
import org.touchhome.app.json.AlwaysOnTopNotificationEntityJSONJSON;
import org.touchhome.app.manager.CacheService;
import org.touchhome.app.manager.WidgetManager;
import org.touchhome.app.manager.scripting.ScriptManager;
import org.touchhome.app.model.entity.SettingEntity;
import org.touchhome.app.repository.SettingRepository;
import org.touchhome.app.repository.crud.base.BaseCrudRepository;
import org.touchhome.app.repository.device.AllDeviceRepository;
import org.touchhome.app.rest.ConsoleController;
import org.touchhome.app.rest.SettingController;
import org.touchhome.app.setting.system.SystemClearCacheButtonSetting;
import org.touchhome.app.setting.system.SystemShowEntityStateSetting;
import org.touchhome.app.workspace.WorkspaceManager;
import org.touchhome.bundle.api.BundleContext;
import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.exception.NotFoundException;
import org.touchhome.bundle.api.json.NotificationEntityJSON;
import org.touchhome.bundle.api.manager.LoggerManager;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.model.DeviceBaseEntity;
import org.touchhome.bundle.api.model.PureEntity;
import org.touchhome.bundle.api.model.UserEntity;
import org.touchhome.bundle.api.model.workspace.WorkspaceStandaloneVariableEntity;
import org.touchhome.bundle.api.model.workspace.bool.WorkspaceBooleanEntity;
import org.touchhome.bundle.api.model.workspace.var.WorkspaceVariableEntity;
import org.touchhome.bundle.api.notification.NotificationType;
import org.touchhome.bundle.api.repository.AbstractRepository;
import org.touchhome.bundle.api.repository.PureRepository;
import org.touchhome.bundle.api.repository.impl.UserRepository;
import org.touchhome.bundle.api.scratch.Scratch3ExtensionBlocks;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.util.ClassFinder;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.bundle.api.workspace.BroadcastLockManager;
import org.touchhome.bundle.arduino.model.ArduinoDeviceEntity;
import org.touchhome.bundle.cloud.impl.NettyClientService;
import org.touchhome.bundle.raspberry.model.RaspberryDeviceEntity;

import javax.persistence.EntityManagerFactory;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.touchhome.bundle.api.model.UserEntity.ADMIN_USER;
import static org.touchhome.bundle.raspberry.model.RaspberryDeviceEntity.DEFAULT_DEVICE_ENTITY_ID;

@Log4j2
@Component
@RequiredArgsConstructor
public class InternalManager implements EntityContext {

    private static final Map<BundleSettingPlugin, String> settingTransientState = new HashMap<>();
    public static Map<String, BundleSettingPlugin> settingPluginsByPluginKey = new HashMap<>();
    static Map<String, AbstractRepository> repositories;
    private static Map<Class<? extends BundleSettingPlugin>, BundleSettingPlugin> settingPluginsByPluginClass = new HashMap<>();
    private static EntityManager entityManager;
    private static Map<String, AbstractRepository> repositoriesByPrefix;
    private static Map<String, PureRepository> pureRepositories;

    private final Map<String, List<BiConsumer>> entityUpdateListeners = new HashMap<>();
    private final Map<Class<? extends BaseEntity>, List<BiConsumer>> entityClassUpdateListeners = new HashMap<>();
    private final Map<DeviceFeature, Boolean> deviceFeatures = Stream.of(DeviceFeature.values()).collect(Collectors.toMap(f -> f, f -> Boolean.TRUE));
    private final Set<NotificationEntityJSON> notifications = new HashSet<>();
    private Map<Class<? extends BundleSettingPlugin>, List<Consumer<?>>> settingListeners = new HashMap<>();

    private final ClassFinder classFinder;
    private final CacheService cacheService;
    private final AllDeviceRepository allDeviceRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final EntityManagerFactory entityManagerFactory;
    private final PlatformTransactionManager transactionManager;
    private final BroadcastLockManager broadcastLockManager;
    private TransactionTemplate transactionTemplate;
    private Boolean showEntityState;

    public Set<NotificationEntityJSON> getNotifications() {
        long time = System.currentTimeMillis();
        notifications.removeIf(entity -> entity instanceof AlwaysOnTopNotificationEntityJSONJSON
                && time - entity.getCreationTime().getTime() > ((AlwaysOnTopNotificationEntityJSONJSON) entity).getDuration() * 1000);
        return notifications;
    }

    @SneakyThrows
    public void afterContextStart(ApplicationContext applicationContext) {
        this.updateDeviceFeatures();
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        for (Class<? extends BundleSettingPlugin> settingPlugin : classFinder.getClassesWithParent(BundleSettingPlugin.class)) {
            BundleSettingPlugin bundleSettingPlugin = settingPlugin.newInstance();
            settingPluginsByPluginKey.put(SettingRepository.getKey(bundleSettingPlugin), bundleSettingPlugin);
            settingPluginsByPluginClass.put(settingPlugin, bundleSettingPlugin);
        }
        entityManager = applicationContext.getBean(EntityManager.class);
        repositoriesByPrefix = applicationContext.getBean("repositoriesByPrefix", Map.class);
        pureRepositories = applicationContext.getBean("pureRepositories", Map.class);
        repositories = applicationContext.getBean("repositories", Map.class);

        applicationContext.getBean(LoggerManager.class).postConstruct();
        applicationContext.getBean(LogService.class).setEntityContext(this);

        applicationContext.getBean(SettingRepository.class).postConstruct(settingPluginsByPluginKey.values());
        registerEntityListeners();

        createUser();
        createRaspberryDevice();
        createArduinoDevice();

        applicationContext.getBean(UserRepository.class).postConstruct(this);
        applicationContext.getBean(NettyClientService.class).postConstruct();
        applicationContext.getBean(ScriptManager.class).postConstruct();
        applicationContext.getBean(ConsoleController.class).postConstruct();
        applicationContext.getBean(SettingController.class).postConstruct(settingPluginsByPluginKey);

        listenSettingValue(SystemClearCacheButtonSetting.class, cacheService::clearCache);

        // init modules
        log.info("Initialize bundles");
        for (BundleContext bundleContext : applicationContext.getBeansOfType(BundleContext.class).values()) {
            bundleContext.init();
        }

        applicationContext.getBean(WorkspaceManager.class).postConstruct();
        applicationContext.getBean(WidgetManager.class).postConstruct();

        // trigger handlers when variables changed
        this.addEntityUpdateListener(WorkspaceVariableEntity.class, (source, oldSource) -> {
            if (oldSource == null || source.getValue() != oldSource.getValue()) {
                Scratch3ExtensionBlocks.sendWorkspaceValueChangeValue(this, source, source.getValue());
                broadcastLockManager.signalAll(source.getEntityID());
            }
        });
        this.addEntityUpdateListener(WorkspaceStandaloneVariableEntity.class, (source, oldSource) -> {
            if (oldSource == null || source.getValue() != oldSource.getValue()) {
                Scratch3ExtensionBlocks.sendWorkspaceValueChangeValue(this, source, source.getValue());
                broadcastLockManager.signalAll(source.getEntityID());
            }
        });
        this.addEntityUpdateListener(WorkspaceBooleanEntity.class, (source, oldSource) -> {
            if (oldSource == null || source.getValue() != oldSource.getValue()) {
                Scratch3ExtensionBlocks.sendWorkspaceBooleanValueChangeValue(this, source, source.getValue());
                broadcastLockManager.signalAll(source.getEntityID());
            }
        });

        notifications.add(new NotificationEntityJSON("app-status")
                .setName("App started")
                .setNotificationType(NotificationType.info));
    }

    private void updateDeviceFeatures() {
        if (EntityContext.isTestApplication() || EntityContext.isDockerEnvironment()) {
            disableFeature(DeviceFeature.HotSpot);
        }
    }

    @Override
    public <T extends BaseEntity> T getEntity(String entityID) {
        return getEntity(entityID, true);
    }

    @Override
    public <T extends BaseEntity> T getEntityOrDefault(String entityID, T defEntity) {
        T entity = getEntity(entityID, true);
        return entity == null ? defEntity : entity;
    }

    @Override
    public <T> T getEntity(String entityID, Class<T> clazz) {
        return entityManager.getEntity(entityID, clazz);
    }

    @Override
    public <T extends BaseEntity> T getEntity(String entityID, boolean useCache) {
        if (entityID == null) {
            throw new NotFoundException("Unable fetch entity for null id");
        }
        T baseEntity = useCache ? entityManager.getEntityWithFetchLazy(entityID) : entityManager.getEntityNoCache(entityID);

        if (baseEntity != null) {
            getRepository(baseEntity).ifPresent(abstractRepository -> abstractRepository.updateEntityAfterFetch(baseEntity));
        }
        return baseEntity;
    }

    @Override
    public Optional<AbstractRepository> getRepository(String entityID) {
        return entityID == null ? Optional.empty() : entityManager.getRepositoryByEntityID(entityID);
    }

    @Override
    public AbstractRepository getRepository(Class<? extends BaseEntity> entityClass) {
        return classFinder.getRepositoryByClass(entityClass);
    }

    @Override
    public <T extends BaseEntity> T getEntity(T entity) {
        return entityManager.getEntity(entity.getEntityID());
    }

    @Override
    public <T extends PureEntity> void saveDelayed(T entity) {
        PureRepository pureRepository = pureRepositories.get(entity.getClass().getSimpleName());
        cacheService.putToCache(pureRepository, entity);
    }

    @Override
    public <T extends BaseEntity> void saveDelayed(T entity) {
        AbstractRepository repositoryByClass = classFinder.getRepositoryByClass(entity.getClass());
        cacheService.putToCache(repositoryByClass, entity);
    }

    @Override
    public <T extends PureEntity> void save(T entity) {
        BaseCrudRepository pureRepository = (BaseCrudRepository) pureRepositories.get(entity.getClass().getSimpleName());
        pureRepository.save(entity);
    }

    @Override
    public <T extends BaseEntity> T save(T entity) {
        AbstractRepository foundRepo = classFinder.getRepositoryByClass(entity.getClass());
        final AbstractRepository repository = foundRepo == null && entity instanceof DeviceBaseEntity ? allDeviceRepository : foundRepo;
        T oldEntity = entity.getEntityID() == null ? null : getEntity(entity);

        T merge = transactionTemplate.execute(status -> {
            T saved = null;
            try {
                saved = (T) repository.save(entity);
                repository.updateEntityAfterFetch(saved);
            } finally {
                if (saved != null) {
                    for (BiConsumer consumer : this.entityUpdateListeners.getOrDefault(saved.getEntityID(), Collections.emptyList())) {
                        consumer.accept(saved, oldEntity);
                    }

                    for (BiConsumer consumer : this.entityClassUpdateListeners.getOrDefault(entity.getClass(), Collections.emptyList())) {
                        consumer.accept(saved, oldEntity);
                    }
                }
            }
            return saved;
        });

        if (StringUtils.isEmpty(entity.getEntityID())) {
            entity.setEntityID(merge.getEntityID());
            entity.setId(merge.getId());
        }

        // post save
        cacheService.entityUpdated(entity);
        entityManager.updateBGPProcesses(entity);

        return merge;
    }

    @Override
    public <T> T getSettingValue(Class<? extends BundleSettingPlugin<T>> settingPluginClazz) {
        BundleSettingPlugin<T> pluginFor = settingPluginsByPluginClass.get(settingPluginClazz);
        if (pluginFor.transientState()) {
            return pluginFor.parseValue(StringUtils.defaultIfEmpty(settingTransientState.get(pluginFor), pluginFor.getDefaultValue()));
        } else {
            SettingEntity settingEntity = getEntity(SettingRepository.getKey(pluginFor));
            return pluginFor.parseValue(StringUtils.defaultIfEmpty(settingEntity == null ? null : settingEntity.getValue(), pluginFor.getDefaultValue()));
        }
    }

    @Override
    public <T> void listenSettingValue(Class<? extends BundleSettingPlugin<T>> bundleSettingPluginClazz, Consumer<T> listener) {
        settingListeners.putIfAbsent(bundleSettingPluginClazz, new ArrayList<>());
        settingListeners.get(bundleSettingPluginClazz).add(listener);
    }

    @Override
    public <T> void setSettingValue(Class<? extends BundleSettingPlugin<T>> settingPluginClazz, T value) {
        BundleSettingPlugin pluginFor = settingPluginsByPluginClass.get(settingPluginClazz);
        setSettingValueSilence(settingPluginClazz, value);
        fireNotifySettingHandlers(settingPluginClazz, value, pluginFor);
    }

    @Override
    public <T> void setSettingValueRaw(Class<? extends BundleSettingPlugin<T>> settingPluginClazz, @NotNull String value) {
        BundleSettingPlugin pluginFor = settingPluginsByPluginClass.get(settingPluginClazz);
        setSettingValue(settingPluginClazz, (T) pluginFor.parseValue(value));
    }

    @Override
    public <T> void setSettingValueSilence(Class<? extends BundleSettingPlugin<T>> settingPluginClazz, @NotNull T value) {
        BundleSettingPlugin pluginFor = settingPluginsByPluginClass.get(settingPluginClazz);
        this.setSettingValueSilenceRaw(settingPluginClazz, pluginFor.writeValue(value));
    }

    @Override
    public <T> void setSettingValueSilenceRaw(Class<? extends BundleSettingPlugin<T>> settingPluginClazz, @NotNull String value) {
        BundleSettingPlugin pluginFor = settingPluginsByPluginClass.get(settingPluginClazz);
        log.debug("Update setting <{}> value <{}>", SettingRepository.getKey(pluginFor), value);

        if (pluginFor.transientState()) {
            settingTransientState.put(pluginFor, value);
        } else {
            SettingEntity settingEntity = getEntity(SettingRepository.getKey(pluginFor));
            settingEntity.setValue(value);
            save(settingEntity);
        }
    }

    @Override
    public void sendNotification(String destination, Object param) {
        messagingTemplate.convertAndSend(WebSocketConfig.DESTINATION_PREFIX + destination, param);
    }

    @Override
    public void showAlwaysOnViewNotification(NotificationEntityJSON notificationEntityJSON, int duration, String color) {
        AlwaysOnTopNotificationEntityJSONJSON alwaysOnTopNotificationEntityJSON = new AlwaysOnTopNotificationEntityJSONJSON(notificationEntityJSON);
        alwaysOnTopNotificationEntityJSON.setColor(color);
        alwaysOnTopNotificationEntityJSON.setDuration(duration);
        notifications.add(alwaysOnTopNotificationEntityJSON);
        sendNotification(alwaysOnTopNotificationEntityJSON);
    }

    @Override
    public void hideAlwaysOnViewNotification(NotificationEntityJSON notificationEntityJSON) {
        AlwaysOnTopNotificationEntityJSONJSON alwaysOnTopNotificationEntityJSON = (AlwaysOnTopNotificationEntityJSONJSON) notifications
                .stream().filter(n -> n.getEntityID().equals(notificationEntityJSON.getEntityID())).findAny().orElse(null);
        if (alwaysOnTopNotificationEntityJSON != null) {
            notifications.remove(alwaysOnTopNotificationEntityJSON);
            alwaysOnTopNotificationEntityJSON.setRemove(true);
            sendNotification(alwaysOnTopNotificationEntityJSON);
        }
    }

    @Override
    public BaseEntity<? extends BaseEntity> delete(BaseEntity baseEntity) {
        return entityManager.delete(baseEntity.getEntityID());
    }

    @Override
    public void sendInfoMessage(String message) {
        sendNotification(new NotificationEntityJSON("info-" + message.hashCode())
                .setName(message)
                .setNotificationType(NotificationType.info));
    }

    @Override
    public void sendErrorMessage(String message, Exception ex) {
        sendNotification(new NotificationEntityJSON("error-" + message.hashCode())
                .setName(message + ". Cause: " + TouchHomeUtils.getErrorMessage(ex))
                .setNotificationType(NotificationType.danger));
    }

    @Override
    public <T extends BaseEntity> List<T> findAll(Class<T> clazz) {
        AbstractRepository repository = getRepository(clazz);
        return entityManager.getEntityIDsByEntityClassFullName((Class<BaseEntity>) clazz).stream()
                .map(entityID -> {
                    T entity = entityManager.getEntityWithFetchLazy(entityID);
                    repository.updateEntityAfterFetch(entity);
                    return entity;
                }).collect(Collectors.toList());
    }

    @Override
    public BaseEntity<? extends BaseEntity> delete(String entityId) {
        return entityManager.delete(entityId);
    }

    @Override
    public AbstractRepository<? extends BaseEntity> getRepositoryByPrefix(String repositoryPrefix) {
        return repositoriesByPrefix.get(repositoryPrefix);
    }

    @Override
    public AbstractRepository<BaseEntity> getRepositoryByClass(String className) {
        Predicate<AbstractRepository> predicate = className.contains(".") ?
                repo -> repo.getEntityClass().getName().equals(className) :
                repo -> repo.getEntityClass().getSimpleName().equals(className);
        return repositories.values().stream().filter(predicate).findFirst().orElse(null);
    }

    @Override
    public <T extends BaseEntity> T getEntityByName(String name, Class<T> entityClass) {
        return classFinder.getRepositoryByClass(entityClass).getByName(name);
    }

    @Override
    public <T extends BaseEntity> void addEntityUpdateListener(String entityID, Consumer<T> listener) {
        this.addEntityUpdateListener(entityID, (t, t2) -> listener.accept((T) t));
    }

    @Override
    public <T extends BaseEntity> void addEntityUpdateListener(String entityID, BiConsumer<T, T> listener) {
        this.entityUpdateListeners.putIfAbsent(entityID, new ArrayList<>());
        this.entityUpdateListeners.get(entityID).add(listener);
    }

    @Override
    public <T extends BaseEntity> void removeEntityUpdateListener(String entityID, BiConsumer<T, T> listener) {
        this.entityUpdateListeners.putIfAbsent(entityID, new ArrayList<>());
        this.entityUpdateListeners.get(entityID).remove(listener);
    }

    @Override
    public void disableFeature(DeviceFeature deviceFeature) {
        deviceFeatures.put(deviceFeature, false);
    }

    @Override
    public Map<DeviceFeature, Boolean> getDeviceFeatures() {
        return deviceFeatures;
    }

    @Override
    public <T extends BaseEntity> void addEntityUpdateListener(Class<T> entityClass, Consumer<T> listener) {
        this.addEntityUpdateListener(entityClass, (t, t2) -> listener.accept(t));
    }

    @Override
    public <T extends BaseEntity> void addEntityUpdateListener(Class<T> entityClass, BiConsumer<T, T> listener) {
        this.entityClassUpdateListeners.putIfAbsent(entityClass, new ArrayList<>());
        this.entityClassUpdateListeners.get(entityClass).add(listener);
    }

    private void createUser() {
        UserEntity userEntity = getEntity(ADMIN_USER);
        if (userEntity == null) {
            save(new UserEntity().computeEntityID(() -> ADMIN_USER));
        }
    }

    private void createRaspberryDevice() {
        if (getEntity(DEFAULT_DEVICE_ENTITY_ID) == null) {
            save(new RaspberryDeviceEntity().computeEntityID(() -> DEFAULT_DEVICE_ENTITY_ID));
        }
    }

    private void createArduinoDevice() {
        if (EntityContext.isTestApplication() && getEntity("ad_TestArduinoDevice") == null) {
            save(new ArduinoDeviceEntity().computeEntityID(() -> "TestArduinoDevice").setPipe(111L));
        }
    }

    private <T> void fireNotifySettingHandlers(Class<? extends BundleSettingPlugin<T>> settingPluginClazz, T value, BundleSettingPlugin pluginFor) {
        if (settingListeners.containsKey(settingPluginClazz)) {
            for (Consumer consumer : settingListeners.get(settingPluginClazz)) {
                consumer.accept(value);
            }
        }
        NotificationEntityJSON notificationEntityJSON = pluginFor.buildHeaderNotificationEntity(value, this);
        if (notificationEntityJSON != null) {
            notifications.remove(notificationEntityJSON); // remove previous one
            notifications.add(notificationEntityJSON);
        }

        this.sendNotification(pluginFor.buildToastrNotificationEntity(value, this));
    }

    private void registerEntityListeners() {
        this.showEntityState = getSettingValue(SystemShowEntityStateSetting.class);
        this.listenSettingValue(SystemShowEntityStateSetting.class, value -> this.showEntityState = value);

        SessionFactoryImpl sessionFactory = entityManagerFactory.unwrap(SessionFactoryImpl.class);
        EventListenerRegistry registry = sessionFactory.getServiceRegistry().getService(EventListenerRegistry.class);
        registry.getEventListenerGroup(EventType.POST_INSERT).appendListener(new PostInsertEventListenerStandardImpl() {
            @Override
            public void onPostInsert(PostInsertEvent event) {
                super.onPostInsert(event);
                updateCacheEntity(event.getEntity(), "created");
            }
        });
        registry.getEventListenerGroup(EventType.POST_UPDATE).appendListener(new PostUpdateEventListenerStandardImpl() {
            @Override
            public void onPostUpdate(PostUpdateEvent event) {
                super.onPostUpdate(event);
                updateCacheEntity(event.getEntity(), "changed");
            }
        });

        registry.getEventListenerGroup(EventType.POST_DELETE).appendListener(new PostDeleteEventListenerStandardImpl() {
            @Override
            public void onPostDelete(PostDeleteEvent event) {
                super.onPostDelete(event);
                updateCacheEntity(event.getEntity(), "removed");
            }
        });
    }

    private void updateCacheEntity(Object entity, String type) {
        try {
            if (entity instanceof BaseEntity) {
                this.cacheService.entityUpdated((BaseEntity) entity);
                // send info if item changed and it could be shown on page
                if (entity.getClass().isAnnotationPresent(UISidebarMenu.class)) {
                    this.sendNotification("-listen-items", new JSONObject().put("type", type).put("value", entity));
                }
                if (showEntityState) {
                    this.sendNotification("-toastr", new JSONObject().put("type", type).put("value", entity));
                }
            }
        } catch (Exception ex) {
            log.error("Unable to update cache entity <{}> for entity: <{}>", type, entity);
        }
    }
}
