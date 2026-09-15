package com.snapspend.app.data.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.snapspend.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Lưu token đã mã hóa bằng khóa trong AndroidKeyStore (AES/GCM).
 * Không lưu plain text; giá trị trong prefs chỉ là bản mã.
 * Phát tín hiệu [unauthorized] khi server trả 401 để UI đưa về màn đăng nhập.
 */
class TokenStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("snapspend_auth", Context.MODE_PRIVATE)
    private val cipher = TokenCipher()

    private val _unauthorized = MutableStateFlow(false)
    val unauthorized: StateFlow<Boolean> = _unauthorized.asStateFlow()

    var token: String?
        get() = prefs.getString(KEY, null)?.let { cipher.decrypt(it) }
        set(value) {
            if (value.isNullOrBlank()) clear()
            else prefs.edit().putString(KEY, cipher.encrypt(value)).apply()
        }

    fun clear() { prefs.edit().remove(KEY).apply() }

    /** Gọi khi 401: xóa token và báo UI quay về Auth. */
    fun markUnauthorized() {
        clear()
        _unauthorized.value = true
    }

    fun consumeUnauthorized() { _unauthorized.value = false }

    private companion object { const val KEY = "token" }
}

/** Mã hóa/giải mã token bằng khóa AES sinh trong AndroidKeyStore của thiết bị. */
private class TokenCipher {
    fun encrypt(plain: String): String {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, key())
        val iv = c.iv
        val body = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + body, Base64.NO_WRAP)
    }

    fun decrypt(data: String): String? = try {
        val all = Base64.decode(data, Base64.NO_WRAP)
        val iv = all.copyOfRange(0, IV_LEN)
        val body = all.copyOfRange(IV_LEN, all.size)
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(c.doFinal(body), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    private companion object {
        const val ALIAS = "snapspend_token_key"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
    }
}

fun createApi(tokenStore: TokenStore): SnapSpendApi {
    val auth = Interceptor { chain ->
        val builder = chain.request().newBuilder()
        tokenStore.token?.let { builder.header("Authorization", "Bearer $it") }
        val response = chain.proceed(builder.build())
        // Token hết hạn/không hợp lệ -> xóa token, UI tự đưa về Auth.
        if (response.code == 401 && tokenStore.token != null) tokenStore.markUnauthorized()
        response
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
