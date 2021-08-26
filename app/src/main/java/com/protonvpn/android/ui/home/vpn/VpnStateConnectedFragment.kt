/*
 * Copyright (c) 2021. Proton Technologies AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.android.ui.home.vpn

import android.animation.LayoutTransition
import android.content.res.ColorStateList
import android.graphics.DashPathEffect
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.jobs.MoveViewJob
import com.google.android.material.color.MaterialColors
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.bus.TrafficUpdate
import com.protonvpn.android.components.BaseFragmentV2
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.databinding.FragmentVpnStateConnectedBinding
import com.protonvpn.android.models.config.NetShieldProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.ui.ServerLoadColor.getColor
import com.protonvpn.android.utils.ConnectionTools
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.utils.ViewUtils.toDp
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStateMonitor
import java.text.DecimalFormat
import javax.inject.Inject

private const val CHART_LINE_WIDTH_DP = 3f
private val CHART_LINE_MODE = LineDataSet.Mode.HORIZONTAL_BEZIER
private const val CHART_LABEL_COUNT = 3 // More or less, the chart adjusts around this value.
private const val CHART_X_AXIS_WIDTH_S = (TrafficMonitor.TRAFFIC_HISTORY_LENGTH_S - 1).toFloat()
private const val CHART_AXIS_LABEL_COLOR_ATTR = R.attr.proton_text_weak
private const val CHART_GRID_LINE_COLOR_ATTR = R.attr.proton_separator_norm
private const val CHART_GRID_LINE_DASH_SPACE = 8f
private const val CHART_GRID_LINE_WIDTH_DP = 1f
private const val CHART_MIN_HEIGHT_DP = 100

@ContentLayout(R.layout.fragment_vpn_state_connected)
class VpnStateConnectedFragment :
    BaseFragmentV2<VpnStateConnectedViewModel, FragmentVpnStateConnectedBinding>() {

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    // All these dependencies are required by the NetShieldSwitch.
    // Once we refactor it, they should be removed.
    @Inject lateinit var userData: UserData
    @Inject lateinit var appConfig: AppConfig
    @Inject lateinit var stateMonitor: VpnStateMonitor
    @Inject lateinit var vpnConnectionManager: VpnConnectionManager
    // End of NetShieldSwitch's dependencies.

    private lateinit var parentViewModel: VpnStateViewModel

    private val downloadDataSet by lazy(LazyThreadSafetyMode.NONE) {
        initDataSet(ContextCompat.getColor(requireContext(), R.color.download))
    }
    private val uploadDataSet by lazy(LazyThreadSafetyMode.NONE) {
        initDataSet(ContextCompat.getColor(requireContext(), R.color.upload))
    }

    private val yAxisValueFormatter = object : ValueFormatter() {
        private val formatter = DecimalFormat("0")

        override fun getFormattedValue(value: Float): String =
            speedAxisFormatter(value, formatter)
    }

    override fun initViewModel() {
        viewModel =
            ViewModelProvider(this, viewModelFactory).get(VpnStateConnectedViewModel::class.java)
        parentViewModel =
            ViewModelProvider(requireParentFragment(), viewModelFactory).get(VpnStateViewModel::class.java)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initChart()

        with(binding) {
            layoutConnected.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)

            buttonSaveToProfile.setOnClickListener { viewModel.saveToProfile() }

            // TODO: NetShield onboarding popup.
            netShieldSwitch.init(
                userData.netShieldProtocol,
                appConfig,
                viewLifecycleOwner,
                userData,
                stateMonitor,
                vpnConnectionManager
            ) { s: NetShieldProtocol? ->
                userData.netShieldProtocol = s
            }
            netShieldSwitch.onRadiosExpandClicked = { parentViewModel.onNetShieldExpandClicked() }
            parentViewModel.netShieldExpandStatus.asLiveData()
                .observe(viewLifecycleOwner, Observer { netShieldSwitch.radiosExpanded = it })
            userData.netShieldLiveData.observe(viewLifecycleOwner, Observer<NetShieldProtocol> { state ->
                if (state != null) {
                    netShieldSwitch.setNetShieldValue(state)
                }
            })
        }

        viewModel.connectionState.asLiveData().observe(viewLifecycleOwner, Observer {
            updateConnectionState(it)
        })
        parentViewModel.trafficStatus.observe(viewLifecycleOwner, Observer {
            updateTrafficInfo(it)
        })
        viewModel.eventNotification.asLiveData().observe(viewLifecycleOwner, Observer { textRes ->
            Toast.makeText(requireActivity(), textRes, Toast.LENGTH_LONG).show()
        })
        viewModel.trafficSpeedKbpsHistory.observe(viewLifecycleOwner, Observer {
            updateChart(it)
        })

        updateTrafficInfo(TrafficUpdate(0L, 0L, 0L, 0L, 0L, 0))
    }

    override fun onDestroyView() {
        // Workaround for charting library memory leak
        // https://github.com/PhilJay/MPAndroidChart/issues/2238
        MoveViewJob.getInstance(null, 0f, 0f, null, null)

        super.onDestroyView()
    }

    private fun updateConnectionState(state: VpnStateConnectedViewModel.ConnectionState) {
        with(binding) {
            textServerName.text = state.serverName
            textProtocol.text = state.protocol
            textServerIp.text = state.exitIp
            textLoad.text = getString(R.string.serverLoad, state.serverLoad.toString())
            ImageViewCompat.setImageTintList(
                imageLoad,
                ColorStateList.valueOf(getColor(imageLoad, state.serverLoadState))
            )
        }
    }

    private fun updateTrafficInfo(update: TrafficUpdate?) = with(binding) {
        if (update != null) {
            textUploadVolume.text = ConnectionTools.bytesToSize(update.sessionUpload)
            textDownloadVolume.text = ConnectionTools.bytesToSize(update.sessionDownload)
        }
    }

    private fun initChart() {
        with(binding) {
            chart.description.isEnabled = false
            chart.setTouchEnabled(false)
            chart.legend.isEnabled = false
            chart.setDrawGridBackground(false)
            chart.axisRight.isEnabled = false
            chart.axisLeft.apply {
                labelCount = CHART_LABEL_COUNT
                textColor = MaterialColors.getColor(chart, CHART_AXIS_LABEL_COLOR_ATTR)
                gridColor = MaterialColors.getColor(chart, CHART_GRID_LINE_COLOR_ATTR)
                gridLineWidth = CHART_GRID_LINE_WIDTH_DP
                val dash = floatArrayOf(CHART_GRID_LINE_DASH_SPACE, CHART_GRID_LINE_DASH_SPACE)
                setGridDashedLine(DashPathEffect(dash, 0f))
                setDrawAxisLine(false)
                axisMinimum = 0f
                granularity = 1f
                valueFormatter = yAxisValueFormatter
            }
            chart.xAxis.isEnabled = false

            chart.viewTreeObserver.addOnGlobalLayoutListener {
                val chartHeightInDp: Float =
                    if (chart.visibility == View.VISIBLE) chart.height.toDp()
                    else spacerNoChart.height.toDp()
                if (chartHeightInDp < CHART_MIN_HEIGHT_DP) {
                    chart.visibility = View.GONE
                    spacerNoChart.visibility = View.VISIBLE
                } else {
                    chart.visibility = View.VISIBLE
                    spacerNoChart.visibility = View.GONE
                }
            }

            chart.data = LineData().apply {
                addDataSet(uploadDataSet)
                addDataSet(downloadDataSet)
            }
            updateChartViewport()
        }
    }

    private fun updateChart(chartData: VpnStateConnectedViewModel.TrafficSpeedChartData) {
        uploadDataSet.values = chartData.uploadKpbsHistory
        downloadDataSet.values = chartData.downloadKbpsHistory
        with(binding.chart) {
            data.notifyDataChanged()
            notifyDataSetChanged()
            updateChartViewport()
        }
    }

    private fun updateChartViewport() {
        with(binding.chart) {
            setVisibleXRange(CHART_X_AXIS_WIDTH_S, CHART_X_AXIS_WIDTH_S)
            setVisibleYRangeMinimum(1f, YAxis.AxisDependency.LEFT)
            moveViewTo(-CHART_X_AXIS_WIDTH_S, 0f, YAxis.AxisDependency.LEFT)
        }
    }

    private fun initDataSet(@ColorInt color: Int): LineDataSet =
        LineDataSet(ArrayList(), "DataSet").apply {
            lineWidth = CHART_LINE_WIDTH_DP
            mode = CHART_LINE_MODE
            setDrawIcons(false)
            this.color = color
            setDrawValues(false)
            setDrawCircleHole(false)
            setDrawCircles(false)
        }

    private fun speedAxisFormatter(speedKbps: Float, formatter: DecimalFormat): String {
        // The chart library cannot be configured to put labels at 1024-based values.
        // The graph isn't meant to be very precise so let's divide by 1000.
        val k: Float = speedKbps
        val m: Float = speedKbps / 1000f
        val g: Float = speedKbps / 1000f / 1000f
        val t: Float = speedKbps / 1000f / 1000f / 1000f

        // TODO: use translatable format strings here and in ConnectionTools.
        return when {
            t > 1 -> formatter.format(t) + " TB/s"
            g > 1 -> formatter.format(g) + " GB/s"
            m > 1 -> formatter.format(m) + " MB/s"
            else -> formatter.format(k) + " KB/s"
        }
    }
}
