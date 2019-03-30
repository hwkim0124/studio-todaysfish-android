
package com.reelingsoft.todaysfish.client





data class ResultError(
    val code: Int,
    val message: String
)


data class ResponseResult(
    val error: ResultError
)
{
    fun isError() : Boolean {
        return (error.code != 0)
    }

    fun isSuccess() : Boolean {
        return (error.code == 0)
    }
}



