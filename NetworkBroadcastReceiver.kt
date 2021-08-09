package com.mycompany.broadcasts

import android.R.attr
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo

import android.net.ConnectivityManager
import androidx.lifecycle.MutableLiveData
import android.R.attr.action
import androidx.lifecycle.LiveData
import kotlinx.coroutines.delay
import java.lang.NullPointerException


class NetworkBroadcastReceiver : BroadcastReceiver() {

    private var _isNetworkOnline = MutableLiveData<Boolean>()
    val isNetworkOnline: LiveData<Boolean> = _isNetworkOnline


    override fun onReceive(context: Context, intent: Intent?) {
        val status = isNetworkAvailable(context)
        _isNetworkOnline.value = status

    }

    private fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetworkInfo = cm.activeNetworkInfo
            activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting
        } catch (e: NullPointerException) {
            false
        }
    }



}