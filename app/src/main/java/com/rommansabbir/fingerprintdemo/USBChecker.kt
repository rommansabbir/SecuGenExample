package com.rommansabbir.fingerprintdemo

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Looper
import android.os.Parcelable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

interface USBChecker {
    companion object {
        private var _connectionStatus: MutableLiveData<UsbDevice?> = MutableLiveData(null)
        val connectionStatus: LiveData<UsbDevice?>
            get() = _connectionStatus

        fun setConnectionStatus(value: UsbDevice?) {
            synchronized(Any()) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    _connectionStatus.value = value
                } else {
                    _connectionStatus.postValue(value)
                }
            }
        }
    }

    fun registerChecker(activity: Activity)
    fun removeChecker(activity: Activity)
}

class USBCheckerImpl : USBChecker {
    companion object {
        private const val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
    }

    private val filter = IntentFilter(ACTION_USB_PERMISSION)
    private val mUsbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    val device =
                        intent.getParcelableExtra<Parcelable>(UsbManager.EXTRA_DEVICE) as UsbDevice?
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        USBChecker.setConnectionStatus(device)
                    } else USBChecker.setConnectionStatus(null)
                }
            }
        }
    }

    private var broadcastReceiver: PendingIntent? = null
    override fun registerChecker(activity: Activity) {
        broadcastReceiver =
            PendingIntent.getBroadcast(activity, 0, Intent(ACTION_USB_PERMISSION), 0)
        activity.registerReceiver(mUsbReceiver, filter)
    }

    override fun removeChecker(activity: Activity) {
        activity.unregisterReceiver(mUsbReceiver)
    }

}