package org.homio.app.manager.common;

import jakarta.persistence.EntityManagerFactory;
import java.lang.annotation.Annotation;
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
import org.homio.addon.mqtt.entity.MQTTClientEntity;
import org.homio.addon.telegram.TelegramEntity;
import org.homio.api.Context;
import org.homio.api.ContextHardware;
import org.homio.api.ContextMedia;
import org.homio.api.ContextNetwork;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.EntityFieldMetadata;
import org.homio.api.entity.storage.BaseFileSystemEntity;
import org.homio.api.exception.ServerException;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.model.Status;
import org.homio.api.repository.GitHubProject;
import org.homio.api.service.discovery.ItemDiscoverySupport;
import org.homio.api.service.discovery.VideoStreamScanner;
import org.homio.api.state.StringType;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.FlowMap;
import org.homio.api.util.Lang;
import org.homio.app.LogService;
import org.homio.app.audio.AudioService;
import org.homio.app.auth.JwtTokenProvider;
import org.homio.app.builder.widget.ContextWidgetImpl;
import org.homio.app.config.TransactionManagerContext;
import org.homio.app.manager.AddonService;
import org.homio.app.manager.CacheService;
import org.homio.app.manager.LoggerService;
import org.homio.app.manager.PortService;
import org.homio.app.manager.ScriptService;
import org.homio.app.manager.WidgetService;
import org.homio.app.manager.common.impl.ContextAddonImpl;
import org.homio.app.manager.common.impl.ContextBGPImpl;
import org.homio.app.manager.common.impl.ContextEventImpl;
import org.homio.app.manager.common.impl.ContextHardwareImpl;
import org.homio.app.manager.common.impl.ContextInstallImpl;
import org.homio.app.manager.common.impl.ContextMediaImpl;
import org.homio.app.manager.common.impl.ContextNetworkImpl;
import org.homio.app.manager.common.impl.ContextServiceImpl;
import org.homio.app.manager.common.impl.ContextSettingImpl;
import org.homio.app.manager.common.impl.ContextStorageImpl;
import org.homio.app.manager.common.impl.ContextUIImpl;
import org.homio.app.manager.common.impl.ContextVarImpl;
import org.homio.app.manager.common.impl.ContextWorkspaceImpl;
import org.homio.app.model.entity.FFMPEGEntity;
import org.homio.app.model.entity.Go2RTCEntity;
import org.homio.app.model.entity.LocalBoardEntity;
import org.homio.app.model.entity.MediaMTXEntity;
import org.homio.app.model.entity.user.UserAdminEntity;
import org.homio.app.repository.AbstractRepository;
import org.homio.app.repository.SettingRepository;
import org.homio.app.repository.VariableBackupRepository;
import org.homio.app.repository.device.AllDeviceRepository;
import org.homio.app.repository.widget.WidgetRepository;
import org.homio.app.repository.widget.WidgetSeriesRepository;
import org.homio.app.rest.ConsoleController;
import org.homio.app.rest.ItemController;
import org.homio.app.rest.SettingController;
import org.homio.app.service.FileSystemService;
import org.homio.app.service.cloud.CloudService;
import org.homio.app.service.scan.BeansItemsDiscovery;
import org.homio.app.setting.ScanDevicesSetting;
import org.homio.app.setting.ScanMediaSetting;
import org.homio.app.setting.system.SystemClearCacheButtonSetting;
import org.homio.app.setting.system.SystemShowEntityStateSetting;
import org.homio.app.setting.system.SystemSoftRestartButtonSetting;
import org.homio.app.spring.ContextCreated;
import org.homio.app.spring.ContextRefreshed;
import org.homio.app.ssh.SshTmateEntity;
import org.homio.app.utils.OptionUtil;
import org.homio.app.video.ffmpeg.FfmpegHardwareRepository;
import org.homio.app.workspace.LockManagerImpl;
import org.homio.app.workspace.WorkspaceService;
import org.homio.hquery.hardware.network.NetworkHardwareRepository;
import org.homio.hquery.hardware.other.MachineHardwareRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@SuppressWarnings("rawtypes")
@Log4j2
@Component
public class ContextImpl implements Context {

    public static final Map<String, Object> FIELD_FETCH_TYPE = new HashMap<>();
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
        BEAN_CONTEXT_CREATED.add(ConsoleController.class);

        BEAN_CONTEXT_REFRESH.add(FileSystemService.class);
        BEAN_CONTEXT_REFRESH.add(AddonService.class);
        BEAN_CONTEXT_REFRESH.add(SettingRepository.class);
        BEAN_CONTEXT_REFRESH.add(SettingController.class);
        BEAN_CONTEXT_REFRESH.add(WorkspaceService.class);
        BEAN_CONTEXT_REFRESH.add(ItemController.class);
        BEAN_CONTEXT_REFRESH.add(PortService.class);
        BEAN_CONTEXT_REFRESH.add(AudioService.class);
    }

    public static ContextImpl INSTANCE;
    private final ContextUIImpl contextUI;
    private final ContextInstallImpl contextInstall;
    private final ContextEventImpl contextEvent;
    private final ContextBGPImpl contextBGP;
    private final ContextSettingImpl contextSetting;
    private final GitHubProject appGitHub = GitHubProject.of("homiodev", "homio-app", CommonUtils.getInstallPath().resolve("homio"))
                                                         .setInstalledVersionResolver((context, gitHubProject) -> setting().getApplicationVersion());
    private final ContextVarImpl contextVar;
    private final ContextHardwareImpl contextHardware;
    private final ContextWidgetImpl contextWidget;
    private final ContextMediaImpl contextMedia;
    private final ContextServiceImpl contextService;
    private final ContextWorkspaceImpl contextWorkspace;
    private final ContextStorageImpl contextStorage;
    private final ContextNetworkImpl contextNetwork;
    private final ClassFinder classFinder;
    @Getter
    private final CacheService cacheService;
    @Getter
    private final Set<ApplicationContext> allApplicationContexts = new HashSet<>();
    private boolean showEntityState;
    private ApplicationContext applicationContext;
    private WorkspaceService workspaceService;
    @Getter
    private final ContextAddonImpl addon;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public ContextImpl(
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
        this.contextMedia = new ContextMediaImpl(this, ffmpegHardwareRepository);
        this.contextService = new ContextServiceImpl(this);
        this.contextWorkspace = new ContextWorkspaceImpl(this);
        this.contextStorage = new ContextStorageImpl(this,
            transactionManagerContext,
            allDeviceRepository,
            entityManager,
            cacheService,
            widgetRepository,
            widgetSeriesRepository);
        this.contextNetwork = new ContextNetworkImpl(this, mhr, nhr);
        this.addon = new ContextAddonImpl(this, cacheService);

        this.contextBGP.builder("flush-delayed-updates").intervalWithDelay(Duration.ofSeconds(30))
                       .execute(cacheService::flushDelayedUpdates);
    }

    @SneakyThrows
    public void afterContextStart(ApplicationContext applicationContext) {
        this.addon.setApplicationContext(applicationContext);
        this.allApplicationContexts.add(applicationContext);
        this.applicationContext = applicationContext;

        this.workspaceService = applicationContext.getBean(WorkspaceService.class);

        rebuildRepositoryByPrefixMap();
        registerAllFieldSubTypes();

        contextEvent.onContextCreated();

        UserAdminEntity.ensureUserExists(this);
        MQTTClientEntity.ensureEntityExists(this);
        LocalBoardEntity.ensureDeviceExists(this);
        SshTmateEntity.ensureEntityExists(this);
        MediaMTXEntity.ensureEntityExists(this);
        Go2RTCEntity.ensureEntityExists(this);
        FFMPEGEntity.ensureEntityExists(this);
        TelegramEntity.ensureEntityExists(this);

        contextSetting.onContextCreated();
        contextVar.onContextCreated();
        contextUI.onContextCreated();
        contextBGP.onContextCreated();
        contextHardware.onContextCreated();
        contextNetwork.onContextCreated();
        // initialize all addons
        addon.onContextCreated();
        contextWorkspace.onContextCreated(workspaceService);

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

        event().fireEventIfNotSame("app-status", new StringType(Status.ONLINE.toString()));
        setting().listenValue(SystemClearCacheButtonSetting.class, "im-clear-cache", () -> {
            cacheService.clearCache();
            ui().toastr().success("Cache has been cleared successfully");
        });
        setting().listenValue(SystemSoftRestartButtonSetting.class, "soft-restart", () -> SystemSoftRestartButtonSetting.restart(this));
        setting().listenValue(ScanDevicesSetting.class, "scan-devices", () ->
                ui().handleResponse(new BeansItemsDiscovery(ItemDiscoverySupport.class).handleAction(this, null)));
        setting().listenValue(ScanMediaSetting.class, "scan-video-sources", () ->
                ui().handleResponse(new BeansItemsDiscovery(VideoStreamScanner.class).handleAction(this, null)));
        INSTANCE = this;
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
    public @NotNull ContextMedia media() {
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

    public static AbstractRepository getRepository(@NotNull String entityIdOrPrefix) {
        for (AbstractRepository repository : ContextImpl.repositoriesByPrefix.values()) {
            if (repository.isMatch(entityIdOrPrefix)) {
                return repository;
            }
        }
        return allDeviceRepository;
    }

    @Override
    public @NotNull List<OptionModel> toOptionModels(@Nullable Collection<? extends BaseEntity> entities) {
        return OptionUtil.buildOptions(entities, this);
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

    public static Object getFetchType(String subType) {
        Object pojoInstance = ContextImpl.FIELD_FETCH_TYPE.get(subType);
        if (pojoInstance == null) {
            throw new ServerException("Unable to find fetch type: " + subType);
        }
        return pojoInstance;
    }

    private void restartEntityServices() {
        log.info("Loading entities and initialize all related services");
        for (BaseEntity baseEntity : db().findAllBaseEntities()) {
            if (baseEntity instanceof BaseFileSystemEntity) {
                ((BaseFileSystemEntity<?>) baseEntity).getFileSystem(this, 0).restart(false);
            }
        }
    }

    /**
     * Fully restart application
     */
    @SneakyThrows
    public static void exitApplication(ApplicationContext applicationContext, int code) {
        SpringApplication.exit(applicationContext, () -> code);
        System.exit(code);
        // sleep to allow program exist
        Thread.sleep(30000);
        log.info("Unable to stop app in 30sec. Force stop it");
        // force exit
        Runtime.getRuntime().halt(code);
    }

    private void updateAppNotificationBlock() {
        ui().notification().addBlock("app", "App", new Icon("fas fa-house", "#E65100"), builder -> {
            builder.setBorderColor("#FF4400");
            String installedVersion = appGitHub.getInstalledVersion(this);
            builder.setUpdating(appGitHub.isUpdating());
            builder.setVersion(installedVersion);
            String latestVersion = appGitHub.getLastReleaseVersion();
            if (!Objects.equals(installedVersion, latestVersion)) {
                builder.setUpdatable(
                        (progressBar, version) -> appGitHub.updateProject("homio", progressBar, false, projectUpdate -> {
                            Path jarLocation = Paths.get(setting().getEnvRequire("appPath", String.class, CommonUtils.getRootPath().toString(), true));
                            Path archiveAppPath = jarLocation.resolve("homio-app.jar.gz");
                            Files.deleteIfExists(archiveAppPath);
                            try {
                                projectUpdate.downloadReleaseFile(version, archiveAppPath.getFileName().toString(), archiveAppPath);
                                ui().dialog().reloadWindow("Finish update", 60);
                                log.info("Exit app to restart it after update");
                            } catch (Exception ex) {
                                log.error("Unable to download homio app", ex);
                                Files.deleteIfExists(archiveAppPath);
                                return null;
                            }
                            exitApplication(applicationContext, 221);
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

    @AllArgsConstructor
    public enum ItemAction {
        Insert(context -> context.ui().toastr().info("TOASTR.ENTITY_INSERTED")),
        Update(context -> context.ui().toastr().info("TOASTR.ENTITY_UPDATED")),
        Remove(context -> context.ui().toastr().warn("TOASTR.ENTITY_REMOVED"));

        private final Consumer<ContextImpl> messageEvent;
    }
}
