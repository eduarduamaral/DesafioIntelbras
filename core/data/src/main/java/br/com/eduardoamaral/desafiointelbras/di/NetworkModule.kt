package br.com.eduardoamaral.desafiointelbras.di

import android.util.Log
import br.com.eduardoamaral.desafiointelbras.core.data.BuildConfig
import br.com.eduardoamaral.desafiointelbras.data.remote.ApiConfig
import br.com.eduardoamaral.desafiointelbras.data.remote.ApiJson
import br.com.eduardoamaral.desafiointelbras.data.remote.DeviceApi
import br.com.eduardoamaral.desafiointelbras.data.remote.KtorDeviceApi
import br.com.eduardoamaral.desafiointelbras.data.remote.createHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // Tag fixa para filtrar no Logcat: tag:DesafioHttp
    private const val HTTP_LOG_TAG = "DesafioHttp"

    @Provides
    @Singleton
    fun provideJson(): Json = ApiJson

    @Provides
    @Singleton
    fun provideApiConfig(): ApiConfig = ApiConfig(
        baseUrl = BuildConfig.API_BASE_URL,
        token = BuildConfig.API_TOKEN,
    )

    @Provides
    @Singleton
    fun provideHttpClient(config: ApiConfig, json: Json): HttpClient = createHttpClient(
        engine = OkHttp.create(),
        config = config,
        json = json,
        httpLog = if (BuildConfig.DEBUG) { message -> Log.d(HTTP_LOG_TAG, message) } else null,
    )

    @Provides
    @Singleton
    fun provideDeviceApi(client: HttpClient): DeviceApi = KtorDeviceApi(client)
}
