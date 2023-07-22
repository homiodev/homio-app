package org.homio.app.ssh;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.homio.api.service.EntityService;
import org.jetbrains.annotations.Nullable;

public interface SshProviderService<T extends SshBaseEntity> extends EntityService.ServiceInstance {

    /**
     * Open ssh session
     *
     * @param sshEntity - ssh entity that hold configuration
     * @return session token
     */
    @Nullable SshSession<T> openSshSession(T sshEntity);

    /**
     * Close ssh session
     *
     * @param token     - session token
     * @param sshEntity - ssh entity that hold configuration
     */
    void closeSshSession(SshSession<T> sshSession);

    default void resizeSshConsole(SshSession<T> sshSession, int cols) {

    }

    @Getter
    @Setter
    @RequiredArgsConstructor
    class SshSession<T extends SshBaseEntity> {

        /**
         * Unique token for session
         */
        private final String token;

        /**
         * Web socker url
         */
        private final String wsURL;

        @JsonIgnore
        private final T entity;

        @Override
        public String toString() {
            return "SshSession{" +
                "token='" + token + '\'' +
                ", wsURL='" + wsURL + '\'' +
                '}';
        }
    }
}
