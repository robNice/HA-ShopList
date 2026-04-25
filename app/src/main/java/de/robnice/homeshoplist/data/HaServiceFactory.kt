package de.robnice.homeshoplist.data

import de.robnice.homeshoplist.util.Debug
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object HaServiceFactory {

    fun create(baseUrl: String): HaApi {

        val normalizedBaseUrl = baseUrl
            .trim()
            .let {
                if (!it.startsWith("http://") && !it.startsWith("https://")) {
                    "https://$it"
                } else it
            }
            .removeSuffix("/")
            .plus("/")

        Debug.log("BASE URL: $normalizedBaseUrl")

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = HaOkHttpFactory.newBuilder()
            .addInterceptor(logging)
            .build()

        val moshi = com.squareup.moshi.Moshi.Builder()
            .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(client)
            .build()

        return retrofit.create(HaApi::class.java)
    }

}
