package org.touchhome.app.manager.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
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
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.touchhome.app.LogService;
import org.touchhome.app.config.TouchHomeProperties;
import org.touchhome.app.config.WebSocketConfig;
import org.touchhome.app.extloader.BundleContext;
import org.touchhome.app.extloader.BundleService;
import org.touchhome.app.json.AlwaysOnTopNotificationEntityJSONJSON;
import org.touchhome.app.manager.BundleManager;
import org.touchhome.app.manager.CacheService;
import org.touchhome.app.manager.ScriptManager;
import org.touchhome.app.manager.WidgetManager;
import org.touchhome.app.model.entity.SettingEntity;
import org.touchhome.app.repository.SettingRepository;
import org.touchhome.app.repository.crud.base.BaseCrudRepository;
import org.touchhome.app.repository.device.AllDeviceRepository;
import org.touchhome.app.rest.ConsoleController;
import org.touchhome.app.rest.ItemController;
import org.touchhome.app.rest.SettingController;
import org.touchhome.app.setting.system.SystemClearCacheButtonSetting;
import org.touchhome.app.setting.system.SystemShowEntityStateSetting;
import org.touchhome.app.utils.CollectionUtils;
import org.touchhome.app.utils.Curl;
import org.touchhome.app.utils.HardwareUtils;
import org.touchhome.app.workspace.WorkspaceController;
import org.touchhome.app.workspace.WorkspaceManager;
import org.touchhome.bundle.api.BundleEntrypoint;
import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.condition.ExecuteOnce;
import org.touchhome.bundle.api.exception.NotFoundException;
import org.touchhome.bundle.api.hardware.other.LinuxHardwareRepository;
import org.touchhome.bundle.api.json.NotificationEntityJSON;
import org.touchhome.bundle.api.manager.En;
import org.touchhome.bundle.api.manager.LoggerManager;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.model.DeviceBaseEntity;
import org.touchhome.bundle.api.model.HasIdIdentifier;
import org.touchhome.bundle.api.model.UserEntity;
import org.touchhome.bundle.api.model.workspace.WorkspaceStandaloneVariableEntity;
import org.touchhome.bundle.api.model.workspace.bool.WorkspaceBooleanEntity;
import org.touchhome.bundle.api.model.workspace.var.WorkspaceVariableEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;
import org.touchhome.bundle.api.repository.PureRepository;
import org.touchhome.bundle.api.repository.impl.UserRepository;
import org.touchhome.bundle.api.scratch.Scratch3ExtensionBlocks;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.bundle.api.widget.WidgetBaseTemplate;
import org.touchhome.bundle.api.workspace.BroadcastLockManager;
import org.touchhome.bundle.arduino.model.ArduinoDeviceEntity;
import org.touchhome.bundle.raspberry.model.RaspberryDeviceEntity;

import javax.persistence.EntityManagerFactory;
import javax.validation.constraints.NotNull;
import java.lang.annotation.Annotation;
import java.text.DateFormat;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.touchhome.bundle.api.model.UserEntity.ADMIN_USER;
import static org.touchhome.bundle.api.util.TouchHomeUtils.*;
import static org.touchhome.bundle.raspberry.model.RaspberryDeviceEntity.DEFAULT_DEVICE_ENTITY_ID;

@Log4j2
@Component
@RequiredArgsConstructor
public class InternalManager implements EntityContext {

    private static final Map<BundleSettingPlugin, String> settingTransientState = new HashMap<>();
    public static Map<String, BundleSettingPlugin> settingPluginsByPluginKey = new HashMap<>();
    public static Map<String, AbstractRepository> repositories = new HashMap<>();
    private static Map<String, BundleSettingPlugin> settingPluginsByPluginClass = new HashMap<>();
    private static EntityManager entityManager;
    private static Map<String, AbstractRepository> repositoriesByPrefix;
    private static Map<String, PureRepository> pureRepositories = new HashMap<>();
    private final String GIT_HUB_URL = "https://api.github.com/repos/touchhome/touchhome-core";

    private final Map<String, List<BiConsumer>> entityUpdateListeners = new HashMap<>();
    private final Map<String, List<BiConsumer>> entityClassUpdateListeners = new HashMap<>();
    private final Map<String, List<Consumer>> entityClassRemoveListeners = new HashMap<>();

    private final Map<String, Boolean> deviceFeatures = new HashMap<>();
    private final Set<NotificationEntityJSON> notifications = CollectionUtils.extendedSet();

    private final ClassFinder classFinder;
    private final CacheService cacheService;
    private final AllDeviceRepository allDeviceRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final EntityManagerFactory entityManagerFactory;
    private final PlatformTransactionManager transactionManager;
    private final BroadcastLockManager broadcastLockManager;
    private final TouchHomeProperties touchHomeProperties;

    private Map<String, List<Consumer<?>>> settingListeners = new HashMap<>();
    private TransactionTemplate transactionTemplate;
    private Boolean showEntityState;
    private ApplicationContext applicationContext;
    @Getter
    private Map<String, InternalBundleContext> bundles = new LinkedHashMap<>();
    private String latestVersion;

    private Set<ApplicationContext> allApplicationContexts = new HashSet<>();

    public Set<NotificationEntityJSON> getNotifications() {
        long time = System.currentTimeMillis();
        notifications.removeIf(entity -> entity instanceof AlwaysOnTopNotificationEntityJSONJSON
                && time - entity.getCreationTime().getTime() > ((AlwaysOnTopNotificationEntityJSONJSON) entity).getDuration() * 1000);

        Set<NotificationEntityJSON> set = new TreeSet<>(notifications);
        for (InternalBundleContext bundleContext : this.bundles.values()) {
            Set<NotificationEntityJSON> notifications = bundleContext.bundleEntrypoint.getNotifications();
            if (notifications != null) {
                set.addAll(notifications);
            }
        }

        return set;
    }

    @SneakyThrows
    public void afterContextStart(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.allApplicationContexts.add(applicationContext);
        this.updateDeviceFeatures();
        this.transactionTemplate = new TransactionTemplate(transactionManager);

        entityManager = applicationContext.getBean(EntityManager.class);
        updateBeans(null, applicationContext, true);

        applicationContext.getBean(LoggerManager.class).postConstruct();
        applicationContext.getBean(LogService.class).setEntityContext(this);

        registerEntityListeners();

        createUser();
        createRaspberryDevice();
        createArduinoDevice();

        applicationContext.getBean(UserRepository.class).postConstruct(this);
        applicationContext.getBean(ScriptManager.class).postConstruct();

        listenSettingValue(SystemClearCacheButtonSetting.class, cacheService::clearCache);

        // loadWorkspace modules
        log.info("Initialize bundles");
        ArrayList<BundleEntrypoint> bundleEntrypoints = new ArrayList<>(applicationContext.getBeansOfType(BundleEntrypoint.class).values());
        Collections.sort(bundleEntrypoints);
        for (BundleEntrypoint bundleEntrypoint : bundleEntrypoints) {
            this.bundles.put(bundleEntrypoint.getBundleId(), new InternalBundleContext(bundleEntrypoint, null));
            bundleEntrypoint.init();
        }

        applicationContext.getBean(BundleService.class).loadBundlesFromPath();
        // remove unused settings only after full load all external bundles
        applicationContext.getBean(SettingRepository.class).deleteRemovedSettings();

        applicationContext.getBean(WorkspaceManager.class).postConstruct(this);
        applicationContext.getBean(WorkspaceManager.class).loadWorkspace();
        applicationContext.getBean(WorkspaceController.class).postConstruct(this);
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
        applicationContext.getBean(SettingRepository.class).deleteRemovedSettings();

        notifications.add(NotificationEntityJSON.info("app-status")
                .setName("App started at " + DateFormat.getDateTimeInstance().format(new Date())));

        this.fetchReleaseVersion();
    }

    @Scheduled(fixedDelay = 360000)
    private void fetchReleaseVersion() {
        try {
            log.info("Try fetch latest version from server");
            notifications.add(NotificationEntityJSON.info("version").setName("App version: " + touchHomeProperties.getVersion()));
            this.latestVersion = Curl.get(GIT_HUB_URL + "/releases/latest", Map.class).get("tag_name").toString();

            if (!touchHomeProperties.getVersion().equals(this.latestVersion)) {
                log.info("Found newest version <{}>. Current version: <{}>", this.latestVersion, touchHomeProperties.getVersion());
                notifications.add(NotificationEntityJSON.danger("version")
                        .setName("Require update app version from " + touchHomeProperties.getVersion() + " to " + this.latestVersion));
            }
        } catch (Exception ex) {
            log.warn("Unable to fetch latest version");
        }
    }

    private void updateDeviceFeatures() {
        for (String feature : new String[]{"HotSpot", "SSH"}) {
            deviceFeatures.put(feature, true);
        }
        if (EntityContext.isDevEnvironment() || EntityContext.isDockerEnvironment()) {
            setFeatureState("HotSpot", false);
        }
        if (!EntityContext.isLinuxOrDockerEnvironment()) {
            setFeatureState("SSH", false);
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
    public <T extends HasIdIdentifier> void saveDelayed(T entity) {
        PureRepository pureRepository = pureRepositories.get(entity.getClass().getSimpleName());
        cacheService.putToCache(pureRepository, entity);
    }

    @Override
    public <T extends BaseEntity> void saveDelayed(T entity) {
        AbstractRepository repositoryByClass = classFinder.getRepositoryByClass(entity.getClass());
        cacheService.putToCache(repositoryByClass, entity);
    }

    @Override
    public <T extends HasIdIdentifier> void save(T entity) {
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

                    for (BiConsumer consumer : this.entityClassUpdateListeners.getOrDefault(entity.getClass().getName(), Collections.emptyList())) {
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
        BundleSettingPlugin<T> pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        if (pluginFor.transientState()) {
            return pluginFor.parseValue(this, StringUtils.defaultIfEmpty(settingTransientState.get(pluginFor), pluginFor.getDefaultValue()));
        } else {
            SettingEntity settingEntity = getEntity(SettingRepository.getKey(pluginFor));
            return pluginFor.parseValue(this, StringUtils.defaultIfEmpty(settingEntity == null ? null : settingEntity.getValue(), pluginFor.getDefaultValue()));
        }
    }

    @Override
    public <T> void listenSettingValue(Class<? extends BundleSettingPlugin<T>> bundleSettingPluginClazz, Consumer<T> listener) {
        settingListeners.putIfAbsent(bundleSettingPluginClazz.getName(), new ArrayList<>());
        settingListeners.get(bundleSettingPluginClazz.getName()).add(listener);
    }

    @Override
    public <T> void setSettingValue(Class<? extends BundleSettingPlugin<T>> settingPluginClazz, T value) {
        BundleSettingPlugin pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        setSettingValueSilence(settingPluginClazz, value);
        fireNotifySettingHandlers(settingPluginClazz, value, pluginFor);
    }

    @Override
    public <T> void setSettingValueRaw(Class<? extends BundleSettingPlugin<T>> settingPluginClazz, @NotNull String value) {
        BundleSettingPlugin pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        setSettingValue(settingPluginClazz, (T) pluginFor.parseValue(this, value));
    }

    @Override
    public <T> void setSettingValueSilence(Class<? extends BundleSettingPlugin<T>> settingPluginClazz, @NotNull T value) {
        BundleSettingPlugin pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        this.setSettingValueSilenceRaw(settingPluginClazz, pluginFor.writeValue(value));
    }

    @Override
    public <T> void setSettingValueSilenceRaw(Class<? extends BundleSettingPlugin<T>> settingPluginClazz, @NotNull String value) {
        BundleSettingPlugin pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        log.debug("Update setting <{}> value <{}>", SettingRepository.getKey(pluginFor), value);

        if (pluginFor.transientState()) {
            settingTransientState.put(pluginFor, value);
        } else {
            SettingEntity settingEntity = getEntity(SettingRepository.getKey(pluginFor));
            if (!Objects.equals(value, settingEntity.getValue())) {
                if (settingEntity.getDefaultValue().equals(value)) {
                    value = "";
                }
                save(settingEntity.setValue(value));
            }
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
    public void sendInfoMessage(String message) {
        sendNotification(NotificationEntityJSON.info("info-" + message.hashCode())
                .setName(message));
    }

    @Override
    public void sendErrorMessage(String message, Exception ex) {
        sendNotification(NotificationEntityJSON.danger("error-" + message.hashCode())
                .setName(message + ". Cause: " + TouchHomeUtils.getErrorMessage(ex)));
    }

    @Override
    public <T extends BaseEntity> List<T> findAll(Class<T> clazz) {
        return findAllByRepository((Class<BaseEntity>) clazz, getRepository(clazz));
    }

    @Override
    public <T extends BaseEntity> List<T> findAllByPrefix(String prefix) {
        AbstractRepository<? extends BaseEntity> repository = getRepositoryByPrefix(prefix);
        return findAllByRepository((Class<BaseEntity>) repository.getEntityClass(), repository);
    }

    @Override
    public BaseEntity<? extends BaseEntity> delete(BaseEntity baseEntity) {
        return delete(baseEntity.getEntityID());
    }

    @Override
    public BaseEntity<? extends BaseEntity> delete(String entityId) {
        BaseEntity<? extends BaseEntity> deletedEntity = entityManager.delete(entityId);
        if (deletedEntity != null) {
            for (Consumer consumer : this.entityClassRemoveListeners.getOrDefault(deletedEntity.getClass().getName(), Collections.emptyList())) {
                consumer.accept(deletedEntity);
            }
        }
        return deletedEntity;
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
    public <T extends BaseEntity> void removeEntityUpdateListener(String entityID, BiConsumer<T, T> listener) {
        this.entityUpdateListeners.putIfAbsent(entityID, new ArrayList<>());
        this.entityUpdateListeners.get(entityID).remove(listener);
    }

    @Override
    public void setFeatureState(String feature, boolean state) {
        deviceFeatures.put(feature, state);
    }

    @Override
    public boolean isFeatureEnabled(String deviceFeature) {
        return Boolean.TRUE.equals(deviceFeatures.get(deviceFeature));
    }

    @Override
    public <T> T getBean(String beanName, Class<T> clazz) {
        return this.allApplicationContexts.stream().filter(c -> c.containsBean(beanName)).map(c -> c.getBean(beanName, clazz))
                .findAny().orElseThrow(() -> new NoSuchBeanDefinitionException(beanName));
    }

    @Override
    public <T> T getBean(Class<T> clazz) {
        for (ApplicationContext context : allApplicationContexts) {
            try {
                return context.getBean(clazz);
            } catch (Exception ignore) {
            }
        }
        throw new NoSuchBeanDefinitionException(clazz);
    }

    @Override
    public <T> Collection<T> getBeansOfType(Class<T> clazz) {
        List<T> values = new ArrayList<>();
        for (ApplicationContext context : allApplicationContexts) {
            values.addAll(context.getBeansOfType(clazz).values());
        }
        return values;
    }

    @Override
    public <T> Map<String, Collection<T>> getBeansOfTypeByBundles(Class<T> clazz) {
        List<T> values = new ArrayList<>();
        Map<String, Collection<T>> res = new HashMap<>();
        for (ApplicationContext context : allApplicationContexts) {
            Collection<T> beans = context.getBeansOfType(clazz).values();
            if (!beans.isEmpty()) {
                res.put(context.getId(), beans);
            }
        }
        return res;
    }

    @Override
    public UserEntity getUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            return getEntity(UserEntity.PREFIX + authentication.getCredentials());
        }
        return null;
    }

    @Override
    public Collection<AbstractRepository> getRepositories() {
        return repositories.values();
    }

    @Override
    public <T> List<Class<? extends T>> getClassesWithAnnotation(Class<? extends Annotation> annotation) {
        return classFinder.getClassesWithAnnotation(annotation);
    }

    @Override
    public Map<String, Boolean> getDeviceFeatures() {
        return deviceFeatures;
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
    public <T extends BaseEntity> void addEntityUpdateListener(Class<T> entityClass, Consumer<T> listener) {
        this.addEntityUpdateListener(entityClass, (t, t2) -> listener.accept(t));
    }

    @Override
    public <T extends BaseEntity> void addEntityUpdateListener(Class<T> entityClass, BiConsumer<T, T> listener) {
        this.entityClassUpdateListeners.putIfAbsent(entityClass.getName(), new ArrayList<>());
        this.entityClassUpdateListeners.get(entityClass.getName()).add(listener);
    }

    @Override
    public <T extends BaseEntity> void addEntityRemovedListener(Class<T> entityClass, Consumer<T> listener) {
        this.entityClassRemoveListeners.putIfAbsent(entityClass.getName(), new ArrayList<>());
        this.entityClassRemoveListeners.get(entityClass.getName()).add(listener);
    }

    @ExecuteOnce(skipIfInstalled = "autossh", requireInternet = true)
    public void installAutossh() {
        getBean(LinuxHardwareRepository.class).installSoftware("autossh");
    }

    private <T extends BaseEntity> List<T> findAllByRepository(Class<BaseEntity> clazz, AbstractRepository repository) {
        return entityManager.getEntityIDsByEntityClassFullName(clazz).stream()
                .map(entityID -> {
                    T entity = entityManager.getEntityWithFetchLazy(entityID);
                    repository.updateEntityAfterFetch(entity);
                    return entity;
                }).collect(Collectors.toList());
    }

    private void createUser() {
        UserEntity userEntity = getEntity(ADMIN_USER);
        if (userEntity == null) {
            save(new UserEntity().computeEntityID(() -> ADMIN_USER)
                    .setRoles(new HashSet<>(Arrays.asList(ADMIN_ROLE, PRIVILEGED_USER_ROLE, GUEST_ROLE))));
        }
    }

    private void createRaspberryDevice() {
        if (getEntity(DEFAULT_DEVICE_ENTITY_ID) == null) {
            save(new RaspberryDeviceEntity().computeEntityID(() -> DEFAULT_DEVICE_ENTITY_ID));
        }
    }

    private void createArduinoDevice() {
        if (EntityContext.isDevEnvironment() && getEntity("ad_TestArduinoDevice") == null) {
            save(new ArduinoDeviceEntity().computeEntityID(() -> "TestArduinoDevice").setPipe(111L));
        }
    }

    private <T> void fireNotifySettingHandlers(Class<? extends BundleSettingPlugin<T>> settingPluginClazz, T value, BundleSettingPlugin pluginFor) {
        if (settingListeners.containsKey(settingPluginClazz.getName())) {
            for (Consumer consumer : settingListeners.get(settingPluginClazz.getName())) {
                consumer.accept(value);
            }
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

    public void addBundle(Map<String, BundleContext> batchContexts) {
        for (String bundleName : batchContexts.keySet()) {
            this.addBundle(batchContexts.get(bundleName), batchContexts);
        }
    }

    public void removeBundle(String bundleName) {
        InternalBundleContext internalBundleContext = bundles.remove(bundleName);
        if (internalBundleContext != null) {
            this.removeBundle(internalBundleContext.bundleContext);
            applicationContext.getBean(SettingRepository.class).deleteRemovedSettings();
        }
    }

    private void removeBundle(BundleContext bundleContext) {
        if (!bundleContext.isInternal() && bundleContext.isInstalled()) {
            ApplicationContext context = bundleContext.getApplicationContext();
            context.getBean(BundleEntrypoint.class).destroy();
            this.allApplicationContexts.remove(context);

            this.cacheService.clearCache();

            for (PureRepository repository : context.getBeansOfType(PureRepository.class).values()) {
                pureRepositories.remove(repository.getEntityClass().getSimpleName());
            }
            context.getBeansOfType(AbstractRepository.class).keySet().forEach(ar -> repositories.remove(ar));
            repositoriesByPrefix = repositories.values().stream().collect(Collectors.toMap(AbstractRepository::getPrefix, r -> r));
            updateBeans(bundleContext, bundleContext.getApplicationContext(), false);
        }
    }

    private void addBundle(BundleContext bundleContext, Map<String, BundleContext> bundleContextMap) {
        if (!bundleContext.isInternal() && !bundleContext.isInstalled()) {
            if (!bundleContext.isLoaded()) {
                notifications.add(NotificationEntityJSON.danger("fail-bundle-" + bundleContext.getBundleName())
                        .setName("Unable to load bundle <" + bundleContext.getBundleFriendlyName() + ">"));
                return;
            }
            allApplicationContexts.add(bundleContext.getApplicationContext());
            bundleContext.setInstalled(true);
            for (String bundleDependency : bundleContext.getDependencies()) {
                addBundle(bundleContextMap.get(bundleDependency), bundleContextMap);
            }
            ApplicationContext context = bundleContext.getApplicationContext();

            this.cacheService.clearCache();

            HardwareUtils.copyResources(bundleContext.getBundleClassLoader().getResource("files"), "/files");
            updateBeans(bundleContext, context, true);

            for (BundleEntrypoint bundleEntrypoint : context.getBeansOfType(BundleEntrypoint.class).values()) {
                bundleEntrypoint.init();
                this.bundles.put(bundleEntrypoint.getBundleId(), new InternalBundleContext(bundleEntrypoint, bundleContext));
            }
        }
    }

    private void updateBeans(BundleContext bundleContext, ApplicationContext context, boolean addBundle) {
        log.info("Starting update all app bundles");
        En.get().clear();
        fetchBundleSettingPlugins(bundleContext, addBundle);

        Map<String, PureRepository> pureRepositoryMap = context.getBeansOfType(PureRepository.class).values()
                .stream().collect(Collectors.toMap(r -> r.getEntityClass().getSimpleName(), r -> r));

        if (addBundle) {
            pureRepositories.putAll(pureRepositoryMap);
            repositories.putAll(context.getBeansOfType(AbstractRepository.class));
        } else {
            pureRepositories.keySet().removeAll(pureRepositoryMap.keySet());
            repositories.keySet().removeAll(context.getBeansOfType(AbstractRepository.class).keySet());
        }
        repositoriesByPrefix = repositories.values().stream().collect(Collectors.toMap(AbstractRepository::getPrefix, r -> r));

        applicationContext.getBean(ConsoleController.class).postConstruct();
        applicationContext.getBean(SettingController.class).postConstruct(this);
        if (!addBundle) {
            applicationContext.getBean(SettingRepository.class).deleteRemovedSettings();
        }
        applicationContext.getBean(WorkspaceManager.class).postConstruct(this);
        applicationContext.getBean(BundleManager.class).postConstruct(this);
        applicationContext.getBean(WorkspaceController.class).postConstruct(this);
        applicationContext.getBean(EntityManager.class).postConstruct();
        applicationContext.getBean(ItemController.class).postConstruct();
        log.info("Finish update all app bundles");
    }

    @SneakyThrows
    private void fetchBundleSettingPlugins(BundleContext bundleContext, boolean addBundle) {
        String basePackage = bundleContext == null ? null : bundleContext.getBasePackage();
        for (Class<? extends BundleSettingPlugin> settingPlugin : classFinder.getClassesWithParent(BundleSettingPlugin.class, null, basePackage)) {
            BundleSettingPlugin bundleSettingPlugin = settingPlugin.newInstance();
            String key = SettingRepository.getKey(bundleSettingPlugin);
            if (addBundle) {
                settingPluginsByPluginKey.put(key, bundleSettingPlugin);
                settingPluginsByPluginClass.put(settingPlugin.getName(), bundleSettingPlugin);
            } else {
                settingPluginsByPluginKey.remove(key);
                settingPluginsByPluginClass.remove(settingPlugin.getName());
                settingListeners.remove(settingPlugin.getName());
            }
        }
    }

    public Object getBeanOfBundleBySimpleName(String bundle, String className) {
        InternalBundleContext internalBundleContext = this.bundles.get(bundle);
        if (internalBundleContext == null) {
            throw new NotFoundException("Unable to find bundle <" + bundle + ">");
        }
        Object o = internalBundleContext.fieldTypes.get(className);
        if (o == null) {
            throw new NotFoundException("Unable to find class <" + className + "> in bundle <" + bundle + ">");
        }
        return o;
    }

    public static class InternalBundleContext {
        private final BundleEntrypoint bundleEntrypoint;
        @Getter
        private final BundleContext bundleContext;
        private final Map<String, Object> fieldTypes = new HashMap<>();

        public InternalBundleContext(BundleEntrypoint bundleEntrypoint, BundleContext bundleContext) {
            this.bundleEntrypoint = bundleEntrypoint;
            this.bundleContext = bundleContext;
            if (bundleContext != null) {
                for (WidgetBaseTemplate widgetBaseTemplate : bundleContext.getApplicationContext().getBeansOfType(WidgetBaseTemplate.class).values()) {
                    fieldTypes.put(widgetBaseTemplate.getClass().getSimpleName(), widgetBaseTemplate);
                }
            }
        }
    }
}
