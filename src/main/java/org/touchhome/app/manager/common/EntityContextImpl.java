package org.touchhome.app.manager.common;

import static org.apache.xmlbeans.XmlBeans.getTitle;
import static org.touchhome.bundle.api.util.TouchHomeUtils.MACHINE_IP_ADDRESS;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.DateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.persistence.EntityManagerFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.event.internal.PostDeleteEventListenerStandardImpl;
import org.hibernate.event.internal.PostInsertEventListenerStandardImpl;
import org.hibernate.event.internal.PostUpdateEventListenerStandardImpl;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventSource;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PreDeleteEventListener;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.metamodel.spi.MetamodelImplementor;
import org.hibernate.persister.entity.Joinable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.touchhome.app.LogService;
import org.touchhome.app.audio.AudioService;
import org.touchhome.app.auth.JwtTokenProvider;
import org.touchhome.app.config.ExtRequestMappingHandlerMapping;
import org.touchhome.app.config.TouchHomeProperties;
import org.touchhome.app.extloader.BundleContext;
import org.touchhome.app.extloader.BundleContextService;
import org.touchhome.app.hardware.StartupHardwareRepository;
import org.touchhome.app.manager.BundleService;
import org.touchhome.app.manager.CacheService;
import org.touchhome.app.manager.LoggerService;
import org.touchhome.app.manager.PortService;
import org.touchhome.app.manager.ScriptService;
import org.touchhome.app.manager.WidgetService;
import org.touchhome.app.manager.common.impl.EntityContextBGPImpl;
import org.touchhome.app.manager.common.impl.EntityContextEventImpl;
import org.touchhome.app.manager.common.impl.EntityContextSettingImpl;
import org.touchhome.app.manager.common.impl.EntityContextUDPImpl;
import org.touchhome.app.manager.common.impl.EntityContextUIImpl;
import org.touchhome.app.manager.common.impl.EntityContextVarImpl;
import org.touchhome.app.manager.common.impl.EntityContextWidgetImpl;
import org.touchhome.app.model.entity.widget.WidgetBaseEntity;
import org.touchhome.app.repository.SettingRepository;
import org.touchhome.app.repository.crud.base.BaseCrudRepository;
import org.touchhome.app.repository.device.AllDeviceRepository;
import org.touchhome.app.rest.ConsoleController;
import org.touchhome.app.rest.FileSystemController;
import org.touchhome.app.rest.ItemController;
import org.touchhome.app.rest.SettingController;
import org.touchhome.app.setting.system.SystemClearCacheButtonSetting;
import org.touchhome.app.setting.system.SystemLanguageSetting;
import org.touchhome.app.setting.system.SystemShowEntityStateSetting;
import org.touchhome.app.spring.ContextCreated;
import org.touchhome.app.spring.ContextRefreshed;
import org.touchhome.app.utils.HardwareUtils;
import org.touchhome.app.workspace.BroadcastLockManagerImpl;
import org.touchhome.app.workspace.WorkspaceController;
import org.touchhome.app.workspace.WorkspaceManager;
import org.touchhome.bundle.api.BundleEntryPoint;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.EntityContextVar;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.DeviceBaseEntity;
import org.touchhome.bundle.api.entity.dependency.DependencyExecutableInstaller;
import org.touchhome.bundle.api.hardware.network.NetworkHardwareRepository;
import org.touchhome.bundle.api.inmemory.InMemoryDB;
import org.touchhome.bundle.api.model.ActionResponseModel;
import org.touchhome.bundle.api.model.HasEntityIdentifier;
import org.touchhome.bundle.api.model.Status;
import org.touchhome.bundle.api.repository.AbstractRepository;
import org.touchhome.bundle.api.repository.PureRepository;
import org.touchhome.bundle.api.repository.UserRepository;
import org.touchhome.bundle.api.service.EntityService;
import org.touchhome.bundle.api.setting.SettingPlugin;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.ui.field.action.HasDynamicContextMenuActions;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputBuilder;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.bundle.api.util.UpdatableSetting;
import org.touchhome.bundle.api.widget.WidgetBaseTemplate;
import org.touchhome.bundle.raspberry.repository.RaspberryDeviceRepository;
import org.touchhome.common.exception.NotFoundException;
import org.touchhome.common.model.UpdatableValue;
import org.touchhome.common.util.CommonUtils;
import org.touchhome.common.util.Curl;
import org.touchhome.common.util.Lang;

@Log4j2
@Component
public class EntityContextImpl implements EntityContext {

  // count how much addBundle/removeBundle invokes
  public static int BUNDLE_UPDATE_COUNT = 0;

  private static final Set<Class<? extends ContextCreated>> BEAN_CONTEXT_CREATED = new LinkedHashSet<>();
  private static final Set<Class<? extends ContextRefreshed>> BEAN_CONTEXT_REFRESH = new LinkedHashSet<>();

  static {
    BEAN_CONTEXT_CREATED.add(FileSystemController.class);
    BEAN_CONTEXT_CREATED.add(ItemController.class);
    BEAN_CONTEXT_CREATED.add(PortService.class);
    BEAN_CONTEXT_CREATED.add(LoggerService.class);
    BEAN_CONTEXT_CREATED.add(WidgetService.class);
    BEAN_CONTEXT_CREATED.add(BundleContextService.class);
    BEAN_CONTEXT_CREATED.add(ScriptService.class);
    BEAN_CONTEXT_CREATED.add(JwtTokenProvider.class);

    BEAN_CONTEXT_REFRESH.add(FileSystemController.class);
    BEAN_CONTEXT_REFRESH.add(BundleService.class);
    BEAN_CONTEXT_REFRESH.add(ConsoleController.class);
    BEAN_CONTEXT_REFRESH.add(SettingRepository.class);
    BEAN_CONTEXT_REFRESH.add(SettingController.class);
    BEAN_CONTEXT_REFRESH.add(WorkspaceManager.class);
    BEAN_CONTEXT_REFRESH.add(WorkspaceController.class);
    BEAN_CONTEXT_REFRESH.add(ItemController.class);
    BEAN_CONTEXT_REFRESH.add(PortService.class);
    BEAN_CONTEXT_REFRESH.add(AudioService.class);
  }

  public static final String CREATE_TABLE_INDEX = "CREATE UNIQUE INDEX IF NOT EXISTS %s_entity_id ON %s (entityid)";

  public static Map<String, AbstractRepository> repositories = new HashMap<>();
  public static Map<String, Class<? extends BaseEntity>> baseEntityNameToClass;
  public static Map<String, AbstractRepository> repositoriesByPrefix;
  private static Map<String, PureRepository> pureRepositories = new HashMap<>();

  private EntityManager entityManager;
  private final EntityContextUIImpl entityContextUI;
  private final EntityContextUDPImpl entityContextUDP;
  private final EntityContextEventImpl entityContextEvent;
  private final EntityContextBGPImpl entityContextBGP;
  private final EntityContextSettingImpl entityContextSetting;
  private final EntityContextVarImpl entityContextVar;
  private final EntityContextWidgetImpl entityContextWidget;
  private final Environment environment;
  private final StartupHardwareRepository startupHardwareRepository;
  private final EntityContextStorage entityContextStorage;

  private final ClassFinder classFinder;
  @Getter
  private final CacheService cacheService;

  @Getter
  private final TouchHomeProperties touchHomeProperties;

  private final Map<String, Boolean> deviceFeatures = new HashMap<>();

  private TransactionTemplate transactionTemplate;
  private Boolean showEntityState;
  private ApplicationContext applicationContext;
  private AllDeviceRepository allDeviceRepository;
  private EntityManagerFactory entityManagerFactory;
  private PlatformTransactionManager transactionManager;
  private WorkspaceManager workspaceManager;

  @Getter
  private Map<String, InternalBundleContext> bundles = new LinkedHashMap<>();
  private String latestVersion;

  private Set<ApplicationContext> allApplicationContexts = new HashSet<>();

  public EntityContextImpl(ClassFinder classFinder, CacheService cacheService, ThreadPoolTaskScheduler taskScheduler,
      SimpMessagingTemplate messagingTemplate, Environment environment,
      StartupHardwareRepository startupHardwareRepository,
      TouchHomeProperties touchHomeProperties) {
    this.classFinder = classFinder;
    this.environment = environment;
    this.cacheService = cacheService;
    this.startupHardwareRepository = startupHardwareRepository;
    this.touchHomeProperties = touchHomeProperties;

    this.entityContextUI = new EntityContextUIImpl(this, messagingTemplate);
    this.entityContextBGP = new EntityContextBGPImpl(this, taskScheduler);
    this.entityContextUDP = new EntityContextUDPImpl(this);
    this.entityContextEvent = new EntityContextEventImpl(this, touchHomeProperties);
    this.entityContextSetting = new EntityContextSettingImpl(this);
    this.entityContextWidget = new EntityContextWidgetImpl(this);
    this.entityContextStorage = new EntityContextStorage(this);
    this.entityContextVar = new EntityContextVarImpl(this);

    LogService.scanEntityLogs(classFinder);
  }

  @SneakyThrows
  public void afterContextStart(ApplicationContext applicationContext) {
    this.allApplicationContexts.add(applicationContext);
    this.applicationContext = applicationContext;
    MACHINE_IP_ADDRESS = getInternalIpAddress();

    this.transactionManager = this.applicationContext.getBean(PlatformTransactionManager.class);
    this.entityManagerFactory = this.applicationContext.getBean(EntityManagerFactory.class);
    this.allDeviceRepository = this.applicationContext.getBean(AllDeviceRepository.class);
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.workspaceManager = applicationContext.getBean(WorkspaceManager.class);
    this.entityManager = applicationContext.getBean(EntityManager.class);

    rebuildAllRepositories(applicationContext, true);

    getBean(UserRepository.class).ensureUserExists();
    getBean(RaspberryDeviceRepository.class).ensureDeviceExists();
    setting().fetchSettingPlugins("org.touchhome", classFinder, true);

    entityContextVar.onContextCreated();
    entityContextUI.onContextCreated();

    for (Class<? extends ContextCreated> beanUpdateClass : BEAN_CONTEXT_CREATED) {
      applicationContext.getBean(beanUpdateClass).onContextCreated(this);
    }
    updateBeans(null, applicationContext, true);

    registerEntityListeners();

    initialiseInlineBundles(applicationContext);

    ui().addBellInfoNotification("app-status", "app", "Started at " + DateFormat.getDateTimeInstance().format(new Date()));

    bgp().builder("check-app-version").interval(Duration.ofDays(1)).execute(this::fetchReleaseVersion);

    event().fireEventIfNotSame("app-status", Status.ONLINE);
    event().runOnceOnInternetUp("internal-ctx", () -> MACHINE_IP_ADDRESS = getInternalIpAddress());
    setting().listenValue(SystemClearCacheButtonSetting.class, "im-clear-cache", cacheService::clearCache);
    setting().listenValueAndGet(SystemLanguageSetting.class, "listen-lang", lang -> Lang.CURRENT_LANG = lang.name());

    this.updateDeviceFeatures();

    this.entityContextStorage.init();
  }

  private void initialiseInlineBundles(ApplicationContext applicationContext) {
    log.info("Initialize bundles...");
    ArrayList<BundleEntryPoint> bundleEntryPoints = new ArrayList<>(applicationContext.getBeansOfType(BundleEntryPoint.class).values());
    Collections.sort(bundleEntryPoints);
    for (BundleEntryPoint bundleEntrypoint : bundleEntryPoints) {
      this.bundles.put(bundleEntrypoint.getBundleId(), new InternalBundleContext(bundleEntrypoint, null));
      log.info("Init bundle: <{}>", bundleEntrypoint.getBundleId());
      try {
        bundleEntrypoint.init();
        bundleEntrypoint.onContextRefresh();
      } catch (Exception ex) {
        log.fatal("Unable to init bundle: " + bundleEntrypoint.getBundleId(), ex);
        throw ex;
      }
    }
    log.info("Done initialize bundles");
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
  public EntityContextVar var() {
    return entityContextVar;
  }

  public EntityContextWidgetImpl widget() {
    return this.entityContextWidget;
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
      baseEntity.afterFetch(this);
    }
    return baseEntity;
  }

  @Override
  public Optional<AbstractRepository> getRepository(@NotNull String entityID) {
    return entityManager.getRepositoryByEntityID(entityID);
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
    Map<String, Object[]> changeFields = new HashMap<>();
    MethodInterceptor handler = (obj, method, args, proxy) -> {
      String setName = method.getName();
      if (setName.startsWith("set")) {
        Object oldValue;
        try {
          oldValue = cacheService.getFieldValue(entity.getIdentifier(), setName);
          if (oldValue == null) {
            oldValue = MethodUtils.invokeMethod(entity, method.getName().replaceFirst("set", "get"));
          }
        } catch (NoSuchMethodException ex) {
          oldValue = MethodUtils.invokeMethod(entity, method.getName().replaceFirst("set", "is"));
        }
        Object newValue = setName.startsWith("setJsonData") ? args[1] : args[0];
        if (!Objects.equals(oldValue, newValue)) {
          changeFields.put(setName, args);
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

    // fire entityUpdateListeners only if method called not from transaction
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      BaseEntity oldEntity = entityManager.getEntityNoCache(entity.getEntityID());
      runUpdateNotifyListeners(entity, oldEntity, event().getEntityUpdateListeners());
    }

    if (!changeFields.isEmpty()) {
      putToCache(entity, changeFields);
    }
    // fire change event manually
    sendEntityUpdateNotification(entity, ItemAction.Update);
  }

  private void putToCache(HasEntityIdentifier entity, Map<String, Object[]> changeFields) {
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

  private <T extends HasEntityIdentifier> void runUpdateNotifyListeners(@Nullable T updatedEntity, T oldEntity,
      EntityContextEventImpl.EntityListener... entityListeners) {
    if (updatedEntity != null || oldEntity != null) {
      bgp().builder("entity-" + (updatedEntity == null ? oldEntity : updatedEntity).getEntityID() + "-updated")
          .hideOnUI(true).execute(() -> {
            for (EntityContextEventImpl.EntityListener entityListener : entityListeners) {
              entityListener.notify(updatedEntity, oldEntity);
            }
          });
    }
  }

  @Override
  public <T extends BaseEntity> T save(T entity, boolean fireNotifyListeners) {
    AbstractRepository foundRepo = classFinder.getRepositoryByClass(entity.getClass());
    final AbstractRepository repository =
        foundRepo == null && entity instanceof DeviceBaseEntity ? allDeviceRepository : foundRepo;
    EntityContextEventImpl.EntityListener entityUpdateListeners = this.event().getEntityUpdateListeners();

    T oldEntity = entity.getEntityID() == null || !fireNotifyListeners ? null :
        entityUpdateListeners.isRequireFetchOldEntity(entity) ? getEntity(entity.getEntityID(), false) : null;

    T updatedEntity = transactionTemplate.execute(status -> {
      T t = (T) repository.save(entity);
      t.afterFetch(this);
      return t;
    });

    if (fireNotifyListeners) {
      if (oldEntity == null) {
        runUpdateNotifyListeners(updatedEntity, oldEntity, entityUpdateListeners,
            this.event().getEntityCreateListeners());
      } else {
        runUpdateNotifyListeners(updatedEntity, oldEntity, entityUpdateListeners);
      }
    }

    if (StringUtils.isEmpty(entity.getEntityID())) {
      entity.setEntityID(updatedEntity.getEntityID());
      entity.setId(updatedEntity.getId());
    }

    // post save
    cacheService.entityUpdated(entity);

    return updatedEntity;
  }

  @Override
  public <T extends BaseEntity> List<T> findAll(Class<T> clazz) {
    return findAllByRepository((Class<BaseEntity>) clazz);
  }

  @Override
  public <T extends BaseEntity> List<T> findAllByPrefix(String prefix) {
    AbstractRepository<? extends BaseEntity> repository = getRepositoryByPrefix(prefix);
    return findAllByRepository((Class<BaseEntity>) repository.getEntityClass());
  }

  @Override
  public BaseEntity<? extends BaseEntity> delete(String entityId) {
    BaseEntity<? extends BaseEntity> deletedEntity = entityManager.delete(entityId);
    cacheService.clearCache();
    runUpdateNotifyListeners(null, deletedEntity, this.event().getEntityRemoveListeners());
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

  public void addBundle(Map<String, BundleContext> artifactIdContextMap) {
    for (String artifactId : artifactIdContextMap.keySet()) {
      this.addBundle(artifactIdContextMap.get(artifactId), artifactIdContextMap);
    }
    BUNDLE_UPDATE_COUNT++;
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

  private <T extends BaseEntity> List<T> findAllByRepository(Class<BaseEntity> clazz) {
    return entityManager.getEntityIDsByEntityClassFullName(clazz).stream().map(entityID -> {
      T entity = entityManager.getEntityWithFetchLazy(entityID);
      if (entity != null) {
        entity.afterFetch(this);
      }
      return entity;
    }).filter(Objects::nonNull).collect(Collectors.toList());
  }

  private void registerEntityListeners() {
    setting().listenValueAndGet(SystemShowEntityStateSetting.class, "im-show-entity-states",
        value -> this.showEntityState = value);

    SessionFactoryImpl sessionFactory = entityManagerFactory.unwrap(SessionFactoryImpl.class);
    EventListenerRegistry registry = sessionFactory.getServiceRegistry().getService(EventListenerRegistry.class);
    registry.getEventListenerGroup(EventType.PRE_DELETE).appendListener((PreDeleteEventListener) event -> {
      Object entity = event.getEntity();
      if (entity instanceof BaseEntity) {
        BaseEntity baseEntity = (BaseEntity) entity;
        String entityID = baseEntity.getEntityID();
        // remove all status for entity
        TouchHomeUtils.STATUS_MAP.remove(entityID);
        // remove in-memory data if any exists
        InMemoryDB.removeService(entityID);
        // clear all registered console plugins if any exists
        ui().unRegisterConsolePlugin(entityID);
        // destroy any additional services
        if (entity instanceof EntityService) {
          try {
            ((EntityService<?, ?>) entity).destroyService();
          } catch (Exception ex) {
            log.warn("Unable to destroy service for entity: {}", getTitle());
          }
        }
        baseEntity.beforeDelete(EntityContextImpl.this);
      }
      return false;
    });
    registry.getEventListenerGroup(EventType.POST_INSERT).appendListener(new PostInsertEventListenerStandardImpl() {
      @Override
      public void onPostInsert(PostInsertEvent event) {
        super.onPostInsert(event);
        Object entity = event.getEntity();
        postInsertUpdate(entity, true);
        updateCacheEntity(event.getEntity(), ItemAction.Insert);
      }
    });
    registry.getEventListenerGroup(EventType.POST_UPDATE).appendListener(new PostUpdateEventListenerStandardImpl() {
      @Override
      public void onPostUpdate(PostUpdateEvent event) {
        super.onPostUpdate(event);
        Object entity = event.getEntity();
        EventSource eventSource = event.getSession();
        EntityEntry entry = eventSource.getPersistenceContextInternal().getEntry(entity);
        // mimic the preUpdate filter
        if (org.hibernate.engine.spi.Status.DELETED != entry.getStatus()) {
          postInsertUpdate(entity, false);
        }
        updateCacheEntity(event.getEntity(), ItemAction.Update);
      }
    });

    registry.getEventListenerGroup(EventType.POST_DELETE).appendListener(new PostDeleteEventListenerStandardImpl() {
      @Override
      public void onPostDelete(PostDeleteEvent event) {
        super.onPostDelete(event);
        Object entity = event.getEntity();
        if (entity instanceof BaseEntity) {
          ((BaseEntity) entity).afterDelete(EntityContextImpl.this);
        }
        sendEntityUpdateNotification(event.getEntity(), ItemAction.Remove);
        updateCacheEntity(event.getEntity(), ItemAction.Remove);
      }
    });
  }

  private void postInsertUpdate(Object entity, boolean persist) {
    if (entity instanceof BaseEntity) {
      // Try instantiate service associated with entity
      if (entity instanceof EntityService) {
        EntityService.ServiceInstance service = ((EntityService<?, ?>) entity).getOrCreateService(this, false, true);
        // Update entity into service
        if (service != null) {
          service.entityUpdated((EntityService) entity);
        }
      }
      ((BaseEntity) entity).afterUpdate(this, persist);
      // Do not send updates to UI in case of Status.DELETED
      sendEntityUpdateNotification(entity, persist ? ItemAction.Insert : ItemAction.Update);
    }
  }

  private void updateCacheEntity(Object entity, ItemAction type) {
    try {
      if (entity instanceof BaseEntity) {
        this.cacheService.entityUpdated((BaseEntity) entity);
      }
    } catch (Exception ex) {
      log.error("Unable to update cache entity <{}> for entity: <{}>. Msg: <{}>", type, entity,
          CommonUtils.getErrorMessage(ex));
    }
  }

  private Map<Class, Boolean> entityClassToHasUISidebarMenu = new HashMap<>();

  public void sendEntityUpdateNotification(Object entity, ItemAction type) {
    if (!(entity instanceof BaseEntity)) {
      return;
    }
    // send info if item changed and it could be shown on page.
    Boolean hasUISidebarMenu = entityClassToHasUISidebarMenu.computeIfAbsent(entity.getClass(), cursor -> {
      while (!cursor.getSimpleName().equals(BaseEntity.class.getSimpleName())) {
        if (cursor.isAnnotationPresent(UISidebarMenu.class)) {
          return true;
        }
        cursor = cursor.getSuperclass();
      }
      return false;
    });
    if (hasUISidebarMenu) {
      JSONObject metadata = new JSONObject().put("type", type.name).put("value", entity);
      if (type != ItemAction.Remove) {
        if (entity instanceof BaseEntity) {
          // add install dependencies if require
          ItemController.ItemsByTypeResponse.TypeDependency dependency =
              ItemController.getTypeDependency((Class<? extends BaseEntity>) entity.getClass(), this).orElse(null);
          if (dependency != null && !dependency.getDependencies().isEmpty()) {
            metadata.append("requireDependencies", dependency.getDependencies());
          }
        }

        // insert context actions if need
        if (entity instanceof HasDynamicContextMenuActions) {
          UIInputBuilder uiInputBuilder = ui().inputBuilder();
          ((HasDynamicContextMenuActions) entity).assembleActions(uiInputBuilder);
          metadata.put("actions", uiInputBuilder.buildAll());
                /* TODO: if (actions != null && !actions.isEmpty()) {
                    metadata.put("actions", actions.stream().map(UIActionResponse::new).collect(Collectors.toSet()));
                }*/
        }
      }
      ui().sendNotification("-global", metadata);
    }
    if (showEntityState) {
      type.messageEvent.accept(this);
    }
  }

  private void removeBundle(BundleContext bundleContext) {
    if (!bundleContext.isInternal() && bundleContext.isInstalled()) {
      ApplicationContext context = bundleContext.getApplicationContext();
      context.getBean(BundleEntryPoint.class).destroy();
      this.allApplicationContexts.remove(context);

      this.cacheService.clearCache();

      rebuildAllRepositories(bundleContext.getApplicationContext(), false);
      updateBeans(bundleContext, bundleContext.getApplicationContext(), false);
      BUNDLE_UPDATE_COUNT++;
    }
  }

  private void addBundle(BundleContext bundleContext, Map<String, BundleContext> artifactIdToContextMap) {
    if (!bundleContext.isInternal() && !bundleContext.isInstalled()) {
      if (!bundleContext.isLoaded()) {
        ui().addBellErrorNotification("fail-bundle-" + bundleContext.getBundleID(), bundleContext.getBundleFriendlyName(),
            "Unable to load bundle");
        return;
      }
      allApplicationContexts.add(bundleContext.getApplicationContext());
      bundleContext.setInstalled(true);
      for (String bundleDependency : bundleContext.getDependencies()) {
        addBundle(artifactIdToContextMap.get(bundleDependency), artifactIdToContextMap);
      }
      ApplicationContext context = bundleContext.getApplicationContext();

      this.cacheService.clearCache();

      HardwareUtils.copyResources(bundleContext.getBundleClassLoader().getResource("external_files.7z"));

      rebuildAllRepositories(context, true);
      updateBeans(bundleContext, context, true);

      for (BundleEntryPoint bundleEntrypoint : context.getBeansOfType(BundleEntryPoint.class).values()) {
        log.info("Init bundle: <{}>", bundleEntrypoint.getBundleId());
        bundleEntrypoint.init();
        this.bundles.put(bundleEntrypoint.getBundleId(), new InternalBundleContext(bundleEntrypoint, bundleContext));
      }
    }
  }

  @SneakyThrows
  private void updateBeans(BundleContext bundleContext, ApplicationContext context, boolean addBundle) {
    log.info("Starting update all app bundles");
    Lang.clear();
    fetchSettingPlugins(bundleContext, addBundle);

    for (Class<? extends ContextRefreshed> beanUpdateClass : BEAN_CONTEXT_REFRESH) {
      applicationContext.getBean(beanUpdateClass).onContextRefresh();
    }

    if (bundleContext != null) {
      applicationContext.getBean(ExtRequestMappingHandlerMapping.class).updateContextRestControllers(context, addBundle);
    }

    reloadListenDependencyInstallSettings();

    registerUpdatableSettings(context);

    // test all services
    for (EntityService entityService : getEntityServices(EntityService.class)) {
      entityService.getOrCreateService(this, false, true);
    }

    log.info("Finish update all app bundles");
  }

  private void rebuildAllRepositories(ApplicationContext context, boolean addBundle) {
    Map<String, PureRepository> pureRepositoryMap = context.getBeansOfType(PureRepository.class).values().stream()
        .collect(Collectors.toMap(r -> r.getEntityClass().getSimpleName(), r -> r));

    if (addBundle) {
      pureRepositories.putAll(pureRepositoryMap);
      repositories.putAll(context.getBeansOfType(AbstractRepository.class));
    } else {
      pureRepositories.keySet().removeAll(pureRepositoryMap.keySet());
      repositories.keySet().removeAll(context.getBeansOfType(AbstractRepository.class).keySet());
    }
    baseEntityNameToClass = classFinder.getClassesWithParent(BaseEntity.class, null, null).stream()
        .collect(Collectors.toMap(Class::getSimpleName, s -> s));

    rebuildRepositoryByPrefixMap();
  }

  private void registerUpdatableSettings(ApplicationContext context) throws IllegalAccessException {
    for (String name : context.getBeanDefinitionNames()) {
      if (!name.contains(".")) {
        Object bean = context.getBean(name);

        Object proxy = this.getTargetObject(bean);
        for (Field field : FieldUtils.getFieldsWithAnnotation(proxy.getClass(), UpdatableSetting.class)) {
          Class<?> settingClass = field.getDeclaredAnnotation(UpdatableSetting.class).value();
          Class valueType = ((SettingPlugin) CommonUtils.newInstance(settingClass)).getType();
          Object value = entityContextSetting.getObjectValue(settingClass);
          UpdatableValue<Object> updatableValue =
              UpdatableValue.ofNullable(value, proxy.getClass().getSimpleName() + "_" + field.getName(), valueType);
          entityContextSetting.listenObjectValue(settingClass, updatableValue.getName(), updatableValue::update);

          FieldUtils.writeField(field, proxy, updatableValue, true);
        }
      }
    }
  }

  private Object getTargetObject(Object proxy) throws BeansException {
    if (AopUtils.isJdkDynamicProxy(proxy)) {
      try {
        return ((Advised) proxy).getTargetSource().getTarget();
      } catch (Exception e) {
        throw new FatalBeanException("Error getting target of JDK proxy", e);
      }
    }
    return proxy;
  }

  private void reloadListenDependencyInstallSettings() {
    setting().unListenWithPrefix("item-controller-listen-change-");

    // listen when setting value has been changed and fire event that dependency may be installed
    for (DependencyExecutableInstaller installer : getBeansOfType(DependencyExecutableInstaller.class)) {
      setting().listenValue(installer.getDependencyPluginSettingClass(),
          "listen-" + installer.getDependencyPluginSettingClass(), (value) -> {
            event().fireEvent(installer.getName() + "-dependency-installed",
                !installer.isRequireInstallDependencies(this, false));
            getBean(ItemController.class).reloadItemsRelatedToDependency(installer);
          });
      if (installer.getInstallButton() != null) {
        setting().listenValue(installer.getInstallButton(), "listen-install-ui-" + installer.getName(), () -> {
          if (installer.isRequireInstallDependencies(this, false)) {
            bgp().runWithProgress("install-deps-" + installer.getName(), false, progressBar -> {
              installer.installDependency(this, progressBar);
              ui().reloadWindow("Mongo db installed");
            }, null, () -> new RuntimeException("INSTALL_DEPENDENCY_IN_PROGRESS"));
          }
        });
      }
    }
  }

  private void rebuildRepositoryByPrefixMap() {
    repositoriesByPrefix = new HashMap<>();
    for (Class<? extends BaseEntity> baseEntity : baseEntityNameToClass.values()) {
      repositoriesByPrefix.put(CommonUtils.newInstance(baseEntity).getEntityPrefix(), getRepository(baseEntity));
    }
  }

  private void fetchSettingPlugins(BundleContext bundleContext, boolean addBundle) {
    if (bundleContext != null) {
      setting().fetchSettingPlugins(bundleContext.getBasePackage(), classFinder, addBundle);
    }
  }

  private void createTableIndexes() {
    List<Class<? extends BaseEntity>> list = classFinder.getClassesWithParent(BaseEntity.class, null, null).stream()
        .filter(l -> !(WidgetBaseEntity.class.isAssignableFrom(l) || DeviceBaseEntity.class.isAssignableFrom(l)))
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
            em.createNativeQuery(String.format(CREATE_TABLE_INDEX, aClass.getSimpleName(), tableName))
                .executeUpdate();
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
      ui().addBellInfoNotification("version", "app", "version: " + touchHomeProperties.getVersion());
      this.latestVersion = Curl.get(touchHomeProperties.getGitHubUrl(), Map.class).get("tag_name").toString();

      if (!String.valueOf(touchHomeProperties.getVersion()).equals(this.latestVersion)) {
        log.info("Found newest version <{}>. Current version: <{}>", this.latestVersion,
            touchHomeProperties.getVersion());
        String description =
            "Require update app version from " + touchHomeProperties.getVersion() + " to " + this.latestVersion;
        ui().addBellErrorNotification("version", "app", description,
            uiInputBuilder -> uiInputBuilder.addButton("handle-version", "fas fa-registered", UI.Color.PRIMARY_COLOR,
                (entityContext, params) -> ActionResponseModel.showInfo(
                    startupHardwareRepository.updateApp(TouchHomeUtils.getFilesPath()))).setText("Update"));
        this.event().fireEventIfNotSame("app-release", this.latestVersion);
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

  public <T> List<T> getEntityServices(Class<T> serviceClass) {
    return allDeviceRepository.listAll().stream().filter(e -> serviceClass.isAssignableFrom(e.getClass())).map(e -> (T) e)
        .collect(Collectors.toList());
  }

  public BaseEntity<?> copyEntity(BaseEntity entity) {
    entity.copy();
    BaseEntity<?> saved = save(entity, true);
    cacheService.clearCache();
    return saved;
  }

  public void fireAllBroadcastLock(Consumer<BroadcastLockManagerImpl> handler) {
    this.workspaceManager.fireAllBroadcastLock(handler);
  }

  @AllArgsConstructor
  public enum ItemAction {
    Insert("addItem", context -> {
      context.ui().sendInfoMessage("TOASTR.ENTITY_INSERTED");
    }), Update("addItem", context -> {
      context.ui().sendInfoMessage("TOASTR.ENTITY_UPDATED");
    }), Remove("removeItem", context -> {
      context.ui().sendWarningMessage("TOASTR.ENTITY_REMOVED");
    });
    private final String name;
    private final Consumer<EntityContextImpl> messageEvent;
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
        for (WidgetBaseTemplate widgetBaseTemplate : bundleContext.getApplicationContext()
            .getBeansOfType(WidgetBaseTemplate.class).values()) {
          fieldTypes.put(widgetBaseTemplate.getClass().getSimpleName(), widgetBaseTemplate);
        }
      }
    }
  }

  private String getInternalIpAddress() {
    return StringUtils.defaultString(checkUrlAccessible(),
        applicationContext.getBean(NetworkHardwareRepository.class).getIPAddress());
  }

  public String checkUrlAccessible() {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(touchHomeProperties.getCheckConnectivityURL(), 80));
      return socket.getLocalAddress().getHostAddress();
    } catch (Exception ignore) {
    }
    return null;
  }

  public <T> T getEnv(String key, Class<T> classType, T defaultValue) {
    return environment.getProperty(key, classType, defaultValue);
  }
}
