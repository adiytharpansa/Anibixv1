package com.anibix.app.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Rational
import androidx.media3.common.Format
import androidx.media3.common.Player
import java.util.Locale
import kotlin.math.roundToInt

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Nama trek: label berkas → nama bahasa → "Trek n". */
internal fun trackLabel(format: Format, index: Int): String {
    val label = format.label
    if (!label.isNullOrBlank()) return label
    val lang = format.language
    if (!lang.isNullOrBlank() && lang != "und") {
        val name = Locale.forLanguageTag(lang).getDisplayLanguage(Locale.getDefault())
        if (name.isNotBlank() && !name.equals(lang, ignoreCase = true)) {
            return name.replaceFirstChar { it.uppercase() }
        }
        return lang
    }
    return "Trek ${index + 1}"
}

internal fun channelSuffix(format: Format): String = when (format.channelCount) {
    6 -> " · 5.1"
    8 -> " · 7.1"
    else -> ""
}

/** Masuk Picture-in-Picture dengan rasio sesuai video (dijepit ke batas Android). */
internal fun enterPictureInPicture(activity: Activity?, player: Player) {
    if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    runCatching {
        val size = player.videoSize
        val ratio = if (size.width > 0 && size.height > 0) {
            val r = size.width.toFloat() / size.height
            if (r in 0.42f..2.39f) Rational(size.width, size.height) else Rational(16, 9)
        } else Rational(16, 9)
        activity.enterPictureInPictureMode(
            PictureInPictureParams.Builder().setAspectRatio(ratio).build()
        )
    }
}

internal fun currentBrightness(activity: Activity?): Float {
    val explicit = activity?.window?.attributes?.screenBrightness ?: -1f
    if (explicit >= 0f) return explicit
    val resolver = activity?.contentResolver ?: return 0.5f
    return runCatching {
        Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    }.getOrDefault(0.5f)
}

internal fun applyBrightness(activity: Activity?, value: Float) {
    val window = activity?.window ?: return
    val attrs = window.attributes
    attrs.screenBrightness = value
    window.attributes = attrs
}

internal fun volumeFraction(am: AudioManager): Float {
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    return if (max > 0) am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max else 0f
}

internal fun applyVolume(am: AudioManager, fraction: Float) {
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    am.setStreamVolume(AudioManager.STREAM_MUSIC, (fraction * max).roundToInt().coerceIn(0, max), 0)
}
