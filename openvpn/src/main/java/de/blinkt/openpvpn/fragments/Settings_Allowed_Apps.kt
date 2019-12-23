/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openpvpn.fragments

import android.Manifest
import android.app.Activity
import android.app.Fragment
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.CompoundButton
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.ListView
import android.widget.SearchView
import android.widget.Switch
import android.widget.TextView

import java.util.Collections
import java.util.Locale
import java.util.Vector

import de.blinkt.openpvpn.R
import de.blinkt.openpvpn.VpnProfile
import de.blinkt.openpvpn.core.ProfileManager

/**
 * Created by arne on 16.11.14.
 */
class Settings_Allowed_Apps : Fragment(), AdapterView.OnItemClickListener, CompoundButton.OnCheckedChangeListener, View.OnClickListener {
    private lateinit var mListView: ListView
    private lateinit var mProfile: VpnProfile
    private lateinit var mDefaultAllowTextView: TextView
    private lateinit var mListAdapter: PackageAdapter
    private lateinit var mSettingsView: View


    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        val avh = view.tag as AppViewHolder
        avh.checkBox.toggle()
    }

    override fun onClick(v: View) {

    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        val packageName = buttonView.tag as String
        if (isChecked) {
            mProfile.mAllowedAppsVpn.add(packageName)
        } else {
            mProfile.mAllowedAppsVpn.remove(packageName)
        }
    }

    override fun onResume() {
        super.onResume()
        changeDisallowText(mProfile.mAllowedAppsVpnAreDisallowed)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val profileUuid = arguments.getString(activity.packageName + ".profileUUID")
        mProfile = ProfileManager.get(activity, profileUuid)
        activity.title = getString(R.string.edit_profile_title, mProfile.name)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.allowed_apps, menu)

        val searchView = menu.findItem(R.id.app_search_widget).actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                mListView.setFilterText(query)
                mListView.isTextFilterEnabled = true
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                mListView.setFilterText(newText)
                mListView.isTextFilterEnabled = !TextUtils.isEmpty(newText)

                return true
            }
        })
        searchView.setOnCloseListener {
            mListView.clearTextFilter()
            mListAdapter.filter.filter("")
            false
        }

        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.allowed_vpn_apps, container, false)

        mSettingsView = inflater.inflate(R.layout.allowed_application_settings, container, false)
        mDefaultAllowTextView = mSettingsView.findViewById<View>(R.id.default_allow_text) as TextView

        val vpnOnDefaultSwitch = mSettingsView.findViewById<View>(R.id.default_allow) as Switch

        vpnOnDefaultSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            changeDisallowText(isChecked)
            mProfile.mAllowedAppsVpnAreDisallowed = isChecked
        }

        vpnOnDefaultSwitch.isChecked = mProfile.mAllowedAppsVpnAreDisallowed

        val vpnAllowBypassSwitch = mSettingsView.findViewById<View>(R.id.allow_bypass) as Switch

        vpnAllowBypassSwitch.setOnCheckedChangeListener { buttonView, isChecked -> mProfile.mAllowAppVpnBypass = isChecked }

        vpnAllowBypassSwitch.isChecked = mProfile.mAllowAppVpnBypass

        mListView = v.findViewById<View>(android.R.id.list) as ListView

        mListAdapter = PackageAdapter(activity, mProfile)
        mListView.adapter = mListAdapter
        mListView.onItemClickListener = this

        mListView.emptyView = v.findViewById(R.id.loading_container)

        Thread(Runnable { mListAdapter.populateList(activity) }).start()

        return v
    }

    private fun changeDisallowText(selectedAreDisallowed: Boolean) {
        if (selectedAreDisallowed)
            mDefaultAllowTextView.setText(R.string.vpn_disallow_radio)
        else
            mDefaultAllowTextView.setText(R.string.vpn_allow_radio)
    }

    internal class AppViewHolder {
        var mInfo: ApplicationInfo? = null
        var rootView: View? = null
        lateinit var appName: TextView
        lateinit var appIcon: ImageView
        //public TextView appSize;
        //public TextView disabled;
        lateinit var checkBox: CompoundButton

        companion object {

            fun createOrRecycle(inflater: LayoutInflater, oldview: View?, parent: ViewGroup): AppViewHolder {
                var convertView = oldview
                if (convertView == null) {
                    convertView = inflater.inflate(R.layout.allowed_application_layout, parent, false)

                    // Creates a ViewHolder and store references to the two children views
                    // we want to bind data to.
                    val holder = AppViewHolder()
                    holder.rootView = convertView
                    holder.appName = convertView.findViewById<View>(R.id.app_name) as TextView
                    holder.appIcon = convertView.findViewById<View>(R.id.app_icon) as ImageView
                    //holder.appSize = (TextView) convertView.findViewById(R.id.app_size);
                    //holder.disabled = (TextView) convertView.findViewById(R.id.app_disabled);
                    holder.checkBox = convertView.findViewById<View>(R.id.app_selected) as CompoundButton
                    convertView.tag = holder


                    return holder
                } else {
                    // Get the ViewHolder back to get fast access to the TextView
                    // and the ImageView.
                    return convertView.tag as AppViewHolder
                }
            }
        }

    }

    internal inner class PackageAdapter(c: Context, vp: VpnProfile) : BaseAdapter(), Filterable {
        private val mInflater: LayoutInflater = LayoutInflater.from(c)
        private val mPm: PackageManager = c.packageManager
        private var mPackages: Vector<ApplicationInfo> = Vector()
        private val mFilter = ItemFilter()
        private var mFilteredData: Vector<ApplicationInfo> = mPackages
        private val mProfile = vp


        fun populateList(c: Activity) {
            val installedPackages = mPm.getInstalledApplications(PackageManager.GET_META_DATA)

            // Remove apps not using Internet

            var androidSystemUid = 0
            val apps = Vector<ApplicationInfo>()

            try {
                val system = mPm.getApplicationInfo("android", PackageManager.GET_META_DATA)
                androidSystemUid = system.uid
                apps.add(system)
            } catch (e: PackageManager.NameNotFoundException) {
            }


            for (app in installedPackages) {

                if (mPm.checkPermission(Manifest.permission.INTERNET, app.packageName) == PackageManager.PERMISSION_GRANTED && app.uid != androidSystemUid) {

                    apps.add(app)
                }
            }

            Collections.sort(apps, ApplicationInfo.DisplayNameComparator(mPm))
            mPackages = apps
            mFilteredData = apps
            c.runOnUiThread { notifyDataSetChanged() }
        }

        override fun getCount(): Int {
            return mFilteredData.size + 1
        }

        override fun getItem(position: Int): Any {
            return mFilteredData[position - 1]
        }

        override fun getItemId(position: Int): Long {
            if (position == 0)
                return "settings".hashCode().toLong()
            return mFilteredData[position - 1].packageName.hashCode().toLong()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View? {
            if (position == 0) {
                return mSettingsView
            } else
                return getViewApp(position - 1, convertView, parent)

        }

        fun getViewApp(position: Int, convertView: View?, parent: ViewGroup): View? {
            val viewHolder = AppViewHolder.createOrRecycle(mInflater, convertView, parent)
            viewHolder.mInfo = mFilteredData[position]
            val mInfo = mFilteredData[position]


            var appName = mInfo.loadLabel(mPm)

            if (TextUtils.isEmpty(appName))
                appName = mInfo.packageName
            viewHolder.appName.text = appName
            viewHolder.appIcon.setImageDrawable(mInfo.loadIcon(mPm))
            viewHolder.checkBox.tag = mInfo.packageName
            viewHolder.checkBox.setOnCheckedChangeListener(this@Settings_Allowed_Apps)


            viewHolder.checkBox.isChecked = mProfile.mAllowedAppsVpn.contains(mInfo.packageName)
            return viewHolder.rootView
        }

        override fun getFilter(): Filter {
            return mFilter
        }

        override fun getViewTypeCount(): Int {
            return 2;
        }

        override fun getItemViewType(position: Int): Int {
            return if (position == 0) 0 else 1
        }

        private inner class ItemFilter : Filter() {
            override fun performFiltering(constraint: CharSequence): Filter.FilterResults {

                val filterString = constraint.toString().toLowerCase(Locale.getDefault())

                val results = Filter.FilterResults()


                val count = mPackages.size
                val nlist = Vector<ApplicationInfo>(count)

                for (i in 0 until count) {
                    val pInfo = mPackages[i]
                    var appName = pInfo.loadLabel(mPm)

                    if (TextUtils.isEmpty(appName))
                        appName = pInfo.packageName

                    if (appName is String) {
                        if (appName.toLowerCase(Locale.getDefault()).contains(filterString))
                            nlist.add(pInfo)
                    } else {
                        if (appName.toString().toLowerCase(Locale.getDefault()).contains(filterString))
                            nlist.add(pInfo)
                    }
                }
                results.values = nlist
                results.count = nlist.size

                return results
            }

            override fun publishResults(constraint: CharSequence, results: Filter.FilterResults) {
                mFilteredData = results.values as Vector<ApplicationInfo>
                notifyDataSetChanged()
            }

        }
    }
}
