package org.homio.app.manager.common.impl;

import com.pivovarit.function.ThrowingSupplier;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.api.ContextInstall;
import org.homio.api.service.DependencyExecutableInstaller;
import org.homio.api.util.CommonUtils;
import org.homio.app.manager.install.NodeJsInstaller;
import org.homio.app.manager.install.PythonInstaller;
import org.homio.hquery.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;

@Log4j2
public class ContextInstallImpl implements ContextInstall {

  private final Context context;
  private final Map<Class<?>, InstallContext> cache = new ConcurrentHashMap<>();
  private final ReentrantLock pythonLock = new ReentrantLock();

  @SneakyThrows
  public ContextInstallImpl(Context context) {
    this.context = context;
    python().requireAsync(null, (installed, exception) -> {
      if (installed) {
        log.info("Python service successfully installed");
      }
    });
  }

  @SneakyThrows
  public PythonEnv pythonEnv(String venv) {
    return executePython(() -> {
      Path path = CommonUtils.getInstallPath().resolve("python");

      Path venvPath = CommonUtils.getConfigPath().resolve("venv");
      Path newVenvPath = venvPath.resolve(venv);

      if (!Files.exists(newVenvPath)) {
        String command = "python -m venv %s".formatted(newVenvPath);
        if (IS_OS_WINDOWS) {
          command = path.resolve("Scripts").resolve("virtualenv.exe") + " " + newVenvPath;
        }
        context.hardware().execute(command, 600);
      }

      return new PythonEnv() {
        @Override
        public PythonEnv install(String packages) {
          String cmd = "%s -m pip install %s".formatted(newVenvPath.resolve("Scripts").resolve("python"), packages);
          context.hardware().execute(cmd, 600);
          return this;
        }

        @Override
        public Path getPath() {
          return newVenvPath;
        }
      };
    });
  }

  public @NotNull InstallContext python() {
    return createContext(PythonInstaller.class, () -> new PythonInstaller(context, pythonLock));
  }

  @Override
  public @NotNull InstallContext nodejs() {
    return createContext(NodeJsInstaller.class, () -> new NodeJsInstaller(context));
  }

  @Override
  public @NotNull InstallContext createInstallContext(
    @NotNull Class<? extends DependencyExecutableInstaller> installerClass) {
    return createContext(installerClass, () ->
      installerClass.getConstructor(Context.class).newInstance(context));
  }

  private <T extends DependencyExecutableInstaller> InstallContext createContext(
    @NotNull Class<T> installerClass,
    @NotNull ThrowingSupplier<?, Exception> installerClassLoader) {
    return cache.computeIfAbsent(installerClass, aClass ->
      new InstallContext() {
        private final T installer;

        private final List<BiConsumer<Boolean, Exception>> waiters = new ArrayList<>();
        private volatile boolean installing;

        {
          try {
            installer = (T) installerClassLoader.get();
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
          if (System.getProperty("spring.profiles.active").contains("offline")) {
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
        public synchronized @Nullable String getExecutablePath(@NotNull Path execName) {
          return installer.getExecutablePath(execName);
        }
      });
  }

  @SneakyThrows
  private <T> T executePython(ThrowingSupplier<T, Exception> handler) {
    try {
      pythonLock.lock();
      return handler.get();
    } finally {
      pythonLock.unlock();
    }
  }
}
