package de.robnice.homeshoplist.wear.data

import okhttp3.OkHttpClient

/** Release builds always use the system certificate validation. */
internal fun baseHttpClientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
