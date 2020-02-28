package at.rtr.rmbt.android.viewmodel

import androidx.lifecycle.LiveData
import at.rtr.rmbt.android.config.AppConfig
import at.rtr.rmbt.android.ui.viewstate.LoopConfigurationViewState
import at.rtr.rmbt.android.util.map
import at.specure.info.connectivity.ConnectivityInfoLiveData
import javax.inject.Inject

class LoopConfigurationViewModel @Inject constructor(config: AppConfig, connectivityInfoLiveData: ConnectivityInfoLiveData) : BaseViewModel() {

    val state = LoopConfigurationViewState(config)

    init {
        addStateSaveHandler(state)
    }

    val isConnected: LiveData<Boolean> = connectivityInfoLiveData.map {
        it != null
    }

    fun isWaitingTimeValid(value: Int, minValue: Int, maxValue: Int) =
        if (value in minValue..maxValue) {
            state.waitingTime.set(value)
            true
        } else false

    fun isDistanceValid(value: Int, minValue: Int, maxValue: Int) =
        if (value in minValue..maxValue) {
            state.distance.set(value)
            true
        } else false

    fun isNumberValid(value: Int, minValue: Int, maxValue: Int) =
        if (value in minValue..maxValue) {
            state.numberOfTests.set(value)
            true
        } else false
}