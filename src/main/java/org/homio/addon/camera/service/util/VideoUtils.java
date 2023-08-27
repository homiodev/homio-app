package org.homio.addon.camera.service.util;

import lombok.SneakyThrows;

import java.io.InputStream;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VideoUtils {

    @SneakyThrows
    public static void downloadImage(@NotNull String snapshotUri, @Nullable String user, @Nullable String password, @NotNull Path output) {
        HttpRequest request = HttpRequest.newBuilder().uri(new URI(snapshotUri)).GET().build();
        HttpResponse<InputStream> response;
        if (user == null || password == null || snapshotUri.contains("&token=")) {
            response = HttpClient.newHttpClient().send(request, BodyHandlers.ofInputStream());
        } else {
            response = HttpClient.newBuilder()
                    .authenticator(new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(
                                    user,
                                    password.toCharArray());
                        }
                    }).build()
                    .send(request, BodyHandlers.ofInputStream());
            // 401 if wrong user/password
            if (response.statusCode() != 200) {
                String body;
                try (InputStream stream = response.body()) {
                    body = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                }
                throw new RuntimeException("Error while download snapshot <" + snapshotUri + ">. Code: " +
                        response.statusCode() + ". Msg: " + body);
            }
        }
        try (InputStream inputStream = response.body()) {
            Files.copy(inputStream, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
