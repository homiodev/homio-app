package org.homio.app.ssh;

import static java.lang.String.format;
import static org.homio.api.ContextSetting.SERVER_PORT;
import static org.homio.api.util.HardwareUtils.MACHINE_IP_ADDRESS;
import static org.homio.app.config.WebSocketConfig.CUSTOM_WEB_SOCKET_ENDPOINT;

import com.sshtools.client.SessionChannelNG;
import com.sshtools.client.SshClient;
import com.sshtools.client.tasks.ShellTask;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.ContextBGP;
import org.homio.api.ContextBGP.ThreadContext;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.manager.common.impl.ContextServiceImpl.DynamicWebSocketHandler;
import org.homio.app.rest.ConsoleController;
import org.homio.app.ssh.SSHServerEndpoint.XtermMessage.XtermHandler;
import org.homio.app.ssh.SSHServerEndpoint.XtermMessage.XtermMessageType;
import org.homio.app.ssh.SshProviderService.SshSession;
import org.jetbrains.annotations.NotNull;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

@Log4j2
@Service
public class SSHServerEndpoint extends BinaryWebSocketHandler implements DynamicWebSocketHandler {

    private static final String TOKEN = "token";
    private static final String COLS = "cols";
    private static final String WEBSSH_PATH = CUSTOM_WEB_SOCKET_ENDPOINT + "/webssh";
    private static final String FORMAT = "ws://%s:%s%s?%s=${TOKEN}&Authentication=${BEARER}&%s=${COLS}";

    private static final PassiveExpiringMap<String, SessionContext> sessionByToken = new PassiveExpiringMap<>(24, TimeUnit.HOURS);
    private static final PassiveExpiringMap<String, SessionContext> sessionBySessionId = new PassiveExpiringMap<>(24, TimeUnit.HOURS);
    private final ContextImpl context;

    public SSHServerEndpoint(ContextImpl context) {
        this.context = context;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = (String) session.getAttributes().get(TOKEN);
        Integer cols = (Integer) session.getAttributes().get(COLS);
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

            sessionContext.connectToSSH(cols);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, @NotNull Throwable throwable) {
        log.error("SSH error: {}", session.getId(), throwable);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, @NotNull CloseStatus status) {
        log.info("SSH close connection: {}/{}", session.getId(), status);
        SessionContext sessionContext = sessionBySessionId.remove(session.getId());
        if (sessionContext != null) {
            sessionContext.closeSessionContext();
        }
    }

    public SshSession openSession(SshGenericEntity entity) {
        context.service().registerWebSocketEndpoint(WEBSSH_PATH, this);

        String token = UUID.randomUUID().toString();

        String url = format(FORMAT, MACHINE_IP_ADDRESS, SERVER_PORT, WEBSSH_PATH, TOKEN, COLS);
        SshSession<SshGenericEntity> session = new SshSession<>(token, url, entity);
        sessionByToken.put(token, new SessionContext(session));

        return session;
    }

    @SneakyThrows
    public void closeSession(SshSession<SshGenericEntity> session) {
        SessionContext sessionContext = sessionByToken.remove(session.getToken());
        if (sessionContext != null) {
            sessionContext.closeSessionContext();
            if (sessionContext.wsSession != null) {
                sessionBySessionId.remove(sessionContext.wsSession.getId());
            }
        }
    }

    public void resizeSshConsole(SshSession<SshGenericEntity> session, int cols) {
        SessionContext sessionContext = sessionByToken.get(session.getToken());
        if (sessionContext != null && sessionContext.sshChannel != null) {
            sessionContext.sshChannel.changeTerminalDimensions(cols, 30, 0, 0);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, @NotNull BinaryMessage message) {
        SessionContext sessionContext = sessionBySessionId.get(session.getId());
        sessionContext.onMessage(message);
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
                case pty_data -> {
                    payload.packArrayHeader(3);
                    payload.packByte((byte) xtermHandler.id);
                    payload.packByte((byte) 0);
                    payload.packString(new String(data));
                }
                case sync -> {
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

    @Override
    public boolean beforeHandshake(@NotNull ServerHttpRequest serverHttpRequest, @NotNull ServerHttpResponse response, @NotNull WebSocketHandler wsHandler,
                                   @NotNull Map<String, Object> attributes) {
        if (serverHttpRequest instanceof ServletServerHttpRequest request) {
            String token = request.getServletRequest().getParameter(TOKEN);
            if (!StringUtils.isEmpty(token) && sessionByToken.containsKey(token)) {
                attributes.put(TOKEN, token);

                String cols = request.getServletRequest().getParameter(COLS);
                if (!StringUtils.isEmpty(cols)) {
                    attributes.put(COLS, Integer.parseInt(cols));
                }
                return true;
            }

        }
        return false;
    }

    @RequiredArgsConstructor
    private class SessionContext {

        private @NotNull
        final SshSession<SshGenericEntity> session;
        private String sessionId;
        private WebSocketSession wsSession;
        private ThreadContext<Void> threadContext;
        private SshClient sshClient;
        private SessionChannelNG sshChannel;
        private boolean closed;

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

        public void connectToSSH(Integer cols) {
            if (threadContext != null && !threadContext.isStopped()) {
                return;
            }
            threadContext = context.bgp().builder("ssh-shell-" + sessionId).execute(() -> {
                try {
                    connect(cols);
                } catch (Exception e) {
                    log.error("SSH error connect to ssh");
                    closeSessionContext();
                }
            });
        }

        @SneakyThrows
        private void closeSessionContext() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            ConsoleController.getSessions().remove(session.getToken());
            log.info("SSH close connection: {}", session);
            try {
                sessionByToken.remove(session.getToken());
                // wsSession.close(CloseStatus.NORMAL);
                if (sshClient != null) {
                    sshClient.close();
                }
                ContextBGP.cancel(threadContext);
            } catch (Exception ex) {
                log.error("SSH error while close ssh session", ex);
            }
        }

        private void transToSSH(byte[] buffer) throws IOException {
            if (sshChannel != null) {
                OutputStream outputStream = sshChannel.getOutputStream();
                outputStream.write(buffer);
                outputStream.flush();
            }
        }

        private void connect(Integer cols) throws IOException {
            try (SshClient sshClient = session.getEntity().createSshClient()) {
                this.sshClient = sshClient;
                log.info("SSH connected: {}", session);
                sshClient.runTask(createShellTask(cols, sshClient));
                closed = false;
            }
        }

        @NotNull
        private ShellTask createShellTask(Integer cols, SshClient sshClient) {
            return new ShellTask(sshClient) {

                @Override
                protected void beforeStartShell(SessionChannelNG session1) {
                    session1.allocatePseudoTerminal("xterm", cols == null ? 80 : cols, 30);
                }

                @Override
                protected void onOpenSession(SessionChannelNG channel) {
                    sshChannel = channel;
                    try {
                        log.info("SSH shell session opened: {}", session);
                        XtermMessage welcomeMessage = new XtermMessage(XtermMessageType.OutMessage, XtermHandler.pty_data);
                        welcomeMessage.setData(("Homio: welcome to " + session.getEntity().getHost() + "\n\r").getBytes());
                        wsSession.sendMessage(welcomeMessage.build());

                        readInfiniteFromTerminal(channel);
                    } catch (Exception ex) {
                        log.error("SSH error during session", ex);
                    }
                }

                private void readInfiniteFromTerminal(SessionChannelNG channel) throws IOException {
                    try (InputStream inputStream = channel.getInputStream()) {
                        byte[] buffer = new byte[1024];
                        int i;
                        while ((i = inputStream.read(buffer)) != -1) {
                            XtermMessage message = new XtermMessage(XtermMessageType.OutMessage, XtermHandler.pty_data);
                            if (i == 1024) { // too much data
                                int availableBytes = inputStream.available();
                                if (availableBytes > 10_000_000) { // read by 1MB if more that 10MB
                                    log.warn("Too much ssh data to read. Read data one by one");
                                    writeBigDataToSocket(inputStream, availableBytes);
                                    continue;
                                }
                                byte[] bigBuffer = new byte[i + availableBytes];
                                int readBytesIntoBigBuffer = inputStream.read(bigBuffer, i, availableBytes);
                                if (readBytesIntoBigBuffer != availableBytes) {
                                    log.warn("Not all data read from ssh stream: {}/{}", readBytesIntoBigBuffer, availableBytes);
                                }
                                System.arraycopy(buffer, 0, bigBuffer, 0, i);
                                message.setData(bigBuffer);
                            } else {
                                message.setData(Arrays.copyOfRange(buffer, 0, i));
                            }
                            wsSession.sendMessage(message.build());
                        }
                    }
                }

                private void writeBigDataToSocket(InputStream inputStream, int availableBytes) throws IOException {
                    byte[] buffer = new byte[1_000_000];
                    XtermMessage message = new XtermMessage(XtermMessageType.OutMessage, XtermHandler.pty_data);
                    while (availableBytes > 0) {
                        int rb = inputStream.read(buffer);
                        if (rb < 1_000_000) {
                            message.setData(Arrays.copyOfRange(buffer, 0, rb));
                        } else {
                            message.setData(buffer);
                        }
                        wsSession.sendMessage(message.build());
                        availableBytes -= rb;
                    }
                }
            };
        }
    }
}
