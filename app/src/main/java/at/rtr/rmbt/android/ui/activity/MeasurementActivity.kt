/*
 *
 *  Licensed under the Apache License, Version 2.0 (the “License”);
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an “AS IS” BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * /
 */

package at.rtr.rmbt.android.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import at.rtr.rmbt.android.R
import at.rtr.rmbt.android.databinding.ActivityMeasurementBinding
import at.rtr.rmbt.android.di.viewModelLazy
import at.rtr.rmbt.android.ui.adapter.QosMeasurementAdapter
import at.rtr.rmbt.android.ui.dialog.SimpleDialog
import at.rtr.rmbt.android.util.listen
import at.rtr.rmbt.android.viewmodel.MeasurementViewModel
import at.specure.measurement.MeasurementState
import kotlinx.android.synthetic.main.activity_measurement.view.measurementBottomView
import kotlinx.android.synthetic.main.measurement_bottom_view.view.qosTestRecyclerView
import kotlinx.android.synthetic.main.measurement_bottom_view.view.speedChartDownloadUpload

private const val CODE_CANCEL = 0
private const val CODE_ERROR = 1

class MeasurementActivity : BaseActivity(), SimpleDialog.Callback {

    private val viewModel: MeasurementViewModel by viewModelLazy()
    private lateinit var binding: ActivityMeasurementBinding
    private val qosAdapter = QosMeasurementAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = bindContentView(R.layout.activity_measurement)
        binding.state = viewModel.state

        binding.buttonCancel.setOnClickListener { onCrossIconClicked() }

        viewModel.measurementFinishLiveData.listen(this) {
            finish()
            ResultsActivity.start(this)
        }

        viewModel.measurementErrorLiveData.listen(this) {
            SimpleDialog.Builder()
                .messageText(R.string.test_dialog_error_text)
                .positiveText(R.string.input_setting_dialog_ok)
                .cancelable(false)
                .show(supportFragmentManager, CODE_ERROR)
        }

        viewModel.submissionErrorLiveData.listen(this) {
            SimpleDialog.Builder()
                .messageText(R.string.test_submission_error_text)
                .positiveText(R.string.test_submission_error_accept)
                .cancelable(false)
                .show(supportFragmentManager, CODE_ERROR)
        }
        binding.root.measurementBottomView.qosTestRecyclerView.apply {

            val itemDecoration = DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
            ContextCompat.getDrawable(context, R.drawable.qos_test_measurement_item_divider)?.let {
                itemDecoration.setDrawable(it)
            }
            addItemDecoration(itemDecoration)
            adapter = qosAdapter
        }

        viewModel.downloadGraphSource.listen(this) {
            if (viewModel.state.measurementState.get() == MeasurementState.DOWNLOAD) {
                binding.root.measurementBottomView.speedChartDownloadUpload.addGraphItems(it)
            }
        }

        viewModel.uploadGraphSource.listen(this) {
            if (viewModel.state.measurementState.get() == MeasurementState.UPLOAD) {
                binding.root.measurementBottomView.speedChartDownloadUpload.addGraphItems(it)
            }
        }

        viewModel.signalStrengthLiveData.listen(this) {
            viewModel.state.signalStrengthInfo.set(it)
        }

        viewModel.activeNetworkLiveData.listen(this) {
            viewModel.state.networkInfo.set(it)
        }

        viewModel.qosProgressLiveData.listen(this) {
            qosAdapter.values = it
        }
    }

    override fun onDialogPositiveClicked(code: Int) {
        if (code == CODE_CANCEL) {
            viewModel.cancelMeasurement()
        }
        // finish activity for in both cases
        finish()
    }

    override fun onDialogNegativeClicked(code: Int) {
        // Do nothing
    }

    override fun onStart() {
        super.onStart()
        viewModel.attach(this)
    }

    override fun onStop() {
        super.onStop()
        viewModel.detach(this)
    }

    override fun onBackPressed() {
        onCrossIconClicked()
    }

    private fun onCrossIconClicked() {
        SimpleDialog.Builder()
            .messageText(R.string.title_cancel_measurement)
            .positiveText(R.string.text_cancel_measurement)
            .negativeText(R.string.text_continue_measurement)
            .cancelable(false)
            .show(supportFragmentManager, CODE_CANCEL)
    }

    companion object {

        fun start(context: Context) {
            val intent = Intent(context, MeasurementActivity::class.java)
            context.startActivity(intent)
        }
    }
}
