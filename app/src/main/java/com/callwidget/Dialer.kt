package com.callwidget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.widget.Toast

/**
 * Звонок через системный Telecom: экран звонка открывает сама система, а не наше приложение.
 *
 * Приложение работает на магнитоле, а звонит телефон, подключённый по Bluetooth (HFP).
 * Без явного аккаунта Telecom может отдать вызов встроенному модему магнитолы (без SIM),
 * и тогда звонок никуда не уходит. Поэтому, если есть Bluetooth-аккаунт, передаём его явно.
 */
object Dialer {

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    fun hasPhoneStatePermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    /** Возвращает false, если позвонить не удалось (нет разрешения). */
    fun call(context: Context, number: String, name: String): Boolean {
        if (!hasPermission(context)) {
            DebugLog.log("call: no CALL_PHONE permission")
            return false
        }
        val telecom = context.getSystemService(TelecomManager::class.java)
        val account = bluetoothAccount(context, telecom)
        DebugLog.log(
            "call: placeCall ${DebugLog.mask(number)} account=${account?.let { describe(it) }} " +
                "(before: ${DebugLog.audioState(context)})"
        )
        return try {
            val extras = Bundle()
            if (account != null) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account)
            telecom.placeCall(Uri.fromParts("tel", number, null), extras)
            DebugLog.log("call: placeCall returned")
            Toast.makeText(context, context.getString(R.string.calling, name), Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            DebugLog.log("call: placeCall failed: $e")
            false
        }
    }

    /** Аккаунт звонков Bluetooth (HFP-клиент), если он есть и есть разрешение его увидеть. */
    private fun bluetoothAccount(context: Context, telecom: TelecomManager): PhoneAccountHandle? =
        callAccounts(context, telecom).firstOrNull { isBluetooth(it) }

    fun callAccounts(context: Context, telecom: TelecomManager): List<PhoneAccountHandle> {
        if (!hasPhoneStatePermission(context)) return emptyList()
        return try {
            telecom.callCapablePhoneAccounts
        } catch (e: SecurityException) {
            DebugLog.log("call: phone accounts unavailable: $e")
            emptyList()
        }
    }

    private fun isBluetooth(handle: PhoneAccountHandle): Boolean {
        val component = handle.componentName
        return component.packageName.contains("bluetooth", ignoreCase = true) ||
            component.className.contains("hfp", ignoreCase = true)
    }

    fun describe(handle: PhoneAccountHandle): String =
        "${handle.componentName.flattenToShortString()}#${handle.id}"
}
