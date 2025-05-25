package org.homio.app.ssh.service;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.homio.api.Context;
import org.homio.api.ContextBGP;
import org.homio.api.ContextBGP.ThreadContext;
import org.homio.api.model.Status;
import org.homio.api.service.EntityService.ServiceInstance;
import org.homio.api.service.ssh.SshProviderService;
import org.homio.api.util.CommonUtils;
import org.homio.app.ssh.SshTmateEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SshTmateService extends ServiceInstance<SshTmateEntity>
    implements SshProviderService<SshTmateEntity> {

  private static final String URL = "wss://lon1.tmate.io/ws/session/%s";
  private SshSession sshSession;
  private ThreadContext<Object> tmateThread;
  private Process process;

  public SshTmateService(Context context, SshTmateEntity entity) {
    super(context, entity, true, "TMATE");
  }

  @Override
  @SneakyThrows
  public SshSession openSshSession(SshTmateEntity entity) {
    if (tmateThread != null) {
      throw new IllegalStateException("Only one tmate session is supported");
    }
    AtomicReference<String> error = new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);
    tmateThread =
        context
            .bgp()
            .builder("tmate-process")
            .execute(
                ctx -> {
                  try {
                    log.info("Open ssh session using tmate provider");
                    ProcessBuilder processBuilder = new ProcessBuilder(List.of("tmate", "-F"));
                    process = processBuilder.start();
                    process.waitFor(10, TimeUnit.SECONDS);
                    InputStream inputStream = process.getInputStream();
                    byte[] array = new byte[inputStream.available()];
                    IOUtils.read(inputStream, array);
                    String[] lines = new String(array, Charset.defaultCharset()).split("\\r?\\n");
                    ctx.writeStreamInfo(array);
                    ctx.attachInputStream(inputStream, process.getErrorStream());
                    String tmateSessionId = TmateSessions.WebSession.find(lines);
                    ctx.rename("tmate-process-" + tmateSessionId);
                    String localSessionId = TmateSessions.LocalSession.find(lines);
                    sshSession = new SshSession(tmateSessionId, URL, entity);
                    sshSession.getMetadata().put("local", localSessionId);
                    countDownLatch.countDown();

                    process.waitFor(
                        context
                            .setting()
                            .getEnv(
                                "tmate-max-timeout",
                                60,
                                true,
                                "Max wait timeout for tmate session(min)"),
                        TimeUnit.MINUTES);
                  } catch (InterruptedException ie) {
                    log.info("Close ssh session using tmate provider");
                  } catch (Exception ex) {
                    error.set(
                        "Error while running tmate: %s".formatted(CommonUtils.getErrorMessage(ex)));
                  } finally {
                    countDownLatch.countDown();
                    process.destroy();
                  }
                  return null;
                });
    // await when session is populated
    countDownLatch.await();
    if (sshSession == null || StringUtils.isEmpty(sshSession.getToken())) {
      throw new RuntimeException(
          StringUtils.defaultIfEmpty(error.get(), "Unable to find wss token from tmate"));
    }
    return sshSession;
  }

  @Override
  @SneakyThrows
  public void execute(
      @NotNull SshProviderService.SshSession<SshTmateEntity> sshSession, @NotNull String command) {
    String local = sshSession.getMetadata().get("local");
    Process process = Runtime.getRuntime().exec(new String[] {"tmate", "-S", local, "-c", command});
    process.waitFor(10, TimeUnit.MINUTES);
    process.destroy();
  }

  @Override
  public void destroy(boolean forRestart, Exception ex) {
    closeSshSession(null);
  }

  @Override
  public void closeSshSession(@Nullable SshSession<SshTmateEntity> sshSession) {
    if (ContextBGP.cancel(tmateThread)) {
      tmateThread = null;
    }
    if (process != null && process.isAlive()) {
      try {
        process.destroy();
      } catch (Exception ignore) {
      }
      process = null;
    }
  }

  public boolean isOpened() {
    return process != null;
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
          return matcher.group().substring(matcher.group().lastIndexOf("/") + 1);
        }
      }
      return null;
    }
  }
}
