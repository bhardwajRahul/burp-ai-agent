package com.six2dez.burp.aiagent.mcp

import burp.api.montoya.MontoyaApi
import com.six2dez.burp.aiagent.config.McpSettings
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.lang.reflect.Method
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.nio.file.Files
import java.nio.file.Path
import java.security.Principal
import java.security.cert.Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

/**
 * SEC-07 / SC5 — the truth table of `McpSupervisor.openConnection`'s loopback TLS branch, asserted
 * through a fake connection so these stay fast and network-free. The handshake itself is proven
 * against a real server in `McpTakeoverCertificatePinTest`; wiring and handshake are different
 * questions and both need answering.
 *
 * The keystore is generated once into a temp directory through `McpTls.resolve`, never at the user's
 * real `~/.burp-ai-agent/certs` path — `resolve` auto-generates into whatever path it is handed.
 */
@Suppress("DEPRECATION")
class McpSupervisorConnectionTest {
    companion object {
        private lateinit var keystoreDir: Path

        @JvmStatic
        @BeforeAll
        fun createKeystore() {
            keystoreDir = Files.createTempDirectory("mcp-conn-ks")
            // Generates the PKCS12 the pin is later computed from.
            McpTls.resolve(tlsSettings())
        }

        @JvmStatic
        @AfterAll
        fun deleteKeystoreDir() {
            keystoreDir.toFile().deleteRecursively()
        }

        private fun tlsSettings(): McpSettings = McpTestServerSupport.localTlsSettings(port = 8443, keystoreDir = keystoreDir)
    }

    private val supervisor = McpSupervisor(mock<MontoyaApi>())

    @Test
    fun openConnection_loopbackTls_setsCustomTrustAndHostnameVerifier() {
        val url = URL(null, "https://localhost:8443/test", connectionHandler())
        val connection = invokeOpenConnection(url, tlsSettings()) as FakeHttpsURLConnection

        assertNotNull(connection.assignedSslSocketFactory)
        assertNotNull(connection.assignedHostnameVerifier)
    }

    @Test
    fun openConnection_nonLoopbackTls_doesNotOverrideTlsVerifier() {
        val url = URL(null, "https://example.com:8443/test", connectionHandler())
        val connection = invokeOpenConnection(url, tlsSettings()) as FakeHttpsURLConnection

        assertNull(connection.assignedSslSocketFactory)
        assertNull(connection.assignedHostnameVerifier)
    }

    @Test
    fun openConnection_loopbackWithoutTls_doesNotOverrideTlsVerifier() {
        val url = URL(null, "https://localhost:8443/test", connectionHandler())
        val connection = invokeOpenConnection(url, tlsSettings().copy(tlsEnabled = false)) as FakeHttpsURLConnection

        assertNull(connection.assignedSslSocketFactory)
        assertNull(connection.assignedHostnameVerifier)
    }

    private fun invokeOpenConnection(
        url: URL,
        settings: McpSettings,
    ): URLConnection {
        val method: Method =
            supervisor.javaClass.getDeclaredMethod(
                "openConnection",
                URL::class.java,
                McpSettings::class.java,
            )
        method.isAccessible = true
        return method.invoke(supervisor, url, settings) as URLConnection
    }

    private fun connectionHandler(): URLStreamHandler =
        object : URLStreamHandler() {
            override fun openConnection(url: URL): URLConnection = FakeHttpsURLConnection(url)
        }

    private class FakeHttpsURLConnection(
        url: URL,
    ) : HttpsURLConnection(url) {
        var assignedSslSocketFactory: SSLSocketFactory? = null
        var assignedHostnameVerifier: HostnameVerifier? = null

        override fun connect() = Unit

        override fun disconnect() = Unit

        override fun usingProxy(): Boolean = false

        override fun setSSLSocketFactory(sf: SSLSocketFactory?) {
            assignedSslSocketFactory = sf
        }

        override fun getSSLSocketFactory(): SSLSocketFactory? = assignedSslSocketFactory

        override fun setHostnameVerifier(v: HostnameVerifier?) {
            assignedHostnameVerifier = v
        }

        override fun getHostnameVerifier(): HostnameVerifier? = assignedHostnameVerifier

        override fun getCipherSuite(): String = "TLS_FAKE"

        override fun getLocalCertificates(): Array<Certificate>? = null

        override fun getServerCertificates(): Array<Certificate>? = null

        override fun getPeerPrincipal(): Principal? = null

        override fun getLocalPrincipal(): Principal? = null
    }
}
