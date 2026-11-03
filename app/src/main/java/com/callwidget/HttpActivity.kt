package com.callwidget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import okhttp3.Call

/**
 * Прозрачная activity для кнопки-запроса: показывает заставку на время запроса,
 * затем «OK» (ответ 2xx) или текст ошибки. Нажатие в любом месте закрывает её.
 */
class HttpActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var call: Call? = null
    private var loading = false

    private lateinit var progress: ProgressBar
    private lateinit var icon: ImageView
    private lateinit var title: TextView
    private lateinit var message: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_http)
        progress = findViewById(R.id.http_progress)
        icon = findViewById(R.id.http_icon)
        title = findViewById(R.id.http_title)
        message = findViewById(R.id.http_message)
        findViewById<View>(R.id.http_root).setOnClickListener { finish() }
        start(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Повторное нажатие, пока запрос идёт, игнорируем; после результата запускаем новый
        if (!loading) start(intent)
    }

    private fun start(intent: Intent) {
        val index = intent.getIntExtra(EXTRA_INDEX, -1)
        val button = ButtonStore.load(this).getOrNull(index)
        val request = button?.http
        DebugLog.log("http activity: click #$index '${button?.name}', processAge=${DebugLog.processAge()}ms")
        if (button == null || request == null) return finish()

        handler.removeCallbacksAndMessages(null)
        loading = true
        progress.visibility = View.VISIBLE
        icon.visibility = View.GONE
        title.text = button.name
        message.text = getString(R.string.http_sending)

        call = HttpRunner.execute(this, request) { result -> runOnUiThread { show(result) } }
    }

    private fun show(result: HttpRunner.Result) {
        if (isFinishing || isDestroyed) return
        loading = false
        progress.visibility = View.GONE
        icon.visibility = View.VISIBLE
        when (result) {
            is HttpRunner.Result.Ok -> {
                icon.setImageResource(R.drawable.ic_ok)
                message.text = getString(R.string.http_ok)
                handler.postDelayed({ finish() }, OK_CLOSE_MS)
            }
            is HttpRunner.Result.Error -> {
                icon.setImageResource(R.drawable.ic_error)
                message.text = result.message
                handler.postDelayed({ finish() }, ERROR_CLOSE_MS)
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        call?.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_INDEX = "com.callwidget.INDEX"
        private const val OK_CLOSE_MS = 1_500L
        private const val ERROR_CLOSE_MS = 15_000L
    }
}
