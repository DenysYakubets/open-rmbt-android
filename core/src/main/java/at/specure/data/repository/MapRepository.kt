package at.specure.data.repository

import androidx.lifecycle.LiveData
import at.rmbt.client.control.data.MapPresentationType
import at.specure.data.entity.MarkerMeasurementRecord

interface MapRepository {

    fun loadMarkers(latitude: Double, longitude: Double, zoom: Int)

    fun getMarkers(latitude: Double?, longitude: Double?): LiveData<List<MarkerMeasurementRecord>>

    fun loadTiles(x: Int, y: Int, zoom: Int, type: MapPresentationType): ByteArray?

    fun loadAutomaticTiles(x: Int, y: Int, zoom: Int): ByteArray?
}