package com.reelingsoft.todaysfish.utility

import android.content.Context
import android.location.Geocoder
import com.opencsv.CSVReader
import com.reelingsoft.todaysfish.client.RestAPI
import com.reelingsoft.todaysfish.client.RestResult
import com.reelingsoft.todaysfish.entity.Address
import com.reelingsoft.todaysfish.entity.GpsCoordinates
import timber.log.Timber
import java.io.IOException
import java.io.InputStreamReader


object AddressLocator {

    // const val is compile time constant, which assigned during compile time.
    private const val ADDRESS_FILE_NAME = "item_address.csv"


    fun readAddressesFromFile(context: Context, fileName: String = ADDRESS_FILE_NAME) : List<Address> {
        val list = mutableListOf<Address>()

        try {
            val inputStream = context.applicationContext.assets.open(fileName)
            val reader = CSVReader(InputStreamReader(inputStream))
            var count = 0
            var empty = 0

            Timber.d("Reading addresses from $fileName")

            do {
                // readNext() returns Array<(out) String!>!.
                // Compiler doesn't enforce it to be null safety.
                val nextLine: Array<String?> = reader.readNext()?:break

                try {
                    val code = nextLine[0]?.toIntOrNull()
                    val data = Address(
                        code = code?:0,
                        province = nextLine[1]?:"",
                        district = nextLine[2]?:"",
                        town = nextLine[3]?:"",
                        village = nextLine[4])

                    // isBlank checks that a char sequence has a zero length or that all indices are white spaces.
                    if (data.isValidName() && data.isValidCode() && data.code >= 1312210000) {
                        list.add(data)
                        count++
                    }
                    // Timber.d("$code : $name, $coord")
                } catch (e: ArrayIndexOutOfBoundsException) {
                    empty++
                }

            } while (true)

            Timber.d("Addresses of $count loaded")

        } catch (e: Exception) {
            e.printStackTrace()
            Timber.e("Reading addresses failed!")
        }
        return list
    }


    fun assignGpsCoordinatesOfAddresses(context:Context, list: List<Address>) {
        var count = 0
        var failed = 0

        for (item in list) {
            var coords = getGpsCoordinatesFromLocation(context, item.name())
            if (coords != null) {
                item.setGpsCoordinates(coords)
                Timber.d("${item.name()} assigned to $coords")
                count += 1
                /*
                if (count > 0) {
                    break
                }
                */
            }
            else {
                failed += 1
                Timber.d("Can't retrieve gps coordinates of $item")
            }
        }

        Timber.d("Gps coordinates of $count addresses retrieved, $failed failed")
        return
    }


    suspend fun updateGpsCoordniatesInDatabaseAsync(context: Context, list: List<Address>) {
        var count = 0
        var error = 0
        var empty = 0

        for (item in list) {
            var coords = getGpsCoordinatesFromLocation(context, item.name())

            if (item.code == 1004060100) {
                coords = GpsCoordinates(35.586284, 129.062026)
            }

            if (coords != null) {
                item.setGpsCoordinates(coords)
                Timber.d("${item.name()} assigned to $coords")

                val result = RestAPI.updateGpsCoordinatesOfAddressAsync(item)
                if (result is RestResult.Success && result.data.isSuccess()) {
                    count++
                } else {
                    Timber.d("Can't update gps coordinates of ${item.name()}")
                    error++
                    break
                }
            }
            else {
                Timber.d("Can't retrieve gps coordinates of ${item.name()}")
                empty++
                break
            }
        }

        Timber.d("Gps coordinates of Addresses database $count updated, $error failed, $empty empty")
        return
    }


    private fun getGpsCoordinatesFromLocation(context: Context, name: String): GpsCoordinates? {

        val geocoder = Geocoder(context)

        try {
            val list = geocoder.getFromLocationName(name, 10)
            if (list.isEmpty()) {
                Timber.d("Can't find gps coordinates corresponding to $name")
                return null
            }
            return GpsCoordinates(list.get(0).latitude, list.get(0).longitude)

        } catch (e: IOException) {
            Timber.e("Couldn't get gps coordinates from $name")
            return null
        }
    }

}

