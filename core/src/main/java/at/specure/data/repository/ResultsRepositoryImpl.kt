package at.specure.data.repository

import android.content.Context
import at.rmbt.client.control.BaseResponse
import at.rmbt.client.control.ControlServerClient
import at.rmbt.util.Maybe
import at.rmbt.util.io
import at.specure.data.ClientUUID
import at.specure.data.CoreDatabase
import at.specure.data.entity.TestTelephonyRecord
import at.specure.data.entity.TestWlanRecord
import at.specure.data.toRequest
import at.specure.info.TransportType
import at.specure.test.DeviceInfo
import at.specure.util.exception.DataMissingException
import javax.inject.Inject

class ResultsRepositoryImpl @Inject constructor(
    context: Context,
    private val db: CoreDatabase,
    private val clientUUID: ClientUUID,
    private val client: ControlServerClient
) : ResultsRepository {

    private val deviceInfo = DeviceInfo(context)

    override fun sendTestResults(testUUID: String, callback: (Maybe<BaseResponse>) -> Unit) = io {
        callback.invoke(sendTestResults(testUUID))
    }

    override fun sendTestResults(testUUID: String): Maybe<BaseResponse> {
        val testDao = db.testDao()
        val testRecord = testDao.get(testUUID) ?: throw DataMissingException("TestRecord not found uuid: $testUUID")
        val clientUUID = clientUUID.value ?: throw DataMissingException("ClientUUID is null")

        val telephonyInfo: TestTelephonyRecord? =
            if (testRecord.transportType == TransportType.CELLULAR) {
                db.testDao().getTelehonyRecord(testUUID)
            } else {
                null
            }

        val wlanInfo: TestWlanRecord? = if (testRecord.transportType == TransportType.WIFI) {
            db.testDao().getWlanRecord(testUUID)
        } else {
            null
        }

        val body = testRecord.toRequest(
            clientUUID = clientUUID,
            deviceInfo = deviceInfo,
            telephonyInfo = telephonyInfo,
            wlanInfo = wlanInfo,
            locations = db.geoLocationDao().get(testUUID),
            capabilities = db.capabilitiesDao().get(testUUID),
            pingList = db.pingDao().get(testUUID),
            cellInfoList = db.cellInfoDao().get(testUUID),
            signalList = db.signalDao().get(testUUID),
            speedInfoList = db.speedDao().get(testUUID),
            cellLocationList = db.cellLocationDao().get(testUUID),
            permissions = db.permissionStatusDao().get(testUUID)
        )

        testDao.updateSubmissionsRetryCounter(testUUID)
        return client.sendTestResults(body)
    }
}