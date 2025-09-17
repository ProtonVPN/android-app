/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.redesign.base.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path.Direction
import android.graphics.Picture
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.SizeF
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.record
import androidx.core.graphics.withSave
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.createHueRotationMatrix
import com.protonvpn.android.base.ui.rgbToHueInRadians
import com.protonvpn.android.base.ui.vpnGreen
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.profiles.ui.toColor
import com.protonvpn.android.profiles.ui.toDrawableRes
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.utils.CountryTools
import me.proton.core.compose.theme.ProtonTheme
import kotlin.math.max
import kotlin.math.roundToInt
import me.proton.core.country.presentation.R as CoreR

private object Dimensions {
    val flagAreaSize = SizeF(30f, 30f)
    val singleFlagSize = SizeF(30f, 20f)
    val twoFlagTop = 3.dp
    val twoFlagMainSizeSmall = SizeF(16f, 10.5f)
    val twoFlagMainSize = SizeF(24f, 16f)
    val companionFlagSizeSmall = SizeF(13f, 9f)
    val companionFlagSize = SizeF(18f, 12f)
    val profileCompanionFlagSize = SizeF(20f, 13.33f)
    val bigProfileIconSize = SizeF(36f, 24f)
    const val companionFlagTop = 15f
    val scUnderlineArcSize = SizeF(26f, 16f)
    val scUnderlineArcOffset = PointF(-4f, 4f + (singleFlagSize.height - scUnderlineArcSize.height))
    const val scUnderlineArcRadius = 6f
    val shadowColor = Color(0x66000000)
    const val smallCorner = 2.5f
    const val regularCorner = 4f
}

object FlagDimensions {
    val singleFlagSize = Dimensions.singleFlagSize.toDpSize()
    val regularShape = RoundedCornerShape(Dimensions.regularCorner.dp)
}

enum class ConnectIntentIconSize(
    val size: Float,
    val backFlagSize: SizeF,
    val frontFlagSize: SizeF,
    val profileVerticalPadding: Float
) {
    SMALL(20f, Dimensions.twoFlagMainSizeSmall, Dimensions.companionFlagSizeSmall, 2f),
    MEDIUM(30f, Dimensions.twoFlagMainSize, Dimensions.profileCompanionFlagSize, 2f),
    LARGE(48f, Dimensions.bigProfileIconSize, Dimensions.singleFlagSize, 4f)
}

sealed interface ConnectIntentIconState {
    data class Fastest(@DrawableRes val flagFastest: Int, @DrawableRes val connectedCountry: Int?, val isSecureCore: Boolean) : ConnectIntentIconState
    data class Country(@DrawableRes val exitCountry: Int, @DrawableRes val entryCountry: Int?) : ConnectIntentIconState

    data class Gateway(@DrawableRes val country: Int?) : ConnectIntentIconState
    data class Profile(@DrawableRes val country: Int, val isGateway: Boolean, val icon: ProfileIcon, val color: ProfileColor) : ConnectIntentIconState
}

fun ConnectIntentPrimaryLabel.toIconState(flagProvider: CountryId.() -> Int): ConnectIntentIconState {
    return when (this) {
        is ConnectIntentPrimaryLabel.Fastest ->
            ConnectIntentIconState.Fastest(
                CountryId.fastest.flagProvider(),
                connectedCountry?.flagProvider(),
                isSecureCore
            )
        is ConnectIntentPrimaryLabel.Country ->
            ConnectIntentIconState.Country(exitCountry.flagProvider(), entryCountry?.flagProvider())
        is ConnectIntentPrimaryLabel.Gateway ->
            ConnectIntentIconState.Gateway(country?.flagProvider())
        is ConnectIntentPrimaryLabel.Profile ->
            ConnectIntentIconState.Profile(country.flagProvider(), isGateway, icon, color)
    }
}

private data class ConnectIntentIconDrawScope(
    private val context: Context,
    val canvas: Canvas,
    val isRtl: Boolean,
    val drawingSize: SizeF,
    val secureCoreArcColorFastest: Color?,
    val secureCoreArcColorRegular: Color?,
) {
    private val paint: Paint = Paint()

    fun getDrawable(@DrawableRes id: Int): Drawable = requireNotNull(
        AppCompatResources.getDrawable(context, id)
    ).mutate()

    fun getPaint() = paint.apply { reset() }

    // Translates the canvas and adjusts the `drawingSize` to the cropped region.
    fun withCropped(dx: Float, dy: Float, size: SizeF? = null, block: ConnectIntentIconDrawScope.() -> Unit) {
        val transformedScope =
            this.copy(drawingSize = size ?: SizeF(drawingSize.width - dx, drawingSize.height - dy))
        canvas.withTranslation(dx, dy) {
            transformedScope.block()
        }
    }

    fun withCentered(size: SizeF, block: ConnectIntentIconDrawScope.() -> Unit) {
        val dx = (drawingSize.width - size.width) / 2
        val dy = (drawingSize.height - size.height) / 2
        withCropped(dx, dy, size, block)
    }
}

private fun DrawScope.drawWithNativeCanvas(
    context: Context,
    secureCoreArcColorFastest: Color,
    secureCoreArcColorRegular: Color,
    block: ConnectIntentIconDrawScope.() -> Unit
) {
    val connectIntentIconDrawScope =
        ConnectIntentIconDrawScope(
            context = context,
            canvas = drawContext.canvas.nativeCanvas,
            isRtl = layoutDirection == LayoutDirection.Rtl,
            drawingSize = drawContext.size.div(density).toSizeF(),
            secureCoreArcColorFastest = secureCoreArcColorFastest,
            secureCoreArcColorRegular = secureCoreArcColorRegular
        )
    with (connectIntentIconDrawScope) {
        canvas.withScale(x = density, y = density) {
            block()
        }
    }
}

@Composable
fun ConnectIntentIcon(label: ConnectIntentPrimaryLabel, modifier: Modifier = Modifier) {
    ConnectIntentIcon(label.toIconState(), modifier)
}

@Composable
private fun ConnectIntentIcon(
    iconState: ConnectIntentIconState,
    modifier: Modifier = Modifier,
) {
    val connectIntentIconSize = ConnectIntentIconSize.MEDIUM
    ConnectIntentIconDrawing(modifier.size(connectIntentIconSize.size.dp)) {
        connectIntentIcon(iconState, connectIntentIconSize)
    }
}

@Composable
fun ProfileConnectIntentIcon(
    label: ConnectIntentPrimaryLabel.Profile,
    profileConnectIntentIconSize: ConnectIntentIconSize,
    modifier: Modifier = Modifier,
) {
    val iconState = with(label) {
        ConnectIntentIconState.Profile(country.flagResource(LocalContext.current), isGateway, icon, color)
    }
    ProfileConnectIntentIcon(iconState, modifier, profileConnectIntentIconSize)
}

@Composable
private fun ProfileConnectIntentIcon(
    iconState: ConnectIntentIconState.Profile,
    modifier: Modifier = Modifier,
    profileConnectIntentIconSize: ConnectIntentIconSize = ConnectIntentIconSize.MEDIUM,
) {
    val size = profileConnectIntentIconSize.size
    ConnectIntentIconDrawing(modifier.size(size.dp)) {
        with (iconState) { profile(country, icon, color, profileConnectIntentIconSize, isGateway) }
    }
}

@Composable
fun Flag(
    exitCountry: CountryId,
    entryCountry: CountryId? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mainFlag = exitCountry.flagResource(context)
    val secondaryFlag =
        if (entryCountry == null || entryCountry.isFastest) null
        else entryCountry.flagResource(context)
    ConnectIntentIconDrawing(
        modifier.size(Dimensions.flagAreaSize.toDpSize())
    ) {
        flag(mainFlag, secondaryFlag, drawSecureCoreArc = entryCountry != null, isFastest = exitCountry.isFastest)
    }
}

@Composable
fun GatewayIndicator(
    country: CountryId?,
    modifier: Modifier = Modifier
) {
    GatewayIndicator(countryFlag = country?.flagResource(LocalContext.current), modifier)
}

@Composable
private fun GatewayIndicator(
    countryFlag: Int?,
    modifier: Modifier = Modifier
) {
    ConnectIntentIconDrawing(modifier.size(Dimensions.singleFlagSize.toDpSize())) {
        gatewayIndicator(countryFlag)
    }
}

// Hue in radians as used in profile icon drawables
const val profileIconOrgHue = 4.6562896f

@Composable
fun ProfileIcon(
    modifier: Modifier = Modifier,
    icon: ProfileIcon,
    color: ProfileColor,
    connectIntentIconSize: ConnectIntentIconSize,
    addContentDescription: Boolean = false,
) {
    val semantics = if (addContentDescription) {
        val description = stringResource(id = R.string.profile_icon_accessibility, icon.ordinal + 1)
        Modifier.semantics { contentDescription = description }
    } else {
        null
    }
    ConnectIntentIconDrawing(
        modifier
            .size(connectIntentIconSize.frontFlagSize.toDpSize())
            .thenNotNull(semantics)
    ) {
        profileIcon(icon, color, connectIntentIconSize)
    }
}

private fun ConnectIntentIconDrawScope.connectIntentIcon(
    state: ConnectIntentIconState,
    profileConnectIntentIconSize: ConnectIntentIconSize = ConnectIntentIconSize.MEDIUM,
) {
    when (state) {
        is ConnectIntentIconState.Fastest -> with(state) {
            flag(flagFastest, connectedCountry, drawSecureCoreArc = isSecureCore, isFastest = true)
        }
        is ConnectIntentIconState.Country -> with(state) {
            flag(exitCountry, entryCountry, drawSecureCoreArc = false, isFastest = false)
        }
        is ConnectIntentIconState.Gateway ->
            gatewayIndicator(state.country)
        is ConnectIntentIconState.Profile -> with(state) {
            profile(country, icon, color, profileConnectIntentIconSize, isGateway)
        }
    }
}

private fun ConnectIntentIconDrawScope.profileIcon(
    icon: ProfileIcon,
    color: ProfileColor,
    connectIntentIconSize: ConnectIntentIconSize,
) {
    val newHue = color.toColor().let { rgbToHueInRadians(it.red, it.green, it.blue) }
    val hueDelta = newHue - profileIconOrgHue
    val hueRotationMatrix =  createHueRotationMatrix(hueDelta)
    val colorFilter = ColorMatrixColorFilter(ColorMatrix(hueRotationMatrix))

    withCentered(connectIntentIconSize.backFlagSize) {
        drawFillImage(icon.toDrawableRes(), connectIntentIconSize.backFlagSize, colorFilter = colorFilter)
    }
}

private fun ConnectIntentIconDrawScope.profile(
    @DrawableRes countryFlag: Int,
    icon: ProfileIcon,
    color: ProfileColor,
    connectIntentIconSize: ConnectIntentIconSize,
    isGateway: Boolean,
) {
    withCropped(
        if (isRtl) (connectIntentIconSize.size - connectIntentIconSize.backFlagSize.width) else 0f,
        connectIntentIconSize.profileVerticalPadding,
        size = connectIntentIconSize.backFlagSize
    ) {
        profileIcon(icon, color, connectIntentIconSize)
    }

    withCropped(
        if (isRtl) 0f else with (connectIntentIconSize) { size - frontFlagSize.width },
        with (connectIntentIconSize) { size - frontFlagSize.height - profileVerticalPadding },
        connectIntentIconSize.frontFlagSize
    ) {
        val scaleFactor = drawingSize.width / Dimensions.singleFlagSize.width
        canvas.scale(scaleFactor, scaleFactor) // Restored by withCropped block.

        val image = if (isGateway) R.drawable.ic_gateway_flag else countryFlag
        drawFillImage(image, Dimensions.singleFlagSize, if (isGateway) 0f else Dimensions.regularCorner)
    }
}

private fun ConnectIntentIconDrawScope.gatewayIndicator(countryFlag: Int?) {
    if (countryFlag == null) {
        drawFillImage(R.drawable.ic_gateway_flag, Dimensions.singleFlagSize)
    } else {
        twoFlagsSmallOnTop(R.drawable.ic_gateway_flag, countryFlag,isRounded = false, drawSecureCoreArc = false)
    }
}

private fun ConnectIntentIconDrawScope.flag(
    @DrawableRes mainFlag: Int,
    secondaryFlag: Int?,
    drawSecureCoreArc: Boolean,
    isFastest: Boolean,
) {
    when {
        secondaryFlag != null && !isFastest ->
            twoFlagsLargeOnTop(largeImage = mainFlag, smallImage = secondaryFlag)
        secondaryFlag != null && isFastest ->
            twoFlagsSmallOnTop(largeImage = mainFlag, smallImage = secondaryFlag, isRounded = true, drawSecureCoreArc = drawSecureCoreArc)
        else ->
            singleFlag(mainFlag, drawSecureCoreArc, isFastest)
    }
}

@Composable
private fun secureCoreArcColor(isFastest: Boolean) =
    if (isFastest) ProtonTheme.colors.vpnGreen.copy(alpha = 0.3f)
    else ProtonTheme.colors.textHint

@Composable
private fun ConnectIntentIconDrawing(
    modifier: Modifier = Modifier,
    block: ConnectIntentIconDrawScope.() -> Unit
) {
    val scColorRegular = secureCoreArcColor(false)
    val scColorFastest = secureCoreArcColor(true)
    val context = LocalContext.current
    val drawModifier = Modifier.drawBehind {
        drawWithNativeCanvas(context, scColorFastest, scColorRegular, block)
    }
    Spacer(modifier.then(drawModifier))
}

fun paintConnectIntentIcon(
    context: Context,
    isRtl: Boolean,
    density: Float,
    connectIntentIconState: ConnectIntentIconState,
): Bitmap {
    val connectIntentIconSize = ConnectIntentIconSize.MEDIUM // MEDIUM is the only size supported by non-profile icons.
    val size = connectIntentIconSize.size
    val sizePx = (size * density).roundToInt()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)

    val canvas = Canvas(bitmap)
    ConnectIntentIconDrawScope(context, canvas, isRtl, SizeF(size, size), null, null).apply {
        canvas.withScale(density, density) {
            connectIntentIcon(connectIntentIconState, connectIntentIconSize)
        }
    }
    return bitmap
}

private fun ConnectIntentIconDrawScope.singleFlag(
    @DrawableRes flagImage: Int,
    drawSecureCoreArc: Boolean,
    isFastest: Boolean,
) {
    withCentered(Dimensions.singleFlagSize) {
        val arcColor = if (isFastest) secureCoreArcColorFastest else secureCoreArcColorRegular
        if (drawSecureCoreArc && arcColor != null) {
            drawScUnderlineArc(arcColor)
        }
        drawFillImage(flagImage, Dimensions.singleFlagSize, Dimensions.regularCorner)
    }
}

private fun ConnectIntentIconDrawScope.twoFlagsLargeOnTop(
    @DrawableRes largeImage: Int,
    @DrawableRes smallImage: Int,
) {
    val companionFlagSize =  Dimensions.companionFlagSize
    withCropped(
        if (isRtl) Dimensions.flagAreaSize.width - companionFlagSize.width else 0f,
        15f,
        companionFlagSize
    ) {
        drawFillImage(smallImage, companionFlagSize, Dimensions.smallCorner)
        drawScFlagShadow(4f, -6f, companionFlagSize, bottomRadius = 6f)
    }

    canvas.withTranslation(if (isRtl) 0f else 6f, Dimensions.twoFlagTop.value) {
        drawFillImage(largeImage, Dimensions.twoFlagMainSize, Dimensions.regularCorner)
    }
}

private fun ConnectIntentIconDrawScope.twoFlagsSmallOnTop(
    @DrawableRes largeImage: Int,
    @DrawableRes smallImage: Int,
    isRounded: Boolean,
    drawSecureCoreArc: Boolean,
) {
    val largeFlagSize = Dimensions.twoFlagMainSize
    withCropped(
        dx = if (isRtl) drawingSize.width - largeFlagSize.width else 0f,
        dy = Dimensions.twoFlagTop.value,
        size = largeFlagSize
    ) {
        if (drawSecureCoreArc && secureCoreArcColorFastest != null) {
            with(Dimensions) {
                val flagScale = twoFlagMainSize.width / singleFlagSize.width
                canvas.withScale(flagScale, flagScale,  pivotX = if (isRtl) drawingSize.width else 0f,  pivotY = 0f) {
                    drawScUnderlineArc(secureCoreArcColorFastest)
                }
            }
        }

        drawFillImage(largeImage, largeFlagSize, if (isRounded) Dimensions.regularCorner else 0f)
        drawScFlagShadow(10f, 10f, largeFlagSize, topRadius = 4f)
    }

    canvas.withTranslation(
        x = if (isRtl) 0f else 12f,
        y = Dimensions.companionFlagTop,
    ) {
        drawFillImage(smallImage, Dimensions.companionFlagSize, Dimensions.smallCorner)
    }
}

private fun createClipPath(size: SizeF, radius: Float) = android.graphics.Path().apply {
    addRoundRect(0f, 0f, size.width, size.height, radius, radius, Direction.CW)
}

private fun ConnectIntentIconDrawScope.drawScFlagShadow(
    offsetLeft: Float,
    offsetTop: Float,
    size: SizeF,
    topRadius: Float = 0f,
    bottomRadius: Float = 0f
) {
    canvas.withSave {
        clipRect(RectF(0f, 0f, size.width, size.height))
        if (isRtl) canvas.scale(-1f, 1f, drawingSize.width / 2, 0f)
        val paint = getPaint().apply {
            color = Dimensions.shadowColor.toArgb()
            style = Paint.Style.FILL
        }
        val shadowPath = android.graphics.Path().apply {
            val radii =
                floatArrayOf(topRadius, topRadius, topRadius, topRadius, bottomRadius, bottomRadius, bottomRadius, bottomRadius)
            addRoundRect(offsetLeft, offsetTop, size.width + offsetLeft, size.height + offsetTop, radii, Direction.CW)
        }
        canvas.drawPath(shadowPath, paint)
    }
}

private fun ConnectIntentIconDrawScope.drawScUnderlineArc(color: Color) {
    val size = Dimensions.scUnderlineArcSize
    val radius = Dimensions.scUnderlineArcRadius

    val path = android.graphics.Path().apply {
        rLineTo(0f, size.height - radius)
        val arcY = size.height - 2 * radius
        addArc(
            RectF(0f, arcY, 2 * radius, arcY + 2 * radius),
            180f,
            -90f
        )
        rLineTo(size.width - radius, 0f)
    }
    val paint = getPaint().apply {
        this.style = Paint.Style.STROKE
        this.color = color.toArgb()
        this.strokeCap = Paint.Cap.ROUND
        this.strokeWidth = 1.85f
    }
    canvas.withSave {
        if (isRtl) scale(-1f, 1f, drawingSize.width / 2, 0f)
        with (Dimensions.scUnderlineArcOffset) { translate(x, y) }
        drawPath(path, paint)
    }
}

private fun ConnectIntentIconDrawScope.drawFillImage(
    @DrawableRes imageRes: Int,
    dstSize: SizeF,
    radius: Float = 0f,
    colorFilter: ColorFilter? = null
) {
    val drawable = getDrawable(imageRes)
    // Drawable.setBounds only accepts int values, use an intermediate Picture to scale and position with float
    // precision.
    val p = Picture()
    val srcWidth = drawable.intrinsicWidth
    val srcHeight = drawable.intrinsicHeight
    p.record(srcWidth, srcHeight) {
        with (drawable) {
            setBounds(0, 0, intrinsicWidth, intrinsicHeight)
            if (colorFilter != null) this.colorFilter = colorFilter
        }

        drawable.draw(this)
    }

    val fillScale = max(dstSize.width / srcWidth, dstSize.height / srcHeight)
    val pictureRect = RectF(0f, 0f, srcWidth * fillScale, srcHeight * fillScale)
    with (pictureRect)  { offset((dstSize.width - width()) / 2f, (dstSize.height - height()) / 2f) }
    canvas.withSave {
        if (radius > 0f) clipPath(createClipPath(dstSize, radius))
        drawPicture(p, pictureRect)
    }
}

@DrawableRes
private fun CountryId.flagResource(context: Context): Int =
    if (isFastest) {
        R.drawable.flag_fastest
    } else {
        // CountryTools is not available in Android Studio previews.
        // Normally LocalInspectionMode should be used to avoid calling it but its also enabled for screenshot
        // tests. Flag resources are needed in screenshot tests.
        // Fall back to fastest flag, previews crash trying to load resource with ID 0.
        CountryTools.getFlagResource(context, countryCode)
            .takeIf { it > 0 }
            ?: R.drawable.flag_fastest
    }

@Composable
private fun ConnectIntentPrimaryLabel.toIconState(): ConnectIntentIconState {
    val context = LocalContext.current
    return toIconState { this.flagResource(context) }
}

private fun SizeF.toDpSize() = DpSize(width.dp, height.dp)
private fun Size.toSizeF() = SizeF(width, height)

private val previewIcons = listOf(
        ConnectIntentIconState.Fastest(R.drawable.flag_fastest, null, false),
        ConnectIntentIconState.Fastest(R.drawable.flag_fastest, CoreR.drawable.flag_se, false),
        ConnectIntentIconState.Fastest(R.drawable.flag_fastest, CoreR.drawable.flag_lt, true),
        ConnectIntentIconState.Country(CoreR.drawable.flag_ch, null),
        ConnectIntentIconState.Country(CoreR.drawable.flag_pl, CoreR.drawable.flag_ch),
        ConnectIntentIconState.Gateway(null),
        ConnectIntentIconState.Gateway(CoreR.drawable.flag_ch),
        ConnectIntentIconState.Profile(R.drawable.flag_fastest, false, ProfileIcon.Icon1, ProfileColor.Color1),
        ConnectIntentIconState.Profile(R.drawable.flag_fastest, true, ProfileIcon.Icon5, ProfileColor.Color1),
        ConnectIntentIconState.Profile(CoreR.drawable.flag_lt, true, ProfileIcon.Icon5, ProfileColor.Color3),
        ConnectIntentIconState.Profile(CoreR.drawable.flag_ao, false, ProfileIcon.Icon7, ProfileColor.Color6),
    )

@ProtonVpnPreview
@Composable
private fun ProfileIconViewPreview() {
    ProtonVpnPreview {
        ProfileIconsPreviewHelper()
    }
}

@ProtonVpnPreview
@Preview(name = "RTL", locale = "fa")
@Composable
private fun FlagPreview() {
    ProtonVpnPreview {
        FlagsPreviewHelper()
    }
}

@Composable
private fun FlagsPreviewHelper() {
    Column {
        val modifier = Modifier.padding(8.dp)
        previewIcons.forEach { icon ->
            ConnectIntentIcon(icon, modifier)
        }
    }
}

@Composable
private fun ProfileIconsPreviewHelper() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ConnectIntentIconSize.entries.forEach { size ->
            previewIcons
                .filterIsInstance<ConnectIntentIconState.Profile>()
                .forEach { profileIcon ->
                    ProfileConnectIntentIcon(profileIcon, profileConnectIntentIconSize = size)
                }
            Spacer(Modifier.height(8.dp))
            ProfileIcon(
                icon = ProfileIcon.Icon2,
                color = ProfileColor.Color4,
                connectIntentIconSize = size
            )
        }
    }
}
