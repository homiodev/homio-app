package org.homio.addon.ibkr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.SystemUtils;
import org.homio.api.Context;
import org.homio.api.ContextBGP;
import org.homio.api.JSDisableMethod;
import org.homio.api.fs.archive.ArchiveUtil;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.model.Status;
import org.homio.api.service.EntityService;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.JsonUtils;
import org.homio.hquery.Curl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;
import static org.homio.api.util.JsonUtils.YAML_OBJECT_MAPPER;

public class IbkrService extends EntityService.ServiceInstance<IbkrEntity>
        implements HasEntityIdentifier {

    private ContextBGP.ProcessContext processContext;
    private static final Path installPath = CommonUtils.getInstallPath().resolve("ibkr_client_portal");
    private static final Path configPath = installPath.resolve("root").resolve("conf.yaml");
    private final LoadingCache<String, JsonNode> fifteenMinQueryCache;

    public IbkrService(@NotNull Context context, @NotNull IbkrEntity ibkrEntity) {
        super(context, ibkrEntity, true, "IBKR");

        this.fifteenMinQueryCache = CacheBuilder.newBuilder().
                expireAfterWrite(15, TimeUnit.MINUTES).build(new CacheLoader<>() {
                    public @NotNull JsonNode load(@NotNull String url) {
                        return Curl.get(entity.getUrl(url), JsonNode.class);
                    }
                });
    }

    @Override
    public void destroy(boolean forRestart, @Nullable Exception ex) throws Exception {
        ContextBGP.cancel(processContext);
    }

    @Override
    public void restartService() {
        super.restartService();
    }

    @Override
    protected void initialize() {
        if (!entity.getMissingMandatoryFields().isEmpty()) {
            return;
        }
        Path execPath = installPath.resolve("bin").resolve("run." + (IS_OS_WINDOWS ? "bat" : "sh"));
        if (Files.exists(execPath)) {
            try {
                runService(execPath);
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
                    runService(execPath);
                },
                ex -> context.ui().toastr().error(ex));
    }

    private void runService(Path execPath) {
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

    public void authenticateUser() {
        entity.setStatus(Status.INITIALIZE);
        context.media().fireSeleniumFirefox("IBKR authenticate", "fas fa-user-lock", "#E63917", driver -> {
            try {
                if (processContext.isStopped()) {
                    return;
                }
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
                    entity.setStatus(Status.REQUIRE_AUTH);
                    log.error("Error authenticating user");
                    ContextBGP.cancel(processContext);
                } else {
                    entity.setStatus(Status.ONLINE);
                    log.info("Successfully authenticated user");
                    if (entity.getAccountId().isEmpty()) {
                        ObjectNode accounts = Curl.get(entity.getUrl("portfolio/account"), ObjectNode.class);
                        if (!accounts.isEmpty()) {
                            JsonNode account = accounts.get(0);
                            entity.setJsonData("aid", account.get("accountId").asText());
                            entity.setJsonData("ccy", account.get("currency").asText());
                            entity.setName(account.get("desc").asText());
                            context.db().save(entity);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error while authenticate to ibkr");
            }
        });
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
        if (entity.getStatus() == Status.ERROR) {
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

    public ArrayNode getBuyOrders() {
        return filterOrders(order -> "BUY".equals(order.get("side").asText()));
    }

    public ArrayNode getSellOrders() {
        return filterOrders(order -> "SELL".equals(order.get("side").asText()));
    }

    public @NotNull ArrayNode filterOrders(Predicate<JsonNode> acceptFn) {
        JsonNode orders = getOrders().get("orders");
        ArrayNode nodes = OBJECT_MAPPER.createArrayNode();
        for (JsonNode order : orders) {
            if (acceptFn.test(order)) {
                nodes.add(order);
            }
        }
        return nodes;
    }

    @SneakyThrows
    public JsonNode getOrders() {
        return fifteenMinQueryCache.get(entity.getUrl("iserver/account/orders"));
    }

    @SneakyThrows
    public ArrayNode getPositions() {
        String baseUrl = entity.getUrl("portfolio/%s/positions/".formatted(entity.getAccountId()));
        ArrayNode allPositions = OBJECT_MAPPER.createArrayNode();
        for (int i = 0; i < 100; i++) {
            JsonNode objectNode = fifteenMinQueryCache.get(baseUrl + i);
            ArrayNode positions = (ArrayNode) objectNode;
            if (positions.isEmpty()) {
                break;
            }
            allPositions.addAll(positions);
        }
        return allPositions;
    }

    @SneakyThrows
    public String getPerformance() {
        String url = entity.getUrl("pa/performance");
        JsonNode response = fifteenMinQueryCache.getIfPresent(url);
        if (response == null) {
            fifteenMinQueryCache.put(url, Curl.post(url,
                    new PerformanceRequest(Set.of(entity.getAccountId())), ObjectNode.class));
            response = fifteenMinQueryCache.get(url);
        }
        return response.get("data").asText();
    }

    @SneakyThrows
    @JSDisableMethod
    public double getSummary(String field) {
        JsonNode response = fifteenMinQueryCache.get(entity.getUrl("portfolio/%s/summary".formatted(entity.getAccountId())));
        return response.get(field).get("amount").asDouble();
    }

    public double getTotalCash() {
        return getSummary("totalcashvalue");
    }

    public double getAvailableFunds() {
        return getSummary("availablefunds");
    }

    @Getter
    @AllArgsConstructor
    private static class PerformanceRequest {
        private final String freq = "D";
        private final Set<String> acctIds;
    }
}
