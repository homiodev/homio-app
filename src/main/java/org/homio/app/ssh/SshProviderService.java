package org.homio.app.ssh;

import lombok.Getter;
import lombok.Setter;
import org.homio.bundle.api.service.EntityService;

public interface SshProviderService<T extends SshBaseEntity> extends EntityService.ServiceInstance {

    /**
     * Open ssh session
     *
     * @param sshEntity - ssh entity that hold configuration
     * @return session token
     */
    SshSession openSshSession(T sshEntity);

    /**
     * Close ssh session
     *
     * @param token     - session token
     * @param sshEntity - ssh entity that hold configuration
     */
    void closeSshSession(String token, T sshEntity);

    @Getter
    @Setter
    class SshSession {

        /**
         * Unique token for session
         */
        private String token;

        /**
         * Web socker url
         */
        private String wsURL;
    }
}
