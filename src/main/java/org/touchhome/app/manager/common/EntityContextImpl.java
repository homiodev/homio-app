package org.touchhome.app.manager.common;

import lombok.Getter;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.touchhome.app.LogService;
import org.touchhome.app.auth.JwtTokenProvider;
import org.touchhome.app.config.ExtRequestMappingHandlerMapping;
import org.touchhome.app.config.TouchHomeProperties;
import org.touchhome.app.extloader.BundleContext;
import org.touchhome.app.extloader.BundleContextService;
import org.touchhome.app.manager.*;
import org.touchhome.app.manager.common.impl.*;
import org.touchhome.app.repository.crud.base.BaseCrudRepository;
import org.touchhome.app.repository.device.AllDeviceRepository;
import org.touchhome.app.rest.ConsoleController;
import org.touchhome.app.rest.ItemController;
import org.touchhome.app.rest.SettingController;
import org.touchhome.app.setting.system.SystemClearCacheButtonSetting;
import org.touchhome.app.setting.system.SystemLanguageSetting;
import org.touchhome.app.setting.system.SystemShowEntityStateSetting;
import org.touchhome.app.utils.Curl;
import org.touchhome.app.utils.HardwareUtils;
import org.touchhome.app.workspace.WorkspaceController;
import org.touchhome.app.workspace.WorkspaceManager;
import org.touchhome.app.workspace.block.core.Scratch3OtherBlocks;
import org.touchhome.bundle.api.BundleEntryPoint;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.Lang;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.DeviceBaseEntity;
import org.touchhome.bundle.api.entity.UserEntity;
import org.touchhome.bundle.api.entity.widget.WidgetBaseEntity;
import org.touchhome.bundle.api.entity.workspace.WorkspaceStandaloneVariableEntity;
import org.touchhome.bundle.api.entity.workspace.bool.WorkspaceBooleanEntity;
import org.touchhome.bundle.api.entity.workspace.var.WorkspaceVariableEntity;
import org.touchhome.bundle.api.exception.NotFoundException;
import org.touchhome.bundle.api.hardware.other.LinuxHardwareRepository;
import org.touchhome.bundle.api.model.HasEntityIdentifier;
import org.touchhome.bundle.api.repository.AbstractRepository;
import org.touchhome.bundle.api.repository.PureRepository;
import org.touchhome.bundle.api.setting.SettingPlugin;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.widget.WidgetBaseTemplate;
import org.touchhome.bundle.api.workspace.BroadcastLockManager;
import org.touchhome.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;

import javax.persistence.EntityManagerFactory;
import java.lang.annotation.Annotation;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.touchhome.bundle.api.entity.UserEntity.ADMIN_USER;
import static org.touchhome.bundle.api.util.TouchHomeUtils.*;

@Log4j2
@Component
public class EntityContextImpl implements EntityContext {

    public static final String CREATE_TABLE_INDEX = "CREATE UNIQUE INDEX IF NOT EXISTS %s_entity_id ON %s (entityid)";
    public static Map<String, AbstractRepository> repositories = new HashMap<>();
    public static Map<String, Class<? extends BaseEntity>> baseEntityNameToClass;
    private static EntityManager entityManager;
    private static Map<String, AbstractRepository> repositoriesByPrefix;
    private static Map<String, PureRepository> pureRepositories = new HashMap<>();
    private final String GIT_HUB_URL = "https://api.github.com/repos/touchhome/touchhome-core";
    private final EntityContextUIImpl entityContextUI;
    private final EntityContextUDPImpl entityContextUDP;
    private final EntityContextEventImpl entityContextEvent;
    private final EntityContextBGPImpl entityContextBGP;
    private final EntityContextSettingImpl entityContextSetting;

    private final ClassFinder classFinder;
    private final CacheService cacheService;

    @Getter
    private final BroadcastLockManager broadcastLockManager;
    private final TouchHomeProperties touchHomeProperties;

    private final Map<String, List<BiConsumer>> entityTypeUpdateListeners = new HashMap<>();
    private final Map<String, List<BiConsumer>> entityTypeRemoveListeners = new HashMap<>();

    private final Map<String, List<BiConsumer>> entityIDUpdateListeners = new HashMap<>();
    private final Map<String, List<BiConsumer>> entityIDRemoveListeners = new HashMap<>();

    private final Map<String, Boolean> deviceFeatures = new HashMap<>();

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

    public EntityContextImpl(ClassFinder classFinder, CacheService cacheService, SimpMessagingTemplate messagingTemplate,
                             BroadcastLockManager broadcastLockManager, TouchHomeProperties touchHomeProperties) {
        this.classFinder = classFinder;
        this.cacheService = cacheService;
        this.broadcastLockManager = broadcastLockManager;
        this.touchHomeProperties = touchHomeProperties;

        this.entityContextUI = new EntityContextUIImpl(messagingTemplate, this);
        this.entityContextUDP = new EntityContextUDPImpl(this);
        this.entityContextEvent = new EntityContextEventImpl(broadcastLockManager);
        this.entityContextBGP = new EntityContextBGPImpl(this, touchHomeProperties);
        this.entityContextSetting = new EntityContextSettingImpl(this);
    }

    @SneakyThrows
    public void afterContextStart(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;

        this.transactionManager = this.applicationContext.getBean(PlatformTransactionManager.class);
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

        setting().listenValue(SystemClearCacheButtonSetting.class, "im-clear-cache", cacheService::clearCache);

        // reset device 'status' and 'joined' values
        this.allDeviceRepository.resetDeviceStatuses();

        // loadWorkspace modules
        log.info("Initialize bundles");
        ArrayList<BundleEntryPoint> bundleEntrypoints = new ArrayList<>(applicationContext.getBeansOfType(BundleEntryPoint.class).values());
        Collections.sort(bundleEntrypoints);
        for (BundleEntryPoint bundleEntrypoint : bundleEntrypoints) {
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

        // applicationContext.getBean(SettingRepository.class).deleteRemovedSettings();

        ui().addHeaderInfoNotification("app-status", "app", "Started at " + DateFormat.getDateTimeInstance().format(new Date()));

        // create indexes on tables
        this.createTableIndexes();
        this.bgp().schedule("check-app-version", 1, TimeUnit.DAYS, this::fetchReleaseVersion, true);
        this.event().addEventAndFire("app-started", "App started");

        // install autossh. Should refactor to move somewhere else
        this.bgp().runOnceOnInternetUp("install-software", () -> {
            LinuxHardwareRepository repository = getBean(LinuxHardwareRepository.class);
            if (!repository.isSoftwareInstalled("autossh")) {
                repository.installSoftware("autossh");
            }
        });
    }

    @Override
    public EntityContextUIImpl ui() {
        return entityContextUI;
    }

    @Override
    public EntityContextEventImpl event() {
        return this.entityContextEvent;
    }

    @Override
    public EntityContextUDPImpl udp() {
        return this.entityContextUDP;
    }

    @Override
    public EntityContextBGPImpl bgp() {
        return entityContextBGP;
    }

    @Override
    public EntityContextSettingImpl setting() {
        return entityContextSetting;
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
    public <T extends HasEntityIdentifier> void createDelayed(T entity) {
        putToCache(entity, null);
    }

    @Override
    public <T extends HasEntityIdentifier> void updateDelayed(T entity, Consumer<T> consumer) {
        Map<String, Object> changeFields = new HashMap<>();
        MethodInterceptor handler = (obj, method, args, proxy) -> {
            String setName = method.getName();
            if (setName.startsWith("set")) {
                Object oldValue;
                try {
                    oldValue = MethodUtils.invokeMethod(entity, method.getName().replaceFirst("set", "get"));
                } catch (NoSuchMethodException ex) {
                    oldValue = MethodUtils.invokeMethod(entity, method.getName().replaceFirst("set", "is"));
                }
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

        entityUpdate(null, null, this.entityIDUpdateListeners, this.entityTypeUpdateListeners, (Supplier<HasEntityIdentifier>) () -> {
            consumer.accept(proxyInstance);
            return entity;
        });
        if (!changeFields.isEmpty()) {
            putToCache(entity, changeFields);
        }
        // fire change event manually
        sendEntityUpdateNotification(entity, "change");
    }

    private void putToCache(HasEntityIdentifier entity, Map<String, Object> changeFields) {
        PureRepository repository;
        if (entity instanceof BaseEntity) {
            repository = classFinder.getRepositoryByClass(((BaseEntity) entity).getClass());
        } else {
            repository = pureRepositories.get(entity.getClass().getSimpleName());
        }
        cacheService.putToCache(repository, entity, changeFields);
    }

    @Override
    public <T extends HasEntityIdentifier> void save(T entity) {
        BaseCrudRepository pureRepository = (BaseCrudRepository) pureRepositories.get(entity.getClass().getSimpleName());
        pureRepository.save(entity);
    }

    private <T extends HasEntityIdentifier> T entityUpdate(T entity, T oldEntity, Map<String, List<BiConsumer>> entityIDListeners,
                                                           Map<String, List<BiConsumer>> entityTypeListeners, Supplier<T> updateHandler) {
        T saved = null;
        try {
            saved = updateHandler.get();
        } finally {
            if (saved != null) {
                for (BiConsumer consumer : entityIDListeners.getOrDefault(saved.getEntityID(), Collections.emptyList())) {
                    consumer.accept(saved, entity);
                }
                notifyEntityListeners(entityTypeListeners, saved, oldEntity);
            }
        }
        return saved;
    }

    @Override
    public <T extends BaseEntity> T save(T entity) {
        AbstractRepository foundRepo = classFinder.getRepositoryByClass(entity.getClass());
        final AbstractRepository repository = foundRepo == null && entity instanceof DeviceBaseEntity ? allDeviceRepository : foundRepo;
        T oldEntity = entity.getEntityID() == null ? null : getEntity(entity);

        T merge = transactionTemplate.execute(status ->
                entityUpdate(entity, oldEntity, this.entityIDUpdateListeners, this.entityTypeUpdateListeners, () -> {
                    T t = (T) repository.save(entity);
                    repository.updateEntityAfterFetch(t);
                    return t;
                }));

        if (StringUtils.isEmpty(entity.getEntityID())) {
            entity.setEntityID(merge.getEntityID());
            entity.setId(merge.getId());
        }

        // post save
        cacheService.entityUpdated(entity);

        return merge;
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
        return entityUpdate(null, null, this.entityIDRemoveListeners, this.entityTypeRemoveListeners, () -> {
            cacheService.delete(entityId);
            return entityManager.delete(entityId);
        });
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
        this.entityIDUpdateListeners.putIfAbsent(entityID, new ArrayList<>());
        this.entityIDUpdateListeners.get(entityID).remove(listener);
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
    public <T> List<Class<? extends T>> getClassesWithParent(Class<T> baseClass, String... packages) {
        List<Class<? extends T>> classes = new ArrayList<>();
        if (packages.length > 0) {
            for (String basePackage : packages) {
                classes.addAll(classFinder.getClassesWithParent(baseClass, null, basePackage));
            }
        } else {
            classes.addAll(classFinder.getClassesWithParent(baseClass, null, baseClass.getPackage().getName()));
        }
        return classes;
    }

    @Override
    public Map<String, Boolean> getDeviceFeatures() {
        return deviceFeatures;
    }

    @Override
    public <T extends BaseEntity> void addEntityUpdateListener(String entityID, BiConsumer<T, T> listener) {
        this.entityIDUpdateListeners.putIfAbsent(entityID, new ArrayList<>());
        this.entityIDUpdateListeners.get(entityID).add(listener);
    }

    @Override
    public <T extends BaseEntity> void addEntityUpdateListener(Class<T> entityClass, BiConsumer<T, T> listener) {
        this.entityTypeUpdateListeners.putIfAbsent(entityClass.getName(), new ArrayList<>());
        this.entityTypeUpdateListeners.get(entityClass.getName()).add(listener);
    }

    @Override
    public <T extends BaseEntity> void addEntityRemovedListener(String entityID, Consumer<T> listener) {
        this.entityIDRemoveListeners.putIfAbsent(entityID, new ArrayList<>());
        this.entityIDRemoveListeners.get(entityID).add((o, o2) -> listener.accept((T) o));
    }

    @Override
    public <T extends BaseEntity> void addEntityRemovedListener(Class<T> entityClass, Consumer<T> listener) {
        this.entityTypeRemoveListeners.putIfAbsent(entityClass.getName(), new ArrayList<>());
        this.entityTypeRemoveListeners.get(entityClass.getName()).add((BiConsumer<T, T>) (o, o2) -> listener.accept(o));
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
                    .setPassword("admin123", getBean(PasswordEncoder.class)).setUserId("admin@gmail.com")
                    .setRoles(new HashSet<>(Arrays.asList(ADMIN_ROLE, PRIVILEGED_USER_ROLE, GUEST_ROLE))));
        }
    }

    private void registerEntityListeners() {
        setting().listenValueAndGet(SystemShowEntityStateSetting.class, "im-show-entity-states", value -> this.showEntityState = value);

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
                sendEntityUpdateNotification(entity, type);
            }
        } catch (Exception ex) {
            log.error("Unable to update cache entity <{}> for entity: <{}>", type, entity);
        }
    }

    private void sendEntityUpdateNotification(Object entity, String type) {
        // send info if item changed and it could be shown on page
        if (entity.getClass().isAnnotationPresent(UISidebarMenu.class)) {
            ui().sendNotification("-listen-items", new JSONObject().put("type", type).put("value", entity));
        }
        if (showEntityState) {
            switch (type) {
                case "removed":
                    this.ui().sendWarningMessage("TOASTR.ENTITY_REMOVED");
                    break;
                case "created":
                    this.ui().sendInfoMessage("TOASTR.ENTITY_INSERTED");
                    break;
                case "changed":
                    this.ui().sendInfoMessage("TOASTR.ENTITY_UPDATED");
                    break;
            }
        }
    }

    private void removeBundle(BundleContext bundleContext) {
        if (!bundleContext.isInternal() && bundleContext.isInstalled()) {
            ApplicationContext context = bundleContext.getApplicationContext();
            context.getBean(BundleEntryPoint.class).destroy();
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
                ui().addHeaderErrorNotification("fail-bundle-" + bundleContext.getBundleID(),
                        bundleContext.getBundleFriendlyName(), "Unable to load bundle");
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

            for (BundleEntryPoint bundleEntrypoint : context.getBeansOfType(BundleEntryPoint.class).values()) {
                bundleEntrypoint.init();
                this.bundles.put(bundleEntrypoint.getBundleId(), new InternalBundleContext(bundleEntrypoint, bundleContext));
            }
        }
    }

    private void updateBeans(BundleContext bundleContext, ApplicationContext context, boolean addBundle) {
        log.info("Starting update all app bundles");
        Lang.clear();
        fetchSettingPlugins(bundleContext, addBundle);

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

        Lang.DEFAULT_LANG = setting().getValue(SystemLanguageSetting.class).name();
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

    private void fetchSettingPlugins(BundleContext bundleContext, boolean addBundle) {
        String basePackage = bundleContext == null ? null : bundleContext.getBasePackage();
        for (Class<? extends SettingPlugin> settingPlugin : classFinder.getClassesWithParent(SettingPlugin.class, null, basePackage)) {
            setting().updatePlugins(settingPlugin, addBundle);
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

    private void fetchReleaseVersion() {
        try {
            log.info("Try fetch latest version from server");
            ui().addHeaderInfoNotification("version", "app", "version: " + touchHomeProperties.getVersion());
            this.latestVersion = Curl.get(GIT_HUB_URL + "/releases/latest", Map.class).get("tag_name").toString();

            if (!touchHomeProperties.getVersion().equals(this.latestVersion)) {
                log.info("Found newest version <{}>. Current version: <{}>", this.latestVersion, touchHomeProperties.getVersion());
                ui().addHeaderErrorNotification("version", "app", "Require update app version from " + touchHomeProperties.getVersion() + " to " + this.latestVersion);
                this.event().fireEvent("app-release", this.latestVersion);
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

    private <T extends HasEntityIdentifier> void notifyEntityListeners(Map<String, List<BiConsumer>> entityListeners, T saved, T oldEntity) {
        Class entityClass = saved.getClass();
        while (!entityClass.getSimpleName().equals(BaseEntity.class.getSimpleName())) {
            for (BiConsumer consumer : entityListeners.getOrDefault(entityClass.getName(), Collections.emptyList())) {
                consumer.accept(saved, oldEntity);
            }
            entityClass = entityClass.getSuperclass();
        }
    }

    public static class InternalBundleContext {
        @Getter
        private final BundleEntryPoint bundleEntrypoint;
        @Getter
        private final BundleContext bundleContext;
        private final Map<String, Object> fieldTypes = new HashMap<>();

        public InternalBundleContext(BundleEntryPoint bundleEntrypoint, BundleContext bundleContext) {
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
