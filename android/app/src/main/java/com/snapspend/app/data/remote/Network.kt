package com.snapspend.app.data.remote

import com.snapspend.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class TokenStore(private val context: android.content.Context) {
    private val prefs = context.getSharedPreferences("snapspend_auth", android.content.Context.MODE_PRIVATE)
    var token: String?
        get() = prefs.getString("token", null)
        set(value) {
            prefs.edit().putString("token", value).apply()
        }
    fun clear() { prefs.edit().remove("token").apply() }
}

fun createApi(tokenStore: TokenStore): SnapSpendApi {
    val auth = Interceptor { chain ->
        val builder = chain.request().newBuilder()
        tokenStore.token?.let { builder.header("Authorization", "Bearer $it") }
        chain.proceed(builder.build())
    }
    val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
    val client = OkHttpClient.Builder().addInterceptor(auth).addInterceptor(logging).build()
    return Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SnapSpendApi::class.java)
}
