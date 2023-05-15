package org.homio.bundle.http;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import com.pivovarit.function.ThrowingBiConsumer;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.homio.app.config.AppProperties;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.entity.EntityFieldMetadata;
import org.homio.bundle.api.state.JsonType;
import org.homio.bundle.api.state.RawType;
import org.homio.bundle.api.state.State;
import org.homio.bundle.api.state.StringType;
import org.homio.bundle.api.ui.field.UIField;
import org.homio.bundle.api.ui.field.UIFieldGroup;
import org.homio.bundle.api.ui.field.UIFieldSlider;
import org.homio.bundle.api.ui.field.UIFieldType;
import org.homio.bundle.api.ui.field.condition.UIFieldShowOnCondition;
import org.homio.bundle.api.util.CommonUtils;
import org.homio.bundle.api.util.SecureString;
import org.homio.bundle.api.workspace.WorkspaceBlock;
import org.homio.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Getter
@Component
public class Scratch3NetworkBlocks extends Scratch3ExtensionBlocks {

    private final DatagramSocket udpSocket = new DatagramSocket();

    public Scratch3NetworkBlocks(EntityContext entityContext, AppProperties appProperties)
        throws SocketException {
        super("#595F4B", entityContext, null, "net");
        this.udpSocket.setBroadcast(true);

        blockCommand(10, "request", "Http [URL] | [SETTING]", this::httpRequestHandler, block -> {
            block.addArgument("URL", appProperties.getServerSiteURL() + "/sample");
            block.addSetting(HttpRequestEntity.class, null);
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
        HttpRequestEntity setting = workspaceBlock.getSetting(HttpRequestEntity.class);
        String url = workspaceBlock.getInputString("URL");

        RequestConfig config = RequestConfig.custom()
                                            .setConnectTimeout(setting.connectTimeout * 1000)
                                            .setSocketTimeout(setting.socketTimeout * 1000).build();
        HttpRequestBase request = CommonUtils.newInstance(setting.httpMethod.httpRequestBaseClass);
        request.setURI(URI.create(url));
        switch (setting.auth) {
            case Basic:
                String auth = setting.user + ":" + setting.password.asString();
                byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(StandardCharsets.ISO_8859_1));
                request.setHeader(AUTHORIZATION, "Basic " + new String(encodedAuth));
                break;
            case Digest:
                throw new IllegalStateException("Not implemented yet");
            case Bearer:
                request.setHeader(AUTHORIZATION, "Basic " + setting.token);
                break;
        }
        applyParentBlocks(request, workspaceBlock.getParent());

        try (CloseableHttpClient client = HttpClientBuilder.create().setDefaultRequestConfig(config).build()) {
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
            String type = contentType.getValue();
            if (type.startsWith(MediaType.APPLICATION_JSON_VALUE)) {
                return new JsonType(rawValue);
            }
        }
        return State.of(rawValue);
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

    @Getter
    @Setter
    public static class HttpRequestEntity implements EntityFieldMetadata {

        @UIField(order = 1, type = UIFieldType.Chips)
        private HttpMethod httpMethod;

        @UIField(order = 2, type = UIFieldType.Chips)
        private List<String> httpHeaders;

        @UIField(order = 3, type = UIFieldType.Chips)
        private List<String> queryParameters;

        @UIField(order = 4)
        private ResponseType responseType = ResponseType.AutoDetect;

        @UIField(order = 5)
        @UIFieldShowOnCondition("return context.get('httpMethod') == 'POST'")
        private String payload;

        @UIField(order = 1)
        @UIFieldGroup(value = "AUTH", order = 10, borderColor = "#9C1A9C")
        private HttpAuth auth = HttpAuth.None;

        @UIField(order = 2)
        @UIFieldGroup(value = "AUTH")
        @UIFieldShowOnCondition("return context.get('auth') == 'Digest' || context.get('auth') == 'Basic'")
        private String user;

        @UIField(order = 3)
        @UIFieldGroup(value = "AUTH")
        @UIFieldShowOnCondition("return context.get('auth') == 'Digest' || context.get('auth') == 'Basic'")
        private SecureString password;

        @UIField(order = 4)
        @UIFieldGroup(value = "AUTH")
        @UIFieldShowOnCondition("return context.get('auth') == 'Bearer'")
        private String token;

        @UIField(order = 1)
        @UIFieldSlider(min = 1, max = 60)
        @UIFieldGroup(value = "CONNECTION", order = 20, borderColor = "#1A9C8F")
        private int connectTimeout = 10;

        @UIField(order = 2)
        @UIFieldSlider(min = 1, max = 60)
        @UIFieldGroup("CONNECTION")
        private int socketTimeout = 10;

        @Override
        public @NotNull String getEntityID() {
            return "net-http";
        }

        @Override
        public @Nullable String getTitle() {
            return "TITLE.NET_HTTP";
        }

        @RequiredArgsConstructor
        public enum HttpMethod {
            GET(HttpGet.class),
            POST(HttpPost.class),
            DELETE(HttpDelete.class),
            HEAD(HttpHead.class);

            private final Class<? extends HttpRequestBase> httpRequestBaseClass;
        }

        public enum HttpAuth {
            None, Basic, Digest, Bearer
        }

        public enum ResponseType {
            AutoDetect, String, Binary, Json
        }
    }
}
