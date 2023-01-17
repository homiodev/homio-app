package org.touchhome.app.utils;

import com.sun.jersey.client.apache.ApacheHttpClient;
import com.sun.jersey.client.apache.ApacheHttpClientHandler;
import com.sun.jersey.client.apache.config.ApacheHttpClientConfig;
import com.sun.jersey.client.apache.config.DefaultApacheHttpClientConfig;
import com.sun.jersey.client.urlconnection.HTTPSProperties;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.params.HttpConnectionParams;

public final class ClientHelper {

    private ClientHelper() {}

    public static ApacheHttpClient createApacheClient() {
        HttpClient httpClient = new HttpClient();
        httpClient.getParams().setParameter(HttpConnectionParams.CONNECTION_TIMEOUT, 10000);
        httpClient.getParams().setParameter(HttpConnectionParams.SO_TIMEOUT, 10000);

        ApacheHttpClientConfig clientConfig = ClientHelper.configureApacheClient(null);
        return new ApacheHttpClient(new ApacheHttpClientHandler(httpClient, clientConfig), null);
    }

    public static ApacheHttpClientConfig configureApacheClient(SSLContext sslContext) {
        ApacheHttpClientConfig config = new DefaultApacheHttpClientConfig();
        if (sslContext != null) {
            config.getProperties()
                    .put(
                            HTTPSProperties.PROPERTY_HTTPS_PROPERTIES,
                            new HTTPSProperties(new PermissiveHostnameVerifier(), sslContext));
        }
        return config;
    }

    public static void setAuthPreemptive(
            final ApacheHttpClient client, final boolean isPreemptive) {
        client.getClientHandler()
                .getConfig()
                .getProperties()
                .put(ApacheHttpClientConfig.PROPERTY_PREEMPTIVE_AUTHENTICATION, isPreemptive);
    }

    private static class PermissiveHostnameVerifier implements HostnameVerifier {

        @Override
        public boolean verify(String s, SSLSession sslSession) {
            return true;
        }
    }
}
