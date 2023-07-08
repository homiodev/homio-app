package org.homio.app.rest;

import static org.apache.commons.io.FileUtils.ONE_MB_BI;
import static org.apache.commons.io.IOUtils.DEFAULT_BUFFER_SIZE;
import static org.apache.commons.io.IOUtils.EOF;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.Collections;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpMessageConverterExtractor;
import org.springframework.web.client.RestTemplate;

@Log4j2
@RequiredArgsConstructor
@SuppressWarnings("unused")
public final class Curl {

    private static final RestTemplate restTemplate = new RestTemplate();

    public static <T> T get(String url, Class<T> responseType, Object... uriVariables) {
        return restTemplate.getForObject(url, responseType, uriVariables);
    }

    public static <T> T post(String url, Object request, Class<T> responseType,
        Object... uriVariables) {
        return restTemplate.postForObject(url, request, responseType, uriVariables);
    }

    public static void delete(String url, Object... uriVariables) {
        restTemplate.delete(url, uriVariables);
    }

    @SneakyThrows
    public static void download(String url, Path targetPath) {
        FileUtils.copyURLToFile(new URL(url), targetPath.toFile(), 60000, 60000);
    }

    @SneakyThrows
    public static RawResponse download(String path, int maxSize) {
        return download(path, maxSize, null, null);
    }

    /**
     * Download file to byte array. Throw error if downloading exceeded maxSize
     */
    @SneakyThrows
    public static RawResponse download(String path, Integer maxSize, String user, String password) {
        HttpGet request = new HttpGet(path);
        if (user == null || password == null) {
            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build();
                 CloseableHttpResponse response = httpClient.execute(request)) {
                return download(response, path, maxSize);
            }
        }
        // request.addHeader(AUTHORIZATION, "Basic " + Base64Utils.encodeToString((user + ":" + password).getBytes
        // (StandardCharsets.UTF_8)));
        HttpHost target = new HttpHost(request.getURI().getHost(), request.getURI().getPort(), request.getURI().getScheme());

        CredentialsProvider provider = new BasicCredentialsProvider();
        provider.setCredentials(
            new AuthScope(target.getHostName(), target.getPort()),
            new UsernamePasswordCredentials(user, password));

        AuthCache authCache = new BasicAuthCache();
        authCache.put(target, new BasicScheme());

        HttpClientContext localContext = HttpClientContext.create();
        localContext.setAuthCache(authCache);

        try (CloseableHttpClient httpClient = HttpClientBuilder.create()
                                                               .setDefaultCredentialsProvider(provider)
                                                               .build();
             CloseableHttpResponse response = httpClient.execute(target, request, localContext)) {

            // 401 if wrong user/password
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("Error while download from <" + path + ">. Code: " +
                    response.getStatusLine().getStatusCode() + ". Msg: " + response.getStatusLine().getReasonPhrase());
            }

            HttpEntity entity = response.getEntity();
            if (entity != null) {
                return download(response, path, maxSize);
            }
        }
        return null;
    }

    @SneakyThrows
    private static RawResponse download(CloseableHttpResponse response, String path, Integer maxSize) {
        if (response.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException(
                "Error while download from <" + path + ">. Code: " + response.getStatusLine().getStatusCode() + ". Msg: " +
                    response.getStatusLine().getReasonPhrase());
        }
        String name = FilenameUtils.getName(path);
        if (maxSize != null) {
            try (final InputStream input = response.getEntity().getContent()) {
                try (final ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    int count = 0, n;
                    byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
                    while (EOF != (n = input.read(buffer))) {
                        output.write(buffer, 0, n);
                        count += n;
                        if (count > maxSize) {
                            throw new IllegalArgumentException("Exceeded max length " + maxSize);
                        }
                    }
                    return new RawResponse(output.toByteArray(), response.getFirstHeader(CONTENT_TYPE).getValue(), name);
                }
            }
        } else {
            return new RawResponse(EntityUtils.toByteArray(response.getEntity()),
                response.getFirstHeader(CONTENT_TYPE).getValue(), name);
        }
    }

    public static RawResponse download(String path) {
        return download(path, null, null);
    }

    @SneakyThrows
    public static RawResponse download(String path, String user, String password) {
        return download(path, null, user, password);
    }

    @SneakyThrows
    public static void downloadWithProgress(String urlStr, Path targetPath, BiConsumer<Double, String> progressBar) {
        progressBar.accept(1D, "Checking file size...");
        URL url = new URL(urlStr);
        double fileSize = getFileSize(url);
        // download without progress if less then 2 megabytes
        if (fileSize / 1000 < 2) {
            download(urlStr, targetPath);
            return;
        }
        int maxMb = (int) (fileSize / ONE_MB_BI.intValue());
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(60000);
        connection.setReadTimeout(60000);
        InputStream input = connection.getInputStream();
        FileUtils.copyInputStreamToFile(new FilterInputStream(input) {
            int readBytes = 0;
            final Consumer<Integer> progressHandler = new Consumer<>() {
                int nextStep = 1;

                @Override
                public void accept(Integer num) {
                    readBytes += num;
                    if (readBytes / ONE_MB_BI.doubleValue() > nextStep) {
                        nextStep++;
                        progressBar.accept((readBytes / fileSize * 100) * 0.9, // max 90%
                            "Downloading " + readBytes / ONE_MB_BI.intValue() + "Mb. of " + maxMb + " Mb.");
                    }
                }
            };

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                int read = super.read(b, off, len);
                progressHandler.accept(read);
                return read;
            }
        }, targetPath.toFile());
    }

    public static int getFileSize(URL url) {
        URLConnection conn = null;
        try {
            conn = url.openConnection();
            if (conn instanceof HttpURLConnection) {
                ((HttpURLConnection) conn).setRequestMethod("HEAD");
            }
            conn.getInputStream();
            return conn.getContentLength();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            if (conn instanceof HttpURLConnection) {
                ((HttpURLConnection) conn).disconnect();
            }
        }
    }

    @SneakyThrows
    public static <T> T getWithTimeout(String command, Class<T> returnType, int timeoutInSec) {
        CloseableHttpResponse response = createApacheHttpClient(timeoutInSec).execute(new HttpGet(command));
        HttpMessageConverterExtractor<T> responseExtractor =
            new HttpMessageConverterExtractor<>(returnType, restTemplate.getMessageConverters());
        return responseExtractor.extractData(new ClientHttpResponse() {
            @Override
            public HttpStatus getStatusCode() {
                return null;
            }

            @Override
            public int getRawStatusCode() {
                return response.getStatusLine().getStatusCode();
            }

            @Override
            public String getStatusText() {
                return response.getStatusLine().getReasonPhrase();
            }

            @Override
            @SneakyThrows
            public void close() {
                response.close();
            }

            @Override
            public InputStream getBody() throws IOException {
                return response.getEntity().getContent();
            }

            @Override
            public HttpHeaders getHeaders() {
                MultiValueMap<String, String> headers = new LinkedMultiValueMap<>(response.getAllHeaders().length);
                for (Header header : response.getAllHeaders()) {
                    headers.put(header.getName(), Collections.singletonList(header.getValue()));
                }
                return new HttpHeaders(headers);
            }
        });
    }

    private static CloseableHttpClient createApacheHttpClient(int timeoutInSec) {
        RequestConfig config = RequestConfig.custom()
                                            .setConnectTimeout(timeoutInSec * 1000)
                                            .setSocketTimeout(timeoutInSec * 1000).build();
        return HttpClientBuilder.create().setDefaultRequestConfig(config).build();
    }

    @Getter
    public static class RawResponse {

        protected String name;
        protected byte[] bytes;
        protected String mimeType;

        public RawResponse(byte[] bytes, String mimeType, String name) {
            if (mimeType.isEmpty()) {
                throw new IllegalArgumentException("mimeType argument must not be blank");
            }
            this.bytes = bytes;
            this.mimeType = mimeType;
            this.name = name;
        }
    }
}
