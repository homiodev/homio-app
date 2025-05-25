package org.homio.app.model.entity;

import com.pivovarit.function.ThrowingConsumer;
import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.persistence.Entity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.SystemUtils;
import org.homio.api.Context;
import org.homio.api.entity.CreateSingleEntity;
import org.homio.api.entity.types.MediaEntity;
import org.homio.api.entity.version.HasFirmwareVersion;
import org.homio.api.exception.ServerException;
import org.homio.api.fs.archive.ArchiveUtil;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Status;
import org.homio.api.service.EntityService;
import org.homio.api.ui.UI;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.util.CommonUtils;
import org.homio.hquery.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

@Log4j2
@Entity
@CreateSingleEntity
@UISidebarChildren(icon = "fab fa-firefox", color = "#E18010", allowCreateItem = false)
public class FirefoxWebDriverEntity extends MediaEntity implements HasFirmwareVersion,
  EntityService<FirefoxWebDriverEntity.FirefoxWebDriverService> {

  private static Thread driverInUseThread;
  private static FirefoxDriver driver;
  private static Timer canceDriverTimer;
  private static FirefoxOptions options = getFirefoxOptions();

  public static boolean isWebDriverAvailable() {
    return FirefoxWebDriverService.version != null;
  }

  public static void executeInWebDriver(@NotNull ThrowingConsumer<WebDriver, Exception> driverHandler, Context context) {
    if (FirefoxWebDriverService.version == null) {
      throw new IllegalStateException("Firefox WebDriver not verified yet");
    }
    executeInDriver(driverHandler, context);
  }

  @SneakyThrows
  private static void executeInDriver(@NotNull ThrowingConsumer<WebDriver, Exception> driverHandler, Context context) {
    if (Files.exists(Paths.get("/opt/homio/installs/geckodriver"))) {
      System.setProperty("webdriver.gecko.driver", "/opt/homio/installs/geckodriver");
    }
    if (context.setting().hasProfile("offline")) {
      throw new IllegalStateException("Unable to run driver in offline mode");
    }

    if (driverInUseThread != null) {
      throw new IllegalStateException("Firefox driver currently in use");
    }
    driverInUseThread = Thread.currentThread();
    boolean closeDriverImmediately = false;

    try {
      cancelTimer();
      if (driver == null) {
        forceKillDriver();
        log.info("Creating Firefox driver...");
        driver = new FirefoxDriver(options);
        log.info("Firefox driver created");
      }
      driverHandler.accept(driver);
    } catch (Exception ex) {
      log.error("Error while connect to firefox driver", ex);
      closeDriverImmediately = (ex instanceof ServerException se && se.getStatus() == Status.ERROR) || ex instanceof WebDriverException;
      throw ex;
    } finally {
      if (driver != null) {
        scheduleCloseTimer(closeDriverImmediately);
      } else {
        // make sure system would not create driver
        forceKillDriver();
      }
      driverInUseThread = null;
    }
  }

  private static void scheduleCloseTimer(boolean closeDriverImmediately) {
    if (closeDriverImmediately) {
      driverInUseThread = null;
      closeDriver();
    } else {
      canceDriverTimer = new Timer();
      canceDriverTimer.schedule(new TimerTask() {
        @Override
        public void run() {
          closeDriver();
        }
      }, TimeUnit.MINUTES.toMillis(3));
    }
  }

  private static void closeDriver() {
    if (driverInUseThread != null) {
      // ignore close driver if someone else started use driver again
      return;
    }
    driverInUseThread = Thread.currentThread();
    try {
      log.info("Closing Firefox driver...");
      // somehow driver.quit() may stuck.
      forceKillDriver();
      driver.quit();
      log.info("Closing Firefox driver successfully.");
      driver = null;
    } catch (Exception ex) {
      log.error("Error while close firefox driver", ex);
    } finally {
      driver = null;
      // make sure driver not running
      driverInUseThread = null;
    }
  }

  private static void forceKillDriver() {
    if (SystemUtils.IS_OS_LINUX) {
      try {
        Runtime.getRuntime().exec("pkill -f firefox");
        Runtime.getRuntime().exec("pkill -f geckodriver");
        Thread.sleep(500); // OS clean resources
      } catch (Exception ignore) {
      }
    }
  }

  private static void cancelTimer() {
    if (canceDriverTimer != null) {
      canceDriverTimer.cancel();
      canceDriverTimer = null;
    }
  }

  private static FirefoxOptions getFirefoxOptions() {
    FirefoxOptions options = new FirefoxOptions();
    options.addArguments("--headless");
    options.addArguments("--disable-gpu");
    options.addArguments("--disable-extensions");
    options.addArguments("--ignore-certificate-errors");
    options.addPreference("plugin.scan.plid.all", false);
    options.addPreference("pdfjs.disabled", true);
    options.addPreference("datareporting.healthreport.uploadEnabled", false);
    options.addPreference("datareporting.policy.dataSubmissionEnabled", false);
    options.addPreference("app.update.auto", false);
    options.addPreference("app.update.enabled", false);
    return options;
  }

  @Override
  public String toString() {
    return "Firefox WebDriver" + getTitle();
  }

  @Override
  public String getDefaultName() {
    return "Firefox WebDriver";
  }

  @Override
  protected @NotNull String getDevicePrefix() {
    return "ffx";
  }

  @Override
  public void assembleActions(UIInputBuilder uiInputBuilder) {

  }

  @Override
  public @Nullable String getFirmwareVersion() {
    return FirefoxWebDriverService.version;
  }

  @Override
  public long getEntityServiceHashCode() {
    return 0;
  }

  @Override
  public @NotNull Class<FirefoxWebDriverService> getEntityServiceItemClass() {
    return FirefoxWebDriverService.class;
  }

  @Nullable
  @Override
  public FirefoxWebDriverService createService(@NotNull Context context) {
    return new FirefoxWebDriverService(context, this);
  }

  @SneakyThrows
  @UIContextMenuAction(value = "RESTART",
    confirmMessage = "W.CONFIRM.RESTART_FIREFOX",
    confirmMessageDialogColor = UI.Color.ERROR_DIALOG,
    icon = "fas fa-power-off",
    iconColor = "#91293E")
  public ActionResponseModel restart() {
    if (!isWebDriverAvailable()) {
      forceKillDriver();
      getService().installDriver();
    }
    return ActionResponseModel.fired();
  }

  public static class FirefoxWebDriverService extends ServiceInstance<FirefoxWebDriverEntity> {
    private static String version;
    private boolean firstInitialized;

    public FirefoxWebDriverService(@NotNull Context context, @NotNull FirefoxWebDriverEntity entity) {
      super(context, entity, false, "Firefox");
    }

    @Override
    public void destroy(boolean forRestart, @Nullable Exception ex) throws Exception {

    }

    @SneakyThrows
    @Override
    protected void initialize() {
      if (!this.firstInitialized) {
        this.firstInitialized = true;
        forceKillDriver();
        installDriver();
        context.bgp().executeOnExit("firefox", () -> {
          forceKillDriver();
        });
      }
    }

    private void installDriver() {
      entity.setStatus(Status.NOT_READY);
      if (!isUnableToConnect()) {
        entity.setStatus(Status.ONLINE);
        version = context().hardware().execute("firefox --version");
        return;
      }
      context().event().runOnceOnInternetUp("install-firefox-driver", () -> {
        entity.setStatus(Status.INITIALIZE);
        context().ui().progress().run("firefox-driver", false, progressBar -> {
          WebDriverManager.firefoxdriver().setup();
          if (isUnableToConnect()) {
            log.error("Error while connect to firefox driver after WebDriverManager install. Trying install firefox-esr");
            try {
              context().hardware().installSoftware("firefox-esr", 600, progressBar);
            } catch (Exception ex) {
              context().hardware().installSoftware("firefox", 600, progressBar);
            }
            if (isUnableToConnect() && !Files.exists(Paths.get("/opt/homio/installs/geckodriver"))) {
              log.error("Error while connect to firefox driver after install firefox-esr. Trying build geckodriver manually");
              // consume huge amount of time
              buildDriverManually(progressBar);

            }
          }
          version = context().hardware().execute("firefox --version");
          entity.setStatus(Status.ONLINE);
        }, ex -> {
          if (ex != null) entity.setStatusError(ex);
        });
      });
    }

    private void buildDriverManually(ProgressBar progressBar) throws IOException {
      context().hardware().installSoftware("gcc-arm-linux-gnueabihf", 1200, progressBar);
      context().hardware().installSoftware("libc6-armhf-cross libc6-dev-armhf-cross", 1200, progressBar);
      Path geckoPath = CommonUtils.getInstallPath().resolve("gecko-dev");
      if (!Files.exists(geckoPath)) {
        Files.createDirectories(geckoPath);
        ArchiveUtil.downloadAndExtract("https://github.com/mozilla/gecko-dev/archive/refs/heads/master.zip",
          "gecko-dev.zip", progressBar, geckoPath);
      }
      Path runFile = geckoPath.resolve("run.sh");
      CommonUtils.writeToFile(runFile, """
        if [[ ! -d "$HOME/.cargo/bin" ]]; then
          curl https://sh.rustup.rs -sSf | bash -s -- -v -y
        fi
        source $HOME/.cargo/env
        rustup target install armv7-unknown-linux-gnueabihf
        rustup update # https://github.com/rust-lang/rustup
        echo -e "[target.armv7-unknown-linux-gnueabihf]
        linker = \\"arm-linux-gnueabihf-gcc\\"" > "$gecko/testing/geckodriver/.cargo/config"
        cd /opt/homio/installs/gecko-dev/testing/geckodriver
        exitstatus=99
        while [[ "$exitstatus" != "0" ]]; do
          CARGO_NET_GIT_FETCH_WITH_CLI=true cargo build --verbose --release --target armv7-unknown-linux-gnueabihf; exitstatus=$?
          cargo clean
          sleep 2
        done
        cp /opt/homio/installs/gecko-dev/target/armv7-unknown-linux-gnueabihf/release/geckodriver /opt/homio/installs/geckodriver
        apt remove -y libc6-armhf-cross libc6-dev-armhf-cross
        apt autoremove -y
        rm -rf /opt/homio/installs/gecko-dev;
        """, false);
      context().hardware().execute("bash " + runFile, 7200, progressBar);
    }

    private boolean isUnableToConnect() {
      AtomicBoolean connected = new AtomicBoolean(false);
      log.info("Verify firefox driver installation");
      executeInDriver(firefoxDriver -> connected.set(true), context);
      return !connected.get();
    }
  }
}
