package com.reelingsoft.todaysfish.client

import com.reelingsoft.todaysfish.entity.Address
import kotlinx.coroutines.Deferred
import retrofit2.Response
import retrofit2.http.*


interface TodaysFishApi {

    // @FormUrlEncoded
    @PUT("/api/addresses/coordinates")
    fun updateGpsCoordinatesOfAddressAsync(
        @Body body: Address
    ) : Deferred<Response<ResponseResult>>

    /*
    @FormUrlEncoded
    @PUT("/api/addresses/coordinates")
    fun updateGpsCoordinatesOfAddressAsync(
        @Field("address_code") address_code: Int,
        @Field("latitude") latitude: Double,
        @Field("longitude") longitude: Double
    ) : Deferred<Response<ResponseResult>>
    */
}