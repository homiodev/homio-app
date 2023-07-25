package org.homio.addon.tuya.internal.cloud;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.tuya.TuyaProjectEntity;
import org.homio.addon.tuya.internal.cloud.dto.CommandRequest;
import org.homio.addon.tuya.internal.cloud.dto.DeviceListInfo;
import org.homio.addon.tuya.internal.cloud.dto.DeviceSchema;
import org.homio.addon.tuya.internal.cloud.dto.FactoryInformation;
import org.homio.addon.tuya.internal.cloud.dto.LoginRequest;
import org.homio.addon.tuya.internal.cloud.dto.ResultResponse;
import org.homio.addon.tuya.internal.cloud.dto.Token;
import org.homio.addon.tuya.internal.util.CryptoUtil;
import org.homio.addon.tuya.internal.util.JoiningMapCollector;
import org.homio.api.model.Status;
import org.homio.api.util.CommonUtils;
import org.jetbrains.annotations.Nullable;

/**
 * The {@link TuyaOpenAPI} is an implementation of the Tuya OpenApi specification
 */
@Log4j2
public class TuyaOpenAPI {

    @Setter
    private TuyaProjectEntity config;

    private final Gson gson = new Gson();
    @Getter
    private Token token = new Token();

    public boolean isConnected() {
        return !token.accessToken.isEmpty() && System.currentTimeMillis() < token.expireTimestamp;
    }

    public boolean refreshToken() {
        try {
            if (System.currentTimeMillis() > token.expireTimestamp) {
                log.warn("Cannot refresh token after expiry. Trying to re-login.");
                return false;
            } else {
                String result = request("/v1.0/token/" + token.refreshToken, Map.of(), null).get();
                return this.processTokenResponse(result);
            }
        } catch (Exception ex) {
            log.error("Error during refresh token: {}", CommonUtils.getErrorMessage(ex));
            return false;
        }
    }

    public boolean login() {
        try {
            LoginRequest loginRequest = LoginRequest.fromProjectConfiguration(config);
            String response = request("/v1.0/iot-01/associated-users/actions/authorized-login", Map.of(), loginRequest).get();
            return processTokenResponse(response);
        } catch (Exception ex) {
            log.error("Error during login: {}", CommonUtils.getErrorMessage(ex));
            return false;
        }
    }

    private boolean processTokenResponse(String contentString) {
        Type type = TypeToken.getParameterized(ResultResponse.class, Token.class).getType();
        ResultResponse<Token> result = Objects.requireNonNull(gson.fromJson(contentString, type));

        if (result.success) {
            config.setStatus(Status.ONLINE);
            Token token = result.result;
            if (token != null) {
                token.expireTimestamp = result.timestamp + token.expire * 1000;
                log.debug("Got token: {}", token);
                this.token = token;
                return true;
            }
        } else {
            log.warn("Request failed: {}, no token received", result);
            this.token = new Token();
            config.setStatus(Status.ERROR, result.code + ": " + result.msg);
        }
        return false;
    }

    public CompletableFuture<List<FactoryInformation>> getFactoryInformation(List<String> deviceIds) {
        Map<String, String> params = Map.of("device_ids", String.join(",", deviceIds));
        return request("/v1.0/iot-03/devices/factory-infos", params, null).thenCompose(
            s -> processResponse(s, TypeToken.getParameterized(List.class, FactoryInformation.class).getType()));
    }

    public CompletableFuture<List<DeviceListInfo>> getDeviceList(int page) {
        Map<String, String> params = Map.of(//
            "from", "", //
            "page_no", String.valueOf(page), //
            "page_size", "100");
        return request("/v1.0/users/" + token.uid + "/devices", params, null).thenCompose(
            s -> processResponse(s, TypeToken.getParameterized(List.class, DeviceListInfo.class).getType()));
    }

    public CompletableFuture<DeviceSchema> getDeviceSchema(String deviceId) {
        return request("/v1.1/devices/" + deviceId + "/specifications", Map.of(), null)
            .thenCompose(s -> processResponse(s, DeviceSchema.class));
    }

    public CompletableFuture<Boolean> sendCommand(String deviceId, CommandRequest command) {
        return request("/v1.0/iot-03/devices/" + deviceId + "/commands", Map.of(), command)
            .thenCompose(s -> processResponse(s, Boolean.class));
    }

    private <T> CompletableFuture<T> processResponse(String contentString, Type type) {
        Type responseType = TypeToken.getParameterized(ResultResponse.class, type).getType();
        ResultResponse<T> resultResponse = Objects.requireNonNull(gson.fromJson(contentString, responseType));
        if (resultResponse.success) {
            return CompletableFuture.completedFuture(resultResponse.result);
        } else {
            if (resultResponse.code >= 1010 && resultResponse.code <= 1013) {
                log.warn("Server reported invalid token. This should never happen. Trying to re-login.");
                config.setStatus(Status.ERROR, "Server reported invalid token");
                return CompletableFuture.failedFuture(new ConnectionException(resultResponse.msg));
            }
            return CompletableFuture.failedFuture(new IllegalStateException(resultResponse.msg));
        }
    }

    private CompletableFuture<String> request(String path, Map<String, String> params,
        @Nullable Object body) {
        long now = System.currentTimeMillis();

        String sign = signRequest(path,
            Map.of("client_id", config.getAccessID()),
            List.of("client_id"), params,
            body, null, now);
        Map<String, String> headers = Map.of( //
            "client_id", config.getAccessID(), //
            "t", Long.toString(now), //
            "Signature-Headers", "client_id", //
            "sign", sign, //
            "sign_method", "HMAC-SHA256", //
            "access_token", this.token.accessToken);

        String fullUrl = config.getDataCenter().getUrl() + signUrl(path, params);
        Builder builder = HttpRequest.newBuilder().uri(URI.create(fullUrl));
//        HttpRequest request = builder.uri(URI.create(fullUrl)).build();

        headers.forEach(builder::header);
        if (body != null) {
            builder.POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)));
            builder.header("Content-Type", "application/json");
        } else {
            builder.GET();
        }
        HttpRequest request = builder.build();

        if (log.isDebugEnabled()) {
            log.debug("Sending to '{}': {}", fullUrl, requestToLogString(request));
        }
        CompletableFuture<String> future = new CompletableFuture<>();
        HttpClient.newBuilder().build().sendAsync(request, BodyHandlers.ofString())
                  .thenAccept(response -> {
                      String content = response.body();
                      if (response.statusCode() >= 200 && response.statusCode() <= 207) {
                          if (content != null) {
                              future.complete(content);
                          } else {
                              future.completeExceptionally(new ConnectionException("Content is null."));
                          }
                      } else {
                          log.debug("Requesting '{}' (method='{}', content='{}') failed: {} {}", request.uri(),
                              request.method(), body, response.statusCode(), content);
                      }
                  });
        return future;
    }

    // package private to allow tests
    String signRequest(String path, Map<String, String> headers, List<String> signHeaders,
        Map<String, String> params, @Nullable Object body, @Nullable String nonce, long now) {
        String stringToSign = stringToSign(path, headers, signHeaders, params, body);
        String tokenToUse = path.startsWith("/v1.0/token") ? "" : this.token.accessToken;
        String fullStringToSign = this.config.getAccessID() + this.config.getAccessID() + tokenToUse + now + (nonce == null ? "" : nonce) + stringToSign;

        return CryptoUtil.hmacSha256(fullStringToSign, config.getAccessSecret().asString());
    }

    private String stringToSign(String path, Map<String, String> headers, List<String> signHeaders,
        Map<String, String> params, @Nullable Object body) {
        String bodyString = CryptoUtil.sha256(body != null ? gson.toJson(body) : "");
        String headerString = headers.entrySet().stream().filter(e -> signHeaders.contains(e.getKey()))
                                     .sorted(Map.Entry.comparingByKey()).collect(JoiningMapCollector.joining(":", "\n"));
        String urlString = signUrl(path, params);
        // add extra \n after header string -> TUYAs documentation is wrong
        return "%s\n%s\n%s\n\n%s".formatted(body == null ? "GET" : "POST", bodyString, headerString, urlString);
    }

    private String signUrl(String path, Map<String, String> params) {
        String paramString = params.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .collect(JoiningMapCollector.joining("=", "&"));
        if (paramString.isEmpty()) {
            return path;
        } else {
            return path + "?" + paramString;
        }
    }

    /**
     * create a log string from a {@link HttpRequest}
     *
     * @param request the request to log
     * @return the string representing the request
     */
    private String requestToLogString(HttpRequest request) {
       /* ContentProvider contentProvider = request.getContent();
        String contentString = contentProvider == null ? "null"
                : StreamSupport.stream(contentProvider.spliterator(), false)
                        .map(b -> StandardCharsets.UTF_8.decode(b).toString()).collect(Collectors.joining(", "));

        return "Method = {" + request.getMethod() + "}, Headers = {"
                + request.getHeaders().stream().map(HttpField::toString).collect(Collectors.joining(", "))
                + "}, Content = {" + contentString + "}";*/
        return request.toString();
    }
}
