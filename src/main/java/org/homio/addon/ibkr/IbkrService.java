package org.homio.addon.ibkr;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.SystemUtils;
import org.homio.api.Context;
import org.homio.api.ContextBGP;
import org.homio.api.fs.archive.ArchiveUtil;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.model.Icon;
import org.homio.api.model.Status;
import org.homio.api.service.EntityService;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.JsonUtils;
import org.homio.hquery.Curl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.homio.api.util.JsonUtils.YAML_OBJECT_MAPPER;

public class IbkrService extends EntityService.ServiceInstance<IbkrEntity>
        implements HasEntityIdentifier {

    private ContextBGP.ProcessContext processContext;
    private static final Path installPath = CommonUtils.getInstallPath().resolve("ibkr_client_portal");
    private static final Path configPath = installPath.resolve("root").resolve("conf.yaml");
    private @Getter String proxyHost;

    public IbkrService(@NotNull Context context, @NotNull IbkrEntity ibkrEntity) {
        super(context, ibkrEntity, true);
    }

    @Override
    public void destroy(boolean forRestart, @Nullable Exception ex) throws Exception {
        ContextBGP.cancel(processContext);
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
        String host = "http://localhost:" + entity.getPort();
        this.proxyHost = context.service().registerUrlProxy(String.valueOf(Math.abs(host.hashCode())), host, builder -> {
        });
    }

    @Override
    public String isRequireRestartService() {
        if (!entity.getStatus().isOnline()) {
            return "Status: " + entity.getStatus();
        }
        return null;
    }

    /*@SneakyThrows
    public void authenticateUser() {
        context.ui().dialog().sendWebDialogRequest("http://localhost:5000");
        System.setProperty("webdriver.chrome.driver", "/usr/lib/chromium-browser/chromedriver");

        ChromeDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.of(10, ChronoUnit.SECONDS));

        try {
            String loginPage = "http://localhost:%d".formatted(entity.getPort());
            driver.get(loginPage);
            Thread.sleep(1000);

            // User is redirected to login page on first visit
            while (driver.getCurrentUrl().equals(loginPage)) {
                Thread.sleep(250); // Adjust as needed
            }

            String currentUrl = driver.getCurrentUrl();
            driver.findElement(By.id("xyz-field-username")).sendKeys(entity.getUser());
            driver.findElement(By.id("xyz-field-password")).sendKeys(entity.getPassword().asString());
            driver.findElement(By.xpath("//button[@type='submit']")).click();

            // Wait until user is redirected to the login confirmation page
            for (int i = 0; i < 30; i++) {
                if (!driver.getCurrentUrl().equals(currentUrl)) {
                    return;
                }
                Thread.sleep(500);
            }
            if (driver.getCurrentUrl().equals(currentUrl)) {
                entity.setStatus(Status.REQUIRE_AUTH);
                context.ui().dialog().sendDialogRequest("ibkr", Lang.getServerMessage("IBKR_INFO"),
                        (responseType, pressedButton, parameters) -> {
                        }, builder -> {
                            List<ActionInputParameter> inputs = new ArrayList<>();
                            inputs.add(ActionInputParameter.message(Lang.getServerMessage("IBKR_AUTH")));
                            builder.submitButton("OK", button -> {
                            }).group("General", inputs);
                        });
                log.warn("Authentication requires using mobile phone");
            }

            for (int i = 0; i < 60; i++) {
                if (!driver.getCurrentUrl().equals(currentUrl)) {
                    return;
                }
                Thread.sleep(1000);
            }
            if (driver.getCurrentUrl().equals(currentUrl)) {
                log.error("IBKR authentication failed");
            } else {
                entity.setStatus(Status.ONLINE);
                MenuBlock.StaticMenuBlock<String> accountMenu = context.getBean(Scratch3IBKRBlocks.class).getAccountMenu();
                ObjectNode nodes = Curl.get(entity.getUrl("portfolio/accounts"), ObjectNode.class);

                if (!nodes.isEmpty()) {
                    JsonNode node = nodes.iterator().next();
                    accountMenu.setDefaultValue(node.get("id").asText());
                    for (int i = 0; i < nodes.size(); i++) {
                        String text = node.get(i).get("id").asText();
                        accountMenu.add(text, text);
                    }
                }
                log.info("IBKR authentication succeed");
            }
        } finally {
            driver.quit();
        }
    }*/

    @Override
    public void updateNotificationBlock() {
        context.ui().notification().addBlockOptional("IBKR", "IBKR", new Icon("fab fa-flickr", "#B55050"));
        context.ui().notification().updateBlock("IBKR", entity);
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
}
