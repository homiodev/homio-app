package org.touchhome.app.service.ssh;

import lombok.Getter;

public interface SshProvider {
    SshSession openSshSession();

    void closeSshSession();

    @Getter
    class SshSession {
        String token;
    }
}
