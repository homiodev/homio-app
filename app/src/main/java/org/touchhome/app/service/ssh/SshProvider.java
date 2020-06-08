package org.touchhome.app.service.ssh;

import lombok.Getter;
import lombok.Setter;
import org.touchhome.app.rest.ConsoleController;

public interface SshProvider {
    SshSession openSshSession();

    void closeSshSession();

    ConsoleController.SessionStatusModel getSshStatus(String token);

    @Getter
    @Setter
    class SshSession {
        String token;
        String tokenReadOnly;
        String ssh;
        String sshReadOnly;
    }
}
