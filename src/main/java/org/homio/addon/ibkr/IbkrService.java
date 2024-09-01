package org.homio.addon.ibkr;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
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

import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.homio.api.util.JsonUtils.YAML_OBJECT_MAPPER;

public class IbkrService extends EntityService.ServiceInstance<IbkrEntity>
        implements HasEntityIdentifier {

    public static String IMAGE = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAMAAABEpIrGAAAAAXNSR0IB2cksfwAAAAlwSFlzAAALEwAACxMBAJqcGAAAAMxQTFRF2BIi////5H2E4k1Z2BQk2RYl/vz9756l797e9MTI3zpH2iIx/vj44UpW7tra5snJ+/b25MTE5mVv6oKK2h4t++fo6dDQ8ayy7ZCY41Rf7NfX4EZT/fr69Ojo5Wlz69TU3Cs66XmC+fLy58zM3jZD3UJO5X+G5MbG2zNA5HmB4EBN5bq82yc1+O/v52t198/S87e8+dze8Ket8+Xl9L3B7pif6HN82hsr5Flk8q606sLE2z5K7sHD9uzs8eHh+t/i8rK36XyF/fP0/O/wTriLqAAAAMhJREFUeJzVkccWgkAMRSeCIE0FC9il2Hvv/f//yciaxJUL3yKbe+dkkgjxi6S/8SbPZbfGC6rGcw8qLC/AguVbgCnHHQVK3JB7BaDAcMkEeBk0158A4NE8KiFX2rI/V/VEIYUcluKA1bRIIap8KswoYS02sWBSQlPosXAnhGDUFz7yW5sQquFxLIYPp0hMke+Vs2d6USnI5lqZhpb8HNOt22H1eslr1LW6GeywmtTBlZOFgf3pEOBB1WTBOHU6NcuSMDvyo/+aNywuDK+t8iEeAAAAAElFTkSuQmCC";

    private final Path execPath = installPath.resolve("bin").resolve("run." + (IS_OS_WINDOWS ? "bat" : "sh"));
    private ContextBGP.ProcessContext processContext;
    private static final Path installPath = CommonUtils.getInstallPath().resolve("ibkr_client_portal");
    private static final Path configPath = installPath.resolve("root").resolve("conf.yaml");
    private @Nullable IbkrApi api;

    public IbkrService(@NotNull Context context, @NotNull IbkrEntity ibkrEntity) {
        super(context, ibkrEntity, true, "IBKR");
    }

    @Override
    public void destroy(boolean forRestart, @Nullable Exception ex) throws Exception {
        if (api != null) {
            api.destroy();
        }
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

    private @NotNull IbkrApi getApi() {
        if (api == null) {
            if (!entity.getStatus().isOnline()) {
                throw new ServerException("IBKR service not online");
            }
            api = new IbkrApi(entity, context);
        }
        return api;
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
            context.media().fireSeleniumFirefox("IBKR authenticate", "fas fa-user-lock", "#E63917", driver -> {
                if (processContext.isStopped()) {
                    return;
                }
                String loginPage = "http://localhost:" + entity.getPort();
                try {
                    driver.get(loginPage);
                } catch (Exception ex) {
                    throw new ServerException("Unable to get ibkr login page: " + loginPage).setStatus(Status.ERROR);
                }

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
                    log.info("Successfully authenticated user");
                }
            });
        } catch (Exception ex) {
            entity.setStatusError(ex);
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

    public List<IbkrApi.Order> getBuyOrders() {
        return filterOrders(order -> "BUY".equals(order.getSide()));
    }

    public List<IbkrApi.Order> getSellOrders() {
        return filterOrders(order -> "SELL".equals(order.getSide()));
    }

    // For widget
    public WidgetInfo getWidgetInfo() {
        List<TickerInfo> tickers = getApi().getAllWidgetInfo().stream().map(TickerInfo::new).toList();
        return new WidgetInfo(tickers,
                getApi().getPerformance(),
                getApi().getTotalCash(),
                getApi().getNetLiquidation(),
                getApi().getGrossPositionValue(),
                getApi().getEquityWithLoanValue(),
                getApi().getPreviousDayEquityWithLoanValue());
    }

    private @NotNull List<IbkrApi.Order> filterOrders(Predicate<IbkrApi.Order> acceptFn) {
        List<IbkrApi.Order> result = new ArrayList<>();
        for (IbkrApi.Order order : getApi().getOrders()) {
            if (acceptFn.test(order)) {
                result.add(order);
            }
        }
        return result;
    }

    public double getTotalCash() {
        return getApi().getTotalCash();
    }

    public double getEquityWithLoanValue() {
        return getApi().getEquityWithLoanValue();
    }

    public List<IbkrApi.Position> getPositions() {
        return getApi().getPositions();
    }

    public List<IbkrApi.Order> getOrders() {
        return getApi().getOrders();
    }

    @Getter
    @RequiredArgsConstructor
    public static class WidgetInfo {
        private final List<TickerInfo> tickers;
        private final ObjectNode performance;
        private final double totalCache;
        private final double netLiquidation;
        private final double grossPositionValue;
        private final double equityWithLoanValue;
        private final double previousDayEquityWithLoanValue;
    }

    @Getter
    public static class TickerInfo {

        private final double position;
        private final double mktPrice;
        private final double mktValue;
        private final double avgPrice;
        private final String ticker;
        private final double unrealizedPnl;
        private final String name;
        private final String group;
        private final List<TickerOrder> orders;
        private final String currency;
        private final Double bidSize;
        private final Double askSize;
        private final Double bidPrice;
        private final Double closePrice;
        private final Double changePrice;
        private final Double changePercent;

        public TickerInfo(IbkrApi.Position position) {
            this.ticker = position.getTicker();
            this.position = position.getPosition();
            this.mktPrice = position.getLastPrice() == null ? position.getMktPrice() : position.getLastPrice();
            this.mktValue = position.getMktValue();
            this.avgPrice = position.getAvgPrice();
            this.currency = position.getCurrency();
            this.unrealizedPnl = position.getUnrealizedPnl();
            this.name = position.getName();
            this.group = position.getGroup();
            this.orders = position.getOrders().values().stream().map(TickerInfo::getOrderInfo).toList();
            this.bidSize = position.getBidSize();
            this.askSize = position.getAskSize();
            this.bidPrice = position.getBidPrice();
            this.closePrice = position.getTodayClosePrice();
            this.changePrice = position.getChangePrice();
            this.changePercent = position.getChangePercent();
        }

        private static TickerOrder getOrderInfo(IbkrApi.Order order) {
            return new TickerOrder(order.getSide().equals("SELL") ? "Sell" : "Buy",
                    order.getTotalSize(),
                    order.getOrderType(),
                    order.getPrice(),
                    order.isOutsideRTH());
        }

        @Getter
        @RequiredArgsConstructor
        private static class TickerOrder {
            private final String side;
            private final double size;
            private final String type;
            private final String price;
            private final boolean outsideRTH;
        }
    }
}
