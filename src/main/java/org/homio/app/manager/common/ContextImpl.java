package org.homio.app.manager.common;

import com.pivovarit.function.ThrowingRunnable;
import jakarta.persistence.EntityManagerFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.api.ContextHardware;
import org.homio.api.ContextNetwork;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.CreateSingleEntity;
import org.homio.api.entity.EntityFieldMetadata;
import org.homio.api.entity.HasJsonData;
import org.homio.api.entity.storage.BaseFileSystemEntity;
import org.homio.api.exception.ServerException;
import org.homio.api.model.OptionModel;
import org.homio.api.service.discovery.ItemDiscoverySupport;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.Lang;
import org.homio.app.LogService;
import org.homio.app.auth.JwtTokenProvider;
import org.homio.app.builder.widget.ContextWidgetImpl;
import org.homio.app.config.TransactionManagerContext;
import org.homio.app.manager.*;
import org.homio.app.manager.common.impl.*;
import org.homio.app.model.entity.FFMPEGEntity;
import org.homio.app.model.var.WorkspaceGroup;
import org.homio.app.repository.AbstractRepository;
import org.homio.app.repository.SettingRepository;
import org.homio.app.repository.VariableBackupRepository;
import org.homio.app.repository.device.AllDeviceRepository;
import org.homio.app.repository.device.DeviceSeriesRepository;
import org.homio.app.repository.widget.WidgetRepository;
import org.homio.app.repository.widget.WidgetSeriesRepository;
import org.homio.app.rest.ConsoleController;
import org.homio.app.rest.ItemController;
import org.homio.app.rest.MediaController;
import org.homio.app.rest.SettingController;
import org.homio.app.service.FileSystemService;
import org.homio.app.service.cloud.CloudService;
import org.homio.app.service.scan.BeansItemsDiscovery;
import org.homio.app.setting.ScanDevicesSetting;
import org.homio.app.setting.WorkspaceGroupEntityCompactModeSetting;
import org.homio.app.setting.system.SystemClearCacheButtonSetting;
import org.homio.app.setting.system.SystemLanguageSetting;
import org.homio.app.setting.system.SystemShowEntityStateSetting;
import org.homio.app.setting.system.SystemSoftRestartButtonSetting;
import org.homio.app.setting.system.db.SystemDatabaseSetting;
import org.homio.app.spring.ContextCreated;
import org.homio.app.spring.ContextRefreshed;
import org.homio.app.ssh.SshTmateEntity;
import org.homio.app.utils.HardwareUtils;
import org.homio.app.utils.NotificationUtils;
import org.homio.app.utils.OptionUtil;
import org.homio.app.video.ffmpeg.FfmpegHardwareRepository;
import org.homio.app.workspace.LockManagerImpl;
import org.homio.app.workspace.WorkspaceService;
import org.homio.hquery.hardware.network.NetworkHardwareRepository;
import org.homio.hquery.hardware.other.MachineHardwareRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;

@SuppressWarnings("rawtypes")
@Log4j2
@Component
public class ContextImpl implements Context {

    public static final Map<String, Object> FIELD_FETCH_TYPE = new HashMap<>();
    public static final ThreadLocal<Map<String, String>> REQUEST = new ThreadLocal<>();
    private static final Set<Class<? extends ContextCreated>> BEAN_CONTEXT_CREATED =
            new LinkedHashSet<>();
    private static final Set<Class<? extends ContextRefreshed>> BEAN_CONTEXT_REFRESH =
            new LinkedHashSet<>();
    public static Map<String, Class<? extends EntityFieldMetadata>> uiFieldClasses;
    public static Map<String, AbstractRepository> repositoriesByPrefix;
    public static ContextImpl INSTANCE;
    private static AllDeviceRepository allDeviceRepository;

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
        BEAN_CONTEXT_CREATED.add(ConsoleController.class);
        BEAN_CONTEXT_CREATED.add(MediaController.class);

        BEAN_CONTEXT_REFRESH.add(FileSystemService.class);
        BEAN_CONTEXT_REFRESH.add(AddonService.class);
        BEAN_CONTEXT_REFRESH.add(SettingRepository.class);
        BEAN_CONTEXT_REFRESH.add(SettingController.class);
        BEAN_CONTEXT_REFRESH.add(WorkspaceService.class);
        BEAN_CONTEXT_REFRESH.add(ItemController.class);
        BEAN_CONTEXT_REFRESH.add(PortService.class);
        BEAN_CONTEXT_REFRESH.add(ConsoleController.class);
    }

    private final ContextUIImpl contextUI;
    private final ContextInstallImpl contextInstall;
    private final ContextEventImpl contextEvent;
    private final ContextBGPImpl contextBGP;
    private final ContextSettingImpl contextSetting;
    private final ContextVarImpl contextVar;
    private final ContextHardwareImpl contextHardware;
    private final ContextWidgetImpl contextWidget;
    private final ContextMediaImpl contextMedia;
    private final ContextServiceImpl contextService;
    private final ContextWorkspaceImpl contextWorkspace;
    private final ContextStorageImpl contextStorage;
    private final ContextNetworkImpl contextNetwork;
    private final ContextUserImpl contextUser;
    private final ClassFinder classFinder;
    @Getter
    private final CacheService cacheService;
    @Getter
    private final Set<ApplicationContext> allApplicationContexts = new HashSet<>();
    @Getter
    private final ContextAddonImpl addon;
    private boolean showEntityState;
    @Getter
    private ApplicationContext applicationContext;
    private WorkspaceService workspaceService;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public ContextImpl(
            TransactionManagerContext transactionManagerContext,
            List<AbstractRepository> repositories,
            AllDeviceRepository allDeviceRepository,
            DeviceSeriesRepository deviceSeriesRepository,
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
            NetworkHardwareRepository nhr,
            FfmpegHardwareRepository ffmpegHardwareRepository) {
        this.classFinder = classFinder;
        this.cacheService = cacheService;

        ContextImpl.allDeviceRepository = allDeviceRepository;
        ContextImpl.repositoriesByPrefix =
                repositories.stream()
                        .filter(r -> !r.getClass().equals(AllDeviceRepository.class))
                        .collect(Collectors.toMap(AbstractRepository::getPrefix, r -> r));

        this.contextHardware = new ContextHardwareImpl(this, mhr, nhr);
        this.contextSetting = new ContextSettingImpl(this, environment, classFinder);
        this.contextUI = new ContextUIImpl(this, messagingTemplate);
        this.contextBGP = new ContextBGPImpl(this, taskScheduler);
        this.contextEvent = new ContextEventImpl(this, entityManagerFactory);
        this.contextInstall = new ContextInstallImpl(this);
        this.contextWidget = new ContextWidgetImpl(this);
        this.contextVar = new ContextVarImpl(this, variableBackupRepository);
        var video = new ContextMediaVideoImpl(this, ffmpegHardwareRepository);
        this.contextMedia = new ContextMediaImpl(this, ffmpegHardwareRepository, video);
        this.contextService = new ContextServiceImpl(this);
        this.contextWorkspace = new ContextWorkspaceImpl(this);
        this.contextStorage =
                new ContextStorageImpl(
                        this,
                        transactionManagerContext,
                        allDeviceRepository,
                        deviceSeriesRepository,
                        entityManager,
                        cacheService,
                        widgetRepository,
                        widgetSeriesRepository);
        this.contextUser = new ContextUserImpl(this);
        this.contextNetwork = new ContextNetworkImpl(this, mhr, nhr);
        this.addon = new ContextAddonImpl(this, cacheService);

        this.contextBGP
                .builder("flush-delayed-updates")
                .intervalWithDelay(Duration.ofSeconds(30))
                .execute(cacheService::flushDelayedUpdates);
    }

    public static AbstractRepository getRepository(@NotNull String entityIdOrPrefix) {
        for (AbstractRepository repository : ContextImpl.repositoriesByPrefix.values()) {
            if (repository.isMatch(entityIdOrPrefix)) {
                return repository;
            }
        }
        return allDeviceRepository;
    }

    public static Object getFetchType(String subType) {
        Object pojoInstance = ContextImpl.FIELD_FETCH_TYPE.get(subType);
        if (pojoInstance == null) {
            throw new ServerException("Unable to find fetch type: " + subType);
        }
        return pojoInstance;
    }

    @SneakyThrows
    public void afterContextStart(ApplicationContext applicationContext) {
        this.addon.setApplicationContext(applicationContext);
        this.allApplicationContexts.add(applicationContext);
        this.applicationContext = applicationContext;
        this.workspaceService = applicationContext.getBean(WorkspaceService.class);

        rebuildRepositoryByPrefixMap();
        registerAllFieldSubTypes();

        NotificationUtils.addAppNotifications(this);
        createSingleMandatoryEntities();

        contextEvent.onContextCreated();

        SshTmateEntity.ensureEntityExists(this);
        FFMPEGEntity.ensureEntityExists(this);

        contextSetting.onContextCreated();
        contextVar.onContextCreated();
        contextUI.onContextCreated();
        contextBGP.onContextCreated();
        contextHardware.onContextCreated();
        contextNetwork.onContextCreated();
        contextMedia.onContextCreated();
        // initialize all addons
        addon.onContextCreated();
        contextWorkspace.onContextCreated(workspaceService);

        for (Class<? extends ContextCreated> beanUpdateClass : BEAN_CONTEXT_CREATED) {
            applicationContext.getBean(beanUpdateClass).onContextCreated(this);
        }

        fireRefreshBeans();
        restartEntityServices();

        setting()
                .listenValueAndGet(
                        SystemShowEntityStateSetting.class,
                        "im-show-entity-states",
                        value -> this.showEntityState = value);

        addon.initializeInlineAddons();

        setting()
                .listenValue(
                        SystemClearCacheButtonSetting.class,
                        "im-clear-cache",
                        () -> {
                            cacheService.clearCache();
                            ui().toastr().success("Cache has been cleared successfully");
                        });
        setting()
                .listenValue(
                        WorkspaceGroupEntityCompactModeSetting.class,
                        "workspace-group-compact-mode",
                        (value) -> ui().updateItems(WorkspaceGroup.class));
        setting()
                .listenValue(
                        SystemSoftRestartButtonSetting.class,
                        "soft-restart",
                        () -> SystemSoftRestartButtonSetting.restart(this));
        setting()
                .listenValue(
                        ScanDevicesSetting.class,
                        "scan-devices",
                        () ->
                                ui().handleResponse(
                                        new BeansItemsDiscovery(ItemDiscoverySupport.class)
                                                .handleAction(this, null)));
        setting().listenValue(SystemDatabaseSetting.class, "db", params -> HardwareUtils.fireMigrationWorkflow(params, this));
        INSTANCE = this;
    }

    public void createSingleMandatoryEntities() {
        List<Class<?>> createSingleItems =
                classFinder.getClassesWithAnnotation(CreateSingleEntity.class);
        for (Class<?> createSingleItem : createSingleItems) {
            CreateSingleEntity createSingleEntity =
                    createSingleItem.getDeclaredAnnotation(CreateSingleEntity.class);
            Class<BaseEntity> baseEntityClass = (Class<BaseEntity>) createSingleItem;
            BaseEntity entity = db().get(baseEntityClass, PRIMARY_DEVICE);
            if (entity == null) {
                entity = (BaseEntity) CommonUtils.newInstance(createSingleItem);
                entity.setEntityID(PRIMARY_DEVICE);
                entity.setName(createSingleEntity.name());
                if (entity instanceof HasJsonData jsonData) {
                    jsonData.setJsonData("dis_del", createSingleEntity.disableDelete());
                }
                db().save(entity);
            }
        }
    }

    public void registerAllFieldSubTypes() {
    /*ContextImpl.FIELD_FETCH_TYPE.clear();
    for (WidgetBaseTemplate template : getBeansOfType(WidgetBaseTemplate.class)) {
        ContextImpl.FIELD_FETCH_TYPE.put(template.getName(), template);
    }*/
    }

    @Override
    public @NotNull ContextInstallImpl install() {
        return this.contextInstall;
    }

    @Override
    public @NotNull ContextWorkspaceImpl workspace() {
        return contextWorkspace;
    }

    @Override
    public @NotNull ContextServiceImpl service() {
        return contextService;
    }

    @Override
    public @NotNull ContextUIImpl ui() {
        return contextUI;
    }

    @Override
    public @NotNull ContextEventImpl event() {
        return this.contextEvent;
    }

    @Override
    public @NotNull ContextBGPImpl bgp() {
        return contextBGP;
    }

    @Override
    public @NotNull ContextSettingImpl setting() {
        return contextSetting;
    }

    @Override
    public @NotNull ContextVarImpl var() {
        return contextVar;
    }

    @Override
    public @NotNull ContextHardware hardware() {
        return contextHardware;
    }

    @Override
    public @NotNull FileLogger getFileLogger(@NotNull BaseEntity baseEntity, @NotNull String suffix) {
        return getBean(LogService.class).getFileLogger(baseEntity, suffix);
    }

    @Override
    public @NotNull ContextUserImpl user() {
        return contextUser;
    }

    @Override
    public @NotNull ContextMediaImpl media() {
        return contextMedia;
    }

    @Override
    public @NotNull ContextStorageImpl db() {
        return contextStorage;
    }

    @Override
    public @NotNull ContextNetwork network() {
        return contextNetwork;
    }

    public @NotNull ContextWidgetImpl widget() {
        return this.contextWidget;
    }

    @Override
    public @NotNull List<OptionModel> toOptionModels(
            @Nullable Collection<? extends BaseEntity> entities) {
        return OptionUtil.buildOptions(entities, this);
    }

    public AbstractRepository<? extends BaseEntity> getRepositoryByPrefix(
            @NotNull String repositoryPrefix) {
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

    public <T> List<T> getEntityServices(Class<T> serviceClass) {
        return allDeviceRepository.listAll().stream()
                .filter(e -> serviceClass.isAssignableFrom(e.getClass()))
                .map(e -> (T) e)
                .collect(Collectors.toList());
    }

    public void fireAllLock(Consumer<LockManagerImpl> handler) {
        this.workspaceService.fireAllLock(handler);
    }

    public void rebuildRepositoryByPrefixMap() {
        uiFieldClasses =
                classFinder.getClassesWithParent(EntityFieldMetadata.class).stream()
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
        for (BaseEntity baseEntity : db().findAllBaseEntities()) {
            if (baseEntity instanceof BaseFileSystemEntity) {
                ((BaseFileSystemEntity<?>) baseEntity).getFileSystem(this, 0).restart(false);
            }
        }
    }

    @AllArgsConstructor
    public enum ItemAction {
        Insert(context -> context.ui().toastr().info("TOASTR.ENTITY_INSERTED")),
        Update(context -> context.ui().toastr().info("TOASTR.ENTITY_UPDATED")),
        Remove(context -> context.ui().toastr().warn("TOASTR.ENTITY_REMOVED"));

        private final Consumer<ContextImpl> messageEvent;
    }
}
