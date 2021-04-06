package org.touchhome.app.service.ssh;

import com.pi4j.system.SystemInfo;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.exception.ServerException;
import org.touchhome.bundle.api.hardware.network.NetworkHardwareRepository;
import org.touchhome.bundle.api.service.SshProviderService;
import org.touchhome.bundle.api.util.Curl;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Log4j2
@Service
@RequiredArgsConstructor
public class TmateSshProvider implements SshProviderService {

    private final NetworkHardwareRepository networkHardwareRepository;
    private final SshHardwareRepository sshHardwareRepository;

    private Thread tmateThread;
    private SshSession sshSession;

    private static SshSession parse(String[] lines) {
        SshSession sshSession = new SshSession();
        sshSession.setToken(TmateSessions.WebSession.find(lines));
        sshSession.setTokenReadOnly(TmateSessions.WebSessionReadOnly.find(lines));
        sshSession.setSsh(TmateSessions.SshSession.find(lines));
        sshSession.setSshReadOnly(TmateSessions.SshSessionReadOnly.find(lines));
        return sshSession;
    }

    @Override
    @SneakyThrows
    public SshSession openSshSession() {
        if (tmateThread == null) {
            checkHardware();
            CountDownLatch countDownLatch = new CountDownLatch(1);
            tmateThread = new Thread(() -> {
                try {
                    log.info("Open ssh session using tmate provider");
                    Process process = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "/opt/touchhome/ssh/tmate -F"});
                    process.waitFor(10, TimeUnit.SECONDS);
                    InputStream inputStream = process.getInputStream();
                    byte[] array = new byte[inputStream.available()];
                    IOUtils.read(inputStream, array);
                    this.sshSession = parse(new String(array, Charset.defaultCharset()).split("\\r?\\n"));
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
            tmateThread.start();
            countDownLatch.await();
        }
        return sshSession;
    }

    @Override
    public void closeSshSession(String token) {
        if (this.sshSession != null && token.equals(this.sshSession.getToken())) {
            tmateThread.interrupt();
        }
    }

    @Override
    public SessionStatusModel getSshStatus(String token) {
        return Curl.get("https://tmate.io/api/t/" + token, SessionStatusModel.class);
    }

    @SneakyThrows
    private void checkHardware() {
        if (!EntityContext.isLinuxEnvironment()) {
            throw new ServerException("Unable to run ssh on non linux environment");
        }
        if (!networkHardwareRepository.isSshGenerated()) {
            networkHardwareRepository.generateSSHKeys();
        }
        if (sshHardwareRepository.getTmateVersion() == null) {
            if ("7".equals(SystemInfo.getCpuArchitecture())) {
                sshHardwareRepository.installTmate("arm32v7");
            }

            String tmateVersion = sshHardwareRepository.getTmateVersion();
            if (tmateVersion == null) {
                throw new ServerException("Unable to find tmate");
            } else {
                log.warn("Tmate installed version: {}" + tmateVersion);
            }
        }
    }

    @AllArgsConstructor
    private enum TmateSessions {
        WebSession("web session: https://tmate.io/t/"),
        SshSession("ssh session: ssh "),
        WebSessionReadOnly("web session read only: https://tmate.io/t/"),
        SshSessionReadOnly("ssh session read only: ssh ");

        private final String prefix;

        public String find(String[] lines) {
            return Stream.of(lines)
                    .filter(l -> l.startsWith(prefix))
                    .map(l -> l.substring(prefix.length()))
                    .findAny().orElse(null);
        }
    }
}
