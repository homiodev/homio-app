package org.homio.app.ssh;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;

import jakarta.persistence.Entity;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.homio.api.Context;
import org.homio.api.ContextBGP.ThreadContext;
import org.homio.api.ContextHardware;
import org.homio.api.model.Status;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.Lang;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.ssh.SshTmateEntity.SshTmateService;
import org.homio.hquery.Curl;
import org.homio.hquery.ProgressBar;
import org.homio.hquery.hardware.other.MachineHardwareRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
@Entity
@UISidebarChildren(icon = "fas fa-satellite-dish", color = "#0088CC", allowCreateItem = false)
public class SshTmateEntity extends SshBaseEntity<SshTmateEntity, SshTmateService> {

    private static final String URL = "wss://lon1.tmate.io/ws/session/%s";

    public static void ensureEntityExists(ContextImpl context) {
        SshTmateEntity tmate = getOrCreateTmateEntity(context);
        if (SystemUtils.IS_OS_LINUX) {
            ContextHardware hardware = context.hardware();
            if (!hardware.isSoftwareInstalled("tmate")) {
                context.event().runOnceOnInternetUp("install-tmate", () ->
                    context.bgp().runWithProgress("install-tmate", false).executeSync(progressBar ->
                        installTmate(tmate, hardware, progressBar)));
            }
        }
    }

    private static @NotNull SshTmateEntity getOrCreateTmateEntity(ContextImpl context) {
        SshTmateEntity tmate = context.db().getEntity(SshTmateEntity.class, PRIMARY_DEVICE);
        if (tmate == null) {
            SshTmateEntity entity = new SshTmateEntity();
            entity.setEntityID(PRIMARY_DEVICE);
            entity.setName("Tmate");
            tmate = context.db().save(entity);
        }
        return tmate;
    }

    @SneakyThrows
    private static void installTmate(SshTmateEntity tmateEntity, ContextHardware repository, ProgressBar progressBar) {
        tmateEntity.setStatus(Status.UPDATING);
        try {
            repository.installSoftware("tmate", 60, progressBar);
        } catch (Exception ex) {
            log.info("Unable to install tmate. Error: {}", ex.getMessage());
            MachineHardwareRepository hardware = repository.context().getBean(MachineHardwareRepository.class);
            String arm = getTmateArm(hardware);
            if (arm != null) {
                Path rootPath = CommonUtils.getInstallPath();
                String url = "https://github.com/tmate-io/tmate/releases/download/2.4.0/tmate-2.4.0-static-linux-%s.tar.xz".formatted(arm);
                Path target = rootPath.resolve("tmate.tar.xz");
                log.info("Download tmate {} to {}", url, target);
                Curl.downloadWithProgress(url, target, progressBar);
                repository.execute("sudo tar -C %s -xvf %s/tmate.tar.xz".formatted(rootPath, rootPath));
                Files.deleteIfExists(target);
                Path unpackedTmate = rootPath.resolve("tmate-2.4.0-static-linux-%s".formatted(arm));
                Files.createDirectories(Paths.get("ssh"));
                Path tmate = Paths.get("ssh/tmate");
                Files.move(unpackedTmate.resolve("tmate"), tmate, StandardCopyOption.REPLACE_EXISTING);
                FileUtils.deleteDirectory(unpackedTmate.toFile());
                hardware.setPermissions(tmate, 555); // r+w for all
            } else {
                log.error("Unable to find device arm");
            }
        } finally {
            tmateEntity.getOrCreateService(repository.context()).ifPresent(ServiceInstance::testServiceWithSetStatus);
        }
    }

    private static String getTmateArm(MachineHardwareRepository repository) {
        String architecture = repository.getMachineInfo().getArchitecture();
        if (architecture.startsWith("armv6")) {
            return "arm32v6";
        } else if (architecture.startsWith("armv7")) {
            return "arm32v7";
        } else if (architecture.startsWith("i386")) {
            return "i386";
        } else if (architecture.startsWith("armv8") || architecture.startsWith("aarch64")) {
            return "arm64v8";
        } else if (architecture.startsWith("x86_64")) {
            return "amd64";
        }
        return null;
    }

    @Override
    public String getDescriptionImpl() {
        return Lang.getServerMessage("TMATE_DESCRIPTION");
    }

    @Override
    public @Nullable Set<String> getConfigurationErrors() {
        return null;
    }

    @Override
    public long getEntityServiceHashCode() {
        return 0;
    }

    @Override
    public @NotNull Class<SshTmateService> getEntityServiceItemClass() {
        return SshTmateService.class;
    }

    @Override
    public String getDefaultName() {
        return "Tmate SSH";
    }

    @Override
    public @Nullable SshTmateService createService(@NotNull Context context) {
        return new SshTmateService(context, this);
    }

    @Override
    public boolean isDisableDelete() {
        return true;
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "ssh-tmate";
    }

    @Override
    @UIFieldIgnore
    public @Nullable String getImageIdentifier() {
        return super.getImageIdentifier();
    }

    public static class SshTmateService extends ServiceInstance<SshTmateEntity> implements SshProviderService<SshTmateEntity> {

        private SshSession sshSession;
        private ThreadContext<Void> tmateThread;
        private Process process;

        public SshTmateService(Context context, SshTmateEntity entity) {
            super(context, entity, true);
        }

        @Override
        @SneakyThrows
        public SshSession openSshSession(SshTmateEntity entity) {
            if (tmateThread == null) {
                CountDownLatch countDownLatch = new CountDownLatch(1);
                tmateThread = context.bgp().builder("tmate-process").execute(() -> {
                    try {
                        log.info("Open ssh session using tmate provider");
                        process = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "/opt/homio/ssh/tmate -F"});
                        process.waitFor(10, TimeUnit.SECONDS);
                        InputStream inputStream = process.getInputStream();
                        byte[] array = new byte[inputStream.available()];
                        IOUtils.read(inputStream, array);
                        String[] lines = new String(array, Charset.defaultCharset()).split("\\r?\\n");
                        String tmateSessionId = TmateSessions.WebSession.find(lines);
                        sshSession = new SshSession(tmateSessionId, URL, entity);
                        // Curl.get("https://tmate.io/api/t/" + tmateSessionId, SessionStatusModel.class);
                        countDownLatch.countDown();

                        process.waitFor();
                    } catch (InterruptedException ie) {
                        log.info("Close ssh session using tmate provider");
                    } catch (Exception e) {
                        log.error("Error while running tmate");
                    }
                    tmateThread = null;
                });
                // await when session is populated
                countDownLatch.await();
            }
            return sshSession;
        }

        @Override
        protected long getEntityHashCode(SshTmateEntity entity) {
            return 1;
        }

        @Override
        public void destroy(boolean forRestart, Exception ex) {
            closeSshSession(null);
        }

        @Override
        protected void initialize() {
            if (!SystemUtils.IS_OS_LINUX) {
                entity.setStatus(Status.OFFLINE, "Only linux compatible");
                return;
            }
            if (!context.hardware().isSoftwareInstalled("tmate")) {
                throw new IllegalStateException("Tmate not installed");
            }
            entity.setStatusOnline();
        }

        @Override
        public void closeSshSession(SshSession<SshTmateEntity> sshSession) {
            if (tmateThread != null) {
                tmateThread.cancel();
                tmateThread = null;
            }
            if (process != null && process.isAlive()) {
                process.destroy();
            }
        }

        @AllArgsConstructor
        private enum TmateSessions {
            WebSession("web session: https://tmate.io/t/");

            private final String prefix;

            public String find(String[] lines) {
                return Stream.of(lines)
                        .filter(l -> l.startsWith(prefix))
                        .map(l -> l.substring(prefix.length()))
                        .findAny()
                        .orElse(null);
            }
        }
    }
}
