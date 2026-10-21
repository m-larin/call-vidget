package com.callwidget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CallButton(val name: String, val number: String)

/** Хранит список кнопок и настройки в SharedPreferences. */
object ButtonStore {
    private const val PREFS = "call_widget"
    private const val KEY_BUTTONS = "buttons"
    private const val KEY_REQUIRE_BT = "require_bt"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): MutableList<CallButton> {
        val json = prefs(context).getString(KEY_BUTTONS, null) ?: return mutableListOf()
        val array = JSONArray(json)
        return MutableList(array.length()) { i ->
            val obj = array.getJSONObject(i)
            CallButton(obj.getString("name"), obj.getString("number"))
        }
    }

    fun save(context: Context, buttons: List<CallButton>) {
        val array = JSONArray()
        buttons.forEach { array.put(JSONObject().put("name", it.name).put("number", it.number)) }
        prefs(context).edit().putString(KEY_BUTTONS, array.toString()).apply()
        CallWidgetProvider.refreshAll(context)
    }

    fun requireBluetooth(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REQUIRE_BT, true)

    fun setRequireBluetooth(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_REQUIRE_BT, value).apply()
    }
}
