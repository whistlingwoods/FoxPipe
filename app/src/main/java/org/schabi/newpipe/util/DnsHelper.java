package org.schabi.newpipe.util;

import android.util.Log;

import androidx.annotation.NonNull;

import org.schabi.newpipe.DownloaderImpl;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import okhttp3.Dns;

/**
 * Utility for DNS-over-HTTPS aware HTTP connections.
 * <p>
 * Resolves hostnames via the DoH-configured OkHttpClient in {@link DownloaderImpl},
 * bypassing any system-level Private DNS blocking, then opens {@link HttpURLConnection}s
 * to the resolved IP with proper SNI and hostname verification for HTTPS.
 * </p>
 */
public final class DnsHelper {

    private static final String TAG = DnsHelper.class.getSimpleName();

    private DnsHelper() {
        // no instances
    }

    /**
     * Opens an {@link HttpURLConnection} to the given URL, resolving its hostname
     * via DNS-over-HTTPS to bypass Private DNS blocking.
     *
     * @param url the URL to connect to
     * @return an {@link HttpURLConnection} ready to be configured and connected
     * @throws IOException if an I/O error occurs
     */
    @NonNull
    public static HttpURLConnection openConnectionWithDoH(@NonNull final URL url)
            throws IOException {
        final String host = url.getHost();
        final Dns dns = DownloaderImpl.getInstance().getClient().dns();

        // Try to resolve via our DoH-configured OkHttpClient DNS
        final List<InetAddress> addresses;
        try {
            addresses = dns.lookup(host);
        } catch (final Exception e) {
            // Fallback to default connection if DoH resolution fails
            return (HttpURLConnection) url.openConnection();
        }

        if (addresses.isEmpty()) {
            return (HttpURLConnection) url.openConnection();
        }

        // Build a URL with the resolved IP instead of hostname
        final String ip = addresses.get(0).getHostAddress();
        final int port = url.getPort() == -1 ? url.getDefaultPort() : url.getPort();
        final URL ipUrl = new URL(url.getProtocol(), ip, port, url.getFile());
        final HttpURLConnection httpConn = (HttpURLConnection) ipUrl.openConnection();

        // Set the Host header to the original hostname
        httpConn.setRequestProperty("Host", host);

        // For HTTPS, configure SNI so the TLS handshake uses the original hostname
        if (httpConn instanceof HttpsURLConnection) {
            final HttpsURLConnection httpsConn = (HttpsURLConnection) httpConn;
            final SSLSocketFactory originalFactory = httpsConn.getSSLSocketFactory();

            httpsConn.setSSLSocketFactory(new SniSslSocketFactory(originalFactory, host));
            httpsConn.setHostnameVerifier((hostname, session) ->
                    host.equalsIgnoreCase(hostname)
                    || HttpsURLConnection.getDefaultHostnameVerifier()
                            .verify(host, session));
        }

        return httpConn;
    }

    /**
     * An {@link SSLSocketFactory} that sets the SNI hostname on the created
     * {@link SSLSocket}, so that HTTPS connections to an IP address still send
     * the correct hostname during the TLS handshake.
     */
    private static final class SniSslSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        private final String hostname;

        SniSslSocketFactory(final SSLSocketFactory delegate, final String hostname) {
            this.delegate = delegate;
            this.hostname = hostname;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(final Socket s, final String host,
                                   final int port, final boolean autoClose)
                throws IOException {
            final Socket socket = delegate.createSocket(s, hostname, port, autoClose);
            setSni(socket);
            return socket;
        }

        @Override
        public Socket createSocket(final String host, final int port)
                throws IOException {
            final Socket socket = delegate.createSocket(hostname, port);
            setSni(socket);
            return socket;
        }

        @Override
        public Socket createSocket(final String host, final int port,
                                   final InetAddress localHost, final int localPort)
                throws IOException {
            final Socket socket = delegate.createSocket(
                    hostname, port, localHost, localPort);
            setSni(socket);
            return socket;
        }

        @Override
        public Socket createSocket(final InetAddress host, final int port)
                throws IOException {
            final Socket socket = delegate.createSocket(host, port);
            setSni(socket);
            return socket;
        }

        @Override
        public Socket createSocket(final InetAddress address, final int port,
                                   final InetAddress localAddress,
                                   final int localPort)
                throws IOException {
            final Socket socket = delegate.createSocket(
                    address, port, localAddress, localPort);
            setSni(socket);
            return socket;
        }

        private void setSni(@NonNull final Socket socket) {
            if (socket instanceof SSLSocket) {
                final SSLSocket sslSocket = (SSLSocket) socket;
                try {
                    final Method setHostname = sslSocket.getClass()
                            .getMethod("setHostname", String.class);
                    setHostname.invoke(sslSocket, hostname);
                } catch (final Exception e) {
                    Log.w(TAG, "Failed to set SNI hostname", e);
                }
            }
        }
    }
}
