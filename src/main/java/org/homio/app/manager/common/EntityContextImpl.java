package org.homio.app.manager.common;

import jakarta.persistence.EntityManagerFactory;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextHardware;
import org.homio.api.EntityContextMedia;
import org.homio.api.EntityContextStorage;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.EntityFieldMetadata;
import org.homio.api.entity.HasJsonData;
import org.homio.api.entity.device.DeviceBaseEntity;
import org.homio.api.entity.storage.BaseFileSystemEntity;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.model.Icon;
import org.homio.api.model.Status;
import org.homio.api.repository.GitHubProject;
import org.homio.api.service.scan.BeansItemsDiscovery;
import org.homio.api.service.scan.ItemDiscoverySupport;
import org.homio.api.service.scan.VideoStreamScanner;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.FlowMap;
import org.homio.api.util.HardwareUtils;
import org.homio.api.util.Lang;
import org.homio.app.LogService;
import org.homio.app.audio.AudioService;
import org.homio.app.auth.JwtTokenProvider;
import org.homio.app.builder.widget.EntityContextWidgetImpl;
import org.homio.app.config.TransactionManagerContext;
import org.homio.app.manager.AddonService;
import org.homio.app.manager.CacheService;
import org.homio.app.manager.LoggerService;
import org.homio.app.manager.PortService;
import org.homio.app.manager.ScriptService;
import org.homio.app.manager.WidgetService;
import org.homio.app.manager.common.impl.EntityContextAddonImpl;
import org.homio.app.manager.common.impl.EntityContextBGPImpl;
import org.homio.app.manager.common.impl.EntityContextEventImpl;
import org.homio.app.manager.common.impl.EntityContextHardwareImpl;
import org.homio.app.manager.common.impl.EntityContextInstallImpl;
import org.homio.app.manager.common.impl.EntityContextMediaImpl;
import org.homio.app.manager.common.impl.EntityContextServiceImpl;
import org.homio.app.manager.common.impl.EntityContextSettingImpl;
import org.homio.app.manager.common.impl.EntityContextStorageImpl;
import org.homio.app.manager.common.impl.EntityContextUIImpl;
import org.homio.app.manager.common.impl.EntityContextVarImpl;
import org.homio.app.manager.common.impl.EntityContextWorkspaceImpl;
import org.homio.app.model.entity.LocalBoardEntity;
import org.homio.app.model.entity.MediaMTXEntity;
import org.homio.app.model.entity.user.UserAdminEntity;
import org.homio.app.model.entity.user.UserBaseEntity;
import org.homio.app.model.entity.widget.WidgetBaseEntity;
import org.homio.app.model.entity.widget.WidgetBaseEntityAndSeries;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.model.entity.widget.attributes.HasPosition;
import org.homio.app.model.entity.widget.impl.WidgetLayoutEntity;
import org.homio.app.repository.AbstractRepository;
import org.homio.app.repository.SettingRepository;
import org.homio.app.repository.VariableBackupRepository;
import org.homio.app.repository.device.AllDeviceRepository;
import org.homio.app.repository.widget.WidgetRepository;
import org.homio.app.repository.widget.WidgetSeriesRepository;
import org.homio.app.rest.ConsoleController;
import org.homio.app.rest.FileSystemController;
import org.homio.app.rest.ItemController;
import org.homio.app.rest.SettingController;
import org.homio.app.service.FileSystemService;
import org.homio.app.service.cloud.CloudService;
import org.homio.app.setting.ScanDevicesSetting;
import org.homio.app.setting.ScanVideoStreamSourcesSetting;
import org.homio.app.setting.system.SystemClearCacheButtonSetting;
import org.homio.app.setting.system.SystemShowEntityStateSetting;
import org.homio.app.setting.system.SystemSoftRestartButtonSetting;
import org.homio.app.spring.ContextCreated;
import org.homio.app.spring.ContextRefreshed;
import org.homio.app.ssh.SshTmateEntity;
import org.homio.app.video.ffmpeg.FfmpegHardwareRepository;
import org.homio.app.workspace.BroadcastLockManagerImpl;
import org.homio.app.workspace.WorkspaceService;
import org.homio.hquery.hardware.network.NetworkHardwareRepository;
import org.homio.hquery.hardware.other.MachineHardwareRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.SpringApplication;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SuppressWarnings("rawtypes")
@Log4j2
@Component
public class EntityContextImpl implements EntityContext {

    private static final Set<Class<? extends ContextCreated>> BEAN_CONTEXT_CREATED = new LinkedHashSet<>();
    private static final Set<Class<? extends ContextRefreshed>> BEAN_CONTEXT_REFRESH = new LinkedHashSet<>();
    private static final long START_TIME = System.currentTimeMillis();
    private static AllDeviceRepository allDeviceRepository;
    public static Map<String, Class<? extends EntityFieldMetadata>> uiFieldClasses;
    public static Map<String, AbstractRepository> repositoriesByPrefix;

    static {
        BEAN_CONTEXT_CREATED.add(AddonService.class);

        BEAN_CONTEXT_CREATED.add(LogService.class);
        BEAN_CONTEXT_CREATED.add(FileSystemService.class);
        BEAN_CONTEXT_CREATED.add(ItemController.class);
        BEAN_CONTEXT_CREATED.add(PortService.class);
        BEAN_CONTEXT_CREATED.add(LoggerService.class);
        BEAN_CONTEXT_CREATED.add(WidgetService.class);
        BEAN_CONTEXT_CREATED.add(ScriptService.class);
        BEAN_CONTEXT_CREATED.add(JwtTokenProvider.class);
        BEAN_CONTEXT_CREATED.add(CloudService.class);

        BEAN_CONTEXT_REFRESH.add(FileSystemService.class);
        BEAN_CONTEXT_REFRESH.add(AddonService.class);
        BEAN_CONTEXT_REFRESH.add(ConsoleController.class);
        BEAN_CONTEXT_REFRESH.add(SettingRepository.class);
        BEAN_CONTEXT_REFRESH.add(SettingController.class);
        BEAN_CONTEXT_REFRESH.add(WorkspaceService.class);
        BEAN_CONTEXT_REFRESH.add(ItemController.class);
        BEAN_CONTEXT_REFRESH.add(PortService.class);
        BEAN_CONTEXT_REFRESH.add(AudioService.class);
    }

    private final GitHubProject appGitHub = GitHubProject.of("homiodev", "homio-app", CommonUtils.getInstallPath().resolve("homio"))
            .setInstalledVersionResolver((entityContext, gitHubProject) -> setting().getApplicationVersion());
    private final EntityContextUIImpl entityContextUI;
    private final EntityContextInstallImpl entityContextInstall;
    private final EntityContextEventImpl entityContextEvent;
    private final EntityContextBGPImpl entityContextBGP;
    private final EntityContextSettingImpl entityContextSetting;
    private final EntityContextVarImpl entityContextVar;
    private final EntityContextHardwareImpl entityContextHardware;
    private final EntityContextWidgetImpl entityContextWidget;
    private final EntityContextMediaImpl entityContextMedia;
    private final EntityContextServiceImpl entityContextService;
    private final EntityContextWorkspaceImpl entityContextWorkspace;
    private final EntityContextStorageImpl entityContextStorage;
    @Getter
    private final EntityContextAddonImpl addon;
    @Getter
    private final EntityContextStorageImpl entityContextStorageImpl;
    private final ClassFinder classFinder;
    @Getter
    private final CacheService cacheService;
    @Getter
    private final Set<ApplicationContext> allApplicationContexts = new HashSet<>();
    private final EntityManager entityManager;
    private final WidgetRepository widgetRepository;
    private final WidgetSeriesRepository widgetSeriesRepository;
    private boolean showEntityState;
    private ApplicationContext applicationContext;
    private WorkspaceService workspaceService;
    private final TransactionManagerContext transactionManagerContext;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public EntityContextImpl(
            TransactionManagerContext transactionManagerContext,
            List<AbstractRepository> repositories,
            AllDeviceRepository allDeviceRepository,
            WidgetRepository widgetRepository,
            WidgetSeriesRepository widgetSeriesRepository,
            EntityManager entityManager,
            ClassFinder classFinder,
            CacheService cacheService,
            ThreadPoolTaskScheduler taskScheduler,
            SimpMessagingTemplate messagingTemplate,
            ConfigurableEnvironment environment,
            VariableBackupRepository variableBackupRepository,
            EntityManagerFactory entityManagerFactory,
            MachineHardwareRepository mhr,
            FfmpegHardwareRepository ffmpegHardwareRepository) {
        this.classFinder = classFinder;
        this.cacheService = cacheService;
        this.entityManager = entityManager;
        this.widgetRepository = widgetRepository;
        this.widgetSeriesRepository = widgetSeriesRepository;
        this.transactionManagerContext = transactionManagerContext;

        EntityContextImpl.allDeviceRepository = allDeviceRepository;
        EntityContextImpl.repositoriesByPrefix =
                repositories.stream()
                        .filter(r -> !r.getClass().equals(AllDeviceRepository.class))
                        .collect(Collectors.toMap(AbstractRepository::getPrefix, r -> r));

        this.entityContextHardware = new EntityContextHardwareImpl(this, mhr);
        this.entityContextSetting = new EntityContextSettingImpl(this, environment, classFinder);
        this.entityContextUI = new EntityContextUIImpl(this, messagingTemplate);
        this.entityContextBGP = new EntityContextBGPImpl(this, taskScheduler);
        this.entityContextEvent = new EntityContextEventImpl(this, entityManagerFactory);
        this.entityContextInstall = new EntityContextInstallImpl(this);
        this.entityContextWidget = new EntityContextWidgetImpl(this);
        this.entityContextStorageImpl = new EntityContextStorageImpl(this);
        this.entityContextVar = new EntityContextVarImpl(this, variableBackupRepository);
        this.entityContextMedia = new EntityContextMediaImpl(this, ffmpegHardwareRepository);
        this.entityContextService = new EntityContextServiceImpl(this);
        this.entityContextWorkspace = new EntityContextWorkspaceImpl(this);
        this.entityContextStorage = new EntityContextStorageImpl(this);
        this.addon = new EntityContextAddonImpl(this, cacheService);

        this.entityContextBGP.builder("flush-delayed-updates").intervalWithDelay(Duration.ofSeconds(30))
                .execute(cacheService::flushDelayedUpdates);
    }

    @SneakyThrows
    public void afterContextStart(ApplicationContext applicationContext) {
        this.addon.setApplicationContext(applicationContext);
        this.allApplicationContexts.add(applicationContext);
        this.applicationContext = applicationContext;
        HardwareUtils.MACHINE_IP_ADDRESS = applicationContext.getBean(NetworkHardwareRepository.class).getIPAddress();

        this.workspaceService = applicationContext.getBean(WorkspaceService.class);

        rebuildRepositoryByPrefixMap();

        entityContextEvent.onContextCreated();

        UserAdminEntity.ensureUserExists(this);
        LocalBoardEntity.ensureDeviceExists(this);
        SshTmateEntity.ensureEntityExists(this);
        MediaMTXEntity.ensureEntityExists(this);

        entityContextSetting.onContextCreated();
        entityContextVar.onContextCreated();
        entityContextUI.onContextCreated();
        entityContextBGP.onContextCreated();
        entityContextMedia.onContextCreated();
        // initialize all addons
        addon.onContextCreated();
        entityContextWorkspace.onContextCreated(workspaceService);

        for (Class<? extends ContextCreated> beanUpdateClass : BEAN_CONTEXT_CREATED) {
            applicationContext.getBean(beanUpdateClass).onContextCreated(this);
        }

        fireRefreshBeans();
        restartEntityServices();

        setting().listenValueAndGet(SystemShowEntityStateSetting.class, "im-show-entity-states", value -> this.showEntityState = value);

        addon.initializeInlineAddons();

        bgp().builder("app-version").interval(Duration.ofDays(1)).delay(Duration.ofSeconds(1))
                .execute(this::updateAppNotificationBlock);
        event().runOnceOnInternetUp("app-version", this::updateAppNotificationBlock);

        event().fireEventIfNotSame("app-status", Status.ONLINE);
        setting().listenValue(SystemClearCacheButtonSetting.class, "im-clear-cache", () -> {
            cacheService.clearCache();
            ui().sendSuccessMessage("Cache has been cleared successfully");
        });
        setting().listenValue(SystemSoftRestartButtonSetting.class, "soft-restart", () -> SystemSoftRestartButtonSetting.restart(this));
        setting().listenValue(ScanDevicesSetting.class, "scan-devices", () ->
                ui().handleResponse(new BeansItemsDiscovery(ItemDiscoverySupport.class).handleAction(this, null)));
        setting().listenValue(ScanVideoStreamSourcesSetting.class, "scan-video-sources", () ->
                ui().handleResponse(new BeansItemsDiscovery(VideoStreamScanner.class).handleAction(this, null)));

        this.entityContextStorageImpl.init();
    }

    @Override
    public @NotNull EntityContextInstallImpl install() {
        return this.entityContextInstall;
    }

    @Override
    public @NotNull EntityContextWorkspaceImpl workspace() {
        return entityContextWorkspace;
    }

    @Override
    public @NotNull EntityContextServiceImpl service() {
        return entityContextService;
    }

    @Override
    public @NotNull EntityContextUIImpl ui() {
        return entityContextUI;
    }

    @Override
    public @NotNull EntityContextEventImpl event() {
        return this.entityContextEvent;
    }

    @Override
    public @NotNull EntityContextBGPImpl bgp() {
        return entityContextBGP;
    }

    @Override
    public @NotNull EntityContextSettingImpl setting() {
        return entityContextSetting;
    }

    @Override
    public @NotNull EntityContextVarImpl var() {
        return entityContextVar;
    }

    @Override
    public @NotNull EntityContextHardware hardware() {
        return entityContextHardware;
    }

    @Override
    public @NotNull EntityContextStorage storage() {
        return entityContextStorage;
    }

    @Override
    public @NotNull EntityContextMedia media() {
        return entityContextMedia;
    }

    public @NotNull EntityContextWidgetImpl widget() {
        return this.entityContextWidget;
    }

    @Override
    public <T extends BaseEntity> T getEntity(@NotNull String entityID, boolean useCache) {
        T baseEntity = useCache ? entityManager.getEntityWithFetchLazy(entityID) : entityManager.getEntityNoCache(entityID);
        if (baseEntity == null) {
            baseEntity = entityManager.getEntityNoCache(entityID);
            if (baseEntity != null) {
                cacheService.clearCache();
            }
        }

        if (baseEntity != null) {
            cacheService.merge(baseEntity);
        }
        return baseEntity;
    }

    public static AbstractRepository getRepository(@NotNull String entityIdOrPrefix) {
        for (AbstractRepository repository : EntityContextImpl.repositoriesByPrefix.values()) {
            if (repository.isMatch(entityIdOrPrefix)) {
                return repository;
            }
        }
        return allDeviceRepository;
    }

    @Override
    public <T extends BaseEntity> void createDelayed(@NotNull T entity) {
        putToCache(entity, null);
    }

    @Override
    public <T extends BaseEntity> void updateDelayed(T entity, Consumer<T> consumer) {
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

            // fire change event manually
            sendEntityUpdateNotification(entity, ItemAction.Update);
        }
    }

    @Override
    public <T extends BaseEntity> @NotNull T save(@NotNull T entity, boolean fireNotifyListeners) {
        AbstractRepository repository = getRepository(entity.getEntityPrefix());
        EntityContextEventImpl.EntityListener entityUpdateListeners = this.event().getEntityUpdateListeners();

        String entityID = entity.getEntityID();
        entity.setEntityContext(this);
        if (entityID == null) {
            if (StringUtils.isEmpty(entity.getName())) {
                entity.setName(entity.refreshName());
            }
            entity.beforePersist();
        } else {
            entity.beforeUpdate();
        }
        entity.validate();
        T oldEntity = entityID == null ? null : getEntity(entityID, false);

        T updatedEntity = transactionManagerContext.executeInTransaction(entityManager -> {
            T t = (T) repository.save(entity);
            return t;
        });

        if (fireNotifyListeners) {
            if (oldEntity == null) {
                runUpdateNotifyListeners(updatedEntity, null, entityUpdateListeners, this.event().getEntityCreateListeners());
            } else {
                runUpdateNotifyListeners(updatedEntity, oldEntity, entityUpdateListeners);
            }
        }

        if (StringUtils.isEmpty(entity.getEntityID())) {
            entity.setEntityID(updatedEntity.getEntityID());
        }

        // post save
        cacheService.entityUpdated(entity);

        return updatedEntity;
    }

    @Override
    public <T extends BaseEntity> @NotNull List<T> findAll(@NotNull Class<T> clazz) {
        return findAllByRepository((Class<BaseEntity>) clazz);
    }

    @Override
    public <T extends BaseEntity> @NotNull List<T> findAllByPrefix(@NotNull String prefix) {
        AbstractRepository<? extends BaseEntity> repository = getRepositoryByPrefix(prefix);
        return findAllByRepository((Class<BaseEntity>) repository.getEntityClass());
    }

    @Override
    public BaseEntity delete(@NotNull String entityID) {
        AbstractRepository repository = getRepository(entityID);
        BaseEntity deletedEntity = repository.deleteByEntityID(entityID);
        cacheService.clearCache();
        getBean(LogService.class).deleteEntityLogsFile(deletedEntity);
        runUpdateNotifyListeners(null, deletedEntity, this.event().getEntityRemoveListeners());
        return deletedEntity;
    }

    public AbstractRepository<? extends BaseEntity> getRepositoryByPrefix(@NotNull String repositoryPrefix) {
        return repositoriesByPrefix.get(repositoryPrefix);
    }

    @Override
    public <T> @NotNull T getBean(@NotNull String beanName, @NotNull Class<T> clazz) {
        return this.allApplicationContexts.stream()
                .filter(c -> c.containsBean(beanName))
                .map(c -> c.getBean(beanName, clazz))
                .findAny()
                .orElseThrow(() -> new NoSuchBeanDefinitionException(beanName));
    }

    @Override
    public <T> @NotNull T getBean(@NotNull Class<T> clazz) {
        for (ApplicationContext context : allApplicationContexts) {
            try {
                return context.getBean(clazz);
            } catch (Exception ignore) {
            }
        }
        throw new NoSuchBeanDefinitionException(clazz);
    }

    @Override
    public <T> @NotNull Collection<T> getBeansOfType(@NotNull Class<T> clazz) {
        List<T> values = new ArrayList<>();
        for (ApplicationContext context : allApplicationContexts) {
            values.addAll(context.getBeansOfType(clazz).values());
        }
        return values;
    }

    @Override
    public <T> @NotNull Map<String, T> getBeansOfTypeWithBeanName(@NotNull Class<T> clazz) {
        Map<String, T> values = new HashMap<>();
        for (ApplicationContext context : allApplicationContexts) {
            values.putAll(context.getBeansOfType(clazz));
        }
        return values;
    }

    public <T> @NotNull Map<String, Collection<T>> getBeansOfTypeByAddons(@NotNull Class<T> clazz) {
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
    public <T> @NotNull List<Class<? extends T>> getClassesWithAnnotation(
            @NotNull Class<? extends Annotation> annotation) {
        return classFinder.getClassesWithAnnotation(annotation);
    }

    @Override
    public <T> @NotNull List<Class<? extends T>> getClassesWithParent(@NotNull Class<T> baseClass) {
        return classFinder.getClassesWithParent(baseClass);
    }

    public void sendEntityUpdateNotification(Object entity, ItemAction type) {
        if (!(entity instanceof BaseEntity)) {
            return;
        }
        if (type == ItemAction.Remove) {
            ui().removeItem((BaseEntity) entity);
        } else {
            ui().updateItem((BaseEntity) entity);
        }
        if (showEntityState) {
            type.messageEvent.accept(this);
        }
    }

    public List<BaseEntity> findAllBaseEntities() {
        return new ArrayList<>(findAll(DeviceBaseEntity.class));
    }

    public <T> List<T> getEntityServices(Class<T> serviceClass) {
        return allDeviceRepository.listAll().stream()
                .filter(e -> serviceClass.isAssignableFrom(e.getClass()))
                .map(e -> (T) e)
                .collect(Collectors.toList());
    }

    public BaseEntity copyEntity(BaseEntity entity) {
        BaseEntity newEntity = copyEntityItem(entity);
        BaseEntity saved = save(newEntity, false);

        // copy children if current entity is layout(it may contain children)
        if (entity instanceof WidgetLayoutEntity) {
            for (BaseEntity baseEntity : findAll(WidgetBaseEntity.class)) {
                if (baseEntity instanceof HasPosition<?> positionEntity) {
                    if (entity.getEntityID().equals(positionEntity.getParent())) {
                        BaseEntity newChildEntity = copyEntityItem(baseEntity);
                        ((HasPosition) newChildEntity).setParent(saved.getEntityID());
                        save(newChildEntity, false);
                    }
                }
            }
        }

        saved = save(saved, true);
        cacheService.clearCache();
        return saved;
    }

    private BaseEntity copyEntityItem(BaseEntity entity) {
        BaseEntity newEntity = buildInitialCopyEntity(entity);
        if (newEntity instanceof WidgetBaseEntity<?> widget) {
            widget.setParent(null);
        }

        // save to assign id
        newEntity = save(newEntity, false);

        if (entity instanceof WidgetBaseEntityAndSeries widgetSeriesEntity) {
            WidgetBaseEntityAndSeries newWidgetSeriesData = (WidgetBaseEntityAndSeries) newEntity;
            Set<WidgetSeriesEntity> entitySeries = widgetSeriesEntity.getSeries();
            Set<WidgetSeriesEntity> series = new HashSet<>();
            for (WidgetSeriesEntity entry : entitySeries) {
                WidgetSeriesEntity seriesEntry = (WidgetSeriesEntity) buildInitialCopyEntity(entry);
                seriesEntry.setPriority(entry.getPriority());
                seriesEntry.setWidgetEntity((WidgetBaseEntityAndSeries) newEntity);
                seriesEntry = save(seriesEntry, false);
                series.add(seriesEntry);
            }
            newWidgetSeriesData.setSeries(series);
        }
        return newEntity;
    }

    @NotNull
    private static BaseEntity buildInitialCopyEntity(BaseEntity entity) {
        BaseEntity newEntity = CommonUtils.newInstance(entity.getClass());
        newEntity.setName(entity.getName());

        if (entity instanceof HasJsonData entityData) {
            HasJsonData newEntityData = (HasJsonData) newEntity;
            for (String key : entityData.getJsonData().keySet()) {
                newEntityData.setJsonData(key, entityData.getJsonData().get(key));
            }
        }

        if (entity instanceof WidgetBaseEntity widgetEntity) {
            WidgetBaseEntity newWidgetData = (WidgetBaseEntity) newEntity;
            newWidgetData.setWidgetTabEntity(widgetEntity.getWidgetTabEntity());
        }

        if (entity instanceof DeviceBaseEntity deviceEntity) {
            DeviceBaseEntity newDeviceData = (DeviceBaseEntity) newEntity;
            newDeviceData.setIeeeAddress(deviceEntity.getIeeeAddress());
            newDeviceData.setImageIdentifier(deviceEntity.getImageIdentifier());
            newDeviceData.setPlace(deviceEntity.getPlace());
        }
        return newEntity;
    }

    public void fireAllBroadcastLock(Consumer<BroadcastLockManagerImpl> handler) {
        this.workspaceService.fireAllBroadcastLock(handler);
    }

    public void rebuildRepositoryByPrefixMap() {
        uiFieldClasses = classFinder.getClassesWithParent(EntityFieldMetadata.class)
                .stream()
                .collect(Collectors.toMap(Class::getSimpleName, s -> s));
    }

    @SneakyThrows
    public void fireRefreshBeans() {
        for (Class<? extends ContextRefreshed> beanUpdateClass : BEAN_CONTEXT_REFRESH) {
            applicationContext.getBean(beanUpdateClass).onContextRefresh(this);
        }
    }

    private void restartEntityServices() {
        log.info("Loading entities and initialize all related services");
        for (BaseEntity baseEntity : findAllBaseEntities()) {
            if (baseEntity instanceof BaseFileSystemEntity) {
                ((BaseFileSystemEntity<?, ?>) baseEntity).getFileSystem(this).restart(false);
            }
        }
    }

    private void updateAppNotificationBlock() {
        ui().addNotificationBlock("app", "App", new Icon("fas fa-house", "#E65100"), builder -> {
            builder.setBorderColor("#FF4400");
            String installedVersion = appGitHub.getInstalledVersion(this);
            builder.setUpdating(appGitHub.isUpdating());
            builder.setVersion(installedVersion);
            String latestVersion = appGitHub.getLastReleaseVersion();
            if (!Objects.equals(installedVersion, latestVersion)) {
                builder.setUpdatable(
                        (progressBar, version) -> appGitHub.updateProject("homio", progressBar, false, projectUpdate -> {
                            Path jarLocation = Paths.get(setting().getEnvRequire("appPath", String.class, CommonUtils.getRootPath().toString(), true));
                            Path archiveAppPath = jarLocation.resolve("homio-app.zip");
                            Files.deleteIfExists(archiveAppPath);
                            projectUpdate.downloadReleaseFile(version, archiveAppPath.getFileName().toString(), archiveAppPath);
                            ui().reloadWindow("Finish update", 60);
                            log.info("Exit app to restart it after update");
                            restartApplication();
                            return null;
                        }, null),
                        appGitHub.getReleasesSince(installedVersion, false));
            }
            builder.fireOnFetch(() -> {
                long runDuration = TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - START_TIME);
                String time = runDuration + "h";
                if (runDuration == 0) {
                    runDuration = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - START_TIME);
                    time = runDuration + "m";
                }
                String serverStartMsg = Lang.getServerMessage("SERVER_STARTED", FlowMap.of("VALUE",
                        new SimpleDateFormat("MM/dd HH:mm").format(new Date(START_TIME)),
                        "TIME", time));
                builder.addInfo("time", new Icon("fas fa-clock"), serverStartMsg);
            });
        });
    }

    /**
     * Fully restart application
     */
    @SneakyThrows
    private void restartApplication() {
        SpringApplication.exit(applicationContext, () -> 4);
        System.exit(4);
        // sleep to allow program exist
        Thread.sleep(30000);
        log.info("Unable to stop app in 30sec. Force stop it");
        // force exit
        Runtime.getRuntime().halt(4);
    }

    private void putToCache(BaseEntity entity, Map<String, Object[]> changeFields) {
        cacheService.putToCache(getRepository(entity.getEntityPrefix()), entity, changeFields);
    }

    private <T extends HasEntityIdentifier> void runUpdateNotifyListeners(@Nullable T updatedEntity, T oldEntity,
                                                                          EntityContextEventImpl.EntityListener... entityListeners) {
        if (updatedEntity != null || oldEntity != null) {
            bgp().builder("entity-" + (updatedEntity == null ? oldEntity : updatedEntity).getEntityID() + "-updated").hideOnUI(true)
                    .execute(() -> {
                        for (EntityContextEventImpl.EntityListener entityListener : entityListeners) {
                            entityListener.notify(updatedEntity, oldEntity);
                        }
                    });
        }
    }

    private <T extends BaseEntity> List<T> findAllByRepository(Class<BaseEntity> clazz) {
        AbstractRepository repository;
        if (Modifier.isAbstract(clazz.getModifiers())) {
            if (DeviceBaseEntity.class.isAssignableFrom(clazz)) {
                repository = allDeviceRepository;
            } else if (WidgetBaseEntity.class.isAssignableFrom(clazz)) {
                repository = widgetRepository;
            } else if (WidgetSeriesEntity.class.isAssignableFrom(clazz)) {
                repository = widgetSeriesRepository;
            } else {
                throw new IllegalStateException("Unable to find repository for class: " + clazz.getSimpleName());
            }

        } else {
            repository = getRepository(CommonUtils.newInstance(clazz).getEntityPrefix());
        }
        if (!repository.isUseCache()) {
            return repository.listAll();
        }
        return entityManager.getEntityIDsByEntityClassFullName(clazz, repository).stream().map(entityID ->
            entityManager.<T>getEntityWithFetchLazy(entityID)).filter(Objects::nonNull).collect(Collectors.toList());
    }

    @AllArgsConstructor
    public enum ItemAction {
        Insert(context -> context.ui().sendInfoMessage("TOASTR.ENTITY_INSERTED")),
        Update(context -> context.ui().sendInfoMessage("TOASTR.ENTITY_UPDATED")),
        Remove(context -> context.ui().sendWarningMessage("TOASTR.ENTITY_REMOVED"));

        private final Consumer<EntityContextImpl> messageEvent;
    }
}
