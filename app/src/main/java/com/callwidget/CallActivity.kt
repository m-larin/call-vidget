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
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast

/**
 * Прозрачная activity, которую запускает кнопка виджета.
 * Проверяет Bluetooth и разрешение, затем звонит. Когда подключено Bluetooth-устройство
 * с профилем гарнитуры (HFP), система сама направляет звук звонка на него.
 * На время набора показывает заставку: на медленных устройствах экран звонка
 * появляется не сразу, и без неё непонятно, сработало ли нажатие.
 */
class CallActivity : Activity() {

    private lateinit var number: String
    private var name: String = ""
    private var callStarted = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Повторное нажатие, пока идёт набор, не должно запускать второй звонок
        if (active) return finish()
        number = intent.getStringExtra(EXTRA_NUMBER) ?: return finish()
        name = intent.getStringExtra(EXTRA_NAME) ?: number
        active = true

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
        setContentView(R.layout.activity_call)
        findViewById<TextView>(R.id.call_name).text = name
        findViewById<TextView>(R.id.call_number).text = number

        // Даём заставке отрисоваться до запуска звонка, иначе на медленном устройстве её не видно
        handler.postDelayed({
            startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null)))
            callStarted = true
            // Если экран звонка так и не перекрыл заставку (например, выбор SIM), не висим вечно
            handler.postDelayed({ finish() }, SPLASH_TIMEOUT_MS)
        }, DRAW_DELAY_MS)
    }

    override fun onStop() {
        super.onStop()
        // Экран звонка открылся и закрыл нас: заставка больше не нужна
        if (callStarted) finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::number.isInitialized) active = false
        super.onDestroy()
    }

    companion object {
        const val EXTRA_NUMBER = "com.callwidget.NUMBER"
        const val EXTRA_NAME = "com.callwidget.NAME"
        private const val REQUEST_CALL = 1
        private const val DRAW_DELAY_MS = 150L
        private const val SPLASH_TIMEOUT_MS = 15_000L

        /** Идёт ли сейчас набор (открыт экземпляр CallActivity). */
        private var active = false

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
