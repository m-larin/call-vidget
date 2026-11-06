package com.callwidget

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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

    /**
     * Приложение Bluetooth-телефона магнитолы (на FYT/SYU это `com.syu.bt/.PhoneActivity`), если в Telecom
     * нет HFP-аккаунта. Такие магнитолы звонят через телефон только своим приложением, а вызов через Telecom
     * уходит в модем магнитолы (и срабатывает разве что со второго нажатия).
     */
    fun bluetoothDialer(context: Context): ComponentName? {
        val telecom = context.getSystemService(TelecomManager::class.java)
        if (bluetoothAccount(context, telecom) != null) return null
        return runCatching {
            context.packageManager
                .queryIntentActivities(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", "0", null)), 0)
                .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
                .firstOrNull { c ->
                    c.packageName !in SYSTEM_CALL_PACKAGES && c.packageName != context.packageName &&
                        BT_KEYWORDS.any { c.packageName.contains(it, ignoreCase = true) }
                }
        }.getOrNull()
    }

    /** Intent звонка прямо в приложение Bluetooth-телефона. */
    fun bluetoothDialerIntent(component: ComponentName, number: String): Intent =
        Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null))
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Возвращает false, если позвонить не удалось (нет разрешения). */
    fun call(context: Context, number: String, name: String): Boolean {
        if (!hasPermission(context)) {
            DebugLog.log("call: no CALL_PHONE permission")
            return false
        }
        bluetoothDialer(context)?.let { component ->
            DebugLog.log("call: ${DebugLog.mask(number)} via ${component.flattenToShortString()}")
            try {
                context.startActivity(bluetoothDialerIntent(component, number))
                return true
            } catch (e: Exception) {
                DebugLog.log("call: bt dialer failed: $e, falling back to placeCall")
            }
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

    private val SYSTEM_CALL_PACKAGES = setOf("com.android.server.telecom", "com.android.phone")
    private val BT_KEYWORDS = listOf("bt", "bluetooth", "hfp")
}
