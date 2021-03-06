package at.specure.data.repository

import android.content.Context
import at.rmbt.client.control.ControlServerClient
import at.rmbt.util.exception.NoConnectionException
import at.rmbt.util.io
import at.specure.data.ClientUUID
import at.specure.data.CoreDatabase
import at.specure.data.entity.SignalMeasurementChunk
import at.specure.data.entity.SignalMeasurementInfo
import at.specure.data.entity.SignalMeasurementRecord
import at.specure.data.entity.TestTelephonyRecord
import at.specure.data.entity.TestWlanRecord
import at.specure.data.toModel
import at.specure.data.toRequest
import at.specure.info.TransportType
import at.specure.test.DeviceInfo
import at.specure.util.exception.DataMissingException
import at.specure.worker.WorkLauncher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import timber.log.Timber

class SignalMeasurementRepositoryImpl(
    private val db: CoreDatabase,
    private val context: Context,
    private val clientUUID: ClientUUID,
    private val client: ControlServerClient
) : SignalMeasurementRepository {

    private val deviceInfo = DeviceInfo(context)
    private val dao = db.signalMeasurementDao()
    private val testDao = db.testDao()

    override fun saveAndRegisterRecord(record: SignalMeasurementRecord) = io {
        dao.saveSignalMeasurementRecord(record)
        registerMeasurement(record.id)
            .catch { e ->
                if (e is NoConnectionException) {
                    emit(false)
                } else {
                    Timber.e(e, "Getting signal info record error")
                    emit(true)
                }
            }
            .collect {
                if (!it) {
                    WorkLauncher.enqueueSignalMeasurementInfoRequest(context, record.id)
                }
            }
    }

    override fun updateSignalMeasurementRecord(record: SignalMeasurementRecord) = io {
        dao.updateSignalMeasurementRecord(record)
    }

    override fun registerMeasurement(measurementId: String): Flow<Boolean> = flow {

        val uuid = clientUUID.value ?: throw DataMissingException("Missing client UUID")
        val record = dao.getSignalMeasurementRecord(measurementId) ?: throw DataMissingException("Measurement record $measurementId is missing")
        val body = record.toRequest(uuid, deviceInfo)

        val info = dao.getSignalMeasurementInfo(record.id)
        if (info == null) {

            val response = client.signalRequest(body)

            response.onSuccess {
                dao.saveSignalMeasurementInfo(it.toModel(measurementId))
            }

            if (response.ok) {
                emit(true)
            } else {
                throw response.failure
            }
        } else {
            emit(true)
        }
    }

    override fun saveMeasurementChunk(chunk: SignalMeasurementChunk) = io {
        dao.saveSignalMeasurementChunk(chunk)
    }

    override fun sendMeasurementChunk(chunk: SignalMeasurementChunk) = io {
        dao.saveSignalMeasurementChunk(chunk)
        sendMeasurementChunk(chunk.id)
            .catch { e ->
                if (e is NoConnectionException) {
                    emit(false)
                } else {
                    Timber.e(e, "Getting signal info record error")
                    emit(true)
                }
            }
            .collect {
                if (!it) {
                    WorkLauncher.enqueueSignalMeasurementChunkRequest(context, chunk.id)
                }
            }
    }

    override fun sendMeasurementChunk(chunkId: String): Flow<Boolean> = flow {
        val chunk = dao.getSignalMeasurementChunk(chunkId) ?: throw DataMissingException("SignalMeasurementChunk not found with id: $chunkId")
        val record = dao.getSignalMeasurementRecord(chunk.measurementId)
            ?: throw DataMissingException("SignalMeasurementRecord not found with id: ${chunk.measurementId}")

        var info = dao.getSignalMeasurementInfo(record.id)

        val clientUUID = clientUUID.value ?: throw DataMissingException("ClientUUID is null")

        val telephonyInfo: TestTelephonyRecord? =
            if (record.transportType == TransportType.CELLULAR) {
                testDao.getTelephonyRecord(chunkId)
            } else {
                null
            }

        val wlanInfo: TestWlanRecord? = if (record.transportType == TransportType.WIFI) {
            testDao.getWlanRecord(chunkId)
        } else {
            null
        }

        val body = record.toRequest(
            measurementInfoUUID = info?.uuid,
            clientUUID = clientUUID,
            chunk = chunk,
            deviceInfo = deviceInfo,
            telephonyInfo = telephonyInfo,
            wlanInfo = wlanInfo,
            locations = db.geoLocationDao().get(chunkId),
            capabilities = db.capabilitiesDao().get(chunkId),
            cellInfoList = db.cellInfoDao().get(chunkId),
            signalList = db.signalDao().get(chunkId),
            permissions = db.permissionStatusDao().get(chunkId),
            networkEvents = db.connectivityStateDao().getStates(chunkId).toRequest(),
            cellLocationList = db.cellLocationDao().get(chunkId)
        )

        val result = client.signalResult(body)

        if (result.ok) {

            if (info == null) {
                info = SignalMeasurementInfo(
                    measurementId = record.id,
                    uuid = result.success.uuid,
                    clientRemoteIp = "", // TODO need to fill that field
                    resultUrl = "", // TODO need to fill that field
                    provider = "" // TODO need to fill that field
                )
                dao.saveSignalMeasurementInfo(info)
            }

            emit(true)

            testDao.removeTelephonyInfo(chunkId)
            testDao.removeWlanRecord(chunkId)
            db.geoLocationDao().remove(chunkId)
            db.capabilitiesDao().remove(chunkId)
            db.cellInfoDao().removeCellInfo(chunkId)
            db.signalDao().remove(chunkId)
            db.permissionStatusDao().remove(chunkId)
            db.connectivityStateDao().remove(chunkId)
            db.cellLocationDao().remove(chunkId)
        } else {
            chunk.submissionRetryCount++

            if (result.failure !is NoConnectionException) {
                chunk.testErrorCause = result.failure.message
            }

            dao.saveSignalMeasurementChunk(chunk)
            throw result.failure
        }
    }
}