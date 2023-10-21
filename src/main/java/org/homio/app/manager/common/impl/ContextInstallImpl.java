package org.homio.app.manager.common.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.api.ContextInstall;
import org.homio.api.service.DependencyExecutableInstaller;
import org.homio.app.manager.install.NodeJsInstaller;
import org.homio.hquery.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
public class ContextInstallImpl implements ContextInstall {

    private final Context context;
    private final Map<Class<?>, InstallContext> cache = new ConcurrentHashMap<>();

    @SneakyThrows
    public ContextInstallImpl(Context context) {
        this.context = context;
    }

    @Override
    public @NotNull InstallContext nodejs() {
        return createContext(NodeJsInstaller.class);
    }

    @Override
    public @NotNull InstallContext createInstallContext(Class<? extends DependencyExecutableInstaller> installerClass) {
        return createContext(installerClass);
    }

    private <T extends DependencyExecutableInstaller> InstallContext createContext(Class<T> installerClass) {
        return cache.computeIfAbsent(installerClass, aClass ->
            new InstallContext() {
                private final T installer;
                private volatile boolean installing;
                private final List<BiConsumer<Boolean, Exception>> waiters = new ArrayList<>();

                {
                    try {
                        installer = installerClass.getConstructor(Context.class).newInstance(context);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public synchronized void requireAsync(@Nullable String version, BiConsumer<Boolean, Exception> finishHandler) {
                    if (getVersion() != null) {
                        finishHandler.accept(false, null);
                        return;
                    }
                    waiters.add(finishHandler);
                    if (installing) { // installing - just return. thread will fire finishHandler
                        return;
                    }
                    installing = true;
                    context.event().runOnceOnInternetUp("wait-inet-for-install-" + installer.getName(), () -> installSoftware(version));
                }

                private void installSoftware(@Nullable String version) {
                    context.bgp().runWithProgress("install-" + installer.getName())
                                 .onFinally(exception -> {
                                     installing = false;
                                     for (BiConsumer<Boolean, Exception> waiter : waiters) {
                                         waiter.accept(true, exception);
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
