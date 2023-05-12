package org.homio.bundle.http;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import com.pivovarit.function.ThrowingBiConsumer;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.homio.app.config.AppProperties;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.state.JsonType;
import org.homio.bundle.api.state.RawType;
import org.homio.bundle.api.state.State;
import org.homio.bundle.api.state.StringType;
import org.homio.bundle.api.util.CommonUtils;
import org.homio.bundle.api.workspace.WorkspaceBlock;
import org.homio.bundle.api.workspace.scratch.MenuBlock;
import org.homio.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Getter
@Component
public class Scratch3NetworkBlocks extends Scratch3ExtensionBlocks {

    private final DatagramSocket udpSocket = new DatagramSocket();

    private final MenuBlock.StaticMenuBlock<HttpMethod> methodMenu;

    public Scratch3NetworkBlocks(EntityContext entityContext, AppProperties appProperties)
        throws SocketException {
        super("#595F4B", entityContext, null, "net");
        this.udpSocket.setBroadcast(true);

        // Menu
        this.methodMenu = menuStatic("method", HttpMethod.class, HttpMethod.GET);

        blockCommand(10, "request", "HTTP [METHOD] url [URL] | RAW: [RAW] ", this::httpRequestHandler, block -> {
            block.addArgument("METHOD", this.methodMenu);
            block.addArgument("URL", appProperties.getServerSiteURL() + "/sample");
            block.addArgument("RAW", false);
        });

        blockCommand(20, HttpApplyHandler.update_header.name(), "HTTP Header [KEY]/[VALUE]", this::skipCommand, block -> {
            block.addArgument("KEY", "key");
            block.addArgument(VALUE, "value");
        });

        blockCommand(30, HttpApplyHandler.update_basic_auth.name(), "HTTP Basic auth [USER]/[PWD]", this::skipCommand, block -> {
            block.addArgument("USER", "user");
            block.addArgument("PWD", "password");
        });

        blockCommand(40, HttpApplyHandler.update_bearer_auth.name(), "HTTP Bearer auth [TOKEN]", this::skipCommand, block -> {
            block.addArgument("TOKEN", "token");
        });

        blockCommand(50, HttpApplyHandler.update_payload.name(), "HTTP Body payload [PAYLOAD]", this::skipCommand, block -> {
            block.addArgument("PAYLOAD");
            block.appendSpace();
        });

        blockHat(60, "udp_listener", "UDP in [HOST]/[PORT]", this::onUdpEventHandler, block -> {
            block.addArgument("HOST", "0.0.0.0");
            block.addArgument("PORT", 8888);
        });

        blockCommand(70, "udp_send", "UDP text [VALUE] out [HOST]/[PORT]", this::sendUDPHandler, block -> {
            block.addArgument("HOST", "255.255.255.255");
            block.addArgument("PORT", 8888);
            block.addArgument(VALUE, "payload");
        });
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
            workspaceBlock.handleAndRelease(() ->
                    entityContext.event().listenUdp(
                        workspaceBlock.getId(),
                        workspaceBlock.getInputString("HOST"),
                        workspaceBlock.getInputInteger("PORT"),
                        (datagramPacket, output) -> {
                            workspaceBlock.setValue(new StringType(output));
                            substack.getNext().handle();
                        }),
                () -> entityContext.event().stopListenUdp(workspaceBlock.getId()));
        }
    }

    @SneakyThrows
    private void httpRequestHandler(WorkspaceBlock workspaceBlock) {
        HttpMethod method = workspaceBlock.getMenuValue("METHOD", this.methodMenu);
        String url = workspaceBlock.getInputString("URL");

        HttpRequestBase request = CommonUtils.newInstance(method.httpRequestBaseClass);
        request.setURI(URI.create(url));
        applyParentBlocks(request, workspaceBlock.getParent());

        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            HttpResponse response = client.execute(request);

            if (workspaceBlock.getInputBoolean("RAW")) {
                workspaceBlock.setValue(new RawType(IOUtils.toByteArray(response.getEntity().getContent())));
            } else {
                workspaceBlock.setValue(convertResult(response));
            }
        }
    }

    @SneakyThrows
    private State convertResult(HttpResponse response) {
        Header contentType = response.getFirstHeader("Content-Type");
        String rawValue = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
        if (contentType != null) {
            switch (contentType.getValue()) {
                case MediaType.APPLICATION_JSON_VALUE:
                    return new JsonType(rawValue);
            }
        }
        return new StringType(rawValue);
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

        private final Class<? extends HttpRequestBase> httpRequestBaseClass;
    }
}
