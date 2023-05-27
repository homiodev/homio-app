package org.homio.app.ssh;

import static org.homio.api.util.CommonUtils.MACHINE_IP_ADDRESS;

import com.sshtools.client.SessionChannelNG;
import com.sshtools.client.SshClient;
import com.sshtools.client.tasks.ShellTask;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextBGP.ThreadContext;
import org.homio.app.ssh.SSHServerEndpoint.XtermMessage.XtermHandler;
import org.homio.app.ssh.SSHServerEndpoint.XtermMessage.XtermMessageType;
import org.homio.app.ssh.SshProviderService.SshSession;
import org.jetbrains.annotations.NotNull;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

@Log4j2
@Service
@RequiredArgsConstructor
public class SSHServerEndpoint extends BinaryWebSocketHandler {

    private final ApplicationContext applicationContext;

    private static final PassiveExpiringMap<String, SessionContext> sessionByToken = new PassiveExpiringMap<>(24, TimeUnit.HOURS);
    private static final PassiveExpiringMap<String, SessionContext> sessionBySessionId = new PassiveExpiringMap<>(24, TimeUnit.HOURS);

    public boolean hasToken(String token) {
        return sessionByToken.containsKey(token);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = (String) session.getAttributes().get("token");
        SessionContext sessionContext = sessionByToken.get(token);
        if (sessionContext == null) {
            session.close(CloseStatus.GOING_AWAY);
        } else {
            sessionBySessionId.put(session.getId(), sessionContext);
            sessionContext.sessionId = session.getId();
            sessionContext.wsSession = session;
            XtermMessage message = new XtermMessage(XtermMessageType.OutMessage, XtermHandler.sync);
            message.setData("bash".getBytes());
            session.sendMessage(message.build());

            sessionContext.connectToSSH();
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, @NotNull BinaryMessage message) {
        SessionContext sessionContext = sessionBySessionId.get(session.getId());
        sessionContext.onMessage(message);
    }

    @Override
    public void handleTransportError(WebSocketSession session, @NotNull Throwable throwable) {
        log.error("SSH error: {}", session.getId(), throwable);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, @NotNull CloseStatus status) {
        log.info("SSH close connection: {}/{}", session.getId(), status);
        SessionContext sessionContext = sessionBySessionId.get(session.getId());
        if (sessionContext != null) {
            sessionContext.closeSessionContext();
        }
    }

    public SshSession openSession(SshGenericEntity entity) {
        String token = UUID.randomUUID().toString();
        SessionContext sessionContext = new SessionContext(token, entity);
        sessionByToken.put(token, sessionContext);

        SshSession session = new SshSession();
        session.setToken(token);
        session.setWsURL("ws://" + MACHINE_IP_ADDRESS + ":9111/webssh?token=" + token);
        return session;
    }

    /**
     * Remove session from UI
     *
     * @param token - token
     */
    @SneakyThrows
    public void closeSession(String token) {
        SessionContext sessionContext = sessionByToken.get(token);
        if (sessionContext != null) {
            sessionContext.closeSessionContext();
            sessionByToken.remove(token);
            if (sessionContext.wsSession != null) {
                sessionBySessionId.remove(sessionContext.wsSession.getId());
            }
        }
    }

    @RequiredArgsConstructor
    private class SessionContext {

        private @NotNull final String token;
        private @NotNull final SshGenericEntity entity;
        private String sessionId;
        private WebSocketSession wsSession;
        private ThreadContext<Void> threadContext;
        private SshClient sshClient;
        private SessionChannelNG sshChannel;

        @SneakyThrows
        private void closeSessionContext() {
            log.info("SSH close connection: {}", token);
            try {
                wsSession.close(CloseStatus.NORMAL);
                if (sshClient != null) {
                    sshClient.close();
                }
                if (threadContext != null) {
                    threadContext.cancel();
                }
            } catch (Exception ex) {
                log.error("SSH error while close ssh session", ex);
            }
        }

        public void onMessage(BinaryMessage buffer) {
            MessageUnpacker payload = MessagePack.newDefaultUnpacker(buffer.getPayload());
            try {
                payload.unpackArrayHeader(); // must be always 3
                payload.unpackByte();
                payload.unpackByte(); // pane id - 0
                String content = payload.unpackString();
                transToSSH(content.getBytes());
            } catch (IOException ex) {
                log.error("SSH send to sse", ex);
                this.closeSessionContext();
            }
        }

        private void transToSSH(byte[] buffer) throws IOException {
            if (sshChannel != null) {
                OutputStream outputStream = sshChannel.getOutputStream();
                outputStream.write(buffer);
                outputStream.flush();
            }
        }

        public void connectToSSH() {
            if (threadContext != null && !threadContext.isStopped()) {
                return;
            }
            threadContext = applicationContext.getBean(EntityContext.class).bgp().builder("ssh-shell-" + sessionId).execute(() -> {
                try {
                    connect();
                } catch (Exception e) {
                    log.error("SSH error connect to ssh");
                    closeSessionContext();
                }
            });
        }

        private void connect() throws IOException {
            try (SshClient sshClient = entity.createSshClient()) {
                this.sshClient = sshClient;
                log.info("SSH connected: {}", token);
                sshClient.runTask(new ShellTask(sshClient) {

                    @Override
                    protected void onOpenSession(SessionChannelNG channel) {
                        sshChannel = channel;
                        try {
                            log.info("SSH shell session opened: {}", token);
                            XtermMessage welcomeMessage = new XtermMessage(XtermMessageType.OutMessage, XtermHandler.pty_data);
                            welcomeMessage.setData(("Homio: welcome to " + entity.getHost() + "\n\r").getBytes());
                            wsSession.sendMessage(welcomeMessage.build());
                            // transToSSH("\r\n".getBytes());

                            try (InputStream inputStream = channel.getInputStream()) {
                                byte[] buffer = new byte[1024];
                                int i;
                                while ((i = inputStream.read(buffer)) != -1) {
                                    byte[] bytesToSend = Arrays.copyOfRange(buffer, 0, i);
                                    XtermMessage message = new XtermMessage(XtermMessageType.OutMessage, XtermHandler.pty_data);
                                    message.setData(bytesToSend);
                                    wsSession.sendMessage(message.build());
                                }
                            }
                        } catch (Exception ex) {
                            log.error("SSH error during session", ex);
                        }
                    }
                });
            }
        }
    }

    @RequiredArgsConstructor
    public static final class XtermMessage {

        private final XtermMessageType messageType;
        private final XtermHandler xtermHandler;
        @Setter
        private byte[] data;

        @SneakyThrows
        public ByteBuffer toByteArray() {
            MessageBufferPacker payload = MessagePack.newDefaultBufferPacker();
            payload.packArrayHeader(2);
            payload.packByte((byte) messageType.id);
            switch (xtermHandler) {
                case pty_data:
                    payload.packArrayHeader(3);
                    payload.packByte((byte) xtermHandler.id);
                    payload.packByte((byte) 0);
                    payload.packString(new String(data));
                    break;
                case sync:
                    payload.packArrayHeader(5);
                    payload.packByte((byte) xtermHandler.id); // handler id
                    payload.packByte((byte) 120); // col width
                    payload.packByte((byte) 29); // row width

                    // write bash
                    payload.packArrayHeader(4);
                    payload.packByte((byte) 0);
                    payload.packString("bash");
                    payload.packByte((byte) 0); // 0 instead of array: [0, 120, 29, 0, 0]
                    payload.packByte((byte) 0);

                    payload.packByte((byte) 0);

            }
            payload.close();
            return ByteBuffer.wrap(payload.toByteArray());
        }

        public BinaryMessage build() {
            return new BinaryMessage(toByteArray());
        }

        @RequiredArgsConstructor
        public enum XtermMessageType {
            OutMessage(0), Snapshot(1);
            private final int id;
        }

        @RequiredArgsConstructor
        public enum XtermHandler {
            sync(1), pty_data(2), status(5), sync_copy_mode(6), fin(8);
            private final int id;
        }
    }
}
