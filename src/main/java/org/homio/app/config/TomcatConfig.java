package org.homio.app.config;

import nl.altindag.ssl.SSLFactory;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.apache.tomcat.util.net.SSLContext;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.*;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

@Configuration
public class TomcatConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Value("${ssl.port:9911}")
    private int sslPort;

    @Value("${ssl.keystore-path}")
    private String keyStorePath;

    @Value("${ssl.keystore-password}")
    private char[] keyStorePassword;

    @Value("${ssl.truststore-path}")
    private String trustStorePath;

    @Value("${ssl.truststore-password}")
    private char[] trustStorePassword;

    @Value("${ssl.client-auth}")
    private boolean isClientAuthenticationRequired;

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        SSLFactory sslFactory = SSLFactory.builder()
                .withSwappableIdentityMaterial()
                .withSwappableTrustMaterial()
                .withIdentityMaterial(keyStorePath, keyStorePassword)
                .withTrustMaterial(trustStorePath, trustStorePassword)
                .withNeedClientAuthentication(isClientAuthenticationRequired)
                .build();
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        configureHttpsConnector(connector, sslFactory);
        factory.addAdditionalTomcatConnectors(connector);
    }

    private void configureHttpsConnector(Connector connector, SSLFactory sslFactory) {
        connector.setScheme("https");
        connector.setSecure(true);
        connector.setPort(sslPort);

        AbstractHttp11Protocol<?> protocol = (AbstractHttp11Protocol<?>) connector.getProtocolHandler();
        protocol.setSSLEnabled(true);

        SSLHostConfig sslHostConfig = new SSLHostConfig();
        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(sslHostConfig, SSLHostConfigCertificate.Type.UNDEFINED);
        certificate.setSslContext(new TomcatSSLContext(sslFactory));
        sslHostConfig.addCertificate(certificate);
        protocol.addSslHostConfig(sslHostConfig);
    }

    private record TomcatSSLContext(SSLFactory sslFactory) implements SSLContext {

        @Override
        public void init(KeyManager[] kms, TrustManager[] tms, SecureRandom sr) {
            // not needed to initialize as it is already initialized
        }

        @Override
        public void destroy() {

        }

        @Override
        public SSLSessionContext getServerSessionContext() {
            return sslFactory.getSslContext().getServerSessionContext();
        }

        @Override
        public SSLEngine createSSLEngine() {
            return sslFactory.getSSLEngine();
        }

        @Override
        public SSLServerSocketFactory getServerSocketFactory() {
            return sslFactory.getSslServerSocketFactory();
        }

        @Override
        public SSLParameters getSupportedSSLParameters() {
            return sslFactory.getSslParameters();
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return sslFactory.getKeyManager()
                    .map(keyManager -> keyManager.getCertificateChain(alias))
                    .orElseThrow();
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return sslFactory.getTrustedCertificates().toArray(new X509Certificate[0]);
        }

    }
}
