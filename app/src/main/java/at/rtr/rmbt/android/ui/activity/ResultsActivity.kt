package at.rtr.rmbt.android.ui.activity

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import at.rtr.rmbt.android.R
import at.rtr.rmbt.android.databinding.ActivityResultsBinding
import at.rtr.rmbt.android.di.viewModelLazy
import at.rtr.rmbt.android.ui.adapter.ResultChartFragmentPagerAdapter
import at.rtr.rmbt.android.ui.adapter.ResultQoEAdapter
import at.rtr.rmbt.android.ui.fragment.ResultChartFragment
import at.rtr.rmbt.android.util.listen
import at.rtr.rmbt.android.viewmodel.ResultViewModel
import at.specure.data.NetworkTypeCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import timber.log.Timber


class ResultsActivity : BaseActivity(), OnMapReadyCallback {

    private val viewModel: ResultViewModel by viewModelLazy()
    private lateinit var binding: ActivityResultsBinding
    private val adapter: ResultQoEAdapter by lazy { ResultQoEAdapter() }
    //private val resultChartAdapter: ResultChartAdapter by lazy { ResultChartAdapter() }
    private val resultChartFragmentPagerAdapter: ResultChartFragmentPagerAdapter by lazy { ResultChartFragmentPagerAdapter(supportFragmentManager) }
    private var googleMap: GoogleMap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = bindContentView(R.layout.activity_results)
        binding.state = viewModel.state

        binding.map.onCreate(savedInstanceState)
        binding.map.getMapAsync(this)

        val testUUID = intent.getStringExtra(KEY_TEST_UUID)
        check(!testUUID.isNullOrEmpty()) { "TestUUID was not passed to result activity" }

        binding.viewPagerCharts.offscreenPageLimit = 3;
        binding.viewPagerCharts.adapter = resultChartFragmentPagerAdapter

        binding.tabLayoutCharts.setupWithViewPager(binding.viewPagerCharts,true)

        viewModel.state.testUUID = testUUID
        viewModel.testServerResultLiveData.listen(this) {
            viewModel.state.testResult.set(it)

            it?.testOpenUUID?.let { it1 ->loadGraphItems(it1) }

            if (it?.latitude != null && it.longitude != null) {
                with(LatLng(it.latitude!!, it.longitude!!)) {
                    googleMap?.addCircle(
                        CircleOptions()
                            .center(this)
                            .fillColor(ContextCompat.getColor(this@ResultsActivity, R.color.map_circle_fill))
                            .strokeColor(ContextCompat.getColor(this@ResultsActivity, R.color.map_circle_stroke))
                            .strokeWidth(STROKE_WIDTH)
                            .radius(CIRCLE_RADIUS)
                    )

                    val icon = when (it.networkType) {
                        NetworkTypeCompat.TYPE_WLAN -> R.drawable.ic_marker_wifi
                        NetworkTypeCompat.TYPE_4G -> R.drawable.ic_marker_4g
                        NetworkTypeCompat.TYPE_3G -> R.drawable.ic_marker_3g
                        NetworkTypeCompat.TYPE_2G -> R.drawable.ic_marker_2g
                        NetworkTypeCompat.TYPE_5G -> throw IllegalArgumentException("Need to add 5G marker image for the map")
                    }

                    googleMap?.addMarker(MarkerOptions().position(this).anchor(ANCHOR_U, ANCHOR_V).icon(bitmapDescriptorFromVector(icon)))
                    googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(this, ZOOM_LEVEL))
                    googleMap?.setOnMapClickListener { DetailedFullscreenMapActivity.start(this@ResultsActivity, testUUID) }
                }
            }



        }

        viewModel.testResultDetailsLiveData.listen(this) {
            Timber.d("found ${it.size} rows of details")
            // todo: display result details
        }






        viewModel.qoeResultLiveData.listen(this) {
            viewModel.state.qoeRecords.set(it)
            adapter.submitList(it)
        }
        binding.qoeResultsRecyclerView.adapter = adapter
        binding.qoeResultsRecyclerView.apply {
            val itemDecoration = DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
            ContextCompat.getDrawable(context, R.drawable.history_item_divider)?.let {
                itemDecoration.setDrawable(it)
            }
            binding.qoeResultsRecyclerView.addItemDecoration(itemDecoration)
        }
        binding.buttonBack.setOnClickListener {
            super.onBackPressed()
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshResults()
        }

        viewModel.loadingLiveData.listen(this) {
            binding.swipeRefreshLayout.isRefreshing = false
            if (viewModel.state.testResult.get() == null) {
                binding.textFailedToLoad.visibility = if (it) View.GONE else View.VISIBLE
            } else {
                binding.textFailedToLoad.visibility = View.GONE
            }
        }

        refreshResults()
    }

    private fun loadGraphItems(openTestUUID: String) {

        viewModel.loadGraphItems(openTestUUID)

        viewModel.downloadGraphItemsLiveData?.listen(this) {
            it?.let { items ->
                resultChartFragmentPagerAdapter.getFragment(0)?.let {
                    (it as ResultChartFragment).setGraphItems(items)
                }
            }
        }

        viewModel.uploadGraphItemsLiveData?.listen(this) {
            it?.let { items ->
                resultChartFragmentPagerAdapter.getFragment(1)?.let {
                    (it as ResultChartFragment).setGraphItems(items)
                }
            }
        }

        viewModel.pingGraphItemsLiveData?.listen(this) {
            it?.let { items ->
                resultChartFragmentPagerAdapter.getFragment(2)?.let {
                    (it as ResultChartFragment).setGraphItems(items)
                }
            }
        }
    }
    private fun refreshResults() {
        viewModel.loadTestResults()
        binding.swipeRefreshLayout.isRefreshing = true
    }

    override fun onMapReady(map: GoogleMap?) {
        googleMap = map
        map?.let {
            with(map.uiSettings) {
                isScrollGesturesEnabled = false
                isZoomGesturesEnabled = false
                isRotateGesturesEnabled = false
            }
        }
    }

    override fun onStart() {
        super.onStart()
        binding.map.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
    }

    override fun onStop() {
        super.onStop()
        binding.map.onStop()
    }

    override fun onPause() {
        binding.map.onPause()
        super.onPause()
    }

    private fun bitmapDescriptorFromVector(vectorResId: Int): BitmapDescriptor? {
        return ContextCompat.getDrawable(this, vectorResId)?.run {
            setBounds(0, 0, intrinsicWidth, intrinsicHeight)
            val bitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
            draw(Canvas(bitmap))
            BitmapDescriptorFactory.fromBitmap(bitmap)
        }
    }

    companion object {

        private const val ZOOM_LEVEL = 15f
        private const val CIRCLE_RADIUS = 13.0
        private const val STROKE_WIDTH = 7f
        private const val ANCHOR_U = 0.5f
        private const val ANCHOR_V = 0.865f

        private const val KEY_TEST_UUID = "KEY_TEST_UUID"

        fun start(context: Context, testUUID: String) {
            val intent = Intent(context, ResultsActivity::class.java)
            intent.putExtra(KEY_TEST_UUID, testUUID)
            context.startActivity(intent)
        }
    }
}