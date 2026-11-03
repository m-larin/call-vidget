package com.callwidget

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Диагностика нажатий: события пишутся только в системный журнал (logcat, тег [TAG]),
 * а меню главного экрана выгружает logcat в файл и отправляет его.
 * Без READ_LOGS приложение видит в logcat только свои строки; с ним (выдаётся через adb) весь журнал.
 */
object DebugLog {
    const val TAG = "CallWidget"
    private const val FILE_NAME = "logcat.txt"

    fun log(event: String) {
        Log.i(TAG, event)
    }

    /** Через сколько мс после старта процесса пришло событие: маленькое значение = холодный старт. */
    fun processAge(): Long = SystemClock.uptimeMillis() - Process.getStartUptimeMillis()

    /** Номер без первых цифр, чтобы в журнале не было полных номеров. */
    fun mask(number: String): String =
        if (number.length <= 4) number else "*".repeat(number.length - 4) + number.takeLast(4)

    fun audioState(context: Context): String {
        val audio = context.getSystemService(AudioManager::class.java)
        val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).joinToString(",") { deviceType(it.type) }
        return "audioMode=${audioMode(audio.mode)} outputs=[$outputs]"
    }

    /**
     * Выгружает logcat в файл и возвращает Intent «Поделиться» с ним.
     * Выполнять не в главном потоке: с READ_LOGS журнал может быть большим.
     */
    fun dumpAndShare(context: Context): Intent {
        val dir = File(context.cacheDir, "logs").apply { mkdirs() }
        val file = File(dir, FILE_NAME)
        val telecom = context.getSystemService(TelecomManager::class.java)
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        val readLogs = context.checkSelfPermission(android.Manifest.permission.READ_LOGS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        file.bufferedWriter().use { out ->
            out.appendLine("app=$version device=${Build.MANUFACTURER} ${Build.MODEL} android=${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            out.appendLine("defaultDialer=${telecom.defaultDialerPackage} callPermission=${Dialer.hasPermission(context)} readLogs=$readLogs")
            out.appendLine("----")
            val logcat = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-b", "main,system,crash")
                .redirectErrorStream(true)
                .start()
            logcat.inputStream.bufferedReader().use { it.copyTo(out) }
            logcat.waitFor()
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.logs", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, FILE_NAME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, null)
    }

    private fun audioMode(mode: Int) = when (mode) {
        AudioManager.MODE_NORMAL -> "NORMAL"
        AudioManager.MODE_RINGTONE -> "RINGTONE"
        AudioManager.MODE_IN_CALL -> "IN_CALL"
        AudioManager.MODE_IN_COMMUNICATION -> "IN_COMMUNICATION"
        else -> mode.toString()
    }

    private fun deviceType(type: Int) = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "earpiece"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bt_sco"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bt_a2dp"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "usb"
        else -> "type$type"
    }
}
