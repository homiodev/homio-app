package org.touchhome.app.manager.common;

import com.pivovarit.function.ThrowingRunnable;
import com.pivovarit.function.ThrowingSupplier;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.hibernate.event.internal.PostDeleteEventListenerStandardImpl;
import org.hibernate.event.internal.PostInsertEventListenerStandardImpl;
import org.hibernate.event.internal.PostUpdateEventListenerStandardImpl;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.metamodel.spi.MetamodelImplementor;
import org.hibernate.persister.entity.Joinable;
import org.json.JSONObject;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.touchhome.app.LogService;
import org.touchhome.app.auth.JwtTokenProvider;
import org.touchhome.app.config.ExtRequestMappingHandlerMapping;
import org.touchhome.app.config.TouchHomeProperties;
import org.touchhome.app.config.WebSocketConfig;
import org.touchhome.app.extloader.BundleContext;
import org.touchhome.app.extloader.BundleContextService;
import org.touchhome.app.hardware.HardwareEventsImpl;
import org.touchhome.app.json.AlwaysOnTopNotificationEntityJSON;
import org.touchhome.app.manager.*;
import org.touchhome.app.model.entity.SettingEntity;
import org.touchhome.app.model.entity.widget.impl.WidgetBaseEntity;
import org.touchhome.app.repository.SettingRepository;
import org.touchhome.app.repository.crud.base.BaseCrudRepository;
import org.touchhome.app.repository.device.AllDeviceRepository;
import org.touchhome.app.rest.ConsoleController;
import org.touchhome.app.rest.ItemController;
import org.touchhome.app.rest.SettingController;
import org.touchhome.app.setting.system.SystemClearCacheButtonSetting;
import org.touchhome.app.setting.system.SystemLanguageSetting;
import org.touchhome.app.setting.system.SystemShowEntityStateSetting;
import org.touchhome.app.utils.CollectionUtils;
import org.touchhome.app.utils.Curl;
import org.touchhome.app.utils.HardwareUtils;
import org.touchhome.app.workspace.WorkspaceController;
import org.touchhome.app.workspace.WorkspaceManager;
import org.touchhome.app.workspace.block.core.Scratch3OtherBlocks;
import org.touchhome.bundle.api.BundleEntrypoint;
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
import org.touchhome.bundle.api.scratch.Scratch3ExtensionBlocks;
import org.touchhome.bundle.api.setting.BundleSettingPlugin;
import org.touchhome.bundle.api.setting.BundleSettingPluginButton;
import org.touchhome.bundle.api.setting.BundleSettingPluginStatus;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.widget.WidgetBaseTemplate;
import org.touchhome.bundle.api.workspace.BroadcastLockManager;

import javax.persistence.EntityManagerFactory;
import javax.validation.constraints.NotNull;
import java.lang.annotation.Annotation;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.touchhome.bundle.api.model.UserEntity.ADMIN_USER;
import static org.touchhome.bundle.api.util.TouchHomeUtils.*;

@Log4j2
@Component
@RequiredArgsConstructor
public class EntityContextImpl implements EntityContext {

    public static final String CREATE_TABLE_INDEX = "CREATE UNIQUE INDEX IF NOT EXISTS %s_entity_id ON %s (entityid)";
    private final ScheduledExecutorService scheduleService = Executors.newScheduledThreadPool(1000, r -> new Thread(r, "entity-manager"));

    @Getter
    private final Map<String, ThreadContextImpl> schedulers = new HashMap<>();

    private static final Map<BundleSettingPlugin, String> settingTransientState = new HashMap<>();
    public static Map<String, BundleSettingPlugin> settingPluginsByPluginKey = new HashMap<>();
    private static Map<String, BundleSettingPlugin> settingPluginsByPluginClass = new HashMap<>();

    public static Map<String, AbstractRepository> repositories = new HashMap<>();
    private static EntityManager entityManager;
    private static Map<String, AbstractRepository> repositoriesByPrefix;
    public static Map<String, Class<? extends BaseEntity>> baseEntityNameToClass;
    private static Map<String, PureRepository> pureRepositories = new HashMap<>();
    private final String GIT_HUB_URL = "https://api.github.com/repos/touchhome/touchhome-core";

    private final Map<String, List<BiConsumer>> entityUpdateListeners = new HashMap<>();
    private final Map<String, List<BiConsumer>> entityClassUpdateListeners = new HashMap<>();
    private final Map<String, List<Consumer>> entityClassRemoveListeners = new HashMap<>();
    private final Map<String, List<Consumer>> entityIDRemoveListeners = new HashMap<>();
    private final Map<String, List<BiConsumer<DatagramPacket, String>>> listenUdpMap = new HashMap<>();

    private final Map<String, Boolean> deviceFeatures = new HashMap<>();
    private final Set<NotificationEntityJSON> notifications = CollectionUtils.extendedSet();

    private final ClassFinder classFinder;
    private final CacheService cacheService;
    private final SimpMessagingTemplate messagingTemplate;
    private final BroadcastLockManager broadcastLockManager;
    private final TouchHomeProperties touchHomeProperties;

    private Map<String, Map<String, Consumer<?>>> settingListeners = new HashMap<>();
    private TransactionTemplate transactionTemplate;
    private Boolean showEntityState;
    private ApplicationContext applicationContext;
    private AllDeviceRepository allDeviceRepository;
    private EntityManagerFactory entityManagerFactory;
    private PlatformTransactionManager transactionManager;

    @Getter
    private Map<String, InternalBundleContext> bundles = new LinkedHashMap<>();
    private String latestVersion;

    private Set<ApplicationContext> allApplicationContexts = new HashSet<>();
    private HardwareEventsImpl hardwareEvents;

    public Set<NotificationEntityJSON> getNotifications() {
        long time = System.currentTimeMillis();
        notifications.removeIf(entity -> {
            if (entity instanceof AlwaysOnTopNotificationEntityJSON) {
                AlwaysOnTopNotificationEntityJSON json = (AlwaysOnTopNotificationEntityJSON) entity;
                return json.getDuration() != null && time - entity.getCreationTime().getTime() > json.getDuration() * 1000;
            }
            return false;
        });

        Set<NotificationEntityJSON> set = new TreeSet<>(notifications);
        for (InternalBundleContext bundleContext : this.bundles.values()) {
            Set<NotificationEntityJSON> notifications = bundleContext.bundleEntrypoint.getNotifications();
            if (notifications != null) {
                set.addAll(notifications);
            }
            Class<? extends BundleSettingPluginStatus> statusSettingClass = bundleContext.bundleEntrypoint.getBundleStatusSetting();
            if (statusSettingClass != null) {
                set.add(getSettingValue(statusSettingClass).toNotification(SettingRepository.getSettingBundleName(this, statusSettingClass)));
            }
        }

        return set;
    }

    @SneakyThrows
    public void afterContextStart(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        ((ScheduledThreadPoolExecutor) this.scheduleService).setRemoveOnCancelPolicy(true);

        this.transactionManager = this.applicationContext.getBean(PlatformTransactionManager.class);
        this.hardwareEvents = this.applicationContext.getBean(HardwareEventsImpl.class);
        this.entityManagerFactory = this.applicationContext.getBean(EntityManagerFactory.class);
        this.allDeviceRepository = this.applicationContext.getBean(AllDeviceRepository.class);
        this.allApplicationContexts.add(applicationContext);
        this.updateDeviceFeatures();
        this.transactionTemplate = new TransactionTemplate(transactionManager);

        entityManager = applicationContext.getBean(EntityManager.class);
        updateBeans(null, applicationContext, true);

        applicationContext.getBean(JwtTokenProvider.class).postConstruct(this);
        applicationContext.getBean(LoggerManager.class).postConstruct();
        applicationContext.getBean(LogService.class).setEntityContext(this);

        registerEntityListeners();

        createUser();

        applicationContext.getBean(ScriptManager.class).postConstruct();

        listenSettingValue(SystemClearCacheButtonSetting.class, "im-clear-cache", cacheService::clearCache);

        // loadWorkspace modules
        log.info("Initialize bundles");
        ArrayList<BundleEntrypoint> bundleEntrypoints = new ArrayList<>(applicationContext.getBeansOfType(BundleEntrypoint.class).values());
        Collections.sort(bundleEntrypoints);
        for (BundleEntrypoint bundleEntrypoint : bundleEntrypoints) {
            this.bundles.put(bundleEntrypoint.getBundleId(), new InternalBundleContext(bundleEntrypoint, null));
            bundleEntrypoint.init();
        }

        applicationContext.getBean(BundleContextService.class).loadBundlesFromPath();

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
        this.allDeviceRepository.resetDeviceStatuses();

        // applicationContext.getBean(SettingRepository.class).deleteRemovedSettings();

        notifications.add(NotificationEntityJSON.info("app-status")
                .setName("app").setDescription("Started at " + DateFormat.getDateTimeInstance().format(new Date())));

        // create indexes on tables
        this.createTableIndexes();
        this.schedule("check-app-version", 1, TimeUnit.DAYS, this::fetchReleaseVersion, true);
        this.hardwareEvents.addEvent("app-release", "Found new app release");
        this.hardwareEvents.addEventAndFire("app-started", "App started");
    }

    @Override
    public <T extends BaseEntity> T getEntity(String entityID, boolean useCache) {
        if (entityID == null) {
            throw new NotFoundException("Unable fetch entity for null id");
        }
        T baseEntity = useCache ? entityManager.getEntityWithFetchLazy(entityID) : entityManager.getEntityNoCache(entityID);
        if (baseEntity == null) {
            baseEntity = entityManager.getEntityNoCache(entityID);
            if (baseEntity != null) {
                cacheService.clearCache();
            }
        }

        if (baseEntity != null) {
            cacheService.merge(baseEntity);
            T finalBaseEntity = baseEntity;
            getRepository(baseEntity).ifPresent(abstractRepository -> abstractRepository.updateEntityAfterFetch(finalBaseEntity));
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
    public <T extends HasIdIdentifier> void createDelayed(T entity) {
        putToCache(entity, null);
    }

    @Override
    public <T extends HasIdIdentifier> void updateDelayed(T entity, Consumer<T> consumer) {
        Map<String, Object> changeFields = new HashMap<>();
        MethodInterceptor handler = (obj, method, args, proxy) -> {
            String setName = method.getName();
            if (setName.startsWith("set")) {
                String getName = method.getName().replaceFirst("s", "g");
                Object oldValue = MethodUtils.invokeMethod(entity, getName);
                if (!Objects.equals(oldValue, args[0])) {
                    changeFields.put(setName, args[0]);
                }
            }
            if (method.getReturnType().isAssignableFrom(entity.getClass())) {
                proxy.invoke(entity, args);
                return obj;
            }
            return proxy.invoke(entity, args);
        };

        T proxyInstance = (T) Enhancer.create(entity.getClass(), handler);
        consumer.accept(proxyInstance);
        if (!changeFields.isEmpty()) {
            putToCache(entity, changeFields);
        }
    }

    private void putToCache(HasIdIdentifier entity, Map<String, Object> changeFields) {
        PureRepository repository;
        if (entity instanceof BaseEntity) {
            repository = classFinder.getRepositoryByClass(((BaseEntity) entity).getClass());
        } else {
            repository = pureRepositories.get(entity.getClass().getSimpleName());
        }
        cacheService.putToCache(repository, entity, changeFields);
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

        return merge;
    }

    @Override
    public <T> T getSettingValue(Class<? extends BundleSettingPlugin<T>> settingPluginClazz) {
        BundleSettingPlugin<T> pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        String value = this.getSettingRawValue(settingPluginClazz);
        return pluginFor.parseValue(this, StringUtils.defaultIfEmpty(value, pluginFor.getDefaultValue()));
    }

    @Override
    public <T> String getSettingRawValue(Class<? extends BundleSettingPlugin<T>> settingPluginClazz) {
        BundleSettingPlugin<T> pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        if (pluginFor.transientState()) {
            return settingTransientState.get(pluginFor);
        } else {
            SettingEntity settingEntity = getEntity(SettingRepository.getKey(pluginFor));
            return settingEntity == null ? null : settingEntity.getValue();
        }
    }

    @Override
    public <T> void listenSettingValue(Class<? extends BundleSettingPlugin<T>> bundleSettingPluginClazz, String key, Consumer<T> listener) {
        settingListeners.putIfAbsent(bundleSettingPluginClazz.getName(), new HashMap<>());
        settingListeners.get(bundleSettingPluginClazz.getName()).put(key, listener);
    }

    @Override
    public <T> void setSettingValue(Class<? extends BundleSettingPlugin<T>> settingPluginClazz, T value) {
        BundleSettingPlugin pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        String strValue = setSettingValueSilence(settingPluginClazz, value);
        fireNotifySettingHandlers(settingPluginClazz, value, pluginFor, strValue);
    }

    /**
     * Fire notification that setting state was changed without writing actual value to db
     */
    public <T> void notifySettingValueStateChanged(Class<? extends BundleSettingPlugin<T>> settingPluginClazz) {
        BundleSettingPlugin<T> pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        SettingEntity settingEntity = getEntity(SettingRepository.getKey(pluginFor));
        T value = pluginFor.parseValue(this, settingEntity.getValue());
        fireNotifySettingHandlers(settingPluginClazz, value, pluginFor, settingEntity.getValue());
    }

    @Override
    public <T> void setSettingValueRaw(Class<? extends BundleSettingPlugin<T>> settingPluginClazz, @NotNull String value) {
        BundleSettingPlugin pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        setSettingValue(settingPluginClazz, (T) pluginFor.parseValue(this, value));
    }

    @Override
    public <T> String setSettingValueSilence(Class<? extends BundleSettingPlugin<T>> settingPluginClazz, @NotNull T value) {
        BundleSettingPlugin pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        String strValue = pluginFor.writeValue(value);
        this.setSettingValueSilenceRaw(settingPluginClazz, strValue);
        return strValue;
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
    public void addHeaderNotification(NotificationEntityJSON notificationEntityJSON) {
        notifications.add(notificationEntityJSON);
    }

    @Override
    public void removeHeaderNotification(NotificationEntityJSON notificationEntityJSON) {
        notifications.remove(notificationEntityJSON);
    }

    @Override
    public void sendNotification(String destination, Object param) {
        messagingTemplate.convertAndSend(WebSocketConfig.DESTINATION_PREFIX + destination, param);
    }

    @Override
    public void showAlwaysOnViewNotification(NotificationEntityJSON notificationEntityJSON, int duration, String color) {
        AlwaysOnTopNotificationEntityJSON alwaysOnTopNotificationEntityJSON = new AlwaysOnTopNotificationEntityJSON(notificationEntityJSON);
        alwaysOnTopNotificationEntityJSON.setColor(color);
        alwaysOnTopNotificationEntityJSON.setDuration(duration);
        notifications.add(alwaysOnTopNotificationEntityJSON);
        sendNotification(alwaysOnTopNotificationEntityJSON);
    }

    @Override
    public void showAlwaysOnViewNotification(NotificationEntityJSON notificationEntityJSON, String icon, String color, Class<? extends BundleSettingPluginButton> stopAction) {
        AlwaysOnTopNotificationEntityJSON alwaysOnTopNotificationEntityJSON = new AlwaysOnTopNotificationEntityJSON(notificationEntityJSON);
        alwaysOnTopNotificationEntityJSON.setColor(color);
        alwaysOnTopNotificationEntityJSON.setIcon(icon);
        alwaysOnTopNotificationEntityJSON.setStopAction(SettingEntity.PREFIX + stopAction.getSimpleName());
        notifications.add(alwaysOnTopNotificationEntityJSON);
        sendNotification(alwaysOnTopNotificationEntityJSON);
    }

    @Override
    public void hideAlwaysOnViewNotification(NotificationEntityJSON notificationEntityJSON) {
        AlwaysOnTopNotificationEntityJSON alwaysOnTopNotificationEntityJSON = (AlwaysOnTopNotificationEntityJSON) notifications
                .stream().filter(n -> n.getEntityID().equals(notificationEntityJSON.getEntityID())).findAny().orElse(null);
        if (alwaysOnTopNotificationEntityJSON != null) {
            notifications.remove(alwaysOnTopNotificationEntityJSON);
            alwaysOnTopNotificationEntityJSON.setRemove(true);
            sendNotification(alwaysOnTopNotificationEntityJSON);
        }
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
    public BaseEntity<? extends BaseEntity> delete(String entityId) {
        BaseEntity<? extends BaseEntity> deletedEntity = entityManager.delete(entityId);
        if (deletedEntity != null) {
            cacheService.delete(entityId);
            for (Consumer consumer : this.entityClassRemoveListeners.getOrDefault(deletedEntity.getClass().getName(), Collections.emptyList())) {
                consumer.accept(deletedEntity);
            }
            for (Consumer listener : this.entityIDRemoveListeners.getOrDefault(entityId, Collections.emptyList())) {
                listener.accept(deletedEntity);
            }
        }
        return deletedEntity;
    }

    @Override
    public AbstractRepository<? extends BaseEntity> getRepositoryByPrefix(String repositoryPrefix) {
        return repositoriesByPrefix.get(repositoryPrefix);
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
    public <T> Map<String, T> getBeansOfTypeWithBeanName(Class<T> clazz) {
        Map<String, T> values = new HashMap<>();
        for (ApplicationContext context : allApplicationContexts) {
            values.putAll(context.getBeansOfType(clazz));
        }
        return values;
    }

    @Override
    public <T> Map<String, Collection<T>> getBeansOfTypeByBundles(Class<T> clazz) {
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
    public Collection<AbstractRepository> getRepositories() {
        return repositories.values();
    }

    @Override
    public <T> List<Class<? extends T>> getClassesWithAnnotation(Class<? extends Annotation> annotation) {
        return classFinder.getClassesWithAnnotation(annotation);
    }

    @Override
    @SneakyThrows
    public void listenUdp(String host, int port, BiConsumer<DatagramPacket, String> listener) {
        String key = host + port;
        if (!this.listenUdpMap.containsKey(key)) {
            this.listenUdpMap.put(key, new ArrayList<>());
            DatagramSocket socket = new DatagramSocket(host == null ? new InetSocketAddress(port) : new InetSocketAddress(host, port));
            DatagramPacket datagramPacket = new DatagramPacket(new byte[255], 255);

            String hostName = (host == null ? "0.0.0.0" : host) + ":" + port;
            ThreadContext<Void> schedule = this.schedule("listen-udp-" + hostName, 1, TimeUnit.SECONDS, () -> {
                socket.receive(datagramPacket);
                byte[] data = datagramPacket.getData();
                String text = new String(data, 0, datagramPacket.getLength());
                for (BiConsumer<DatagramPacket, String> udpListener : listenUdpMap.get(key)) {
                    udpListener.accept(datagramPacket, text);
                }
            }, true);
            schedule.setDescription("Listen udp: " + hostName);
        }
        this.listenUdpMap.get(key).add(listener);
    }

    @Override
    public ThreadContext<Void> schedule(String name, int timeout, TimeUnit timeUnit, ThrowingRunnable<Exception> command, boolean showOnUI) {
        return addSchedule(name, timeout, timeUnit, () -> {
            command.run();
            return null;
        }, ScheduleType.DELAY, showOnUI);
    }

    @Override
    public boolean isThreadExists(String name) {
        return this.schedulers.containsKey(name);
    }

    @Override
    public <T> ThreadContext<T> run(String name, ThrowingSupplier<T, Exception> command, boolean showOnUI) {
        return addSchedule(name, 0, TimeUnit.MILLISECONDS, command, ScheduleType.SINGLE, showOnUI);
    }

    @Override
    public void cancelThread(String name) {
        if (name != null) {
            ThreadContextImpl context = this.schedulers.remove(name);
            if (context != null) {
                context.cancel();
            }
        }
    }

    @Override
    public Map<String, Boolean> getDeviceFeatures() {
        return deviceFeatures;
    }

    @Override
    public <T extends BaseEntity> void addEntityUpdateListener(String entityID, BiConsumer<T, T> listener) {
        this.entityUpdateListeners.putIfAbsent(entityID, new ArrayList<>());
        this.entityUpdateListeners.get(entityID).add(listener);
    }

    @Override
    public <T extends BaseEntity> void addEntityUpdateListener(Class<T> entityClass, BiConsumer<T, T> listener) {
        this.entityClassUpdateListeners.putIfAbsent(entityClass.getName(), new ArrayList<>());
        this.entityClassUpdateListeners.get(entityClass.getName()).add(listener);
    }

    @Override
    public <T extends BaseEntity> void addEntityRemovedListener(String entityID, Consumer<T> listener) {
        this.entityIDRemoveListeners.putIfAbsent(entityID, new ArrayList<>());
        this.entityIDRemoveListeners.get(entityID).add(listener);
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

    public void addBundle(Map<String, BundleContext> artifactIdContextMap) {
        for (String artifactId : artifactIdContextMap.keySet()) {
            this.addBundle(artifactIdContextMap.get(artifactId), artifactIdContextMap);
        }
    }

    public void removeBundle(String bundleId) {
        InternalBundleContext internalBundleContext = bundles.remove(bundleId);
        if (internalBundleContext != null) {
            this.removeBundle(internalBundleContext.bundleContext);
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

    private <T extends BaseEntity> List<T> findAllByRepository(Class<BaseEntity> clazz, AbstractRepository repository) {
        return entityManager.getEntityIDsByEntityClassFullName(clazz).stream()
                .map(entityID -> {
                    T entity = entityManager.getEntityWithFetchLazy(entityID);
                    repository.updateEntityAfterFetch(entity);
                    return entity;
                }).collect(Collectors.toList());
    }

    private void createUser() {
        UserEntity userEntity = getEntity(ADMIN_USER, false);
        if (userEntity == null) {
            save(new UserEntity().computeEntityID(() -> ADMIN_USER)
                    .setRoles(new HashSet<>(Arrays.asList(ADMIN_ROLE, PRIVILEGED_USER_ROLE, GUEST_ROLE))));
        }
    }

    private <T> void fireNotifySettingHandlers(Class<? extends BundleSettingPlugin<T>> settingPluginClazz, T value,
                                               BundleSettingPlugin pluginFor, String strValue) {
        if (settingListeners.containsKey(settingPluginClazz.getName())) {
            for (Consumer consumer : settingListeners.get(settingPluginClazz.getName()).values()) {
                try {
                    consumer.accept(value);
                } catch (Exception ex) {
                    log.error("Error while fire listener for setting <{}>. Value: <{}>", settingPluginClazz.getSimpleName(), value);
                }
            }
        }
        broadcastLockManager.signalAll(SettingEntity.PREFIX + settingPluginClazz.getSimpleName(), StringUtils.defaultIfEmpty(strValue, pluginFor.getDefaultValue()));
        this.sendNotification(pluginFor.buildToastrNotificationEntity(value, strValue, this));
    }

    private void registerEntityListeners() {
        this.showEntityState = getSettingValue(SystemShowEntityStateSetting.class);
        this.listenSettingValue(SystemShowEntityStateSetting.class, "im-show-entity-states", value -> this.showEntityState = value);

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

    private void addBundle(BundleContext bundleContext, Map<String, BundleContext> artifactIdToContextMap) {
        if (!bundleContext.isInternal() && !bundleContext.isInstalled()) {
            if (!bundleContext.isLoaded()) {
                notifications.add(NotificationEntityJSON.danger("fail-bundle-" + bundleContext.getBundleID())
                        .setName(bundleContext.getBundleFriendlyName()).setDescription("Unable to load bundle"));
                return;
            }
            allApplicationContexts.add(bundleContext.getApplicationContext());
            bundleContext.setInstalled(true);
            for (String bundleDependency : bundleContext.getDependencies()) {
                addBundle(artifactIdToContextMap.get(bundleDependency), artifactIdToContextMap);
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
        En.DEFAULT_LANG = getSettingValue(SystemLanguageSetting.class);

        Map<String, PureRepository> pureRepositoryMap = context.getBeansOfType(PureRepository.class).values()
                .stream().collect(Collectors.toMap(r -> r.getEntityClass().getSimpleName(), r -> r));

        if (addBundle) {
            pureRepositories.putAll(pureRepositoryMap);
            repositories.putAll(context.getBeansOfType(AbstractRepository.class));
        } else {
            pureRepositories.keySet().removeAll(pureRepositoryMap.keySet());
            repositories.keySet().removeAll(context.getBeansOfType(AbstractRepository.class).keySet());
        }
        baseEntityNameToClass = classFinder.getClassesWithParent(BaseEntity.class, null, null).stream().collect(Collectors.toMap(Class::getSimpleName, s -> s));
        repositoriesByPrefix = repositories.values().stream().collect(Collectors.toMap(AbstractRepository::getPrefix, r -> r));

        applicationContext.getBean(ConsoleController.class).postConstruct();
        applicationContext.getBean(BundleManager.class).postConstruct(this);
        applicationContext.getBean(SettingController.class).postConstruct(this);
        /*if (!addBundle) {
            applicationContext.getBean(SettingRepository.class).deleteRemovedSettings();
        }*/
        applicationContext.getBean(WorkspaceManager.class).postConstruct(this);
        applicationContext.getBean(WorkspaceController.class).postConstruct(this);
        applicationContext.getBean(ItemController.class).postConstruct();
        applicationContext.getBean(Scratch3OtherBlocks.class).postConstruct();
        applicationContext.getBean(PortManager.class).postConstruct();

        if (bundleContext != null) {
            applicationContext.getBean(ExtRequestMappingHandlerMapping.class).updateContextRestControllers(context, addBundle);
        }

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

    private void createTableIndexes() {
        List<Class<? extends BaseEntity>> list = classFinder.getClassesWithParent(BaseEntity.class, null, null)
                .stream().filter(l -> !(WidgetBaseEntity.class.isAssignableFrom(l) || DeviceBaseEntity.class.isAssignableFrom(l)))
                .collect(Collectors.toList());
        list.add(DeviceBaseEntity.class);
        list.add(WidgetBaseEntity.class);

        javax.persistence.EntityManager em = applicationContext.getBean(javax.persistence.EntityManager.class);
        MetamodelImplementor meta = (MetamodelImplementor) em.getMetamodel();
        new TransactionTemplate(transactionManager).execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus transactionStatus) {
                for (Class<? extends BaseEntity> aClass : list) {
                    String tableName = ((Joinable) meta.entityPersister(aClass)).getTableName();
                    try {
                        em.createNativeQuery(String.format(CREATE_TABLE_INDEX, aClass.getSimpleName(), tableName)).executeUpdate();
                    } catch (Exception ex) {
                        log.error("Error while creating index for table: <{}>", tableName, ex);
                    }
                }
            }
        });
    }

    private <T> ThreadContext<T> addSchedule(String name, int timeout, TimeUnit timeUnit, ThrowingSupplier<T, Exception> command,
                                             ScheduleType scheduleType, boolean showOnUI) {
        this.cancelThread(name);
        ThreadContextImpl<T> threadContext = new ThreadContextImpl<T>(name, command, scheduleType, timeout > 0 ? timeUnit.toMillis(timeout) : null, showOnUI);
        this.schedulers.put(name, threadContext);

        Runnable runnable = () -> {
            try {
                threadContext.runCount++;
                threadContext.state = "STARTED";
                threadContext.getCommand().get();
                threadContext.state = "FINISHED";
            } catch (Exception ex) {
                log.error("Exception in thread: <{}>", name);
                threadContext.errorListener.accept(ex);
            }
        };
        ScheduledFuture<?> scheduledFuture = null;
        switch (scheduleType) {
            case DELAY:
                scheduledFuture = scheduleService.scheduleWithFixedDelay(runnable, 0, timeout, timeUnit);
                break;
            case RATE:
                scheduledFuture = scheduleService.scheduleAtFixedRate(runnable, 0, timeout, timeUnit);
                break;
            case SINGLE:
                scheduledFuture = scheduleService.schedule(runnable, 0, TimeUnit.MILLISECONDS);
                break;
        }
        threadContext.scheduledFuture = (ScheduledFuture<T>) scheduledFuture;
        return threadContext;
    }

    private void fetchReleaseVersion() {
        try {
            log.info("Try fetch latest version from server");
            notifications.add(NotificationEntityJSON.info("version").setName("app").setDescription("version: " + touchHomeProperties.getVersion()));
            this.latestVersion = Curl.get(GIT_HUB_URL + "/releases/latest", Map.class).get("tag_name").toString();

            if (!touchHomeProperties.getVersion().equals(this.latestVersion)) {
                log.info("Found newest version <{}>. Current version: <{}>", this.latestVersion, touchHomeProperties.getVersion());
                notifications.add(NotificationEntityJSON.danger("version")
                        .setName("app").setDescription("Require update app version from " + touchHomeProperties.getVersion() + " to " + this.latestVersion));
                this.hardwareEvents.fireEvent("app-release", this.latestVersion);
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

    @Getter
    @RequiredArgsConstructor
    public static class ThreadContextImpl<T> implements ThreadContext<T> {
        private final String name;
        private final ThrowingSupplier<T, Exception> command;
        private final ScheduleType scheduleType;
        private final Long period;
        private final boolean showOnUI;
        private ScheduledFuture<T> scheduledFuture;
        @Setter
        private String state;
        @Setter
        private String description;
        private boolean stopped;
        @Setter
        private int runCount;
        @Getter
        private Date creationTime = new Date();

        private Consumer<Exception> errorListener;

        public Long getTimeToNextSchedule() {
            if (period == null) {
                return null;
            }
            return scheduledFuture.getDelay(TimeUnit.MILLISECONDS) / 1000;
        }

        @Override
        public void cancel() {
            if (scheduledFuture.isCancelled() || scheduledFuture.isDone() || scheduledFuture.cancel(true)) {
                stopped = true;
            }
        }

        @Override
        public T await(long timeout, TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
            return scheduledFuture.get(timeout, timeUnit);
        }

        public void onError(Consumer<Exception> errorListener) {
            this.errorListener = errorListener;
        }
    }

    @RequiredArgsConstructor
    public enum ScheduleType {
        DELAY, RATE, SINGLE
    }
}
