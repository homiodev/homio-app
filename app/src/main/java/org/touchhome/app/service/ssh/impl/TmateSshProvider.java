package org.touchhome.app.service.ssh.impl;

import com.pi4j.system.SystemInfo;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;
import org.touchhome.app.service.ssh.SshHardwareRepository;
import org.touchhome.app.service.ssh.SshProvider;
import org.touchhome.bundle.api.hardware.other.LinuxHardwareRepository;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Log4j2
@Component
@RequiredArgsConstructor
public class TmateSshProvider implements SshProvider {

    private final SshHardwareRepository sshHardwareRepository;
    private final LinuxHardwareRepository linuxHardwareRepository;
    private Thread tmateThread;

    @Override
    public SshSession openSshSession() {
        checkHardware();
        if (tmateThread == null) {
            tmateThread = new Thread(() -> {
                try {
                    Process process = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "/opt/touchhome/ssh/tmate -F"});
                    process.waitFor(10, TimeUnit.SECONDS);
                    List<String> inputs = IOUtils.readLines(process.getInputStream());
                    process.waitFor();
                } catch (InterruptedException ie) {
                    log.info("Close tmate ssh session");
                } catch (Exception e) {
                    log.error("Error while running tmate");
                }
                tmateThread = null;
            });
            tmateThread.start();
        }
        return null;
    }

    @Override
    public void closeSshSession() {
        if (tmateThread != null) {
            tmateThread.interrupt();
        }
    }

    @SneakyThrows
    private void checkHardware() {
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
}
