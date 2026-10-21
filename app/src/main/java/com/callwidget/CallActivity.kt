package com.callwidget

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast

/**
 * Прозрачная activity, которую запускает кнопка виджета.
 * Проверяет Bluetooth и разрешение, затем звонит. Когда подключено Bluetooth-устройство
 * с профилем гарнитуры (HFP), система сама направляет звук звонка на него.
 */
class CallActivity : Activity() {

    private lateinit var number: String
    private var name: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        number = intent.getStringExtra(EXTRA_NUMBER) ?: return finish()
        name = intent.getStringExtra(EXTRA_NAME) ?: number

        if (ButtonStore.requireBluetooth(this) && !isBluetoothAudioConnected(this)) {
            Toast.makeText(this, R.string.bt_not_connected, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            call()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), REQUEST_CALL)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode != REQUEST_CALL) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            call()
        } else {
            Toast.makeText(this, R.string.call_permission_denied, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun call() {
        Toast.makeText(this, getString(R.string.calling, name), Toast.LENGTH_SHORT).show()
        startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null)))
        finish()
    }

    companion object {
        const val EXTRA_NUMBER = "com.callwidget.NUMBER"
        const val EXTRA_NAME = "com.callwidget.NAME"
        private const val REQUEST_CALL = 1

        /** Подключено ли Bluetooth-устройство, способное передавать звук разговора. */
        fun isBluetoothAudioConnected(context: Context): Boolean {
            val audio = context.getSystemService(AudioManager::class.java)
            return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
            }
        }
    }
}
