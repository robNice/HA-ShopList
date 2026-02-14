package de.robnice.homeasssistant_shoppinglist.data

import okhttp3.OkHttpClient
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

        println("BASE URL: $normalizedBaseUrl")

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
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
