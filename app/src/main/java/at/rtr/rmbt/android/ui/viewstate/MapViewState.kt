package at.rtr.rmbt.android.ui.viewstate

import android.os.Bundle
import androidx.databinding.ObservableField
import androidx.lifecycle.MutableLiveData
import at.rmbt.client.control.data.MapPresentationType
import at.rmbt.client.control.data.MapStyleType
import at.rtr.rmbt.android.ui.fragment.START_ZOOM_LEVEL
import com.google.android.gms.maps.model.LatLng

private const val KEY_TYPE = "KEY_TYPE"
private const val KEY_STYLE = "KEY_STYLE"

private const val KEY_ZOOM = "KEY_ZOOM"

private const val KEY_LATITUDE = "KEY_LATITUDE"
private const val KEY_LONGITUDE = "KEY_LONGITUDE"

class MapViewState : ViewState {

    var coordinatesLiveData: MutableLiveData<LatLng> = MutableLiveData()

    val type = ObservableField<MapPresentationType>(MapPresentationType.POINTS)
    val style = ObservableField<MapStyleType>(MapStyleType.STANDARD)

    var zoom: Float = START_ZOOM_LEVEL

    override fun onRestoreState(bundle: Bundle?) {
        bundle?.getInt(KEY_TYPE)?.let { type.set(MapPresentationType.values()[it]) }
        bundle?.getInt(KEY_STYLE)?.let { style.set(MapStyleType.values()[it]) }
        bundle?.getDouble(KEY_LATITUDE)?.let { coordinatesLiveData.postValue(LatLng(it, bundle.getDouble(KEY_LONGITUDE))) }
        bundle?.getFloat(KEY_ZOOM)?.let { zoom = it }
    }

    override fun onSaveState(bundle: Bundle?) {
        bundle?.putInt(KEY_TYPE, type.get()?.ordinal ?: 1)
        bundle?.putInt(KEY_STYLE, style.get()?.ordinal ?: 0)
        coordinatesLiveData.value?.latitude?.let { bundle?.putDouble(KEY_LATITUDE, it) }
        coordinatesLiveData.value?.longitude?.let { bundle?.putDouble(KEY_LONGITUDE, it) }
        bundle?.putFloat(KEY_ZOOM, zoom)
    }
}