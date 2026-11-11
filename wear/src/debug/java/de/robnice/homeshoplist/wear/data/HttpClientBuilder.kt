package de.robnice.homeshoplist.wear.data

import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Debug builds only: accepts self-signed certificates of local test servers.
 * Kept out of src/main so this code never ships in a release build
 * (Google Play rejects apps containing an unsafe X509TrustManager).
 */
@Suppress("TrustAllX509TrustManager", "CustomX509TrustManager")
internal fun baseHttpClientBuilder(): OkHttpClient.Builder {
    val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    })
    val sslContext = SSLContext.getInstance("TLS").apply { init(null, trustAll, SecureRandom()) }
    return OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, trustAll[0] as X509TrustManager)
        .hostnameVerifier { _, _ -> true }
}
