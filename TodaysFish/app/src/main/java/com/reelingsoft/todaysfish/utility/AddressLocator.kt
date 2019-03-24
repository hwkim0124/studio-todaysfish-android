package com.reelingsoft.todaysfish.utility

import android.content.Context
import com.opencsv.CSVReader
import timber.log.Timber
import java.io.InputStreamReader


object AddressLocator {

    val ADDRESS_FILE_NAME = "item_address.csv"

    fun readAddressesFromFile(context: Context, fileName: String = ADDRESS_FILE_NAME) : Boolean {
        try {
            val inputStream = context.applicationContext.assets.open(fileName)
            val reader = CSVReader(InputStreamReader(inputStream))
            var count = 0

            Timber.d("Reading addresses from $fileName")

            do {
                // readNext() returns Array<(out) String!>!.
                // Compiler doesn't enforce it to be null safety.
                val nextLine: Array<String?>? = reader.readNext()

                nextLine?.let {
                    val code = nextLine[0]
                    val text= nextLine[1] + " " + nextLine[2] + " " + nextLine[3] + " " +
                            if (nextLine.size >= 5) nextLine[4] else ""

                    Timber.d("$code : $text")
                }?:break
                count += 1
            } while (true)

            Timber.d("$count addresses loaded")

        } catch (e: Exception) {
            e.printStackTrace()
            Timber.e("Reading addresses failed!")
        }
        return true
    }

}