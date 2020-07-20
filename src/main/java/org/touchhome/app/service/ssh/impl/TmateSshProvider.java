package org.touchhome.app.service.ssh.impl;

import com.pi4j.system.SystemInfo;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.touchhome.app.rest.ConsoleController;
import org.touchhome.app.service.ssh.SshHardwareRepository;
import org.touchhome.app.service.ssh.SshProvider;
import org.touchhome.bundle.api.hardware.wifi.WirelessHardwareRepository;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Log4j2
@Component
@RequiredArgsConstructor
public class TmateSshProvider implements SshProvider {

    private final WirelessHardwareRepository wirelessHardwareRepository;
    private final SshHardwareRepository sshHardwareRepository;
    private final RestTemplate restTemplate = new RestTemplate();

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
    public ConsoleController.SessionStatusModel getSshStatus(String token) {
        return restTemplate.getForObject("https://tmate.io/api/t/" + token, ConsoleController.SessionStatusModel.class);
    }

    @SneakyThrows
    private void checkHardware() {
        if (!wirelessHardwareRepository.isSshGenerated()) {
            wirelessHardwareRepository.generateSSHKeys();
        }
        if (sshHardwareRepository.getTmateVersion() == null) {
            if ("7".equals(SystemInfo.getCpuArchitecture())) {
                sshHardwareRepository.installTmate("arm32v7");
            }

            String tmateVersion = sshHardwareRepository.getTmateVersion();
            if (tmateVersion == null) {
                throw new RuntimeException("Unable to find tmate");
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
