package com.callwidget

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast

/**
 * Прозрачная activity для кнопки виджета, пока нет разрешения CALL_PHONE:
 * запрашивает его, звонит и перестраивает виджет, чтобы дальше кнопки звонили через [CallReceiver].
 */
class CallActivity : Activity() {

    private var number: String? = null
    private var name: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        number = intent.getStringExtra(EXTRA_NUMBER) ?: return finish()
        name = intent.getStringExtra(EXTRA_NAME) ?: number
        DebugLog.log("activity: click '$name', processAge=${DebugLog.processAge()}ms, permission=${Dialer.hasPermission(this)}")

        if (Dialer.hasPermission(this)) {
            call()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), REQUEST_CALL)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode != REQUEST_CALL) return
        DebugLog.log("activity: permission result=${grantResults.toList()}")
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            CallWidgetProvider.refreshAll(this)
            call()
        } else {
            Toast.makeText(this, R.string.call_permission_denied, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun call() {
        Dialer.call(this, number!!, name!!)
        finish()
    }

    companion object {
        const val EXTRA_NUMBER = "com.callwidget.NUMBER"
        const val EXTRA_NAME = "com.callwidget.NAME"
        private const val REQUEST_CALL = 1
    }
}
