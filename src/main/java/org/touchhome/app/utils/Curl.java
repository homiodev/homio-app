package org.touchhome.app.utils;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URL;
import java.nio.file.Path;

@Log4j2
@RequiredArgsConstructor
public final class Curl {
    private static final RestTemplate restTemplate = new RestTemplate();

    public static <T> T get(String url, Class<T> responseType, Object... uriVariables) {
        return restTemplate.getForObject(url, responseType, uriVariables);
    }

    @SneakyThrows
    public static void downloadToFile(String url, Path targetPath) {
        FileUtils.copyURLToFile(new URL(url), targetPath.toFile(), 30000, 30000);
    }
}
