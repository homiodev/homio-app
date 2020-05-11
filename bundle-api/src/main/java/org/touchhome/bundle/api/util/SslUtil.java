package org.touchhome.bundle.api.util;

import lombok.SneakyThrows;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;

public class SslUtil {
    private static final String JKS = "JKS";
    private static final String PROTOCOL = "TLSv1.2";

    private static KeyManagerFactory createKeyManagerFactory(byte[] certificate, char[] storePassword, char[] keyPassword) throws GeneralSecurityException, IOException {
        String algorithm = KeyManagerFactory.getDefaultAlgorithm();
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);

        KeyStore ks = KeyStore.getInstance(JKS);
        try (InputStream ksIs = new ByteArrayInputStream(certificate)) {
            ks.load(ksIs, storePassword);
        }
        kmf.init(ks, keyPassword);

        return kmf;
    }

    public static SSLContext createSSLContext(byte[] certificate, String password) throws Exception {
        SSLContext context = SSLContext.getInstance(PROTOCOL);
        TrustManager[] trustManagers = SslUtil.createTrustManagers(certificate, password.toCharArray());
        KeyManager[] keyManagers = SslUtil.createKeyManagerFactory(certificate, password.toCharArray(), password.toCharArray()).getKeyManagers();
        context.init(keyManagers, trustManagers, new SecureRandom());
        return context;
    }

    @SneakyThrows
    public static void validateKeyStore(byte[] certificate, String password) {
        KeyStore ks = KeyStore.getInstance(JKS);
        try (InputStream ksIs = new ByteArrayInputStream(certificate)) {
            ks.load(ksIs, password.toCharArray());
        }
    }

    private static TrustManager[] createTrustManagers(byte[] certificate, char[] password) throws GeneralSecurityException, IOException {
        String algorithm = TrustManagerFactory.getDefaultAlgorithm();
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(algorithm);

        KeyStore ks = KeyStore.getInstance(JKS);
        try (InputStream ksIs = new ByteArrayInputStream(certificate)) {
            ks.load(ksIs, password);
        }

        tmf.init(ks);

        return tmf.getTrustManagers();
    }
}
