package org.homio.addon.tuya.internal.cloud;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.tuya.TuyaDeviceEntity;
import org.homio.addon.tuya.TuyaProjectEntity;
import org.homio.addon.tuya.internal.cloud.dto.*;
import org.homio.addon.tuya.internal.util.JoiningMapCollector;
import org.homio.api.entity.DeviceBaseEntity;
import org.homio.api.model.Status;
import org.homio.api.util.CommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.annotation.adapters.HexBinaryAdapter;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.util.*;

/**
 * Implementation of the Tuya OpenApi specification
 */
@Log4j2
@Service
public class TuyaOpenAPI {

    public static final @NotNull Gson gson = new Gson();
    private static final String NONE_STRING = "";
    private static final String EMPTY_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private static TuyaProjectEntity projectEntity;

    private @NotNull TuyaTokenDTO token = new TuyaTokenDTO();

    public static void setProjectEntity(@NotNull TuyaProjectEntity projectEntity) {
        TuyaOpenAPI.projectEntity = projectEntity;
    }

    public boolean isConnected() {
        return !token.accessToken.isEmpty() && System.currentTimeMillis() < token.expireTimestamp;
    }

    public synchronized void login() throws Exception {
        assertApiReady();
        if (isConnected()) {
            return;
        }
        try {
            String response = request("/v1.0/token?grant_type=1", Map.of(), null);
            Type type = TypeToken.getParameterized(ResultResponse.class, TuyaTokenDTO.class).getType();
            ResultResponse<TuyaTokenDTO> result = Objects.requireNonNull(gson.fromJson(response, type));

            if (result.success) {
                projectEntity.setStatus(Status.ONLINE);
                TuyaTokenDTO tuyaToken = result.result;
                if (tuyaToken != null) {
                    tuyaToken.expireTimestamp = result.timestamp + tuyaToken.expire * 1000;
                    log.debug("[{}]: Got token: {}", projectEntity.getEntityID(), tuyaToken);
                    this.token = tuyaToken;
                }
            } else {
                log.warn("[{}]: Request failed: {}, no token received", projectEntity.getEntityID(), result);
                this.token = new TuyaTokenDTO();
                projectEntity.setStatus(Status.ERROR, result.code + ": " + result.msg);
                throw new IllegalStateException("Request failed: %s, no token received".formatted(result));
            }
        } catch (Exception ex) {
            log.error("[{}]: Error during login: {}", projectEntity.getEntityID(), CommonUtils.getErrorMessage(ex));
            throw ex;
        }
    }

    private static void assertApiReady() {
        if (projectEntity == null) {
            throw new TuyaApiNotReadyException();
        }
    }

    public TuyaDeviceDTO getDevice(String deviceID, TuyaDeviceEntity entity) throws Exception {
        login();
        String response = request("/v1.1/iot-03/devices/" + deviceID, Map.of(), null);
        return processResponse(response, TuyaDeviceDTO.class, entity);
    }

    @SneakyThrows
    public List<TuyaSubDeviceInfoDTO> getSubDevices(String deviceID, TuyaDeviceEntity entity) {
        login();
        String response = request("/v1.0/devices/" + deviceID + "/sub-devices", Map.of(), null);
        return processResponse(response, TypeToken.getParameterized(List.class, TuyaSubDeviceInfoDTO.class).getType(), entity);
    }


    public List<FactoryInformation> getFactoryInformation(List<String> deviceIds, TuyaDeviceEntity entity) throws Exception {
        login();
        Map<String, String> params = Map.of("device_ids", String.join(",", deviceIds));
        String response = request("/v1.0/iot-03/devices/factory-infos", params, null);
        return processResponse(response, TypeToken.getParameterized(List.class, FactoryInformation.class).getType(), entity);
    }

    public List<TuyaDeviceDTO> getDeviceList(int page) throws Exception {
        login();
        Map<String, String> params = Map.of(
                "from", "",
                "page_no", String.valueOf(page),
                "page_size", "100");
        String response = request("/v1.0/users/" + projectEntity.getTuyaUserUID() + "/devices", params, null);
        return processResponse(response, TypeToken.getParameterized(List.class, TuyaDeviceDTO.class).getType(), projectEntity);
    }

    public DeviceSchema getDeviceSchema(String deviceId, TuyaDeviceEntity entity) throws Exception {
        login();
        String response = request("/v1.1/devices/" + deviceId + "/specifications", Map.of(), null);
        return processResponse(response, DeviceSchema.class, entity);
    }

    public Boolean sendCommand(String deviceId, CommandRequest command, TuyaDeviceEntity entity) throws Exception {
        login();
        String response = request("/v1.0/iot-03/devices/" + deviceId + "/commands", Map.of(), gson.toJson(command));
        return processResponse(response, Boolean.class, entity);
    }

    private <T> T processResponse(String contentString, Type type, DeviceBaseEntity entity) throws ConnectionException {
        Type responseType = TypeToken.getParameterized(ResultResponse.class, type).getType();
        ResultResponse<T> resultResponse = Objects.requireNonNull(gson.fromJson(contentString, responseType));
        if (resultResponse.success) {
            return resultResponse.result;
        } else {
            if (resultResponse.code >= 1010 && resultResponse.code <= 1013) {
                log.warn("[{}]: Server reported invalid token. This should never happen. Trying to re-login.", entity.getEntityID());
                entity.setStatus(Status.ERROR, resultResponse.code + ":Server reported invalid token");
                throw new ConnectionException(resultResponse.msg);
            } else {
                entity.setStatus(Status.ERROR, "%s:%s".formatted(resultResponse.code, resultResponse.msg));
            }
            throw new IllegalStateException(resultResponse.msg);
        }
    }

    private String request(String path, Map<String, String> params,
        @Nullable String body) throws Exception {
        assertApiReady();
        String urlString = buildUrl(path, params);
        String t = Long.toString(System.currentTimeMillis());
        Map<String, String> signatureHeaders = new HashMap<>();

        String stringToSign = stringToSign(urlString, body, signatureHeaders);
        String sign = sign(projectEntity.getAccessID(), projectEntity.getAccessSecret().asString(), t, this.token.accessToken, NONE_STRING, stringToSign);

        Map<String, String> headers = new HashMap<>();
        headers.put("client_id", projectEntity.getAccessID());
        headers.put("t", t);
        headers.put("Signature-Headers", String.join(":", signatureHeaders.keySet()));
        headers.put("sign", sign);
        headers.put("lang", "en");
        headers.put("nonce", "");
        headers.put("sign_method", "HMAC-SHA256");

        if (StringUtils.isNotEmpty(this.token.accessToken)) {
            headers.put("access_token", this.token.accessToken);
        }

        String fullUrl = projectEntity.getDataCenter().getUrl() + buildUrl(path, params);
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
        HttpResponse<String> response = HttpClient.newBuilder().build().send(request, BodyHandlers.ofString());
        String content = response.body();
        if (response.statusCode() >= 200 && response.statusCode() <= 207) {
            if (content != null) {
                return content;
            } else {
                throw new ConnectionException("Content is null.");
            }
        } else {
            log.debug("Requesting '{}' (method='{}', content='{}') failed: {} {}", request.uri(),
                request.method(), body, response.statusCode(), content);
            throw new ConnectionException("Request failed " + content);
        }
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
        log.debug(sb);
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

        @SneakyThrows
        public static String sha256HMAC(String content, String secret) {
            Mac sha256HMAC = Mac.getInstance("HmacSHA256");
            SecretKey secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            try {
                sha256HMAC.init(secretKey);
            } catch (InvalidKeyException ex) {
                log.error("Error sha256HMAC", ex);
            }
            byte[] digest = sha256HMAC.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return new HexBinaryAdapter().marshal(digest).toUpperCase();
        }
    }

    public static class TuyaApiNotReadyException extends IllegalStateException {
        @Override
        public String getMessage() {
            return "Tuya api not ready yet";
        }
    }
}
