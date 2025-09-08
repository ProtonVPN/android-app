package com.protonvpn.android.telemetry.settings

import com.protonvpn.android.redesign.recents.data.DefaultConnection
import com.protonvpn.android.redesign.recents.usecases.ObserveDefaultConnection
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.telemetry.CommonDimensions
import com.protonvpn.android.telemetry.toTelemetry
import com.protonvpn.android.theme.ThemeType
import com.protonvpn.android.tv.settings.IsTvAutoConnectFeatureFlagEnabled
import com.protonvpn.android.ui.settings.AppIconManager
import com.protonvpn.android.ui.settings.CustomAppIconData
import com.protonvpn.android.utils.isIPv6
import com.protonvpn.android.vpn.ConnectivityMonitor
import com.protonvpn.android.vpn.usecases.GetTruncationMustHaveIDs
import com.protonvpn.android.vpn.usecases.ServerListTruncationEnabled
import com.protonvpn.android.widget.WidgetType
import com.protonvpn.android.widget.data.WidgetTracker
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetSettingsTelemetrySnapshotDimensions @Inject constructor(
    private val appIconManager: AppIconManager,
    private val connectivityMonitor: ConnectivityMonitor,
    private val commonDimensions: CommonDimensions,
    private val effectiveCurrentUserSettings: EffectiveCurrentUserSettings,
    private val getTruncationMustHaveIDs: GetTruncationMustHaveIDs,
    private val isServerListTruncationEnabled: ServerListTruncationEnabled,
    private val widgetTracker: WidgetTracker,
    private val isTvAutoConnectFeatureFlagEnabled: IsTvAutoConnectFeatureFlagEnabled,
    private val observeDefaultConnection: ObserveDefaultConnection,
) {

    suspend operator fun invoke(): Map<String, String> = buildMap {
        put(
            key = DIMENSION_APP_ICON,
            value = appIconManager.getCurrentIconData().getTelemetryName()
        )

        put(
            key = DIMENSION_DEFAULT_CONNECTION_TYPE,
            value = observeDefaultConnection().first().getTelemetryName()
        )

        put(
            key = DIMENSION_IS_SYSTEM_CUSTOM_DNS_ENABLED,
            value = connectivityMonitor.isPrivateDnsActive
                .first()
                ?.toTelemetry()
                ?: CommonDimensions.NO_VALUE
        )

        put(
            key = DIMENSION_WIDGET_COUNT,
            value = widgetTracker.widgetCount
                .filterNotNull()
                .first()
                .toWidgetCountBucketString()
        )

        widgetTracker.firstWidgetType()?.let { widgetType ->
            put(
                key = DIMENSION_FIRST_WIDGET_SIZE,
                value = widgetType.getTelemetrySizeName(),
            )

            put(
                key = DIMENSION_FIRST_WIDGET_THEME,
                value = widgetType.getTelemetryThemeName(),
            )
        }

        val settings = effectiveCurrentUserSettings.effectiveSettings.first()

        put(
            key = DIMENSION_IS_CUSTOM_DNS_ENABLED,
            value = settings.customDns.effectiveEnabled.toTelemetry()
        )

        put(
            key = DIMENSION_IS_IPV6_ENABLED,
            value = settings.ipV6Enabled.toTelemetry()
        )

        put(
            key = DIMENSION_UI_THEME,
            value = settings.theme.toTelemetry()
        )

        put(
            key = DIMENSION_LAN_MODE,
            value = lanModeToTelemetry(
                lanConnections = settings.lanConnections,
                lanConnectionsAllowDirect = settings.lanConnectionsAllowDirect,
            )
        )

        val customDnsList = settings.customDns.rawDnsList

        put(
            key = DIMENSION_CUSTOM_DNS_COUNT,
            value = customDnsList.size.toCustomDnsCountBucketString()
        )

        put(
            key = DIMENSION_FIRST_CUSTOM_DNS_ADDRESS_FAMILY,
            value = customDnsList.firstOrNull()
                ?.toTelemetryAddressFamily()
                ?: CommonDimensions.NO_VALUE
        )

        if (isServerListTruncationEnabled()) {
            put(
                key = DIMENSION_SERVER_LIST_TRUNCATION_PROTECTED_IDS_COUNT,
                value = getTruncationMustHaveIDs(
                    maxRecents = Int.MAX_VALUE,
                    maxMustHaves = Int.MAX_VALUE
                )
                    .size
                    .toTruncationMustHaveSizeBucketString()
            )
        }

        if (isTvAutoConnectFeatureFlagEnabled()) {
            put(
                key = DIMENSION_AUTO_CONNECT_ENABLED,
                value = settings.tvAutoConnectOnBoot.toTelemetry()
            )
        }

        commonDimensions.add(this, CommonDimensions.Key.USER_TIER)
    }

    private fun CustomAppIconData.getTelemetryName() = when (this) {
        CustomAppIconData.DEFAULT -> "default"
        CustomAppIconData.DARK -> "dark"
        CustomAppIconData.RETRO -> "retro"
        CustomAppIconData.WEATHER -> "weather"
        CustomAppIconData.NOTES -> "notes"
        CustomAppIconData.CALCULATOR -> "calculator"
    }

    private fun DefaultConnection.getTelemetryName() = when (this) {
        is DefaultConnection.FastestConnection -> "fastest"
        is DefaultConnection.LastConnection -> "last_connection"
        is DefaultConnection.Recent -> "recent"
    }

    private fun WidgetType.getTelemetrySizeName() = if (isSmall) "small" else "large"

    private fun WidgetType.getTelemetryThemeName() = if (isMaterial) "material" else "proton"

    private fun lanModeToTelemetry(lanConnections: Boolean, lanConnectionsAllowDirect: Boolean) = when {
        !lanConnections -> "off"
        !lanConnectionsAllowDirect -> "standard"
        else -> "direct"
    }

    private fun ThemeType.toTelemetry() = when (this) {
        ThemeType.System -> "system"
        ThemeType.Light -> "light"
        ThemeType.Dark -> "dark"
    }

    private fun Int.toCustomDnsCountBucketString() = when {
        this == 0 -> "0"
        this == 1 -> "1"
        this <= 4 -> "2-4"
        else -> ">=5"
    }

    private fun String.toTelemetryAddressFamily() = if (isIPv6()) "ipv6" else "ipv4"

    private fun Int.toTruncationMustHaveSizeBucketString() = when {
        this == 0 -> "0"
        this == 1 -> "1"
        this <= 10 -> "2-10"
        this <= 50 -> "11-50"
        else -> ">=51"
    }

    private fun Int.toWidgetCountBucketString() = when {
        this == 0 -> "0"
        this == 1 -> "1"
        this <= 4 -> "2-4"
        else -> ">=5"
    }

    private companion object {
        private const val DIMENSION_APP_ICON = "app_icon"
        private const val DIMENSION_AUTO_CONNECT_ENABLED = "is_auto_connect_enabled"
        private const val DIMENSION_DEFAULT_CONNECTION_TYPE = "default_connection_type"
        private const val DIMENSION_WIDGET_COUNT = "widget_count"
        private const val DIMENSION_FIRST_WIDGET_SIZE = "first_widget_size"
        private const val DIMENSION_FIRST_WIDGET_THEME = "first_widget_theme"
        private const val DIMENSION_IS_SYSTEM_CUSTOM_DNS_ENABLED = "is_system_custom_dns_enabled"
        private const val DIMENSION_IS_IPV6_ENABLED = "is_ipv6_enabled"
        private const val DIMENSION_IS_CUSTOM_DNS_ENABLED = "is_custom_dns_enabled"
        private const val DIMENSION_CUSTOM_DNS_COUNT = "custom_dns_count"
        private const val DIMENSION_UI_THEME = "ui_theme"
        private const val DIMENSION_FIRST_CUSTOM_DNS_ADDRESS_FAMILY = "first_custom_dns_address_family"
        private const val DIMENSION_LAN_MODE = "lan_mode"
        private const val DIMENSION_SERVER_LIST_TRUNCATION_PROTECTED_IDS_COUNT = "server_list_truncation_protected_ids_count"
    }

}
