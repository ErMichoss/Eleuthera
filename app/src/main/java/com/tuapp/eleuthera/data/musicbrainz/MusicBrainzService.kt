package com.tuapp.eleuthera.data.musicbrainz

import android.util.Log
import com.google.gson.Gson
import com.tuapp.eleuthera.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MusicBrainzService {

    private var accessToken: String? = null

    // Configuración de OkHttp con credenciales
    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d("MusicBrainzNetwork", message)
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY // Imprime el cuerpo completo de la respuesta
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val originalRequest = chain.request()
            val requestBuilder = originalRequest.newBuilder()
                .header("User-Agent", "EleutheraApp/1.0.0 (contacto@ejemplo.com)")

            // Si ya tenemos un token de usuario, lo añadimos de forma automática
            accessToken?.let { token ->
                requestBuilder.header("Authorization", "Bearer $token")
            }

            chain.proceed(requestBuilder.build())
        }
        .addInterceptor(loggingInterceptor)
        .build()

    // Intercambia el código por el token y lo almacena
    suspend fun exchangeCodeForToken(code: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.exchangeToken(
                    clientId = BuildConfig.MUSICBRAINZ_CLIENT_ID,
                    clientSecret = BuildConfig.MUSICBRAINZ_CLIENT_SECRET,
                    code = code
                )
                accessToken = response.accessToken
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    private val api: MusicBrainzApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://musicbrainz.org/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MusicBrainzApiService::class.java)
    }

    suspend fun searchRecordings(query: String, limit: Int): RecordingSearchResponse {
        return withContext(Dispatchers.IO) {
            api.searchRecordings(query = query, limit = limit)
        }
    }

    suspend fun getRecordingDetails(id: String): Recording {
        return withContext(Dispatchers.IO) {
            api.getRecordingDetails(id = id)
        }
    }

    suspend fun searchReleases(query: String, limit: Int): ReleaseSearchResponse {
        return withContext(Dispatchers.IO) {
            api.searchReleases(query = query, limit = limit)
        }
    }

    suspend fun getReleaseDetails(id: String): Release {
        return withContext(Dispatchers.IO) {
            api.getReleaseDetails(id = id)
        }
    }
}