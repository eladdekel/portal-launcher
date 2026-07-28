package com.iblu01.portallauncher

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AlertOverlayState {
    var activeMessage by mutableStateOf<String?>(null)
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    fun showAlert(message: String, durationMs: Long = 5000L) {
        handler.post {
            dismissRunnable?.let { handler.removeCallbacks(it) }
            activeMessage = message
            val runnable = Runnable {
                activeMessage = null
            }
            dismissRunnable = runnable
            handler.postDelayed(runnable, durationMs)
        }
    }

    fun dismiss() {
        handler.post {
            dismissRunnable?.let { handler.removeCallbacks(it) }
            activeMessage = null
            dismissRunnable = null
        }
    }
}
