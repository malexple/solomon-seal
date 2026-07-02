package ru.mcs.solomonseal.core.llm;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;

/**
 * Утилита для построения HttpClient, опционально отключающего проверку
 * цепочки SSL-сертификатов. Нужна для локальных/внутренних endpoint'ов
 * (например Ollama по HTTPS с самоподписанным сертификатом),
 * где стандартный JVM truststore не может построить PKIX-путь доверия.
 *
 * ВНИМАНИЕ: trustSelfSigned=true отключает защиту от MITM.
 * Использовать только для доверенных внутренних адресов.
 */
public final class InsecureSslUtil {

    private InsecureSslUtil() {}

    public static HttpClient buildClient(boolean trustSelfSigned) {
        var builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10));

        if (trustSelfSigned) {
            builder.sslContext(buildInsecureSslContext());
        }
        return builder.build();
    }

    private static SSLContext buildInsecureSslContext() {
        try {
            var trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };
            var sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException("Failed to build insecure SSLContext", e);
        }
    }
}