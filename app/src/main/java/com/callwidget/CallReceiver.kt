package com.callwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Получает нажатие кнопки виджета и сразу звонит через TelecomManager.
 * Activity не нужна, поэтому звонок не зависит от ограничений на запуск activity из фона.
 */
class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val number = intent.getStringExtra(CallActivity.EXTRA_NUMBER) ?: return
        val name = intent.getStringExtra(CallActivity.EXTRA_NAME) ?: number
        DebugLog.log("receiver: click '$name', processAge=${DebugLog.processAge()}ms")
        if (!Dialer.call(context, number, name)) {
            // Разрешение отозвали после построения виджета: кнопки снова поведут на запрос разрешения
            Toast.makeText(context, R.string.call_permission_denied, Toast.LENGTH_LONG).show()
            CallWidgetProvider.refreshAll(context)
            return
        }
        // Пишем в журнал, начался ли звонок на самом деле: режим аудио переходит в IN_CALL
        val pending = goAsync()
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({ DebugLog.log("after 1.5s: ${DebugLog.audioState(context)}") }, 1500)
        handler.postDelayed({
            DebugLog.log("after 5s: ${DebugLog.audioState(context)}")
            pending.finish()
        }, 5000)
    }
}
