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
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.homio.app.ssh.SshTmateEntity.SshTmateService;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.EntityContextBGP.ThreadContext;
import org.homio.bundle.api.service.EntityService;
import org.homio.bundle.api.ui.UISidebarChildren;
import org.homio.bundle.api.util.Curl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
@Entity
@SuppressWarnings("unused")
@UISidebarChildren(icon = "fas fa-satellite-dish", color = "#0088CC", allowCreateItem = false)
public class SshTmateEntity extends SshBaseEntity<SshTmateEntity, SshTmateService>
    implements EntityService<SshTmateService, SshTmateEntity> {

    public static final String PREFIX = "sshtmate_";

    /*@Override
    public void configureOptionModel(OptionModel optionModel) {
        String user = getUser();
        optionModel.setDescription((user.isEmpty() ? "" : user + "@") + getHost() + ":" + getPort());
        optionModel.setStatus(this);
    }*/

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
    public @NotNull Class<SshTmateService> getEntityServiceItemClass() {
        return SshTmateService.class;
    }

    @Override
    public @Nullable SshTmateService createService(@NotNull EntityContext entityContext) {
        SshTmateService service = new SshTmateService(entityContext);
        service.entityUpdated(this);
        return service;
    }

    @Log4j2
    @RequiredArgsConstructor
    public static class SshTmateService implements SshProviderService<SshGenericEntity> {

        private final EntityContext entityContext;

        @Getter
        private SshGenericEntity entity;

        private ThreadContext<Void> tmateThread;

        @Override
        public boolean entityUpdated(@NotNull EntityService entity) {
            SshGenericEntity model = (SshGenericEntity) entity;
            /*long code = model.getDeepHashCode();
            boolean requireTestService = this.entity == null || code != snapshotCode;*/
            this.entity = model;
            /*this.snapshotCode = code;
            return requireTestService;*/
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
        public SshSession openSshSession(SshGenericEntity entity) {
            if (tmateThread == null) {
                CountDownLatch countDownLatch = new CountDownLatch(1);
                tmateThread = entityContext.bgp().builder("tmate-process").execute(() -> {
                    try {
                        log.info("Open ssh session using tmate provider");
                        Process process = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "/opt/homio/ssh/tmate -F"});
                        process.waitFor(10, TimeUnit.SECONDS);
                        InputStream inputStream = process.getInputStream();
                        byte[] array = new byte[inputStream.available()];
                        IOUtils.read(inputStream, array);
                        String[] lines = new String(array, Charset.defaultCharset()).split("\\r?\\n");
                        String webSessionURI = TmateSessions.WebSession.find(lines);
                        SshSession sshSession = parse(new String(array, Charset.defaultCharset()).split("\\r?\\n"));
                        Curl.get("https://tmate.io/api/t/" + webSessionURI, SessionStatusModel.class);
                        countDownLatch.countDown();

                        process.waitFor();
                    } catch (InterruptedException ie) {
                        log.info("Close ssh session using tmate provider");
                    } catch (Exception e) {
                        log.error("Error while running tmate");
                    }
                    sshSession = null;
                    tmateThread = null;
                });
                // await when session is populated
                countDownLatch.await();
            }
            return sshSession;
        }

        @Override
        public void closeSshSession(String token, SshGenericEntity entity) {
            sshServerEndpoint.closeSession(token);
        }

        private static SshSession parse(String[] lines) {
            SshSession sshSession = new SshSession();
            sshSession.setToken(TmateSessions.WebSession.find(lines));
            sshSession.setWsURL("WWWWWWWWWWWWWWWWWW");
            return sshSession;
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
