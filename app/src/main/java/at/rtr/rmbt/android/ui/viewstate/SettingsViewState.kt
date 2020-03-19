package at.rtr.rmbt.android.ui.viewstate

import androidx.databinding.ObservableField
import androidx.databinding.ObservableInt
import at.rmbt.util.io
import at.rtr.rmbt.android.BuildConfig
import at.rtr.rmbt.android.config.AppConfig
import at.rtr.rmbt.android.util.addOnPropertyChanged
import at.specure.data.ClientUUID
import at.specure.data.ControlServerSettings
import at.specure.data.MeasurementServers
import at.specure.data.repository.SettingsRepository
import at.specure.location.LocationProviderState

class SettingsViewState constructor(
    val appConfig: AppConfig,
    val clientUUID: ClientUUID,
    private val measurementServers: MeasurementServers,
    private val controlServerSettings: ControlServerSettings,
    private val settingsRepository: SettingsRepository
) : ViewState {

    val isNDTEnabled = ObservableField(appConfig.NDTEnabled)
    val qosMeasurement = ObservableField(!appConfig.skipQoSTests)
    val canManageLocationSettings = ObservableField(appConfig.canManageLocationSettings)
    val loopModeEnabled = ObservableField(appConfig.loopModeEnabled)
    val expertModeEnabled = ObservableField(appConfig.expertModeEnabled)
    val expertModeUseIpV4Only = ObservableField(appConfig.expertModeUseIpV4Only)
    val loopModeWaitingTimeMin = ObservableField(appConfig.loopModeWaitingTimeMin)
    val loopModeDistanceMeters = ObservableField(appConfig.loopModeDistanceMeters)
    val loopModeNumberOfTests = ObservableInt(appConfig.loopModeNumberOfTests)
    val developerModeIsAvailable = appConfig.developerModeIsAvailable
    val developerModeIsEnabled = ObservableField(appConfig.developerModeIsEnabled)
    val controlServerOverrideEnabled = ObservableField(appConfig.controlServerOverrideEnabled)
    val controlServerHost = ObservableField(appConfig.controlServerHost)
    val controlServerPort = ObservableField(appConfig.controlServerPort)
    val controlServerUseSSL = ObservableField(appConfig.controlServerUseSSL)
    val isLocationEnabled = ObservableField<LocationProviderState>()
    val numberOfTests = ObservableInt(appConfig.testCounter)
    val emailAddress = ObservableField(appConfig.aboutEmailAddress)
    val githubRepositoryUrl = ObservableField(appConfig.aboutGithubRepositoryUrl)
    val webPageUrl = ObservableField(appConfig.aboutWebPageUrl)
    val dataPrivacyAndTermsUrl = ObservableField(appConfig.dataPrivacyAndTermsUrl)
    val mapServerOverrideEnabled = ObservableField(appConfig.mapServerOverrideEnabled)
    val mapServerHost = ObservableField(appConfig.mapServerHost)
    val mapServerPort = ObservableField(appConfig.mapServerPort)
    val mapServerUseSSL = ObservableField(appConfig.mapServerUseSSL)
    val qosSSL = ObservableField(appConfig.qosSSL)
    val selectedMeasurementServer = ObservableField(measurementServers.selectedMeasurementServer)

    init {
        isNDTEnabled.addOnPropertyChanged { value ->
            value.get()?.let {
                appConfig.NDTEnabled = it
            }
        }
        qosMeasurement.addOnPropertyChanged { value ->
            value.get()?.let {
                appConfig.skipQoSTests = !it
            }
        }
        canManageLocationSettings.addOnPropertyChanged { value ->
            value.get()?.let {
                appConfig.canManageLocationSettings = it
            }
        }
        loopModeEnabled.addOnPropertyChanged { value ->
            value.get()?.let {
                appConfig.loopModeEnabled = it
            }
        }
        expertModeEnabled.addOnPropertyChanged { value ->
            value.get()?.let {
                appConfig.expertModeEnabled = it
            }
        }
        expertModeUseIpV4Only.addOnPropertyChanged { value ->
            value.get()?.let {
                appConfig.expertModeUseIpV4Only = it
                if (it) {
                    controlServerSettings.controlServerV4Url?.let { controlServerIPv4HostAddress ->
                        appConfig.controlServerHost = controlServerIPv4HostAddress
                    }
                } else {
                    appConfig.controlServerHost = BuildConfig.CONTROL_SERVER_HOST.value
                }
            }
        }
        loopModeWaitingTimeMin.addOnPropertyChanged { value ->
            value.get()?.let {
                appConfig.loopModeWaitingTimeMin = it
            }
        }
        loopModeDistanceMeters.addOnPropertyChanged { value ->
            value.get()?.let {
                appConfig.loopModeDistanceMeters = it
            }
        }
        loopModeNumberOfTests.addOnPropertyChanged { value ->
            value.get().let {
                appConfig.loopModeNumberOfTests = it
            }
        }
        developerModeIsEnabled.addOnPropertyChanged { value ->
            value.get()?.let {
                appConfig.developerModeIsEnabled = it
            }
        }
        controlServerHost.addOnPropertyChanged { value ->
            value.get()?.let {
                appConfig.controlServerHost = it
                io {
                    settingsRepository.refreshSettings()
                }
            }
        }
        controlServerPort.addOnPropertyChanged { value ->
            value.get()?.let {
                appConfig.controlServerPort = it
            }
        }
        controlServerUseSSL.addOnPropertyChanged { value ->
            value.get()?.let {
                appConfig.controlServerUseSSL = it
            }
        }
        controlServerOverrideEnabled.addOnPropertyChanged { value ->
            value.get()?.let {
                appConfig.controlServerOverrideEnabled = it
            }
        }
        mapServerHost.addOnPropertyChanged { value ->
            value.get()?.let {
                appConfig.mapServerHost = it
            }
        }
        mapServerPort.addOnPropertyChanged { value ->
            value.get()?.let {
                appConfig.mapServerPort = it
            }
        }
        mapServerUseSSL.addOnPropertyChanged { value ->
            value.get()?.let {
                appConfig.mapServerUseSSL = it
            }
        }
        mapServerOverrideEnabled.addOnPropertyChanged { value ->
            value.get()?.let {
                appConfig.mapServerOverrideEnabled = it
                if (!it) {
                    io {
                        settingsRepository.refreshSettings()
                    }
                }
            }
        }
        qosSSL.addOnPropertyChanged { value ->
            value.get()?.let {
                appConfig.qosSSL = it
            }
        }
        selectedMeasurementServer.addOnPropertyChanged { value ->
            value.get().let {
                measurementServers.selectedMeasurementServer = it
            }
        }
    }
}
