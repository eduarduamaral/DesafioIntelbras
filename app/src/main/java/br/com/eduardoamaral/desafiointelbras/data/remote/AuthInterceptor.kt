package br.com.eduardoamaral.desafiointelbras.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/** Adiciona o token em toda requisição (esquema "bearer" definido no OpenAPI da API). */
class AuthInterceptor(private val token: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(request)
    }
}
