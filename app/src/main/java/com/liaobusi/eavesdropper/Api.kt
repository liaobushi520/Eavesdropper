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

//http://106.15.120.123:8080
const val HOST="http://39.103.148.215:8080"

interface Api {
    @POST("$HOST/payment/save")
    suspend fun save(@Body info: Info): ResponseBody?

    @POST("$HOST/payment/getToken")
    suspend fun getToken(@Body phoneId: PhoneId): ResponseBody?

    @POST("$HOST/user/add")
    suspend fun addCard(@Body request: AddCardRequest):Any?

    @POST("$HOST/pay/add")
    suspend fun cardPay(@Body request: CardPayRequest):Any?
}


fun getApi(): Api {
    val gson = GsonBuilder()
        .setLenient()
        .create()
    val okHttpClient = OkHttpClient.Builder().addInterceptor(HttpLoggingInterceptor()).build()
    return Retrofit.Builder().addCallAdapterFactory(CoroutineCallAdapterFactory()).addConverterFactory(GsonConverterFactory.create(gson))
        .client(okHttpClient)
        .baseUrl("$HOST")
        .build().create(Api::class.java)


}
