package org.touchhome.app.service.ssh;

import lombok.Getter;

public interface SshProvider {
    SshSession openSshSession();

    @Getter
    class SshSession {
        String token;
    }
}
