package ir.bayanebartar.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/** Sync helper for cached admin lists. app_setting.php and remote config are untouched. */
object DataSyncManager {
    fun hasInternet(context: Context): Boolean {
        val cm=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n=cm.activeNetwork ?: return false
        val c=cm.getNetworkCapabilities(n) ?: return false
        return c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun watch(context: Context, onAvailable:()->Unit) {
        val cm=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.registerNetworkCallback(
            NetworkRequest.Builder().build(),
            object: ConnectivityManager.NetworkCallback(){
                override fun onAvailable(network: Network){ onAvailable() }
            }
        )
    }
}
