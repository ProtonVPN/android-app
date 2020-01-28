/*
 * Copyright (c) 2019 Proton Technologies AG
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
package com.protonvpn.android.vpn

import android.os.Bundle
import android.os.FileObserver
import android.os.Handler
import android.os.Looper.getMainLooper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import butterknife.BindView
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseFragment
import com.protonvpn.android.components.BaseViewHolder
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.models.config.UserData
import de.blinkt.openpvpn.core.LogItem
import de.blinkt.openpvpn.core.VpnStatus
import org.slf4j.MDC.clear
import org.strongswan.android.logic.CharonVpnService
import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList

@ContentLayout(R.layout.fragment_log)
class LogFragment : BaseFragment(), VpnStatus.LogListener {

    companion object {
        private val DATE_FORMATTER = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }

    private var logFilePath: String? = null
    private var logHandler: Handler? = null

    private val logAdapter = LogAdapter()
    private var directoryObserver: FileObserver? = null
    internal var log: MutableList<String> = ArrayList()
    internal var openVpnLog = false

    @BindView(R.id.recyclerView) @JvmField var recyclerView: RecyclerView? = null
    @Inject lateinit var stateMonitor: VpnStateMonitor
    @Inject lateinit var userData: UserData

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        logFilePath = activity!!.filesDir.toString() + File.separator + CharonVpnService.LOG_FILE

        logHandler = Handler(getMainLooper())

        directoryObserver = LogDirectoryObserver(activity!!.filesDir.absolutePath)
    }

    override fun onViewCreated() {
        recyclerView?.adapter = logAdapter

        openVpnLog = stateMonitor.connectionProfile?.isOpenVPNSelected(userData)
                ?: userData.isOpenVPNSelected

        addPreviousEntries()
    }

    override fun onStart() {
        super.onStart()
        if (openVpnLog) {
            VpnStatus.addLogListener(this)
        } else {
            logAdapter.restart()
            directoryObserver!!.startWatching()
        }
    }

    override fun onStop() {
        super.onStop()
        if (openVpnLog) {
            VpnStatus.removeLogListener(this)
        } else {
            directoryObserver!!.stopWatching()
            logAdapter.stop()
        }
    }

    private fun addPreviousEntries() {
        for (item in VpnStatus.getlogbuffer()) {
            log.add(formatOpenVpnLogItem(item))
        }

        recyclerView?.scrollToPosition(log.size - 1)
    }

    private fun formatOpenVpnLogItem(item: LogItem) =
            "${DATE_FORMATTER.format(Date(item.logtime))} ${item.getString(activity)}"

    override fun newLog(logItem: LogItem) {
        addToLog(formatOpenVpnLogItem(logItem))
    }

    private fun addToLog(item: String) {
        logHandler!!.post {
            log.add(item)
            logAdapter.notifyDataSetChanged()
            recyclerView?.scrollToPosition(log.size - 1)
        }
    }

    private inner class LogAdapter : RecyclerView.Adapter<LogLineViewHolder>(), Runnable {

        private var reader: BufferedReader? = null
        private var thread: Thread? = null
        @Volatile private var isRunning: Boolean = false

        fun restart() {
            if (isRunning) {
                stop()
            }

            clear()

            reader = try {
                BufferedReader(FileReader(logFilePath))
            } catch (e: FileNotFoundException) {
                BufferedReader(StringReader(""))
            }

            isRunning = true
            thread = Thread(this)
            readPrevious()
            thread!!.start()
        }

        fun readPrevious() {
            reader!!.lineSequence().forEach(::addToLog)
        }

        fun stop() {
            try {
                reader?.close()
                isRunning = false
                thread!!.interrupt()
                thread!!.join()
            } catch (e: InterruptedException) {
            }
        }

        override fun run() {
            while (isRunning && !openVpnLog) {
                try {
                    val line = reader!!.readLine()
                    if (line != null) {
                        addToLog(line)
                        /* wait until there is more to log */
                        Thread.sleep(1000)
                    }
                } catch (e: Exception) {
                    break
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogLineViewHolder {
            return LogLineViewHolder(
                    LayoutInflater.from(parent.context).inflate(R.layout.log_item, parent, false))
        }

        override fun onBindViewHolder(holder: LogLineViewHolder, position: Int) {
            holder.bindData(log[position])
        }

        override fun getItemCount(): Int {
            return log.size
        }
    }

    inner class LogLineViewHolder internal constructor(view: View) : BaseViewHolder<String>(view) {

        @BindView(R.id.logLine) lateinit var logLine: TextView

        override fun bindData(message: String) {
            logLine.text = message
        }
    }

    private inner class LogDirectoryObserver(path: String) : FileObserver(path, CREATE or MODIFY or DELETE) {

        private val file: File = File(logFilePath)
        private var size: Long = 0

        init {
            size = file.length()
        }

        override fun onEvent(event: Int, path: String?) {
            if (path == null || path != CharonVpnService.LOG_FILE) {
                return
            }
            when (event) {
                CREATE, DELETE -> restartLogReader()
                MODIFY -> {
                    /* if the size got smaller reopen the log file, as it was probably truncated */
                    val size = file.length()
                    if (size < this.size) {
                        restartLogReader()
                    }
                    this.size = size
                }
            }
        }

        private fun restartLogReader() {
            logHandler!!.post { logAdapter.restart() }
        }
    }
}
