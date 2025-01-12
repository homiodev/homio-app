package org.homio.addon.http;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.ACCEPT_ENCODING;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpHeaders.HOST;
import static org.springframework.http.HttpHeaders.LOCATION;
import static org.springframework.http.HttpHeaders.USER_AGENT;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;
import static org.springframework.http.MediaType.TEXT_HTML_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

import com.fasterxml.jackson.core.type.TypeReference;
import com.pivovarit.function.ThrowingBiConsumer;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Map.Entry;

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
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.homio.api.Context;
import org.homio.api.entity.EntityFieldMetadata;
import org.homio.api.state.JsonType;
import org.homio.api.state.RawType;
import org.homio.api.state.State;
import org.homio.api.state.StringType;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldKeyValue;
import org.homio.api.ui.field.UIFieldKeyValue.Option;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.SecureString;
import org.homio.api.workspace.WorkspaceBlock;
import org.homio.api.workspace.scratch.Scratch3Block.ScratchSettingBaseEntity;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Getter
@Component
public class Scratch3NetworkBlocks extends Scratch3ExtensionBlocks {

  private final DatagramSocket udpSocket = new DatagramSocket();

  public Scratch3NetworkBlocks(Context context) throws SocketException {
    super("#595F4B", context, null, "net");
    this.udpSocket.setBroadcast(true);

    blockCommand(10, "request", "HTTP [URL] | [SETTING]", this::httpRequestHandler, block -> {
      block.addArgument("URL", "https://homio.org/sample");
      block.addSetting(HttpRequestEntity.class);
    });

    blockCommand(20, HttpApplyHandler.update_header.name(), "HTTP Header [KEY]/[VALUE]", this::skipCommand, block -> {
      block.addArgument("KEY", "key");
      block.addArgument(VALUE, "value");
    });

    blockCommand(30, HttpApplyHandler.update_basic_auth.name(), "HTTP Basic auth [USER]/[PWD]", this::skipCommand, block -> {
      block.addArgument("USER", "user");
      block.addArgument("PWD", "password");
    });

    blockCommand(40, HttpApplyHandler.update_bearer_auth.name(), "HTTP Bearer auth [TOKEN]", this::skipCommand, block ->
      block.addArgument("TOKEN", "token"));

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

  @NotNull
  private static DatagramSocket getDatagramSocket(String host, Integer port) throws SocketException {
    DatagramSocket socket;
    if (StringUtils.isEmpty(host) || host.equals("255.255.255.255") || host.equals("0.0.0.0")) {
      socket = new DatagramSocket(port);
    } else {
      socket = new DatagramSocket();
      socket.bind(new InetSocketAddress(host, port));
    }
    return socket;
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
    workspaceBlock.handleNextOptional(substack -> {
      String host = workspaceBlock.getInputString("HOST");
      Integer port = workspaceBlock.getInputInteger("PORT");
      try (DatagramSocket socket = getDatagramSocket(host, port)) {
        DatagramPacket datagramPacket = new DatagramPacket(new byte[1024], 1024);
        while (!Thread.currentThread().isInterrupted()) {
          socket.receive(datagramPacket);
          byte[] data = datagramPacket.getData();
          String text = new String(data, 0, datagramPacket.getLength());
          workspaceBlock.setValue(new StringType(text));
          substack.handle();
        }
        workspaceBlock.logWarn("Finish listen udp: {}", port);
      }
    });
  }

  @SneakyThrows
  private void httpRequestHandler(WorkspaceBlock workspaceBlock) {
    HttpRequestEntity setting = workspaceBlock.getSetting(HttpRequestEntity.class);
    String url = workspaceBlock.getInputString("URL");

    RequestConfig config = RequestConfig.custom()
      .setConnectTimeout(setting.connectTimeout * 1000)
      .setSocketTimeout(setting.socketTimeout * 1000).build();
    HttpRequestBase request = CommonUtils.newInstance(setting.httpMethod.httpRequestBaseClass);
    switch (setting.auth) {
      case Basic -> {
        String auth = setting.user + ":" + setting.password.asString();
        byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(StandardCharsets.ISO_8859_1));
        request.setHeader(AUTHORIZATION, "Basic " + new String(encodedAuth));
      }
      case Digest -> throw new IllegalStateException("Not implemented yet");
      case Bearer -> request.setHeader(AUTHORIZATION, "Basic " + setting.token);
    }

    // build headers
    if (setting.httpHeaders != null) {
      Map<String, String> headers = OBJECT_MAPPER.readValue(setting.httpHeaders, new TypeReference<>() {
      });
      for (Entry<String, String> headerEntry : headers.entrySet()) {
        request.setHeader(headerEntry.getKey(), headerEntry.getValue());
      }
    }

    // Build query uri
    URIBuilder uriBuilder = new URIBuilder(URI.create(url));
    if (setting.queryParameters != null) {
      Map<String, String> queries = OBJECT_MAPPER.readValue(setting.queryParameters, new TypeReference<>() {
      });
      for (Entry<String, String> queryEntry : queries.entrySet()) {
        uriBuilder.addParameter(queryEntry.getKey(), queryEntry.getValue());
      }
    }
    request.setURI(uriBuilder.build());

    // override parameters
    applyParentBlocks(request, workspaceBlock.getParent());

    try (CloseableHttpClient client = HttpClientBuilder.create().setDefaultRequestConfig(config).build()) {
      HttpResponse response = client.execute(request);
      workspaceBlock.setValue(convertResult(response, setting));
    }
  }

  @SneakyThrows
  private State convertResult(HttpResponse response, HttpRequestEntity setting) {
    switch (setting.responseType) {
      case String -> {
        return new StringType(IOUtils.toString(response.getEntity().getContent(), UTF_8));
      }
      case Binary -> {
        return new RawType(IOUtils.toByteArray(response.getEntity().getContent()));
      }
      case Json -> {
        return new JsonType(IOUtils.toString(response.getEntity().getContent(), UTF_8));
      }
    }
    Header contentType = response.getFirstHeader("Content-Type");
    String rawValue = IOUtils.toString(response.getEntity().getContent(), UTF_8);
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
    update_bearer_auth((workspaceBlock, request) ->
      request.setHeader(AUTHORIZATION, "Basic " + workspaceBlock.getInputString("TOKEN"))),
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
  public static class HttpRequestEntity implements ScratchSettingBaseEntity {

    @UIField(order = 1)
    private HttpMethod httpMethod = HttpMethod.GET;

    @UIField(order = 2, icon = "fa fa-list", fullWidth = true)
    @UIFieldKeyValue(maxSize = 10, keyPlaceholder = "Header name", valuePlaceholder = "Header value",
      options = {
        @Option(key = ACCEPT, values = {TEXT_PLAIN_VALUE, TEXT_HTML_VALUE, APPLICATION_JSON_VALUE, APPLICATION_XML_VALUE}),
        @Option(key = ACCEPT_ENCODING, values = {"gzip", "deflate", "compress", "br"}),
        @Option(key = AUTHORIZATION, values = {}),
        @Option(key = CONTENT_TYPE, values = {
          "text/css",
          "application/zip",
          TEXT_PLAIN_VALUE,
          TEXT_HTML_VALUE,
          APPLICATION_JSON_VALUE,
          APPLICATION_XML_VALUE
        }),
        @Option(key = CACHE_CONTROL, values = {"max-age=0", "max-age=86400", "no-cache"}),
        @Option(key = USER_AGENT, values = {"Mozilla/5.0"}),
        @Option(key = LOCATION, values = {}),
        @Option(key = HOST, values = {})
      })
    private String httpHeaders;

    @UIField(order = 3, icon = "fa fa-cheese", fullWidth = true)
    @UIFieldKeyValue(maxSize = 10, keyPlaceholder = "Query name", valuePlaceholder = "Query value")
    private String queryParameters;

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
