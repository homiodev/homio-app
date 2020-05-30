package org.touchhome.app.service.ssh.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.touchhome.app.service.ssh.SshHardwareRepository;
import org.touchhome.app.service.ssh.SshProvider;
import org.touchhome.bundle.api.hardware.other.LinuxHardwareRepository;

@Log4j2
@Component
@AllArgsConstructor
public class TmateSshProvider implements SshProvider {

    private final SshHardwareRepository sshHardwareRepository;
    private final LinuxHardwareRepository linuxHardwareRepository;

    @Override
    public SshSession openSshSession() {
        checkHardware();
        return (SshSession) sshHardwareRepository.openTmateSsh();
    }

    private void checkHardware() {
        if (sshHardwareRepository.getTmateVersion() == null) {
            String cpuArch = linuxHardwareRepository.getCpuInfo().getCpuArch();
            if ("armv7l".equals(cpuArch)) {
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
