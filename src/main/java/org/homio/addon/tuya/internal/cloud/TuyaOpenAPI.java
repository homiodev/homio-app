package org.homio.addon.tuya.internal.cloud;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.annotation.adapters.HexBinaryAdapter;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.tuya.TuyaProjectEntity;
import org.homio.addon.tuya.internal.cloud.dto.CommandRequest;
import org.homio.addon.tuya.internal.cloud.dto.DeviceListInfo;
import org.homio.addon.tuya.internal.cloud.dto.DeviceSchema;
import org.homio.addon.tuya.internal.cloud.dto.FactoryInformation;
import org.homio.addon.tuya.internal.cloud.dto.ResultResponse;
import org.homio.addon.tuya.internal.cloud.dto.TuyaToken;
import org.homio.addon.tuya.internal.util.JoiningMapCollector;
import org.homio.api.model.Status;
import org.homio.api.util.CommonUtils;
import org.jetbrains.annotations.Nullable;

/**
 * The {@link TuyaOpenAPI} is an implementation of the Tuya OpenApi specification
 */
@Log4j2
public class TuyaOpenAPI {

    private static final String NONE_STRING = "";
    private static final String EMPTY_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Setter
    private TuyaProjectEntity config;

    private final Gson gson = new Gson();
    @Getter
    private TuyaToken tuyaToken = new TuyaToken();

    public boolean isConnected() {
        return !tuyaToken.accessToken.isEmpty() && System.currentTimeMillis() < tuyaToken.expireTimestamp;
    }

    public boolean refreshToken() {
        try {
            if (System.currentTimeMillis() > tuyaToken.expireTimestamp) {
                log.error("Cannot refresh token after expiry. Trying to re-login.");
                return false;
            } else {
                String result = request("/v1.0/token/" + tuyaToken.refreshToken, Map.of(), null).get();
                return processTokenResponse(result);
            }
        } catch (Exception ex) {
            log.error("Error during refresh token: {}", CommonUtils.getErrorMessage(ex));
            return false;
        }
    }

    public boolean login() {
        try {
            String response = request("/v1.0/token?grant_type=1", Map.of(), null).get();
            return processTokenResponse(response);
        } catch (Exception ex) {
            log.error("Error during login: {}", CommonUtils.getErrorMessage(ex));
            return false;
        }
    }

    private boolean processTokenResponse(String contentString) {
        Type type = TypeToken.getParameterized(ResultResponse.class, TuyaToken.class).getType();
        ResultResponse<TuyaToken> result = Objects.requireNonNull(gson.fromJson(contentString, type));

        if (result.success) {
            config.setStatus(Status.ONLINE);
            TuyaToken tuyaToken = result.result;
            if (tuyaToken != null) {
                tuyaToken.expireTimestamp = result.timestamp + tuyaToken.expire * 1000;
                log.debug("Got token: {}", tuyaToken);
                this.tuyaToken = tuyaToken;
                return true;
            }
        } else {
            log.warn("Request failed: {}, no token received", result);
            this.tuyaToken = new TuyaToken();
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
        return request("/v1.0/users/" + tuyaToken.uid + "/devices", params, null).thenCompose(
            s -> processResponse(s, TypeToken.getParameterized(List.class, DeviceListInfo.class).getType()));
    }

    public CompletableFuture<DeviceSchema> getDeviceSchema(String deviceId) {
        return request("/v1.1/devices/" + deviceId + "/specifications", Map.of(), null)
            .thenCompose(s -> processResponse(s, DeviceSchema.class));
    }

    public CompletableFuture<Boolean> sendCommand(String deviceId, CommandRequest command) {
        return request("/v1.0/iot-03/devices/" + deviceId + "/commands", Map.of(), gson.toJson(command))
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
        @Nullable String body) {
        String urlString = buildUrl(path, params);
        String t = Long.toString(System.currentTimeMillis());
        Map<String, String> signatureHeaders = new HashMap<>();

        String stringToSign = stringToSign(urlString, body, signatureHeaders);
        String sign = sign(config.getAccessID(), config.getAccessSecret().asString(), t, this.tuyaToken.accessToken, NONE_STRING, stringToSign);

        Map<String, String> headers = new HashMap<>();
        headers.put("client_id", config.getAccessID());
        headers.put("t", t);
        headers.put("Signature-Headers", String.join(":", signatureHeaders.keySet()));
        headers.put("sign", sign);
        headers.put("lang", "en");
        headers.put("nonce", "");
        headers.put("sign_method", "HMAC-SHA256");

        if (StringUtils.isNotEmpty(this.tuyaToken.accessToken)) {
            headers.put("access_token", this.tuyaToken.accessToken);
        }

        String fullUrl = config.getDataCenter().getUrl() + buildUrl(path, params);
        Builder builder = HttpRequest.newBuilder().uri(URI.create(fullUrl));

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

    private static String sign(String accessId, String secret, String t, String accessToken, String nonce, String stringToSign) {
        StringBuilder sb = new StringBuilder();
        sb.append(accessId);
        if (StringUtils.isNotBlank(accessToken)) {
            sb.append(accessToken);
        }
        sb.append(t);
        if (StringUtils.isNotBlank(nonce)) {
            sb.append(nonce);
        }
        sb.append(stringToSign);
        System.out.println(sb);
        return Sha256Util.sha256HMAC(sb.toString(), secret);
    }

    private static String stringToSign(String urlString, @Nullable String body, Map<String, String> signatureHeaders) {
        List<String> lines = new ArrayList<>(16);
        lines.add(body == null ? "GET" : "POST");
        String bodyHash = EMPTY_HASH;
        if (body != null) {
            bodyHash = Sha256Util.encryption(body);
        }
        String headerLine = "";
        if (!signatureHeaders.isEmpty()) {
            headerLine = signatureHeaders.entrySet().stream()
                                         .sorted(Map.Entry.comparingByKey())
                                         .collect(JoiningMapCollector.joining(":", "\n"));
        }
        lines.add(bodyHash);
        lines.add(headerLine);
        lines.add(urlString);
        return String.join("\n", lines);
    }

    private String buildUrl(String path, Map<String, String> params) {
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

    static class Sha256Util {

        public static String encryption(String str) {
            return encryption(str.getBytes(StandardCharsets.UTF_8));
        }

        @SneakyThrows
        public static String encryption(byte[] buf) {
            MessageDigest messageDigest;
            messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(buf);
            return byte2Hex(messageDigest.digest());
        }

        private static String byte2Hex(byte[] bytes) {
            StringBuilder stringBuffer = new StringBuilder();
            String temp;
            for (byte aByte : bytes) {
                temp = Integer.toHexString(aByte & 0xFF);
                if (temp.length() == 1) {
                    stringBuffer.append("0");
                }
                stringBuffer.append(temp);
            }
            return stringBuffer.toString();
        }

        public static String sha256HMAC(String content, String secret) {
            Mac sha256HMAC = null;
            try {
                sha256HMAC = Mac.getInstance("HmacSHA256");
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
            SecretKey secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            try {
                sha256HMAC.init(secretKey);
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            }
            byte[] digest = sha256HMAC.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return new HexBinaryAdapter().marshal(digest).toUpperCase();
        }
    }
}
