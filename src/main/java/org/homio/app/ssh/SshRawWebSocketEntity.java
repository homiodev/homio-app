package org.homio.app.ssh;

import jakarta.persistence.Entity;
import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.Context;
import org.homio.api.model.OptionModel;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIField;
import org.homio.api.util.CommonUtils;
import org.homio.app.ssh.SshRawWebSocketEntity.RawWebSocketService;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Entity
@UISidebarChildren(icon = "fas fa-draw-polygon", color = "#CC0092")
public class SshRawWebSocketEntity extends SshBaseEntity<SshRawWebSocketEntity, RawWebSocketService> {

    @Override
    public void configureOptionModel(OptionModel optionModel) {
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
    protected @NotNull String getDevicePrefix() {
        return "ssh-raw";
    }

    @Override
    public long getEntityServiceHashCode() {
        return getRawWebSocketAddress().hashCode();
    }

    @Override
    public @NotNull Class<RawWebSocketService> getEntityServiceItemClass() {
        return RawWebSocketService.class;
    }

    @Override
    public @Nullable RawWebSocketService createService(@NotNull Context context) {
        return new RawWebSocketService(context, this);
    }

    public static class RawWebSocketService extends ServiceInstance<SshRawWebSocketEntity> implements SshProviderService<SshRawWebSocketEntity> {

        public RawWebSocketService(Context context, SshRawWebSocketEntity entity) {
            super(context, entity, true);
        }

        @Override
        protected void initialize() {
            testServiceWithSetStatus();
        }

        @Override
        public void destroy() throws Exception {

        }

        @Override
        protected long getEntityHashCode(SshRawWebSocketEntity entity) {
            return Objects.hash(entity.getRawWebSocketAddress());
        }

        @SneakyThrows
        // https://github.com/TooTallNate/Java-WebSocket/blob/master/src/main/example/SSLClientExample.java
        protected void testService() {
            if (StringUtils.isEmpty(entity.getRawWebSocketAddress())) {
                throw new IllegalArgumentException("W.ERROR.URL_EMPTY");
            }
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> error = new AtomicReference<>("Unknown error");
            AtomicBoolean success = new AtomicBoolean(false);
            WebSocketClient webSocketClient = new WebSocketClient(new URI(entity.getRawWebSocketAddress())) {
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
                return;
            }
            throw new IllegalStateException(error.get());
        }

        @Override
        public SshSession openSshSession(SshRawWebSocketEntity sshEntity) {
            return new SshSession(UUID.randomUUID().toString(), entity.getRawWebSocketAddress(), sshEntity);
        }

        @Override
        public void closeSshSession(SshSession<SshRawWebSocketEntity> sshSession) {
            // no need to close session due it's raw ws address
        }
    }
}
