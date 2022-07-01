package dev.haenara.adbdetector

import android.content.Context
import android.content.IntentFilter
import android.provider.Settings
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * ADB Detector can check USB Debugging is activated both dynamically and statically.
 * To check it dynamically, use a register a BroadcastReceiver.
 * To check it statically, use checkUsbDebuggingMode function.
 */
@Suppress("MemberVisibilityCanBePrivate")
class AdbDetector(private val mContext: Context) {
    /**
     * Register an ADB-detect receiver,so that checks
     * if USB Debugging is enabled and usb is connected dynamically.
     */
    fun registerAdbDetectReceiver(listener: OnAdbUsbListener? = null) {
        // Register USB connect filter.
        unregisterAdbDetectReceiver()
        val filter = IntentFilter()
        filter.addAction("android.hardware.usb.action.USB_STATE")
        receiver = UsbDebugDetectReceiver(listener)
        mContext.registerReceiver(receiver, filter)
    }

    fun registerAdbDetectReceiver(
        connected: ((context: Context) -> Unit)?,
        disconnected: ((context: Context) -> Unit)?
    ) {
        registerAdbDetectReceiver(object : OnAdbUsbListener {
            override fun onAdbUsbConnected(context: Context) {
                connected?.let { connected(context) }
            }

            override fun onAdbUsbDisconnected(context: Context) {
                disconnected?.let { disconnected(context) }
            }
        })
    }

    /**
     * Unregister the last Adb detect receiver.
     */
    fun unregisterAdbDetectReceiver() {
        // Unregister USB connect filter.
        receiver?.let {
            mContext.unregisterReceiver(receiver)
        }
    }


    /**
     * Check if USB Debugging is enabled and usb is connected statically.
     */
    fun checkUsbDebuggingMode(): Boolean = isDebugEnabled() and isUsbConnected()

    /**
     * Check if USB Debugging is enabled.
     */
    fun isDebugEnabled(): Boolean =
        Settings.Secure.getInt(
            mContext.contentResolver, Settings.Global.ADB_ENABLED, 0
        ) != 0

    /**
     * Check if USB is connected statically.
     */
    fun isUsbConnected(): Boolean {
        val intent = mContext.registerReceiver(
            null,
            IntentFilter("android.hardware.usb.action.USB_STATE")
        )
        return intent?.extras?.getBoolean("connected") ?: false
    }

    /**
     * Check adb port open
     *
     * @remark
     * Only check adb port open. The actual connection cannot be checked.
     */
    fun isOpenAdbPort(): Boolean {
        fun exec(cmd: String): BufferedReader = Runtime.getRuntime().exec(cmd).let {
            BufferedReader(InputStreamReader(it.inputStream))
        }

        return try {
            // same isDebugEnabled
            val adbRunning = exec("/system/bin/getprop init.svc.adbd").readLine().equals("running")

            // get adb port
            val adbPort = exec("/system/bin/getprop service.adb.tcp.port").readLine()

            adbRunning and adbPort.isNotEmpty()
        } catch (ignore: Exception) {
            false
        }
    }

    companion object {
        private var receiver: UsbDebugDetectReceiver? = null
    }
}