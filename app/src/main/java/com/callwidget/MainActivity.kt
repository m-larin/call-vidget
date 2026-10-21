package com.callwidget

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
import com.google.android.material.materialswitch.MaterialSwitch
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

    private val requestCallPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

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

        val requireBt = findViewById<MaterialSwitch>(R.id.require_bt)
        requireBt.isChecked = ButtonStore.requireBluetooth(this)
        requireBt.setOnCheckedChangeListener { _, checked -> ButtonStore.setRequireBluetooth(this, checked) }

        emptyView = findViewById(R.id.empty)
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
        findViewById<FloatingActionButton>(R.id.add).setOnClickListener { showEditDialog(null) }

        buttons.addAll(ButtonStore.load(this))
        updateEmpty()

        // Разрешение спрашиваем заранее, чтобы первый звонок с виджета прошёл сразу
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestCallPermission.launch(Manifest.permission.CALL_PHONE)
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
        dialogName = nameEdit
        dialogNumber = numberEdit

        position?.let {
            nameEdit.setText(buttons[it].name)
            numberEdit.setText(buttons[it].number)
        }
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
            nameLayout.error = if (name.isEmpty()) getString(R.string.error_name) else null
            numberLayout.error = if (number.isEmpty()) getString(R.string.error_number) else null
            if (name.isEmpty() || number.isEmpty()) return@setOnClickListener

            val button = CallButton(name, number)
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
            holder.number.text = button.number
            holder.up.visibility = if (position > 0) View.VISIBLE else View.INVISIBLE
            holder.down.visibility = if (position < buttons.lastIndex) View.VISIBLE else View.INVISIBLE
            holder.itemView.setOnClickListener { showEditDialog(holder.bindingAdapterPosition) }
            holder.up.setOnClickListener { holder.bindingAdapterPosition.let { move(it, it - 1) } }
            holder.down.setOnClickListener { holder.bindingAdapterPosition.let { move(it, it + 1) } }
        }
    }
}
