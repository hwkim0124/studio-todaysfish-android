package com.reelingsoft.todaysfish.activity

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.reelingsoft.todaysfish.R
import com.reelingsoft.todaysfish.utility.AddressLocator
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import okhttp3.Dispatcher


// Activity class implementing CoroutineScope by delegation with default factory functions,
// which combines the desired dispatcher with the scope.
// Destroying this activity cancels all the coroutines that were launched.
// (by a single invocation of job.cancel() in Activity.destroy()

// MainScope() creates scope for UI applications and uses Dispatchers.Main as default dispatcher.
// Every coroutine launched from within a MainActivity has its job as a parent and is immediately
// cancelled when activity is destroyed.

class MainActivity : AppCompatActivity(),
    CoroutineScope by CoroutineScope(Dispatchers.Main) {

    // The default dispatcher, that is used when coroutines are launced in GlobalScope,
    // is represented by Dispatchers.Default and uses shared background pool of threads,
    // so launch(Dispatchers.Default) {...} uses the same dispatcher as GlobalScope.launch {...}
    /*
    private var networkJob = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + networkJob
    */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        button_convert_address.setOnClickListener {
            updateGpsCoordinatesOfAddresses()
        }

        button_preview.setOnClickListener {
            startPreviewActivity()
        }
    }


    private fun startPreviewActivity() {
        val intent = Intent(this, PreviewActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.abc_slide_in_bottom, R.anim.abc_slide_out_top)
    }


    private fun updateGpsCoordinatesOfAddresses() {
        val context = this
        frame_progress.visibility = View.VISIBLE

        // Global scope is used to launch top-level coroutines which are operating on the whole
        // application lifetime and are not cancelled prematurely.
        // Application code usually should use application-defined CoroutineScope, using
        // async or launch on the instance of GlobalScope is highly discouraged.

        // Suspending functions can be used inside coroutines just like regular functions,
        // and they can use other suspending functions like delay to suspend execution of a coroutine.
        // https://kotlinlang.org/docs/reference/coroutines/basics.html

        // suspend pauses the execution of the current coroutine, saving all local variables,
        // when its work completes, instead of calling a callback to notify the main thread,
        // it can simply resume the coroutine it suspended.
        launch {
            withContext(Dispatchers.IO) {
                val list = AddressLocator.readAddressesFromFile(context)
                AddressLocator.updateGpsCoordniatesInDatabaseAsync(context, list)
            }

            // launch(Dispatchers.Main) launches coroutine in the main UI context, we can freely
            // update UI from this coroutine and invoke suspending functions like delay at the same time.
            // UI is not frozen while delay waits, because it doesn't block the UI thread
            // - it just suspends the coroutine.

            // withContext function changes a context of a coroutine while still staying in the same
            // coroutine. Note that withContext is itself a suspend function.
            withContext(Dispatchers.Main) {
                frame_progress.visibility = View.INVISIBLE
            }
        }
    }

    override fun onStop() {
        super.onStop()

        // Job.cancel is completely thread-safe and non-blocking.
        // It just signals the coroutine to cancel its job, without waiting for it to actually terminate.
        // networkJob.cancel()
        // cancel()
    }

}
