package org.homio.addon.ibkr;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.SneakyThrows;
import org.apache.commons.lang3.SystemUtils;
import org.homio.api.Context;
import org.homio.api.ContextBGP;
import org.homio.api.exception.ServerException;
import org.homio.api.fs.archive.ArchiveUtil;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.model.Status;
import org.homio.api.service.EntityService;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.JsonUtils;
import org.homio.api.widget.CustomWidgetDataStore;
import org.homio.hquery.Curl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.homio.api.util.JsonUtils.YAML_OBJECT_MAPPER;

public class IbkrService extends EntityService.ServiceInstance<IbkrEntity>
  implements HasEntityIdentifier {

  private static final Path installPath = CommonUtils.getInstallPath().resolve("ibkr_client_portal");
  private static final Path configPath = installPath.resolve("root").resolve("conf.yaml");
  private final Path execPath = installPath.resolve("bin").resolve("run." + (IS_OS_WINDOWS ? "bat" : "sh"));
  private final IbkrApi api = new IbkrApi(entity, this);
  private ContextBGP.ProcessContext processContext;
  private CustomWidgetDataStore widgetDataStore;

  public IbkrService(@NotNull Context context, @NotNull IbkrEntity ibkrEntity) {
    super(context, ibkrEntity, true, "IBKR");
  }

  @Override
  public void destroy(boolean forRestart, @Nullable Exception ex) throws Exception {
    if (api != null) {
      api.destroy(context);
    }
    ContextBGP.cancel(processContext);
  }

  @Override
  protected void initialize() {
    if (!context.media().isWebDriverAvailable()) {
      context.ui().toastr().error("Unable to run IBKR. WebDriver is not available");
      return;
    }
    if (Files.exists(execPath)) {
      try {
        runService();
        return;
      } catch (Exception ex) {
        log.error("Error running IBKR CLI portal", ex);
      }
    }
    Path ibkrZipPath = CommonUtils.getTmpPath().resolve("ibkr.zip");
    context.ui().progress().run("install-ibkr", false, progressBar -> {
        Curl.downloadWithProgress("https://download2.interactivebrokers.com/portal/clientportal.gw.zip",
          ibkrZipPath, progressBar);
        ArchiveUtil.unzipAndMove(progressBar, ibkrZipPath, installPath);
        if (SystemUtils.IS_OS_LINUX) {
          context.hardware().execute("chmod +x " + execPath);
          context.hardware().update();
          context.hardware().installSoftware("chromium-browser", 600, progressBar);
        }
        runService();
      },
      ex -> context.ui().toastr().error(ex));
  }

  private void runService() {
    entity.setStatus(Status.INITIALIZE);
    syncConfiguration();
    Path installPath = execPath.getParent().getParent();
    Path runPath = execPath.subpath(installPath.getNameCount(), execPath.getNameCount());
    ContextBGP.cancel(processContext);
    processContext = context.bgp()
      .processBuilder(entity, log)
      .onStarted(this::authenticateUser)

      .workingDir(installPath)
      .execute("bash", runPath.toString(), "root/conf.yaml");
    entity.setStatus(Status.INITIALIZE);
  }

  private void authenticateUser() {
    entity.setStatus(Status.INITIALIZE);
    log.info("Authenticate to IBKR");
    try {
      context.media().fireSelenium("IBKR authenticate", "fas fa-user-lock", "#E63917", driver -> {
        if (processContext.isStopped()) {
          return;
        }
        log.info("Getting IBKR login page...");
        String loginPage = "http://localhost:" + entity.getPort();
        driver.get(loginPage);

        waitAction(driver, loginPage, 60);

        String currentUrl = driver.getCurrentUrl();
        WebElement usernameInput = driver.findElement(By.id("xyz-field-username"));
        usernameInput.sendKeys(entity.getUser());
        WebElement passwordInput = driver.findElement(By.id("xyz-field-password"));
        passwordInput.sendKeys(entity.getPassword().asString());
        WebElement submitButton = driver.findElement(By.xpath("//button[@type='submit']"));
        submitButton.click();

        if (!waitAction(driver, currentUrl, 120)) {
          throw new ServerException("Error authenticating user", Status.REQUIRE_AUTH);
        } else {
          entity.setStatus(Status.ONLINE);
          api.init(context);
          setDataToUI();
          log.info("Successfully authenticated user");
        }
      });
    } catch (Exception ex) {
      entity.setStatusError(ex);
      context.ui().toastr().error(ex);
      log.error(ex.getMessage());
      ContextBGP.cancel(processContext);
    }
  }

  private boolean waitAction(WebDriver driver, String loginPage, int maxWaitSeconds) throws InterruptedException {
    long count = 0;
    while (driver.getCurrentUrl().equals(loginPage) && count < maxWaitSeconds) {
      TimeUnit.MILLISECONDS.sleep(1000);
      count++;
      if (processContext.isStopped()) {
        throw new RuntimeException();
      }
    }
    return !driver.getCurrentUrl().equals(loginPage);
  }

  @Override
  public String isRequireRestartService() {
    boolean restartStatus = entity.getStatus() == Status.ERROR || entity.getStatus() == Status.REQUIRE_AUTH;
    if (restartStatus && context.media().isWebDriverAvailable()) {
      return "Status: " + entity.getStatus();
    }
    return null;
  }

  @SneakyThrows
  private void syncConfiguration() {
    ObjectNode configuration = YAML_OBJECT_MAPPER.readValue(configPath.toFile(), ObjectNode.class);
    boolean updated = false;

    if (configuration.get("listenPort").asInt() != entity.getPort()) {
      configuration.put("port", entity.getPort());
      updated = true;
    }
    if (configuration.get("listenSsl").asBoolean()) {
      configuration.put("listenSsl", false);
      updated = true;
    }
    if (updated) {
      JsonUtils.saveToFile(configuration, configPath);
    }
  }

  public List<IbkrApi.Order> getBuyOrders() {
    return filterOrders(order -> "BUY".equals(order.getSide()));
  }

  public List<IbkrApi.Order> getSellOrders() {
    return filterOrders(order -> "SELL".equals(order.getSide()));
  }

  private @NotNull List<IbkrApi.Order> filterOrders(Predicate<IbkrApi.Order> acceptFn) {
    List<IbkrApi.Order> result = new ArrayList<>();
    for (IbkrApi.Order order : getOrders()) {
      if (acceptFn.test(order)) {
        result.add(order);
      }
    }
    return result;
  }

  public double getTotalCash() {
    return api.getTotalCash();
  }

  public double getEquityWithLoanValue() {
    return api.getEquityWithLoanValue();
  }

  public double getDayPNL() {
    return api.getNetLiquidation() - api.getPreviousDayEquityWithLoanValue();
  }

  public List<IbkrApi.Position> getPositions() {
    return api.getAllPosition().stream().filter(p -> p.getPosition() > 0).collect(Collectors.toList());
  }

  public List<IbkrApi.Order> getOrders() {
    return api.getAllPosition().stream().flatMap(p -> p.getOrders().values().stream()).toList();
  }

  public void setDataToUI() {
    var store = widgetDataStore;
    if (store != null) {
      store.update(api.getWidgetInfo());
    }
  }

  public void setWidgetDataStore(CustomWidgetDataStore widgetDataStore) {
    this.widgetDataStore = widgetDataStore;
    setDataToUI();
  }
}
