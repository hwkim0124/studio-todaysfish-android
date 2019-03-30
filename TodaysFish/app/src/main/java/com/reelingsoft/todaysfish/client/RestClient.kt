package com.reelingsoft.todaysfish.client

import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import com.reelingsoft.todaysfish.entity.Address
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.lang.Exception



sealed class RestResult<out T: Any> {
    data class Success<out T: Any>(val data: T) : RestResult<T>()
    data class Error(val exception: Exception) : RestResult<Nothing>()
}


object RestAPI {

    private const val BASE_URL = "http://211.107.29.223:8000"
    private const val ERROR_SUCCESS = 0

    private val RestApi: TodaysFishApi


    init {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addCallAdapterFactory(CoroutineCallAdapterFactory())
            .addConverterFactory(MoshiConverterFactory.create())
            .build()


        RestApi = retrofit.create(TodaysFishApi::class.java)
    }


    suspend fun updateGpsCoordinatesOfAddressAsync(data: Address
    ) : RestResult<ResponseResult>
    {
        try {
            val res = RestApi.updateGpsCoordinatesOfAddressAsync(data).await()
            if (res.isSuccessful) {
                // The rest api request was completed by response from the server,
                // but this is not whether the operation was done successfully on the server side,
                // which should be checked by client.
                return RestResult.Success(res.body()!!)
            }
            throw IOException("updateGpsCoordinatesOfAddressAsync() failed!")
        } catch (e: Exception) {
            e.printStackTrace()
            return RestResult.Error(e)
        }
    }
}