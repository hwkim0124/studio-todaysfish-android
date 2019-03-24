package com.reelingsoft.todaysfish.activity

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.reelingsoft.todaysfish.R
import com.reelingsoft.todaysfish.utility.AddressLocator
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        button_convert_address.setOnClickListener {
            AddressLocator.readAddressesFromFile(this)
        }
    }
}
