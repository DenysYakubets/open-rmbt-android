package at.specure.measurement

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import at.rmbt.client.control.data.TestFinishReason
import at.rmbt.util.exception.HandledException
import at.rmbt.util.exception.NoConnectionException
import at.rtr.rmbt.client.v2.task.result.QoSTestResultEnum
import at.rtr.rmbt.util.IllegalNetworkChangeException
import at.specure.config.Config
import at.specure.data.entity.LoopModeState
import at.specure.data.repository.ResultsRepository
import at.specure.data.repository.TestDataRepository
import at.specure.di.CoreInjector
import at.specure.di.NotificationProvider
import at.specure.location.LocationState
import at.specure.location.LocationWatcher
import at.specure.measurement.signal.SignalMeasurementProducer
import at.specure.measurement.signal.SignalMeasurementService
import at.specure.test.DeviceInfo
import at.specure.test.StateRecorder
import at.specure.test.TestController
import at.specure.test.TestProgressListener
import at.specure.test.toDeviceInfoLocation
import at.specure.util.CustomLifecycleService
import at.specure.worker.WorkLauncher
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class MeasurementService : CustomLifecycleService() {

    private var elapsedTime: Long = 0
    private var startTime: Long = 0

    @Inject
    lateinit var config: Config

    @Inject
    lateinit var runner: TestController

    @Inject
    lateinit var stateRecorder: StateRecorder

    @Inject
    lateinit var notificationProvider: NotificationProvider

    @Inject
    lateinit var testDataRepository: TestDataRepository

    @Inject
    lateinit var resultRepository: ResultsRepository

    @Inject
    lateinit var connectivityManager: ConnectivityManager

    @Inject
    lateinit var locationWatcher: LocationWatcher

    private val producer: Producer by lazy { Producer() }
    private val clientAggregator: ClientAggregator by lazy { ClientAggregator() }
    private var signalMeasurementProducer: SignalMeasurementProducer? = null
    private var signalMeasurementPauseRequired = false
    private var measurementState: MeasurementState = MeasurementState.IDLE

    private var measurementProgress = 0
    private var pingNanos = 0L
    private var downloadSpeedBps = 0L
    private var uploadSpeedBps = 0L
    private var hasErrors = false
    private var startNetwork: Network? = null
    private var qosTasksPassed = 0

    private var qosTasksTotal = 0
    private var qosProgressMap: Map<QoSTestResultEnum, Int> = mapOf()
    private var isLoopModeRunning = false
    private val notificationManager: NotificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    private lateinit var wakeLock: PowerManager.WakeLock

    private lateinit var wifiLock: WifiManager.WifiLock
    private var loopCountdownTimer: CountDownTimer? = null
    private var startPendingTest = false

    private val signalMeasurementConnection = object : ServiceConnection {

        override fun onServiceDisconnected(name: ComponentName?) {
            signalMeasurementProducer = null
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            signalMeasurementProducer = service as SignalMeasurementProducer
            if (signalMeasurementPauseRequired) {
                signalMeasurementProducer?.pauseMeasurement()
                signalMeasurementPauseRequired = false
            }
        }
    }

    private val testListener = object : TestProgressListener {

        override fun onProgressChanged(state: MeasurementState, progress: Int) {
            measurementState = state
            measurementProgress = progress
            clientAggregator.onProgressChanged(state, progress)

            if (state != MeasurementState.QOS) {
                notifyDelayed(
                    notificationProvider.measurementServiceNotification(
                        progress,
                        state,
                        !config.shouldRunQosTest,
                        stateRecorder.loopModeRecord,
                        config.loopModeNumberOfTests,
                        stopTestsIntent(this@MeasurementService)
                    )
                )
            }
        }

        /**
         * This methods solves problem with changing notification too often (then it is problem to click on the button in the notification)
         */
        fun notifyDelayed(notification: Notification) {
            if (elapsedTime > NOTIFICATION_UPDATE_INTERVAL_MS) {
                Handler(Looper.getMainLooper()).post(Runnable {
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        notification
                    )
                    startTime = System.currentTimeMillis()
                    elapsedTime = 0
                })
            } else elapsedTime = System.currentTimeMillis() - startTime
        }

        override fun onPingChanged(pingNanos: Long) {
            this@MeasurementService.pingNanos = pingNanos
            clientAggregator.onPingChanged(pingNanos)
        }

        override fun onDownloadSpeedChanged(progress: Int, speedBps: Long) {
            downloadSpeedBps = speedBps
            stateRecorder.onDownloadSpeedChanged(progress, speedBps)
            clientAggregator.onDownloadSpeedChanged(progress, speedBps)
        }

        override fun onUploadSpeedChanged(progress: Int, speedBps: Long) {
            uploadSpeedBps = speedBps
            stateRecorder.onUploadSpeedChanged(progress, speedBps)
            clientAggregator.onUploadSpeedChanged(progress, speedBps)
        }

        override fun onFinish() {
            resumeSignalMeasurement()

            stateRecorder.onLoopTestFinished()
            if (startPendingTest) {
                runner.reset()
                runTest()
            } else {
                if (!config.loopModeEnabled || (config.loopModeEnabled && (stateRecorder.loopTestCount >= config.loopModeNumberOfTests))) {
                    loopCountdownTimer?.cancel()

                    if (config.loopModeEnabled) {
                        notificationManager.notify(NOTIFICATION_LOOP_FINISHED_ID, notificationProvider.loopModeFinishedNotification())
                    }
                    stopForeground(true)
                    isLoopModeRunning = false
                    stateRecorder.finish()
                    unlock()
                }
            }
        }

        override fun onError() {
            resumeSignalMeasurement()
            if (startNetwork != connectivityManager.activeNetwork) {
                Timber.e("Network change!")
                try {
                    throw IllegalNetworkChangeException("Illegal network change during the test")
                } catch (ex: Exception) {
                    stateRecorder.setErrorCause(Log.getStackTraceString(ex))
                }
            }

            stateRecorder.onUnsuccessTest(TestFinishReason.ERROR)
            stateRecorder.onLoopTestFinished()

            if (config.loopModeEnabled) {
                if (stateRecorder.loopUuid == null) {
                    loopCountdownTimer?.cancel()
                    hasErrors = true
                    clientAggregator.onMeasurementError()
                    stateRecorder.finish()
                    isLoopModeRunning = false
                    unlock()
                    stopForeground(true)
                }
            }

            Timber.d("TEST ERROR HANDLING")

            if (startPendingTest) {
                Timber.d("TEST ERROR HANDLING - PENDING")
                runner.reset()
                runTest()
            } else {
                Timber.d("TEST ERROR HANDLING - NOT PENDING")
                if (!config.loopModeEnabled || (config.loopModeEnabled && (stateRecorder.loopTestCount >= config.loopModeNumberOfTests))) {
                    loopCountdownTimer?.cancel()
                    Timber.d("TEST ERROR HANDLING - NOT PENDING LOOP DISABLED")
                    if (config.loopModeEnabled) {
                        notificationManager.notify(NOTIFICATION_LOOP_FINISHED_ID, notificationProvider.loopModeFinishedNotification())
                    }
                    hasErrors = true
                    clientAggregator.onMeasurementError()
                    stateRecorder.finish()
                    isLoopModeRunning = false
                    unlock()
                    stopForeground(true)
                }
            }
        }

        override fun onClientReady(testUUID: String, loopUUID: String?, testStartTimeNanos: Long) {
            clientAggregator.onClientReady(testUUID, loopUUID)
            startNetwork = connectivityManager.activeNetwork
            stateRecorder.onReadyToSubmit = { shouldShowResults ->
                resultRepository.sendTestResults(testUUID) {
                    it.onSuccess {
                        if (shouldShowResults) {
                            clientAggregator.onSubmitted()
                        }
                    }

                    it.onFailure { ex ->
                        if (shouldShowResults) {
                            clientAggregator.onSubmitted()
                        }
                        if (ex is NoConnectionException) {
                            Timber.d("Delayed submission work created")
                            WorkLauncher.enqueueDelayedDataSaveRequest(applicationContext, testUUID)
                        }
                    }
                }
            }
        }

        override fun onQoSTestProgressUpdate(tasksPassed: Int, tasksTotal: Int, progressMap: Map<QoSTestResultEnum, Int>) {
            clientAggregator.onQoSTestProgressUpdated(tasksPassed, tasksTotal, progressMap)
            qosTasksPassed = tasksPassed
            qosTasksTotal = tasksTotal
            qosProgressMap = progressMap

            val progress = (tasksPassed / tasksTotal.toFloat()) * 100

            notifyDelayed(
                notificationProvider.measurementServiceNotification(
                    progress.toInt(),
                    MeasurementState.QOS,
                    !config.shouldRunQosTest,
                    stateRecorder.loopModeRecord,
                    config.loopModeNumberOfTests,
                    stopTestsIntent(this@MeasurementService)
                )
            )
        }
    }

    private fun resumeSignalMeasurement() {
        signalMeasurementPauseRequired = false
        signalMeasurementProducer?.resumeMeasurement()
    }

    private fun pauseSignalMeasurement() {
        signalMeasurementPauseRequired = true // in case when service connection wasn't established before test started
        signalMeasurementProducer?.pauseMeasurement()
    }

    override fun onCreate() {
        super.onCreate()
        CoreInjector.inject(this)

        stateRecorder.bind(this)

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "$packageName:RMBTWifiLock")
        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:RMBTWakeLock")

        stateRecorder.onLoopDistanceReached = {
            Timber.i("LOOP MODE DISTANCE REACHED")
            loopCountdownTimer?.cancel()

            if (stateRecorder.loopTestCount < config.loopModeNumberOfTests) {
                if (runner.isRunning) {
                    startPendingTest = true
                } else {
                    runner.reset()
                    runTest()
                }
            }
        }

        bindService(SignalMeasurementService.intent(this), signalMeasurementConnection, Context.BIND_AUTO_CREATE)
    }

    @Suppress("UNNECESSARY_SAFE_CALL")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            attachToForeground()
            when (intent?.action) {
                ACTION_START_TESTS -> startTests()
                ACTION_STOP_TESTS -> stopTests()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        super.onBind(intent)
        Timber.i("onBind")
        return producer
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Timber.i("onUnbind")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        resumeSignalMeasurement()
        signalMeasurementConnection.onServiceDisconnected(null)
        unbindService(signalMeasurementConnection)
        super.onDestroy()
    }

    private fun startTests() {
        Timber.d("Start tests")
        stateRecorder.resetLoopMode()
        isLoopModeRunning = config.loopModeEnabled

        attachToForeground()
        lock()

        runTest()
    }

    private fun scheduleNextLoopTest() {
        stateRecorder.onLoopTestScheduled()
        try {
            Handler(Looper.getMainLooper()).post {
                loopCountdownTimer?.cancel()
                loopCountdownTimer = null
                loopCountdownTimer = object : CountDownTimer(loopDelayMs, 1000) {

                    override fun onFinish() {
                        Timber.i("CountDownTimer finished")
                        if (runner.isRunning) {
                            startPendingTest = true
                        } else {
                            runner.reset()
                            runTest()
                        }
                    }

                    override fun onTick(millisUntilFinished: Long) {
                        Timber.d("CountDownTimer tick $millisUntilFinished - ${this.hashCode()}")
                        if (!runner.isRunning) {
                            val location = stateRecorder.locationInfo
                            val locationAvailable =
                                locationWatcher.state == LocationState.ENABLED &&
                                        location != null &&
                                        location.accuracy < config.loopModeDistanceMeters
                            val distancePassed = stateRecorder.loopModeRecord?.movementDistanceMeters ?: 0

                            Timber.v("Created measurement notification time remaining")
                            if (stateRecorder.loopModeRecord?.status == LoopModeState.IDLE) {
                                val notification = notificationProvider.loopCountDownNotification(
                                    millisUntilFinished,
                                    distancePassed,
                                    config.loopModeDistanceMeters,
                                    stateRecorder.loopTestCount,
                                    config.loopModeNumberOfTests,
                                    stopTestsIntent(this@MeasurementService),
                                    locationAvailable
                                )
                                notificationManager.notify(NOTIFICATION_ID, notification)
                                Timber.v("Created measurement notification time remaining")
                            }
                            clientAggregator.onLoopCountDownTimer(loopDelayMs - millisUntilFinished, loopDelayMs)
                            clientAggregator.onLoopDistanceChanged(distancePassed, config.loopModeDistanceMeters, locationAvailable)
                        }
                    }
                }

                loopCountdownTimer?.start()
            }

            Timber.d("CountDownTimer started")
        } catch (ex: Exception) {
            Timber.e(ex, "CountDownTimer")
        }
    }

    private val loopDelayMs: Long
        get() {
            val timeAwait = TimeUnit.MINUTES.toMillis(config.loopModeWaitingTimeMin.toLong())
            if (timeAwait == 0L) {
                return 3000
            }
            return timeAwait
        }

    private fun runTest() {
        startPendingTest = false
        if (config.loopModeEnabled && (stateRecorder.loopTestCount < config.loopModeNumberOfTests || (config.loopModeNumberOfTests == 0 && config.developerModeIsEnabled))) {
            scheduleNextLoopTest()
        }

        if (!runner.isRunning) {
            resetStates()
        }

        pauseSignalMeasurement()

        var location: DeviceInfo.Location? = null

        if (locationWatcher.state == LocationState.ENABLED) {
            stateRecorder.locationInfo?.let {
                location = it.toDeviceInfoLocation()
            }
        }

        stateRecorder.updateLocationInfo()
        stateRecorder.onTestInLoopStarted()

        val deviceInfo = DeviceInfo(
            context = this,
            location = location
        )

        qosTasksPassed = 0
        qosTasksTotal = 0
        qosProgressMap = mapOf()

        hasErrors = false

        runner.start(deviceInfo, stateRecorder.loopUuid, stateRecorder.loopTestCount, testListener, stateRecorder)
    }

    private fun attachToForeground() {
        startForeground(
            NOTIFICATION_ID,
            notificationProvider.measurementServiceNotification(
                0,
                MeasurementState.INIT,
                true,
                stateRecorder.loopModeRecord,
                config.loopModeNumberOfTests,
                stopTestsIntent(this@MeasurementService)
            )
        )
    }

    private fun stopTests() {
        Timber.d("Stop tests")
        isLoopModeRunning = false
        runner.stop()
        loopCountdownTimer?.cancel()
        config.previousTestStatus = TestFinishReason.ABORTED.name // cannot be handled in TestController
        stateRecorder.onUnsuccessTest(TestFinishReason.ABORTED)
        stateRecorder.finish()
        clientAggregator.onMeasurementCancelled()
        stopForeground(true)
        unlock()
    }

    private fun resetStates() {
        testListener.onProgressChanged(MeasurementState.IDLE, 0)
        testListener.onPingChanged(0)
        testListener.onDownloadSpeedChanged(0, 0)
        testListener.onUploadSpeedChanged(0, 0)
    }

    private fun lock() {
        try {
            if (!wakeLock.isHeld) {
                wakeLock.acquire(TimeUnit.MINUTES.toMillis(10))
            }
            if (!wifiLock.isHeld) {
                wifiLock.acquire()
            }
            Timber.i("Wake locked")
        } catch (ex: Exception) {
            Timber.e(ex, "Wake lock failed")
        }
    }

    private fun unlock() {
        try {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
            if (wifiLock.isHeld) {
                wifiLock.release()
            }
            Timber.v("Wake unlocked")
        } catch (ex: Exception) {
            Timber.e(ex, "Wake unlock failed")
        }
    }

    private inner class Producer : Binder(), MeasurementProducer {

        override fun addClient(client: MeasurementClient) {
            with(client) {
                clientAggregator.addClient(this)
                onProgressChanged(measurementState, measurementProgress)
                onPingChanged(pingNanos)
                onDownloadSpeedChanged(measurementProgress, downloadSpeedBps)
                onUploadSpeedChanged(measurementProgress, uploadSpeedBps)
                isQoSEnabled(config.shouldRunQosTest)
                runner.testUUID?.let {
                    onClientReady(it, stateRecorder.loopUuid)
                }
                if (hasErrors) {
                    client.onMeasurementError()
                }

                if (qosProgressMap.isNotEmpty()) {
                    onQoSTestProgressUpdated(qosTasksPassed, qosTasksTotal, qosProgressMap)
                }
            }
        }

        override fun removeClient(client: MeasurementClient) {
            clientAggregator.removeClient(client)
        }

        override val measurementState: MeasurementState
            get() = this@MeasurementService.measurementState

        override val measurementProgress: Int
            get() = this@MeasurementService.measurementProgress

        override val downloadSpeedBps: Long
            get() = this@MeasurementService.downloadSpeedBps

        override val uploadSpeedBps: Long
            get() = this@MeasurementService.uploadSpeedBps

        override val pingNanos: Long
            get() = this@MeasurementService.pingNanos

        override val isTestsRunning: Boolean
            get() = runner.isRunning || isLoopModeRunning

        override val testUUID: String?
            get() = runner.testUUID

        override val loopUUID: String?
            get() = stateRecorder.loopUuid

        override fun startTests() {
            this@MeasurementService.startTests()
        }

        override fun stopTests() {
            this@MeasurementService.stopTests()
        }
    }

    private inner class ClientAggregator : MeasurementClient {

        private val clients = mutableSetOf<MeasurementClient>()

        fun addClient(client: MeasurementClient) {
            clients.add(client)
        }

        fun removeClient(client: MeasurementClient) {
            clients.add(client)
        }

        override fun onProgressChanged(state: MeasurementState, progress: Int) {
            clients.forEach {
                it.onProgressChanged(state, progress)
            }
        }

        override fun onMeasurementError() {
            clients.forEach {
                it.onMeasurementError()
            }
        }

        override fun onDownloadSpeedChanged(progress: Int, speedBps: Long) {
            clients.forEach {
                it.onDownloadSpeedChanged(progress, speedBps)
            }
        }

        override fun onUploadSpeedChanged(progress: Int, speedBps: Long) {
            clients.forEach {
                it.onUploadSpeedChanged(progress, speedBps)
            }
        }

        override fun onPingChanged(pingNanos: Long) {
            clients.forEach {
                it.onPingChanged(pingNanos)
            }
        }

        override fun onClientReady(testUUID: String, loopUUID: String?) {
            clients.forEach {
                it.onClientReady(testUUID, loopUUID)
            }
        }

        override fun isQoSEnabled(enabled: Boolean) {
            clients.forEach {
                it.isQoSEnabled(enabled)
            }
        }

        override fun onSubmitted() {
            if (config.loopModeEnabled) {
                if (stateRecorder.loopTestCount >= config.loopModeNumberOfTests && config.loopModeNumberOfTests != 0) {
                    clients.forEach {
                        it.onSubmitted()
                    }
                }
            } else {
                clients.forEach {
                    it.onSubmitted()
                }
            }
        }

        override fun onSubmissionError(exception: HandledException) {
            if (config.loopModeEnabled) {
                if (config.loopModeNumberOfTests >= config.loopModeNumberOfTests && config.loopModeNumberOfTests != 0) {
                    clients.forEach {
                        it.onSubmissionError(exception)
                    }
                }
            } else {
                clients.forEach {
                    it.onSubmissionError(exception)
                }
            }
        }

        override fun onQoSTestProgressUpdated(tasksPassed: Int, tasksTotal: Int, progressMap: Map<QoSTestResultEnum, Int>) {
            clients.forEach {
                it.onQoSTestProgressUpdated(tasksPassed, tasksTotal, progressMap)
            }
        }

        override fun onLoopCountDownTimer(timePassedMillis: Long, timeTotalMillis: Long) {
            clients.forEach {
                it.onLoopCountDownTimer(timePassedMillis, timeTotalMillis)
            }
        }

        override fun onLoopDistanceChanged(distancePassed: Int, distanceTotal: Int, locationAvailable: Boolean) {
            clients.forEach {
                it.onLoopDistanceChanged(distancePassed, distanceTotal, locationAvailable)
            }
        }

        override fun onMeasurementCancelled() {
            clients.forEach {
                it.onMeasurementCancelled()
            }
        }
    }

    companion object {

        private const val NOTIFICATION_ID = 1
        const val NOTIFICATION_LOOP_FINISHED_ID = 2
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 500


        private const val ACTION_START_TESTS = "KEY_START_TESTS"
        private const val ACTION_STOP_TESTS = "KEY_STOP_TESTS"

        fun startTests(context: Context) {
            val intent = intent(context)
            intent.action = ACTION_START_TESTS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopTestsIntent(context: Context): Intent = Intent(context, MeasurementService::class.java).apply {
            action = ACTION_STOP_TESTS
        }

        fun intent(context: Context) = Intent(context, MeasurementService::class.java)
    }
}