package com.reelingsoft.todaysfish.entity


data class Address(
    var id: Int = 0,
    var province: String = "",
    var district: String = "",
    var town: String = "",
    var village: String? = null,
    var code: Int = 0,
    var obs_code: String? = "",
    var latitude: Double? = null,
    var longitude: Double? = null
)
{
    fun name() : String {
        return "$province $district $town ${village?:""}".trim()
    }

    fun isValidName() : Boolean {
        return name().isNotBlank()
    }

    fun isValidCode() : Boolean {
        return code > 0
    }

    fun isValidGpsCoordinates() : Boolean {
        return (latitude != null && longitude != null)
    }

    fun setGpsCoordinates(coords: GpsCoordinates) {
        latitude = coords.latitude
        longitude = coords.longitude
    }

}


data class GpsCoordinates(val latitude: Double, val longitude: Double)