package org.homio.app.ssh;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.homio.app.ssh.SshRawWebSocketEntity.RawWebSocketService;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.model.OptionModel;
import org.homio.bundle.api.service.EntityService;
import org.homio.bundle.api.ui.UISidebarChildren;
import org.homio.bundle.api.ui.field.UIField;
import org.homio.bundle.api.util.CommonUtils;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Entity
@UISidebarChildren(icon = "fas fa-draw-polygon", color = "#CC0092")
public class SshRawWebSocketEntity extends SshBaseEntity<SshRawWebSocketEntity, RawWebSocketService> {

    public static final String PREFIX = "sshraw_";

    @Override
    public void configureOptionModel(OptionModel optionModel) {
        optionModel.setStatus(this);
        optionModel.setDescription(getRawWebSocketAddress());
    }

    @UIField(order = 10, required = true, inlineEditWhenEmpty = true)
    public String getRawWebSocketAddress() {
        return getJsonData("raw_addr");
    }

    @SuppressWarnings("unused")
    public void setRawWebSocketAddress(String value) {
        setJsonData("raw_addr", value);
    }

    @Override
    public String getDefaultName() {
        return "SSH raw address";
    }

    @Override
    public @NotNull String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public @NotNull Class<RawWebSocketService> getEntityServiceItemClass() {
        return RawWebSocketService.class;
    }

    @Override
    public @Nullable RawWebSocketService createService(@NotNull EntityContext entityContext) {
        return new RawWebSocketService();
    }

    public static class RawWebSocketService implements SshProviderService<SshRawWebSocketEntity> {

        @Getter
        private SshRawWebSocketEntity entity;
        private String address;

        @Override
        public boolean entityUpdated(@NotNull EntityService entity) {
            SshRawWebSocketEntity model = (SshRawWebSocketEntity) entity;
            boolean reTest = address == null || !address.equals(model.getRawWebSocketAddress());
            this.entity = model;
            this.address = model.getRawWebSocketAddress();
            return reTest;
        }

        @Override
        @SneakyThrows
        // https://github.com/TooTallNate/Java-WebSocket/blob/master/src/main/example/SSLClientExample.java
        public boolean testService() {
            if (StringUtils.isEmpty(address)) {
                throw new IllegalArgumentException("W.ERROR.URL_EMPTY");
            }
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> error = new AtomicReference<>("Unknown error");
            AtomicBoolean success = new AtomicBoolean(false);
            WebSocketClient webSocketClient = new WebSocketClient(new URI(address)) {
                @Override
                public void onOpen(ServerHandshake serverHandshake) {
                    success.set(true);
                    latch.countDown();
                }

                @Override
                public void onMessage(String s) {
                    success.set(true);
                    latch.countDown();
                }

                @Override
                public void onClose(int i, String s, boolean b) {
                    latch.countDown();
                }

                @Override
                public void onError(Exception ex) {
                    error.set(CommonUtils.getErrorMessage(ex));
                    latch.countDown();
                }
            };
            if (!webSocketClient.connectBlocking(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Unable connect");
            }
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Connection timeout");
            }
            webSocketClient.closeBlocking();
            if (success.get()) {
                return true;
            }
            throw new IllegalStateException(error.get());
        }

        @Override
        public void destroy() {

        }

        @Override
        public SshSession openSshSession(SshRawWebSocketEntity sshEntity) {
            SshSession session = new SshSession();
            session.setWsURL(address);
            session.setToken(UUID.randomUUID().toString());
            return session;
        }

        @Override
        public void closeSshSession(String token, SshRawWebSocketEntity sshEntity) {
            // no need to close session due it's raw ws address
        }
    }
}
