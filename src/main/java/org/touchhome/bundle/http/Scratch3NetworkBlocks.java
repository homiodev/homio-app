package org.touchhome.bundle.http;

import com.pivovarit.function.ThrowingBiConsumer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.stereotype.Component;
import org.touchhome.app.config.TouchHomeProperties;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.workspace.WorkspaceBlock;
import org.touchhome.bundle.api.workspace.scratch.*;

import java.net.*;
import java.nio.charset.StandardCharsets;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Getter
@Component
public class Scratch3NetworkBlocks extends Scratch3ExtensionBlocks {
    private final DatagramSocket udpSocket = new DatagramSocket();

    private final MenuBlock.StaticMenuBlock<HttpMethod> methodMenu;

    private final Scratch3Block httpRequestCommand;
    private final Scratch3Block httpRequestHeaderCommand;
    private final Scratch3Block httpBasicAuthCommand;
    private final Scratch3Block httpBearerAuthCommand;
    private final Scratch3Block httpPayloadCommand;
    private final Scratch3Block onUDPEventCommand;
    private final Scratch3Block sendUDPCCommand;

    public Scratch3NetworkBlocks(EntityContext entityContext, TouchHomeProperties touchHomeProperties) throws SocketException {
        super("#595F4B", entityContext, null, "net");
        this.udpSocket.setBroadcast(true);

        // Menu
        this.methodMenu = MenuBlock.ofStatic("method", HttpMethod.class, HttpMethod.GET);

        this.httpRequestCommand = Scratch3Block.ofHandler(10, "request", BlockType.command,
                "HTTP [METHOD] url [URL] | RAW: [RAW] ", this::httpRequestHandler);
        this.httpRequestCommand.addArgument("METHOD", this.methodMenu);
        this.httpRequestCommand.addArgument("URL", touchHomeProperties.getServerSiteURL() + "/sample");
        this.httpRequestCommand.addArgument("RAW", false);

        this.httpRequestHeaderCommand = Scratch3Block.ofHandler(20, HttpApplyHandler.update_header.name(), BlockType.command,
                "HTTP Header [KEY]/[VALUE]", this::skipCommand);
        this.httpRequestHeaderCommand.addArgument("KEY", "key");
        this.httpRequestHeaderCommand.addArgument(VALUE, "value");

        this.httpBasicAuthCommand = Scratch3Block.ofHandler(30, HttpApplyHandler.update_basic_auth.name(), BlockType.command,
                "HTTP Basic auth [USER]/[PWD]", this::skipCommand);
        this.httpBasicAuthCommand.addArgument("USER", "user");
        this.httpBasicAuthCommand.addArgument("PWD", "password");

        this.httpBearerAuthCommand = Scratch3Block.ofHandler(40, HttpApplyHandler.update_bearer_auth.name(), BlockType.command,
                "HTTP Bearer auth [TOKEN]", this::skipCommand);
        this.httpBearerAuthCommand.addArgument("TOKEN", "token");

        this.httpPayloadCommand = Scratch3Block.ofHandler(50, HttpApplyHandler.update_payload.name(), BlockType.command,
                "HTTP Body payload [PAYLOAD]", this::skipCommand);
        this.httpPayloadCommand.addArgument("PAYLOAD");
        this.httpPayloadCommand.appendSpace();

        this.onUDPEventCommand = Scratch3Block.ofHat(60, "udp_listener",
                "UDP in [HOST]/[PORT]", this::onUdpEventHandler);
        this.onUDPEventCommand.addArgument("HOST", "0.0.0.0");
        this.onUDPEventCommand.addArgument("PORT", 8888);
        //   this.onUDPEventCommand.overrideColor("#A4B96C");

        this.sendUDPCCommand = Scratch3Block.ofHandler(70, "udp_send", BlockType.command,
                "UDP text [VALUE] out [HOST]/[PORT]", this::sendUDPHandler);
        this.sendUDPCCommand.addArgument("HOST", "255.255.255.255");
        this.sendUDPCCommand.addArgument("PORT", 8888);
        this.sendUDPCCommand.addArgument(VALUE, "payload");
        //  this.sendUDPCCommand.overrideColor("#A4B96C");
    }

    @SneakyThrows
    private void sendUDPHandler(WorkspaceBlock workspaceBlock) {
        String payload = workspaceBlock.getInputString(VALUE);
        if (StringUtils.isNotEmpty(payload)) {
            String host = workspaceBlock.getInputString("HOST");
            Integer port = workspaceBlock.getInputInteger("PORT");
            InetSocketAddress address = StringUtils.isEmpty(host) ? new InetSocketAddress(port) : new InetSocketAddress(host, port);
            byte[] buf = payload.getBytes();
            udpSocket.send(new DatagramPacket(buf, buf.length, address));
        }
    }

    private void onUdpEventHandler(WorkspaceBlock workspaceBlock) {
        WorkspaceBlock substack = workspaceBlock.getNext();
        if (substack != null) {
            entityContext.udp().listenUdp(workspaceBlock.getId(), workspaceBlock.getInputString("HOST"),
                    workspaceBlock.getInputInteger("PORT"), (datagramPacket, output) -> {
                        workspaceBlock.setValue(output);
                        substack.getNext().handle();
                    });
            workspaceBlock.onRelease(() -> entityContext.udp().stopListenUdp(workspaceBlock.getId()));
        }
    }

    @SneakyThrows
    private void httpRequestHandler(WorkspaceBlock workspaceBlock) {
        HttpMethod method = workspaceBlock.getMenuValue("METHOD", this.methodMenu);
        String url = workspaceBlock.getInputString("URL");

        HttpRequestBase request = method.httpRequestBaseClass.newInstance();
        request.setURI(URI.create(url));
        applyParentBlocks(request, workspaceBlock.getParent());

        HttpClient client = HttpClientBuilder.create().build();
        HttpResponse response = client.execute(request);

        if (workspaceBlock.getInputBoolean("RAW")) {
            workspaceBlock.setValue(IOUtils.toByteArray(response.getEntity().getContent()));
        } else {
            workspaceBlock.setValue(IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8));
        }
    }

    private void skipCommand(WorkspaceBlock workspaceBlock) {
        // skip execution
    }

    @SneakyThrows
    private void applyParentBlocks(HttpRequestBase request, WorkspaceBlock parent) {
        if (parent == null || !parent.getBlockId().startsWith("http_update_")) {
            return;
        }
        applyParentBlocks(request, parent.getParent());
        HttpApplyHandler.valueOf(parent.getOpcode()).applyFn.accept(parent, request);
    }

    @AllArgsConstructor
    private enum HttpApplyHandler {
        update_payload((workspaceBlock, request) -> {
            if (request instanceof HttpEntityEnclosingRequestBase) {
                String payload = workspaceBlock.getInputString("PAYLOAD");
                ((HttpEntityEnclosingRequestBase) request).setEntity(new StringEntity(payload));
            }
        }),
        update_bearer_auth((workspaceBlock, request) -> {
            request.setHeader(AUTHORIZATION, "Basic " + workspaceBlock.getInputString("TOKEN"));
        }),
        update_basic_auth((workspaceBlock, request) -> {
            String auth = workspaceBlock.getInputString("USER") + ":" + workspaceBlock.getInputString("PWD");
            byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(StandardCharsets.ISO_8859_1));
            request.setHeader(AUTHORIZATION, "Basic " + new String(encodedAuth));
        }),
        update_header((workspaceBlock, request) -> {
            String key = workspaceBlock.getInputString("KEY");
            if (StringUtils.isNotEmpty(key)) {
                request.setHeader(key, workspaceBlock.getInputString(VALUE));
            }
        });

        private final ThrowingBiConsumer<WorkspaceBlock, HttpRequestBase, Exception> applyFn;
    }

    @AllArgsConstructor
    private enum HttpMethod {
        GET(HttpGet.class),
        POST(HttpPost.class),
        DELETE(HttpDelete.class);

        private Class<? extends HttpRequestBase> httpRequestBaseClass;
    }
}
