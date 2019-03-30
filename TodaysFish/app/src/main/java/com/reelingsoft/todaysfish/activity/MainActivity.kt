package com.reelingsoft.todaysfish.activity

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.reelingsoft.todaysfish.R
import com.reelingsoft.todaysfish.utility.AddressLocator
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext


class MainActivity : AppCompatActivity(), CoroutineScope {

    private var networkJob = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + networkJob


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        button_convert_address.setOnClickListener {
            updateGpsCoordinatesOfAddresses()
        }
    }

    private fun updateGpsCoordinatesOfAddresses() {
        val context = this
        frame_progress.visibility = View.VISIBLE

        networkJob = GlobalScope.launch {
            val list = AddressLocator.readAddressesFromFile(context)
            AddressLocator.updateGpsCoordniatesInDatabaseAsync(context, list)

            withContext(Dispatchers.Main) {
                frame_progress.visibility = View.INVISIBLE
            }
        }
    }

    override fun onStop() {
        super.onStop()
        networkJob.cancel()
    }

}
