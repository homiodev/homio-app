package org.homio.app.ssh;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.ssh.SshTmateEntity.SshTmateService;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.EntityContextBGP.ThreadContext;
import org.homio.bundle.api.model.OptionModel;
import org.homio.bundle.api.service.EntityService;
import org.homio.bundle.api.ui.UISidebarChildren;
import org.homio.bundle.api.util.Lang;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
@Entity
@UISidebarChildren(icon = "fas fa-satellite-dish", color = "#0088CC", allowCreateItem = true)
public class SshTmateEntity extends SshBaseEntity<SshTmateEntity, SshTmateService> {

    public static final String PREFIX = "sshtmate_";
    private static final String DEFAULT_TMATE_ENTITY_ID = PREFIX + "primary";

    public static void ensureEntityExists(EntityContextImpl entityContext) {
        if (entityContext.getEntity(DEFAULT_TMATE_ENTITY_ID) == null) {
            SshTmateEntity tmateSshEntity = new SshTmateEntity()
                .setEntityID(DEFAULT_TMATE_ENTITY_ID)
                .setName("tmate internal");
            entityContext.save(tmateSshEntity);
        }
    }

    @Override
    public void configureOptionModel(OptionModel optionModel) {
        optionModel.setStatus(this);
    }

    @Override
    public @NotNull Class<SshTmateService> getEntityServiceItemClass() {
        return SshTmateService.class;
    }

    @Override
    public boolean requireTestServiceInBackground() {
        return true;
    }

    @Override
    public String getDefaultName() {
        return "Tmate SSH";
    }

    @Override
    public @NotNull String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public @Nullable SshTmateService createService(@NotNull EntityContext entityContext) {
        SshTmateService service = new SshTmateService(entityContext);
        service.entityUpdated(this);
        return service;
    }

    @Override
    protected void beforePersist() {
        super.beforePersist();
        setJsonData("description", Lang.getServerMessage("TMATE_DESCRIPTION"));
    }

    @Override
    public boolean isDisableDelete() {
        return true;
    }

    @Log4j2
    @RequiredArgsConstructor
    public static class SshTmateService implements SshProviderService<SshTmateEntity> {

        private final EntityContext entityContext;

        @Getter
        private SshTmateEntity entity;

        private ThreadContext<Void> tmateThread;

        private final SshSession sshSession = new SshSession();
        private Process process;

        @Override
        public boolean entityUpdated(@NotNull EntityService entity) {
            SshTmateEntity model = (SshTmateEntity) entity;
            this.entity = model;
            return false;
        }

        @Override
        public boolean testService() {

            return true;
        }

        @Override
        public void destroy() {

        }

        @Override
        @SneakyThrows
        public SshSession openSshSession(SshTmateEntity entity) {
            if (tmateThread == null) {
                CountDownLatch countDownLatch = new CountDownLatch(1);
                tmateThread = entityContext.bgp().builder("tmate-process").execute(() -> {
                    try {
                        log.info("Open ssh session using tmate provider");
                        process = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "/opt/homio/ssh/tmate -F"});
                        process.waitFor(10, TimeUnit.SECONDS);
                        InputStream inputStream = process.getInputStream();
                        byte[] array = new byte[inputStream.available()];
                        IOUtils.read(inputStream, array);
                        String[] lines = new String(array, Charset.defaultCharset()).split("\\r?\\n");
                        String tmateSessionId = TmateSessions.WebSession.find(lines);
                        sshSession.setToken(tmateSessionId);
                        sshSession.setWsURL("wss://lon1.tmate.io/ws/session/" + tmateSessionId);
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
        public void closeSshSession(String token, SshTmateEntity entity) {
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
