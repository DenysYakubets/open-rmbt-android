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

package at.rtr.rmbt.android.config

import android.content.Context
import at.rtr.rmbt.android.BuildConfig
import at.rtr.rmbt.android.util.ConfigValue
import at.specure.config.Config
import javax.inject.Inject

private const val FILENAME = "config.pref"

private const val KEY_TEST_COUNTER = "KEY_TEST_COUNTER"

class AppConfig @Inject constructor(context: Context) : Config {

    private val preferences = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    private fun getInt(configValue: ConfigValue): Int {
        return preferences.getInt(configValue.name, configValue.value.toInt())
    }

    private fun setInt(configValue: ConfigValue, value: Int) {
        preferences.edit()
            .putInt(configValue.name, value)
            .apply()
    }

    private fun getString(configValue: ConfigValue): String {
        return preferences.getString(configValue.name, configValue.value)!!
    }

    private fun setString(configValue: ConfigValue, value: String) {
        preferences.edit()
            .putString(configValue.name, value)
            .apply()
    }

    private fun getBoolean(configValue: ConfigValue): Boolean {
        return preferences.getBoolean(configValue.name, configValue.value.toBoolean())
    }

    private fun setBoolean(configValue: ConfigValue, value: Boolean) {
        preferences.edit()
            .putBoolean(configValue.name, value)
            .apply()
    }

    override var NDTEnabled: Boolean
        get() = getBoolean(BuildConfig.NDT_ENABLED)
        set(value) = setBoolean(BuildConfig.NDT_ENABLED, value)

    override var skipQoSTests: Boolean
        get() = getBoolean(BuildConfig.SKIP_QOS_TESTS)
        set(value) = setBoolean(BuildConfig.SKIP_QOS_TESTS, value)

    override var canManageLocationSettings: Boolean
        get() = getBoolean(BuildConfig.CAN_MANAGE_LOCATION_SETTINGS)
        set(value) = setBoolean(BuildConfig.CAN_MANAGE_LOCATION_SETTINGS, value)

    override var loopModeEnabled: Boolean
        get() = getBoolean(BuildConfig.LOOP_MODE_ENABLED)
        set(value) = setBoolean(BuildConfig.LOOP_MODE_ENABLED, value)

    override var loopModeWaitingTimeMin: Int
        get() = getInt(BuildConfig.LOOP_MODE_WAITING_TIME_MIN)
        set(value) = setInt(BuildConfig.LOOP_MODE_WAITING_TIME_MIN, value)

    override var loopModeDistanceMeters: Int
        get() = getInt(BuildConfig.LOOP_MODE_DISTANCE_METERS)
        set(value) = setInt(BuildConfig.LOOP_MODE_DISTANCE_METERS, value)

    override var expertModeEnabled: Boolean
        get() = getBoolean(BuildConfig.EXPERT_MODE_ENABLED)
        set(value) = setBoolean(BuildConfig.EXPERT_MODE_ENABLED, value)

    override var expertModeUseIpV4Only: Boolean
        get() = getBoolean(BuildConfig.EXPERT_MODE_IPV4_ONLY)
        set(value) = setBoolean(BuildConfig.EXPERT_MODE_IPV4_ONLY, value)

    override var controlServerUseSSL: Boolean
        get() = getBoolean(BuildConfig.CONTROL_SERVER_USE_SSL)
        set(value) = setBoolean(BuildConfig.CONTROL_SERVER_USE_SSL, value)

    override var controlServerPort: Int
        get() = getInt(BuildConfig.CONTROL_SERVER_PORT)
        set(value) = setInt(BuildConfig.CONTROL_SERVER_PORT, value)

    override var controlServerHost: String
        get() = getString(BuildConfig.CONTROL_SERVER_HOST)
        set(value) = setString(BuildConfig.CONTROL_SERVER_HOST, value)

    override var controlServerCheckPrivateIPv4Host: String
        get() = getString(BuildConfig.CONTROL_SERVER_CHECK_PRIVATE_IPV4_HOST)
        set(value) = setString(BuildConfig.CONTROL_SERVER_CHECK_PRIVATE_IPV4_HOST, value)

    override var controlServerCheckPrivateIPv6Host: String
        get() = getString(BuildConfig.CONTROL_SERVER_CHECK_PRIVATE_IPV6_HOST)
        set(value) = setString(BuildConfig.CONTROL_SERVER_CHECK_PRIVATE_IPV6_HOST, value)

    override var controlServerCheckPublicIPv4Url: String
        get() = getString(BuildConfig.CONTROL_SERVER_CHECK_PUBLIC_IPV4_URL)
        set(value) = setString(BuildConfig.CONTROL_SERVER_CHECK_PUBLIC_IPV4_URL, value)

    override var controlServerCheckPublicIPv6Url: String
        get() = getString(BuildConfig.CONTROL_SERVER_CHECK_PUBLIC_IPV6_URL)
        set(value) = setString(BuildConfig.CONTROL_SERVER_CHECK_PUBLIC_IPV6_URL, value)

    override var controlServerSettingsPath: String
        get() = getString(BuildConfig.CONTROL_SERVER_SETTINGS_PATH)
        set(value) = setString(BuildConfig.CONTROL_SERVER_SETTINGS_PATH, value)

    override var controlServerRequestTestPath: String
        get() = getString(BuildConfig.CONTROL_SERVER_TEST_REQUEST_PATH)
        set(value) = setString(BuildConfig.CONTROL_SERVER_TEST_REQUEST_PATH, value)

    override var testCounter: Int
        get() = preferences.getInt(KEY_TEST_COUNTER, 0)
        set(value) = preferences.edit()
            .putInt(KEY_TEST_COUNTER, value)
            .apply()

    override var capabilitiesRmbtHttp: Boolean
        get() = getBoolean(BuildConfig.CAPABILITIES_RMBT_HTTP)
        set(value) = setBoolean(BuildConfig.CAPABILITIES_RMBT_HTTP, value)

    override var capabilitiesQosSupportsInfo: Boolean
        get() = getBoolean(BuildConfig.CAPABILITIES_QOS_SUPPORTS_INFO)
        set(value) = setBoolean(BuildConfig.CAPABILITIES_QOS_SUPPORTS_INFO, value)

    override var capabilitiesClassificationCount: Int
        get() = getInt(BuildConfig.CAPABILITIES_CLASSIFICATION_COUNT)
        set(value) = setInt(BuildConfig.CAPABILITIES_CLASSIFICATION_COUNT, value)
}