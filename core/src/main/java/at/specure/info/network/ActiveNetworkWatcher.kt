/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package at.specure.info.network

import at.specure.info.TransportType
import at.specure.info.cell.CellInfoWatcher
import at.specure.info.cell.CellNetworkInfo
import at.specure.info.connectivity.ConnectivityInfo
import at.specure.info.connectivity.ConnectivityWatcher
import at.specure.info.wifi.WifiInfoWatcher
import at.specure.util.synchronizedForEach
import java.util.Collections

/**
 * Active network watcher that is aggregates all network watchers
 * to detect which one is currently active and get its data
 */
class ActiveNetworkWatcher(
    private val connectivityWatcher: ConnectivityWatcher,
    private val wifiInfoWatcher: WifiInfoWatcher,
    private val cellInfoWatcher: CellInfoWatcher
) {

    private val listeners = Collections.synchronizedSet(mutableSetOf<NetworkChangeListener>())
    private var isCallbacksRegistered = false

    private var lastConnectivityInfo: ConnectivityInfo? = null

    private var _currentNetworkInfo: NetworkInfo? = null
        set(value) {
            field = value
            listeners.synchronizedForEach {
                it.onActiveNetworkChanged(value)
            }
        }

    /**
     * Returns active network information [NetworkInfo] if it is available
     */
    val currentNetworkInfo: NetworkInfo?
        get() = _currentNetworkInfo

    private val connectivityCallback = object : ConnectivityWatcher.ConnectivityChangeListener {

        override fun onConnectivityChanged(connectivityInfo: ConnectivityInfo?) {
            lastConnectivityInfo = connectivityInfo
            _currentNetworkInfo = if (connectivityInfo == null) {
                null
            } else {
                when (connectivityInfo.transportType) {
                    TransportType.WIFI -> wifiInfoWatcher.activeWifiInfo
                    TransportType.CELLULAR -> cellInfoWatcher.activeNetwork
                    else -> null
                }
            }
        }
    }

    private val cellInfoCallback = object : CellInfoWatcher.CellInfoChangeListener {

        override fun onCellInfoChanged(activeNetwork: CellNetworkInfo?) {
            if (lastConnectivityInfo?.transportType == TransportType.CELLULAR) {
                _currentNetworkInfo = activeNetwork
            }
        }
    }

    /**
     * Add callback to start receiving active network changes
     */
    fun addListener(listener: NetworkChangeListener) {
        listeners.add(listener)
        listener.onActiveNetworkChanged(currentNetworkInfo)
        if (listeners.size == 1) {
            registerCallbacks()
        }
    }

    /**
     * Remove callback from receiving updates of active network changes
     */
    fun removeListener(listener: NetworkChangeListener) {
        listeners.remove(listener)
        if (listeners.isEmpty()) {
            unregisterCallbacks()
        }
    }

    private fun registerCallbacks() {
        connectivityWatcher.addListener(connectivityCallback)
        cellInfoWatcher.addListener(cellInfoCallback)
        isCallbacksRegistered = true
    }

    private fun unregisterCallbacks() {
        if (isCallbacksRegistered) {
            connectivityWatcher.removeListener(connectivityCallback)
            cellInfoWatcher.removeListener(cellInfoCallback)
            isCallbacksRegistered = false
        }
    }

    /**
     * Callback that is used to observe active network change tracked by [ActiveNetworkWatcher]
     */
    interface NetworkChangeListener {

        /**
         * When active network change is detected this callback will be triggered
         * if no active network is available null will be returned
         */
        fun onActiveNetworkChanged(info: NetworkInfo?)
    }
}