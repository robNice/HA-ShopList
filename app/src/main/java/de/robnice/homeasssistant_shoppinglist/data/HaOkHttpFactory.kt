package de.robnice.homeasssistant_shoppinglist.data

import de.robnice.homeasssistant_shoppinglist.BuildConfig
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object HaOkHttpFactory {

    fun newBuilder(): OkHttpClient.Builder {
        val builder = if (BuildConfig.ALLOW_INSECURE_HA) {
            buildInsecureBuilder()
        } else {
            OkHttpClient.Builder()
        }

        return builder.pingInterval(30, TimeUnit.SECONDS)
    }

    private fun buildInsecureBuilder(): OkHttpClient.Builder {
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        )

        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }

        val trustManager = trustAllCerts[0] as X509TrustManager

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier(HostnameVerifier { _, _ -> true })
    }
}
