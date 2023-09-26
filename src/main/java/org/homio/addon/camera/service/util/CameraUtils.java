package org.homio.addon.camera.service.util;

import static org.springframework.http.HttpHeaders.WWW_AUTHENTICATE;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.DigestScheme;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.homio.addon.camera.onvif.util.Helper;
import org.homio.api.exception.ServerException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;

@Log4j2
public class CameraUtils {

    private static Map<String, AuthHandler> imageAuthHandler = new ConcurrentHashMap<>();

    @SneakyThrows
    public static void downloadImage(@NotNull String snapshotUri, @Nullable String user, @Nullable String password, @NotNull Path output) {
        Builder request = HttpRequest.newBuilder().uri(new URI(snapshotUri)).GET();
        AuthHandler authHandler = imageAuthHandler.get(snapshotUri);
        if (authHandler != null) {
            authHandler.run(request, user, password);
        }
        HttpResponse<InputStream> response = HttpClient.newHttpClient().send(request.build(), BodyHandlers.ofInputStream());
        if (response.statusCode() == 401) {
            String authenticate = response.headers().firstValue(WWW_AUTHENTICATE).orElse(null);
            if(StringUtils.isNotEmpty(authenticate)) {
                authHandler = (requestBuilder, user1, password1) -> {
                    DigestScheme md5Auth = new DigestScheme();
                    md5Auth.processChallenge(new BasicHeader(WWW_AUTHENTICATE, authenticate));
                    Header solution = md5Auth.authenticate(
                        new UsernamePasswordCredentials(user1, password1),
                        new BasicHttpRequest("GET", new URL(snapshotUri).getPath()), new HttpClientContext());
                    requestBuilder.header(solution.getName(), solution.getValue());
                };
            }
            //authHandler = buildAuthHandler(response, user, password, snapshotUri);
            if (authHandler != null) {
                imageAuthHandler.put(snapshotUri, authHandler);
                authHandler.run(request, user, password);
                response = HttpClient.newHttpClient().send(request.build(), BodyHandlers.ofInputStream());
            }
        }
        if (response.statusCode() != 200) {
            String body;
            try (InputStream stream = response.body()) {
                body = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            throw new RuntimeException("Error while download snapshot <" + snapshotUri + ">. Code: " +
                response.statusCode() + ". Msg: " + body);
        }
        try (InputStream inputStream = response.body()) {
            Files.copy(inputStream, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static @Nullable AuthHandler buildAuthHandler(HttpResponse<InputStream> response, String user,
        String password, @NotNull String snapshotUri) {
        Optional<String> authenticateOptional = response.headers().firstValue("WWW-Authenticate");
        if (authenticateOptional.isEmpty()) {
            return null;
        }
        String authenticate = authenticateOptional.get();
        if (authenticate.contains("Basic realm=\"")) {
            return (requestBuilder, user1, password1) -> {
                String auth = "Basic " + Base64.getEncoder().encodeToString((user1 + ":" + password1).getBytes());
                requestBuilder.header(HttpHeaders.AUTHORIZATION, auth);
            };
        }
        /////// Fresh Digest Authenticate method follows as Basic is already handled and returned ////////
        String realm = Helper.searchString(authenticate, "realm=\"");
        if (realm.isEmpty()) {
            log.warn("Could not find a valid WWW-Authenticate response in :{}", authenticate);
            return null;
        }
        String nonce = Helper.searchString(authenticate, "nonce=\"");
        String opaque = Helper.searchString(authenticate, "opaque=\"");
        String qop = Helper.searchString(authenticate, "qop=\"");

        if (qop.isEmpty()) {
            log.warn("!!!! Something is wrong with the reply back from the camera. WWW-Authenticate header: qop:{}, realm:{}", qop, realm);
        }

        String stale = Helper.searchString(authenticate, "stale=\"");
        if (stale.equalsIgnoreCase("true")) {
            log.debug("Camera reported stale=true which normally means the NONCE has expired.");
        }

        if (password.isEmpty()) {
            throw new ServerException("Camera gave a 401 reply: You need to provide a password.");
        }
        return (requestBuilder, user12, password12) -> {
            String ha1 = user12 + ":" + realm + ":" + password12;
            ha1 = calcMD5Hash(ha1);
            Random random = new SecureRandom();
            String cnonce = Integer.toHexString(random.nextInt());
            int ncCounter = new Random().nextInt(125) + 1;
            String nc = String.format("%08X", ncCounter); // 8 digit hex number
            String ha2 = "GET" + ":" + snapshotUri;
            ha2 = calcMD5Hash(ha2);

            String response1 = ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2;
            response1 = calcMD5Hash(response1);

            String digestString = "username=\"" + user12 + "\", realm=\"" + realm + "\", nonce=\"" + nonce + "\", uri=\""
                + snapshotUri + "\", cnonce=\"" + cnonce + "\", nc=" + nc + ", qop=\"" + qop + "\", response=\""
                + response1 + "\"";
            if (!opaque.isEmpty()) {
                digestString += ", opaque=\"" + opaque + "\"";
            }
            requestBuilder.header(HttpHeaders.AUTHORIZATION, "Digest " + digestString);
        };
    }

    private interface AuthHandler {

        void run(Builder requestBuilder, @Nullable String user, @Nullable String password) throws Exception;
    }

    public static String calcMD5Hash(String toHash) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte[] array = messageDigest.digest(toHash.getBytes());
            StringBuilder stringBuffer = new StringBuilder();
            for (byte bt : array) {
                stringBuffer.append(Integer.toHexString((bt & 0xFF) | 0x100), 1, 3);
            }
            return stringBuffer.toString();
        } catch (NoSuchAlgorithmException e) {
            log.warn("NoSuchAlgorithmException error when calculating MD5 hash");
        }
        return "";
    }
}
