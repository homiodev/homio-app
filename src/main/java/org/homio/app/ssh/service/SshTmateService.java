package org.homio.app.ssh.service;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.homio.api.Context;
import org.homio.api.ContextBGP.ThreadContext;
import org.homio.api.model.Status;
import org.homio.api.service.EntityService.ServiceInstance;
import org.homio.api.service.ssh.SshProviderService;
import org.homio.app.ssh.SshTmateEntity;
import org.jetbrains.annotations.NotNull;

public class SshTmateService extends ServiceInstance<SshTmateEntity> implements SshProviderService<SshTmateEntity> {

    private static final String URL = "wss://lon1.tmate.io/ws/session/%s";
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
                    process = Runtime.getRuntime().exec(new String[]{"tmate -F"});
                    process.waitFor(10, TimeUnit.SECONDS);
                    InputStream inputStream = process.getInputStream();
                    byte[] array = new byte[inputStream.available()];
                    IOUtils.read(inputStream, array);
                    String[] lines = new String(array, Charset.defaultCharset()).split("\\r?\\n");
                    String tmateSessionId = TmateSessions.WebSession.find(lines);
                    String localSessionId = TmateSessions.LocalSession.find(lines);
                    sshSession = new SshSession(tmateSessionId, URL, entity);
                    sshSession.getMetadata().put("local", localSessionId);
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
        if (StringUtils.isEmpty(sshSession.getToken())) {
            closeSshSession(sshSession);
            throw new RuntimeException("Unable to find wss token from tmate");
        }
        return sshSession;
    }

    @Override
    @SneakyThrows
    public void execute(@NotNull SshProviderService.SshSession<SshTmateEntity> sshSession, @NotNull String command) {
        String local = sshSession.getMetadata().getString("local");
        process = Runtime.getRuntime().exec(new String[]{"tmate", "-S", local, "-c", command});
        process.waitFor(10, TimeUnit.MINUTES);
        process.destroy();
    }

    @Override
    public void destroy(boolean forRestart, Exception ex) {
        closeSshSession(null);
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

    @Override
    protected long getEntityHashCode(SshTmateEntity entity) {
        return 1;
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

    @AllArgsConstructor
    private enum TmateSessions {
        LocalSession(Pattern.compile("/tmp/tmate-\\d+/[a-zA-Z0-9]+")),
        WebSession(Pattern.compile("web session: https://tmate.io/t/([a-zA-Z0-9]+)"));

        private final Pattern pattern;

        public String find(String[] lines) {
            for (String line : lines) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    return matcher.group();
                }
            }
            return null;
        }
    }
}
