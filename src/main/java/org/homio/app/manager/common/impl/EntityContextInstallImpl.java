package org.homio.app.manager.common.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.log4j.Log4j2;
import org.homio.app.manager.install.FfmpegInstaller;
import org.homio.app.manager.install.MosquittoInstaller;
import org.homio.app.manager.install.NodeJsInstaller;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.EntityContextInstall;
import org.homio.bundle.api.entity.dependency.DependencyExecutableInstaller;
import org.homio.bundle.api.ui.field.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
public class EntityContextInstallImpl implements EntityContextInstall {

    private final EntityContext entityContext;
    private final Map<Class, InstallContext> cache = new ConcurrentHashMap<>();

    public EntityContextInstallImpl(EntityContext entityContext) {
        this.entityContext = entityContext;
        entityContext.event().runOnceOnInternetUp("install-services", () ->
            ffmpeg().requireAsync(null, () ->
                log.info("FFPMEG service successfully installed")));
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
        return cache.computeIfAbsent(installerClass, aClass -> new InstallContext() {
            private final T installer;
            private final ReentrantLock lock = new ReentrantLock();
            private final ReentrantLock rLock = new ReentrantLock();

            {
                try {
                    installer = installerClass.getConstructor(EntityContext.class).newInstance(entityContext);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void requireAsync(@Nullable String version, Runnable finishHandler) {
                if (getVersion() == null) {
                    lock.lock();
                    if (getVersion() == null) {
                        entityContext.bgp().runWithProgress("install-" + installer.getName(), false,
                            pb -> {
                                pb.progress(0, "install-" + installer.getName());
                                installer.installDependency(pb, version);
                                lock.unlock();
                            }, exception -> finishHandler.run());
                    }
                }
            }

            @Override
            public @NotNull InstallContext requireSync(@NotNull ProgressBar progressBar, @Nullable String version) throws Exception {
                if (getVersion() == null) {
                    try {
                        lock.lock();
                        if (getVersion() == null) {
                            installer.installDependency(progressBar, version);
                        }
                    } finally {
                        lock.unlock();
                    }
                }
                return this;
            }

            @Override
            public String getVersion() {
                try {
                    rLock.lock();
                    return installer.getVersion();
                } finally {
                    rLock.unlock();
                }
            }

            @Override
            public @Nullable String getPath(@NotNull String execName) {
                try {
                    rLock.lock();
                    return installer.getExecutablePath(execName);
                } finally {
                    rLock.unlock();
                }
            }
        });
    }
}
