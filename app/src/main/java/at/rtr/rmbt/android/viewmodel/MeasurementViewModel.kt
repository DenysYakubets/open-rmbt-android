package at.rtr.rmbt.android.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import at.rtr.rmbt.android.ui.viewstate.MeasurementViewState
import at.rtr.rmbt.android.util.plusAssign
import at.specure.database.entity.GraphItemRecord
import at.specure.info.network.ActiveNetworkLiveData
import at.specure.info.strength.SignalStrengthLiveData
import at.specure.measurement.MeasurementClient
import at.specure.measurement.MeasurementProducer
import at.specure.measurement.MeasurementService
import at.specure.measurement.MeasurementState
import at.specure.repository.TestDataRepository
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class MeasurementViewModel @Inject constructor(
    private val testDataRepository: TestDataRepository,
    val signalStrengthLiveData: SignalStrengthLiveData,
    val activeNetworkLiveData: ActiveNetworkLiveData
) : BaseViewModel(), MeasurementClient {

    private val _measurementFinishLiveData = MutableLiveData<Boolean>()
    private val _isTestsRunningLiveData = MutableLiveData<Boolean>()
    private val _measurementErrorLiveData = MutableLiveData<Boolean>()
    private val _downloadGraphLiveData = MutableLiveData<List<GraphItemRecord>>()
    private val _uploadGraphLiveData = MutableLiveData<List<GraphItemRecord>>()

    private var producer: MeasurementProducer? = null // TODO make field private

    val state = MeasurementViewState()

    val measurementFinishLiveData: LiveData<Boolean>
        get() = _measurementFinishLiveData

    val isTestsRunningLiveData: LiveData<Boolean>
        get() = _isTestsRunningLiveData

    val measurementErrorLiveData: LiveData<Boolean>
        get() = _measurementErrorLiveData

    val downloadGraphSource: LiveData<List<GraphItemRecord>>
        get() = _downloadGraphLiveData

    val uploadGraphSource: LiveData<List<GraphItemRecord>>
        get() = _uploadGraphLiveData

    private val serviceConnection = object : ServiceConnection {

        override fun onServiceDisconnected(componentName: ComponentName?) {
            producer?.removeClient(this@MeasurementViewModel)
            producer = null
            Timber.i("On service disconnected")
        }

        override fun onServiceConnected(componentName: ComponentName?, binder: IBinder?) {
            producer = binder as MeasurementProducer?
            Timber.i("On service connected")

            _isTestsRunningLiveData.postValue(producer?.isTestsRunning ?: false)

            producer?.let {
                it.addClient(this@MeasurementViewModel)

                with(state) {
                    measurementState.set(it.measurementState)
                    measurementProgress.set(it.measurementProgress)
                    pingMs.set(TimeUnit.NANOSECONDS.toMillis(it.pingNanos))
                    downloadSpeedBps.set(it.downloadSpeedBps)
                    uploadSpeedBps.set(it.uploadSpeedBps)
                }
            }
        }
    }

    init {
        addStateSaveHandler(state)
    }

    fun attach(context: Context) {
        context.bindService(MeasurementService.intent(context), serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun detach(context: Context) {
        producer?.removeClient(this)
        context.unbindService(serviceConnection)
    }

    override fun onProgressChanged(state: MeasurementState, progress: Int) {
        this.state.measurementState.set(state)
        this.state.measurementProgress.set(progress)
    }

    override fun onMeasurementFinish() {
        _measurementFinishLiveData.postValue(true)
    }

    override fun onMeasurementError() {
        _measurementErrorLiveData.postValue(true)
    }

    override fun onDownloadSpeedChanged(progress: Int, speedBps: Long) {
        state.downloadSpeedBps.set(speedBps)
        state.measurementDownloadUploadProgress.set(progress)

        if (state.measurementState.get() == MeasurementState.DOWNLOAD) {
            _downloadGraphLiveData += GraphItemRecord(testUUID = "", progress = progress, value = speedBps, type = GraphItemRecord.GRAPH_ITEM_TYPE_DOWNLOAD)
        }
    }

    override fun onUploadSpeedChanged(progress: Int, speedBps: Long) {
        state.uploadSpeedBps.set(speedBps)
        state.measurementDownloadUploadProgress.set(progress)

        if (state.measurementState.get() == MeasurementState.UPLOAD) {
            _uploadGraphLiveData += GraphItemRecord(testUUID = "", progress = progress, value = speedBps, type = GraphItemRecord.GRAPH_ITEM_TYPE_UPLOAD)
        }
    }

    override fun onPingChanged(pingNanos: Long) {
        state.pingMs.set(TimeUnit.NANOSECONDS.toMillis(pingNanos))
    }

    override fun isQoSEnabled(enabled: Boolean) {
        state.qosEnabled.set(enabled)
    }

    fun cancelMeasurement() {
        producer?.stopTests()
    }

    override fun onClientReady(testUUID: String) {

        testDataRepository.getDownloadGraphItemsLiveData(testUUID) {
            _downloadGraphLiveData.postValue(it)
        }

        testDataRepository.getUploadGraphItemsLiveData(testUUID) {
            _uploadGraphLiveData.postValue(it)
        }
    }
}