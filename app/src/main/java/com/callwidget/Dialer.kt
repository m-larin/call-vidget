package com.callwidget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.TelecomManager
import android.widget.Toast

/** Звонок через системный Telecom: экран звонка открывает сама система, а не наше приложение. */
object Dialer {

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    /** Возвращает false, если позвонить не удалось (нет разрешения). */
    fun call(context: Context, number: String, name: String): Boolean {
        if (!hasPermission(context)) {
            DebugLog.log("call: no CALL_PHONE permission")
            return false
        }
        DebugLog.log("call: placeCall ${DebugLog.mask(number)} (before: ${DebugLog.audioState(context)})")
        return try {
            context.getSystemService(TelecomManager::class.java)
                .placeCall(Uri.fromParts("tel", number, null), null)
            DebugLog.log("call: placeCall returned")
            Toast.makeText(context, context.getString(R.string.calling, name), Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            DebugLog.log("call: placeCall failed: $e")
            false
        }
    }
}
