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

package at.specure.info.ip

import at.rmbt.client.control.ControlServerClient
import at.rmbt.client.control.IpInfoResponse
import at.rmbt.client.control.IpProtocol
import at.rmbt.util.Maybe
import at.rmbt.util.io
import at.specure.info.connectivity.ConnectivityInfo
import at.specure.info.connectivity.ConnectivityWatcher
import at.specure.util.synchronizedForEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.Collections
import javax.inject.Inject

/**
 * Basic implementation of [IpChangeWatcher] that uses [ConnectivityWatcher] to detect network changes
 */
class IpChangeWatcherImpl @Inject constructor(
    private val controlServerClient: ControlServerClient,
    private val connectivityWatcher: ConnectivityWatcher
) : IpChangeWatcher, ConnectivityWatcher.ConnectivityChangeListener {

    private val listenersV4 = Collections.synchronizedSet(mutableSetOf<IpChangeWatcher.OnIpV4ChangedListener>())
    private val listenersV6 = Collections.synchronizedSet(mutableSetOf<IpChangeWatcher.OnIpV6ChangedListener>())

    private var _lastIPv4Address: IpInfo = IpInfo(IpProtocol.V4, null, null, IpStatus.NO_INFO)
        set(value) {
            field = value
            listenersV4.synchronizedForEach {
                it.onIpV4Changed(value)
            }
        }

    private var _lastIPv6Address: IpInfo = IpInfo(IpProtocol.V6, null, null, IpStatus.NO_INFO)
        set(value) {
            field = value
            listenersV6.synchronizedForEach {
                it.onIpV6Changed(value)
            }
        }

    override val lastIPv4Address: IpInfo
        get() = _lastIPv4Address

    override val lastIPv6Address: IpInfo
        get() = _lastIPv6Address

    override fun updateIpV4() {
        io {
            var publicIp4: Maybe<IpInfoResponse>? = null
            var privateIp4: Maybe<IpInfoResponse>? = null

            coroutineScope {
                launch { publicIp4 = controlServerClient.getPublicIpV4Address() }
                launch { privateIp4 = controlServerClient.getPrivateIpV4Address() }
            }
            val privateAddress: String? = if (privateIp4?.ok == true) privateIp4!!.success.ipAddress else null
            val publicAddress: String? = if (publicIp4?.ok == true) publicIp4!!.success.ipAddress else null
            val status = getIpStatus(privateAddress, publicAddress)

            Timber.i("IPv4 private: $privateAddress public: $publicAddress status: ${status.name} has connection: ${connectivityWatcher.activeNetwork == null}")

            _lastIPv4Address = IpInfo(IpProtocol.V4, privateAddress, publicAddress, status)
        }
    }

    override fun updateIpV6() {
        io {
            var publicIp6: Maybe<IpInfoResponse>? = null
            var privateIp6: Maybe<IpInfoResponse>? = null

            coroutineScope {
                launch { publicIp6 = controlServerClient.getPublicIpV6Address() }
                launch { privateIp6 = controlServerClient.getPrivateIpV6Address() }
            }

            val privateAddress: String? = if (publicIp6?.ok == true) publicIp6!!.success.ipAddress else null
            val publicAddress: String? = if (privateIp6?.ok == true) privateIp6!!.success.ipAddress else null
            val status = getIpStatus(privateAddress, publicAddress)

            Timber.i("IPv6 private: $privateAddress public: $publicAddress status: ${status.name} has connection: ${connectivityWatcher.activeNetwork == null}")

            _lastIPv6Address = IpInfo(IpProtocol.V6, privateAddress, publicAddress, status)
        }
    }

    private fun getIpStatus(privateAddressStr: String?, publicAddressStr: String?): IpStatus {
        val privateAdr: InetAddress? = if (privateAddressStr == null) null else InetAddress.getByName(privateAddressStr)
        val publicAdr: InetAddress? = if (publicAddressStr == null) null else InetAddress.getByName(publicAddressStr)

        return if (publicAdr != null && privateAdr != null) {
            if (privateAdr is Inet4Address && publicAdr is Inet6Address) {
                IpStatus.NAT_IPV4_TO_IPV6
            } else if (privateAdr is Inet6Address && publicAdr is Inet4Address) {
                IpStatus.NAT_IPV6_TO_IPV4
            } else if (privateAdr == publicAdr) {
                IpStatus.NO_NAT
            } else {
                IpStatus.NAT
            }
        } else if (privateAdr != null) {
            IpStatus.ONLY_LOCAL
        } else if (connectivityWatcher.activeNetwork == null) {
            IpStatus.NO_INFO
        } else {
            IpStatus.NO_ADDRESS
        }
    }

    override fun addListener(listener: IpChangeWatcher.OnIpV4ChangedListener) {
        listenersV4.add(listener)
        updateIpV4()
        if (listenersV6.isEmpty() && listenersV4.size == 1) {
            connectivityWatcher.addListener(this)
        }
    }

    override fun removeListener(listener: IpChangeWatcher.OnIpV4ChangedListener) {
        listenersV4.remove(listener)
        if (listenersV4.isEmpty() && listenersV6.isEmpty()) {
            connectivityWatcher.removeListener(this)
        }
    }

    override fun addListener(listener: IpChangeWatcher.OnIpV6ChangedListener) {
        listenersV6.add(listener)
        updateIpV6()
        if (listenersV4.isEmpty() && listenersV6.size == 1) {
            connectivityWatcher.addListener(this)
        }
    }

    override fun removeListener(listener: IpChangeWatcher.OnIpV6ChangedListener) {
        listenersV6.remove(listener)
        if (listenersV6.isEmpty() && listenersV4.isEmpty()) {
            connectivityWatcher.removeListener(this)
        }
    }

    override fun onConnectivityChanged(connectivityInfo: ConnectivityInfo?) {
        _lastIPv4Address = IpInfo(IpProtocol.V4, null, null, IpStatus.NO_INFO)
        _lastIPv6Address = IpInfo(IpProtocol.V6, null, null, IpStatus.NO_INFO)
        if (connectivityInfo != null) {
            updateIpV4()
            updateIpV6()
        }
    }
}