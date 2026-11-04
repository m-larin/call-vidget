package com.callwidget

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class MainActivity : AppCompatActivity() {

    private val buttons = mutableListOf<CallButton>()
    private val adapter = ButtonsAdapter()
    private lateinit var emptyView: View

    /** Поля открытого диалога — сюда подставляется выбранный контакт. */
    private var dialogName: TextInputEditText? = null
    private var dialogNumber: TextInputEditText? = null

    private val pickContact = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                dialogNumber?.setText(cursor.getString(0))
                if (dialogName?.text.isNullOrBlank()) dialogName?.setText(cursor.getString(1))
            }
        }
    }

    private val requestCallPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        CallWidgetProvider.refreshAll(this)
    }

    override fun onResume() {
        super.onResume()
        // Разрешение могли выдать или отозвать в настройках: от него зависит, куда ведут кнопки виджета
        CallWidgetProvider.refreshAll(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.share_log -> {
            // logcat читается не мгновенно (особенно с READ_LOGS), поэтому не в главном потоке
            Thread {
                val share = try {
                    DebugLog.dumpAndShare(this)
                } catch (e: Exception) {
                    Log.w(DebugLog.TAG, "logcat dump failed", e)
                    null
                }
                runOnUiThread {
                    if (share != null) startActivity(share)
                    else Toast.makeText(this, R.string.log_failed, Toast.LENGTH_LONG).show()
                }
            }.start()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))

        emptyView = findViewById(R.id.empty)
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
        findViewById<FloatingActionButton>(R.id.add).setOnClickListener { showEditDialog(null) }

        buttons.addAll(ButtonStore.load(this))
        updateEmpty()

        // Разрешение спрашиваем заранее, чтобы первый звонок с виджета прошёл сразу
        // READ_PHONE_STATE нужен, чтобы найти аккаунт звонков Bluetooth (см. Dialer)
        val missing = arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            requestCallPermission.launch(missing.toTypedArray())
        }
    }

    private fun persist() {
        ButtonStore.save(this, buttons)
        adapter.notifyDataSetChanged()
        updateEmpty()
    }

    private fun updateEmpty() {
        emptyView.visibility = if (buttons.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showEditDialog(position: Int?) {
        val view = layoutInflater.inflate(R.layout.dialog_edit, null)
        val nameLayout = view.findViewById<TextInputLayout>(R.id.name_layout)
        val numberLayout = view.findViewById<TextInputLayout>(R.id.number_layout)
        val nameEdit = view.findViewById<TextInputEditText>(R.id.name)
        val numberEdit = view.findViewById<TextInputEditText>(R.id.number)
        val typeGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.type)
        val httpSection = view.findViewById<View>(R.id.http_section)
        val urlLayout = view.findViewById<TextInputLayout>(R.id.url_layout)
        val urlEdit = view.findViewById<TextInputEditText>(R.id.url)
        val methodEdit = view.findViewById<MaterialAutoCompleteTextView>(R.id.method)
        val bodyLayout = view.findViewById<View>(R.id.body_layout)
        val bodyEdit = view.findViewById<TextInputEditText>(R.id.body)
        val headersLayout = view.findViewById<TextInputLayout>(R.id.headers_layout)
        val headersEdit = view.findViewById<TextInputEditText>(R.id.headers)
        val basicAuth = view.findViewById<MaterialSwitch>(R.id.basic_auth)
        val authSection = view.findViewById<View>(R.id.auth_section)
        val userLayout = view.findViewById<TextInputLayout>(R.id.user_layout)
        val userEdit = view.findViewById<TextInputEditText>(R.id.user)
        val passwordEdit = view.findViewById<TextInputEditText>(R.id.password)
        dialogName = nameEdit
        dialogNumber = numberEdit

        methodEdit.setSimpleItems(HttpRequest.METHODS)
        methodEdit.setText(HttpRequest.METHODS.first(), false)
        position?.let {
            val button = buttons[it]
            nameEdit.setText(button.name)
            numberEdit.setText(button.number)
            if (button.type == ButtonType.HTTP) typeGroup.check(R.id.type_http)
            button.http?.let { http ->
                urlEdit.setText(http.url)
                methodEdit.setText(http.method, false)
                bodyEdit.setText(http.body)
                headersEdit.setText(http.headers)
                basicAuth.isChecked = http.basicAuth
                userEdit.setText(http.user)
                passwordEdit.setText(http.password)
            }
        }

        // Видимость полей зависит от типа кнопки, метода и Basic-аутентификации
        fun isHttp() = typeGroup.checkedButtonId == R.id.type_http
        fun updateVisibility() {
            numberLayout.isVisible = !isHttp()
            httpSection.isVisible = isHttp()
            bodyLayout.isVisible = methodEdit.text.toString() !in HttpRequest.NO_BODY
            authSection.isVisible = basicAuth.isChecked
        }
        typeGroup.addOnButtonCheckedListener { _, _, _ -> updateVisibility() }
        methodEdit.doAfterTextChanged { updateVisibility() }
        basicAuth.setOnCheckedChangeListener { _, _ -> updateVisibility() }
        updateVisibility()

        numberLayout.setEndIconOnClickListener {
            pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        }

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(if (position == null) R.string.add_button else R.string.edit_button)
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
        if (position != null) {
            builder.setNeutralButton(R.string.delete) { _, _ ->
                buttons.removeAt(position)
                persist()
            }
        }
        val dialog = builder.create()
        dialog.setOnDismissListener {
            dialogName = null
            dialogNumber = null
        }
        dialog.show()

        // Свой обработчик, чтобы не закрывать диалог при ошибке ввода
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = nameEdit.text.toString().trim()
            val number = numberEdit.text.toString().trim()
            val url = urlEdit.text.toString().trim()
            val headers = headersEdit.text.toString()
            val user = userEdit.text.toString()
            val http = isHttp()

            nameLayout.error = if (name.isEmpty()) getString(R.string.error_name) else null
            numberLayout.error = if (!http && number.isEmpty()) getString(R.string.error_number) else null
            urlLayout.error = if (http && !HttpRunner.isValidUrl(url)) getString(R.string.error_url) else null
            headersLayout.error = if (!http) null else try {
                HttpRunner.parseHeaders(headers)
                null
            } catch (e: IllegalArgumentException) {
                getString(R.string.error_headers, e.message)
            }
            userLayout.error =
                if (http && basicAuth.isChecked && user.isEmpty()) getString(R.string.error_user) else null
            val layouts = listOf(nameLayout, numberLayout, urlLayout, headersLayout, userLayout)
            if (layouts.any { it.error != null }) return@setOnClickListener

            // Параметры запроса сохраняем и для кнопки-звонка, чтобы не терять их при переключении типа
            val request = HttpRequest(
                url = url,
                method = methodEdit.text.toString(),
                body = bodyEdit.text.toString(),
                headers = headers.trim(),
                basicAuth = basicAuth.isChecked,
                user = user,
                password = passwordEdit.text.toString(),
            ).takeIf { http || url.isNotEmpty() }
            val button = CallButton(name, number, if (http) ButtonType.HTTP else ButtonType.CALL, request)
            if (position == null) buttons.add(button) else buttons[position] = button
            persist()
            dialog.dismiss()
        }
    }

    private fun move(from: Int, to: Int) {
        if (from !in buttons.indices || to !in buttons.indices) return
        buttons.add(to, buttons.removeAt(from))
        persist()
    }

    private inner class ButtonsAdapter : RecyclerView.Adapter<ButtonsAdapter.Holder>() {
        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.name)
            val number: TextView = view.findViewById(R.id.number)
            val up: ImageButton = view.findViewById(R.id.up)
            val down: ImageButton = view.findViewById(R.id.down)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_button, parent, false))

        override fun getItemCount() = buttons.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val button = buttons[position]
            holder.name.text = button.name
            holder.number.text = when (button.type) {
                ButtonType.CALL -> button.number
                ButtonType.HTTP -> button.http?.let { "${it.method} ${it.url}" }.orEmpty()
            }
            holder.up.visibility = if (position > 0) View.VISIBLE else View.INVISIBLE
            holder.down.visibility = if (position < buttons.lastIndex) View.VISIBLE else View.INVISIBLE
            holder.itemView.setOnClickListener { showEditDialog(holder.bindingAdapterPosition) }
            holder.up.setOnClickListener { holder.bindingAdapterPosition.let { move(it, it - 1) } }
            holder.down.setOnClickListener { holder.bindingAdapterPosition.let { move(it, it + 1) } }
        }
    }
}
