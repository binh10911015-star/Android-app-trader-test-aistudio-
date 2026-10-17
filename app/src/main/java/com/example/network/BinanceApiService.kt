package com.example.network

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

interface BinanceApiService {
    @GET("fapi/v1/ticker/24hr")
    suspend fun get24hrTickers(): ResponseBody

    @GET("fapi/v1/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String, // "4h" or "1d"
        @Query("limit") limit: Int = 100
    ): ResponseBody
}
