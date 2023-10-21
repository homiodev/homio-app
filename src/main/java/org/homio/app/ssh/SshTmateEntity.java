package org.homio.app.ssh;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;

import jakarta.persistence.Entity;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.homio.api.Context;
import org.homio.api.ContextBGP.ThreadContext;
import org.homio.api.ContextHardware;
import org.homio.api.model.Icon;
import org.homio.api.model.Status;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.action.HasDynamicContextMenuActions;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.util.Lang;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.ssh.SshTmateEntity.SshTmateService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Entity
@UISidebarChildren(icon = "fas fa-satellite-dish", color = "#0088CC", allowCreateItem = false)
public class SshTmateEntity extends SshBaseEntity<SshTmateEntity, SshTmateService> implements HasDynamicContextMenuActions {

    private static final String URL = "wss://lon1.tmate.io/ws/session/%s";
    private static boolean TMATE_INSTALLED = false;

    public static void ensureEntityExists(ContextImpl context) {
        if (context.db().getEntity(SshTmateEntity.class, PRIMARY_DEVICE) == null) {
            SshTmateEntity entity = new SshTmateEntity();
            entity.setEntityID(PRIMARY_DEVICE);
            entity.setName("Tmate");
            context.db().save(entity);
        }
    }

    @Override
    public String getDescriptionImpl() {
        return Lang.getServerMessage("TMATE_DESCRIPTION");
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
    public void assembleActions(UIInputBuilder uiInputBuilder) {
        if (SystemUtils.IS_OS_LINUX) {
            ContextHardware hardware = uiInputBuilder.context().hardware();
            TMATE_INSTALLED = TMATE_INSTALLED || hardware.isSoftwareInstalled("tmate");
            if (!TMATE_INSTALLED) {
                addTmateInstallButton(uiInputBuilder, hardware);
            }
        }
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
    public void beforePersist() {
        super.beforePersist();
    }

    private void addTmateInstallButton(UIInputBuilder uiInputBuilder, ContextHardware hardware) {
        uiInputBuilder.addSelectableButton("install_tmate", new Icon("fab fa-instalod"), (context, params) -> {
            hardware.installSoftware("tmate", 60);
            TMATE_INSTALLED = hardware.isSoftwareInstalled("tmate");
            if (!TMATE_INSTALLED) {
                throw new IllegalStateException("Something went wrong with installing tmate");
            }
            return null;
        });
    }

    @Log4j2
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
            return 0;
        }

        @Override
        public void destroy() {
            closeSshSession(null);
        }

        @Override
        protected void initialize() {
            if (!SystemUtils.IS_OS_LINUX) {
                entity.setStatus(Status.OFFLINE, "Only linux compatible");
                return;
            }
            if (context.hardware().isSoftwareInstalled("tmate")) {
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
