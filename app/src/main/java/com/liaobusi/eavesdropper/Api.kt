package com.liaobusi.eavesdropper

import com.google.gson.GsonBuilder
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST


interface Api {
    @POST("http://106.15.120.123:8080/payment/save")
    suspend fun save(@Body info: Info): ResponseBody?

    @POST("http://106.15.120.123:8080/payment/getToken")
    suspend fun getToken(@Body phoneId: PhoneId): ResponseBody?
}


fun getApi(): Api {
    val gson = GsonBuilder()
        .setLenient()
        .create()
    val okHttpClient = OkHttpClient.Builder().addInterceptor(HttpLoggingInterceptor()).build()
    return Retrofit.Builder().addCallAdapterFactory(CoroutineCallAdapterFactory()).addConverterFactory(GsonConverterFactory.create(gson))
        .client(okHttpClient)
        .baseUrl("http://106.15.120.123:8080")
        .build().create(Api::class.java)


}
