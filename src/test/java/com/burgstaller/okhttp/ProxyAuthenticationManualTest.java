package com.burgstaller.okhttp;

import com.burgstaller.okhttp.basic.BasicAuthenticator;
import com.burgstaller.okhttp.digest.Credentials;
import com.burgstaller.okhttp.digest.DigestAuthenticator;
import okhttp3.Authenticator;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.logging.HttpLoggingInterceptor.Level;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests Proxy authentication. This test can only be run with a properly configured proxy server.
 * To use this test set up a proxy server with the given credentials which can be provided via
 * System properties or environment variables:
 * <dl>
 *     <dt>PROXY_HOST</dt><dd>The hostname of the proxy server</dd>
 *     <dt>PROXY_PORT</dt><dd>The TCPIP port number of the proxy server</dd>
 *     <dt>PROXY_PASSWORD</dt><dd>The proxy server password for the users 'okhttp_basic' and
 *     'okhttp_digest'</dd>
 * </dl>
 * You can set up an authenticated squid proxy by following these instructions:
 * <a href="https://blog.mafr.de/2013/06/16/setting-up-a-web-proxy-with-squid/">
 *     https://blog.mafr.de/2013/06/16/setting-up-a-web-proxy-with-squid/</a>
 * (eg. inside a docker container via
 * <pre>
 * docker run -p 13128:13128 -it ubuntu
 * </pre>).
 * <p>
 *
 * Additional instructions can be found here:
 * <a href="https://github.com/softwaremill/sttp/blob/master/manual-tests/proxy-digest-test.md">
 *     https://github.com/softwaremill/sttp/blob/master/manual-tests/proxy-digest-test.md</a>
 * </p>
 */
@Disabled
public class ProxyAuthenticationManualTest {

    private static final HttpLoggingInterceptor.Logger LOGGER = new HttpLoggingInterceptor.Logger() {
        @Override
        public void log(String message) {
            System.out.println(message);
        }
    };
    private static final HttpLoggingInterceptor LOGGING_INTERCEPTOR = new HttpLoggingInterceptor(LOGGER);
    private static final String AUTH_BASIC_USERNAME = "okhttp_basic";
    private static final String AUTH_DIGEST_USERNAME = "okhttp_digest";

    static {
        LOGGING_INTERCEPTOR.setLevel(Level.HEADERS);
    }

    private Proxy proxy;
    private String authPass = "allCorrect@auth";
    private String authUser = "okhttp_basic";

    @BeforeEach
    public void setupProxy(TestInfo testInfo) {

        String proxyAddress = System.getenv("PROXY_HOST");
        if (proxyAddress == null) {
            proxyAddress = System.getProperty("PROXY_HOST", "localhost");
        }
        String proxyPortString = System.getenv("PROXY_PORT");
        if (proxyPortString == null) {
            proxyPortString = System.getProperty("PROXY_PORT", "8080");
        }
        authPass = System.getenv("PROXY_PASSWORD");
        if (authPass == null) {
            authPass = System.getProperty("PROXY_PASSWORD", "password");
        }
        String userOverride = System.getenv("PROXY_USER");
        if (userOverride == null) {
            userOverride = System.getProperty("PROXY_USER", authUser);
        }
        if (userOverride != null) {
            authUser = userOverride;
        }

        authPass = System.getenv("PROXY_PASSWORD");
        if (authPass == null) {
            authPass = System.getProperty("PROXY_PASSWORD", "test");
        }
        int proxyPort = Integer.valueOf(proxyPortString);
        proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyAddress, proxyPort));

        System.err.println("starting " + testInfo.getTestMethod());
    }

    @Test
    public void testConnection_WithoutProxy_Expect200() throws IOException {
        final OkHttpClient client = new OkHttpClient.Builder()
                .build();
        final Request request = new Request.Builder()
                .url("https://www.google.com/favicon.ico")
                .build();
        Response response = client.newCall(request).execute();
        assertEquals(200, response.code());
    }

    @Test
    public void testConnection_WithProxyButNoAuth_ExpectAuthException() throws IOException {
        final OkHttpClient client = givenHttpClientWithoutAuth();
        final Request request = new Request.Builder()
                .url("https://www.google.com/favicon.ico")
                .build();
        try {
            client.newCall(request).execute();
            fail("this call should fail with an exception");
        } catch (IOException e) {
            assertEquals("Failed to authenticate with proxy", e.getMessage());
        }
    }

    /**
     * This test requires additional BASIC squid proxy auth setup which is not explained in the article linked above.
     * So its normal that this fails.
     */
    @Test
    public void testConnection_WithProxyBasicAuthWithoutTunnel_Expect200() throws IOException {
        final BasicAuthenticator authenticator = givenBasicAuthenticator();

        final OkHttpClient client = givenHttpClientWithProxyAuth(authenticator);
        final Request request = new Request.Builder()
                .url("http://edition.cnn.com")
                .build();
        Response response = client.newCall(request).execute();
        assertEquals(200, response.code());
    }

    /**
     * This test requires additional BASIC squid proxy auth setup which is not explained in the article linked above.
     * So its normal that this fails.
     */
    @Test
    public void testConnection_WithProxyBasicAuthWithTunnel_Expect200() throws IOException {
        final BasicAuthenticator authenticator = givenBasicAuthenticator();

        final OkHttpClient client = givenHttpClientWithProxyAuth(authenticator);
        final Request request = new Request.Builder()
                .url("https://www.google.com/favicon.ico")
                .build();
        Response response = client.newCall(request).execute();
        assertEquals(200, response.code());
    }

    /**
     * This test requires additional BASIC squid proxy auth setup which is not explained in the article linked above.
     * So its normal that this fails.
     */
    @Test
    public void testConnection_WithProxyBasicAuthWithNotAllowedSites_Expect403() throws IOException {
        final BasicAuthenticator authenticator = givenBasicAuthenticator();

        final OkHttpClient client = givenHttpClientWithProxyAuth(authenticator);
        final Request request = new Request.Builder()
                .url("http://www.youtube.com")
                .build();
        Response response = client.newCall(request).execute();
        assertEquals(403, response.code());
    }

    @Test
    public void testConnection_WithProxyDigestAuthWithoutTunnel_Expect200() throws IOException {
        final DigestAuthenticator authenticator = givenDigestAuthenticator();

        final OkHttpClient client = givenHttpClientWithProxyAuth(authenticator);
        final Request request = new Request.Builder()
                .url("http://edition.cnn.com")
                .build();
        Response response = client.newCall(request).execute();
        assertEquals(200, response.code());
    }

    @Test
    public void testConnection_WithProxyDigestAuthWithTunnel_Expect200() throws IOException {
        final DigestAuthenticator authenticator = givenDigestAuthenticator();

        final OkHttpClient client = givenHttpClientWithProxyAuth(authenticator);
        final Request request = new Request.Builder()
                .url("https://www.google.com/favicon.ico")
                .build();
        Response response = client.newCall(request).execute();
        assertEquals(200, response.code());
    }

    /**
     * This test requires a more elaborate setup with disabled sites (which is not found in the above setup article),
     * so this test will typically fail).
     */
    @Test
    public void testConnection_WithProxyDigestAuthWithNotAllowedSites_Expect403() throws IOException {
        final DigestAuthenticator authenticator = givenDigestAuthenticator();

        final OkHttpClient client = givenHttpClientWithProxyAuth(authenticator);
        final Request request = new Request.Builder()
                .url("http://www.youtube.com")
                .build();
        Response response = client.newCall(request).execute();
        assertEquals(403, response.code());
    }

    @Test
    public void testConnection_WithProxyWithoutAuth_tryGetAuthenticated__Expect200() throws IOException {
        final DigestAuthenticator authenticator = givenDigestAuthenticator();
        final OkHttpClient client = new OkHttpClient.Builder()
                .proxy(proxy)
                .authenticator(authenticator)
                .addNetworkInterceptor(LOGGING_INTERCEPTOR)
                .build();
        final Request request = new Request.Builder()
                .url("https://httpbin.org/digest-auth/auth/okhttp_basic/test")
                .build();
        Response response = client.newCall(request).execute();
        assertEquals(200, response.code());
    }

    private BasicAuthenticator givenBasicAuthenticator() {
        System.out.println("using basic authenticator with user " + authUser + ", password " + authPass);
        return new BasicAuthenticator(
                new Credentials(authUser, authPass));
    }

    private DigestAuthenticator givenDigestAuthenticator() {
        System.out.println("using digest authenticator with user " + authUser + ", password " + authPass);
        return new DigestAuthenticator(
                new Credentials(authUser, authPass));
    }

    private OkHttpClient givenHttpClientWithProxyAuth(Authenticator authenticator) {
        return new OkHttpClient.Builder()
                .proxy(proxy)
                .proxyAuthenticator(authenticator)
                .addNetworkInterceptor(LOGGING_INTERCEPTOR)
                .build();
    }

    private OkHttpClient givenHttpClientWithoutAuth() {
        return new OkHttpClient.Builder()
                .proxy(proxy)
                .addNetworkInterceptor(LOGGING_INTERCEPTOR)
                .build();
    }
}
