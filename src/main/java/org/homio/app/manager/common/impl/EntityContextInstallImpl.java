package org.homio.app.manager.common.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import lombok.extern.log4j.Log4j2;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextInstall;
import org.homio.api.service.DependencyExecutableInstaller;
import org.homio.app.manager.install.FfmpegInstaller;
import org.homio.app.manager.install.MosquittoInstaller;
import org.homio.app.manager.install.NodeJsInstaller;
import org.homio.hquery.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
public class EntityContextInstallImpl implements EntityContextInstall {

    private final EntityContext entityContext;
    private final Map<Class<?>, InstallContext> cache = new ConcurrentHashMap<>();

    public EntityContextInstallImpl(EntityContext entityContext) {
        this.entityContext = entityContext;
        entityContext.event().runOnceOnInternetUp("install-services", () ->
            ffmpeg().requireAsync(null, installed -> {
                if (installed) {log.info("FFPMEG service successfully installed");}
            }));
    }

    @Override
    public @NotNull InstallContext nodejs() {
        return createContext(NodeJsInstaller.class);
    }

    @Override
    public @NotNull InstallContext mosquitto() {
        return createContext(MosquittoInstaller.class);
    }

    @Override
    public @NotNull InstallContext ffmpeg() {
        return createContext(FfmpegInstaller.class);
    }

    private <T extends DependencyExecutableInstaller> InstallContext createContext(Class<T> installerClass) {
        return cache.computeIfAbsent(installerClass, aClass ->
            new InstallContext() {
                private final T installer;
                private volatile boolean installing;
                private final List<Consumer<Boolean>> waiters = new ArrayList<>();

                {
                    try {
                        installer = installerClass.getConstructor(EntityContext.class).newInstance(entityContext);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public synchronized void requireAsync(@Nullable String version, Consumer<Boolean> finishHandler) {
                    if (getVersion() != null) {
                        finishHandler.accept(false);
                        return;
                    }
                    waiters.add(finishHandler);
                    if (installing) { // installing - just return. thread will fire finishHandler
                        return;
                    }
                    installing = true;
                    entityContext.bgp().runWithProgress("install-" + installer.getName())
                                 .onFinally(exception -> {
                                     installing = false;
                                     for (Consumer<Boolean> waiter : waiters) {
                                         waiter.accept(true);
                                     }
                                     waiters.clear();
                                 })
                                 .execute(pb -> {
                                     pb.progress(0, "install-" + installer.getName());
                                     installer.installDependency(pb, version);
                                 });
                }

                @Override
                public @NotNull
                synchronized InstallContext requireSync(@NotNull ProgressBar progressBar, @Nullable String version) throws Exception {
                    if (getVersion() == null) {
                        installer.installDependency(progressBar, version);
                    }
                    return this;
                }

                @Override
                public synchronized String getVersion() {
                    return installer.getVersion();
                }

                @Override
                public synchronized @Nullable String getPath(@NotNull String execName) {
                    return installer.getExecutablePath(execName);
                }
        });
    }
}
