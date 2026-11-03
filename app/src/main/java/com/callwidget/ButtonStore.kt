package com.callwidget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class ButtonType { CALL, HTTP }

/** Параметры REST-запроса кнопки. Заголовки хранятся текстом: по одному «Имя: значение» на строку. */
data class HttpRequest(
    val url: String,
    val method: String = "GET",
    val body: String = "",
    val headers: String = "",
    val basicAuth: Boolean = false,
    val user: String = "",
    val password: String = "",
) {
    /** Пароль сохраняется только зашифрованным ключом из Android Keystore ([SecretStore]). */
    fun toJson(): JSONObject = JSONObject()
        .put("url", url)
        .put("method", method)
        .put("body", body)
        .put("headers", headers)
        .put("basicAuth", basicAuth)
        .put("user", user)
        .put(KEY_PASSWORD_ENC, SecretStore.encrypt(password))

    companion object {
        val METHODS = arrayOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD")

        /** Методы, для которых тело не отправляется. */
        val NO_BODY = setOf("GET", "HEAD")

        private const val KEY_PASSWORD_ENC = "passwordEnc"

        /** Открытый пароль из версий до шифрования: при чтении переносится в [KEY_PASSWORD_ENC]. */
        const val KEY_PASSWORD_PLAIN = "password"

        fun fromJson(obj: JSONObject) = HttpRequest(
            url = obj.optString("url"),
            method = obj.optString("method", "GET"),
            body = obj.optString("body"),
            headers = obj.optString("headers"),
            basicAuth = obj.optBoolean("basicAuth"),
            user = obj.optString("user"),
            password = if (obj.has(KEY_PASSWORD_ENC)) {
                SecretStore.decrypt(obj.getString(KEY_PASSWORD_ENC)).orEmpty()
            } else {
                obj.optString(KEY_PASSWORD_PLAIN)
            },
        )
    }
}

/** Кнопка виджета: звонок на [number] или REST-запрос [http]. */
data class CallButton(
    val name: String,
    val number: String = "",
    val type: ButtonType = ButtonType.CALL,
    val http: HttpRequest? = null,
)

/** Хранит список кнопок в SharedPreferences. */
object ButtonStore {
    private const val PREFS = "call_widget"
    private const val KEY_BUTTONS = "buttons"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): MutableList<CallButton> {
        val json = prefs(context).getString(KEY_BUTTONS, null) ?: return mutableListOf()
        val array = JSONArray(json)
        var hasPlainPassword = false
        val buttons = MutableList(array.length()) { i ->
            val obj = array.getJSONObject(i)
            // Кнопки из старых версий не имеют типа: это звонки
            val type = runCatching { ButtonType.valueOf(obj.optString("type")) }.getOrDefault(ButtonType.CALL)
            val http = obj.optJSONObject("http")
            if (http?.has(HttpRequest.KEY_PASSWORD_PLAIN) == true) hasPlainPassword = true
            CallButton(
                name = obj.getString("name"),
                number = obj.optString("number"),
                type = type,
                http = http?.let(HttpRequest::fromJson),
            )
        }
        // Пароли, сохранённые до шифрования, сразу перезаписываем зашифрованными
        if (hasPlainPassword) write(context, buttons)
        return buttons
    }

    fun save(context: Context, buttons: List<CallButton>) {
        write(context, buttons)
        CallWidgetProvider.refreshAll(context)
    }

    private fun write(context: Context, buttons: List<CallButton>) {
        val array = JSONArray()
        buttons.forEach { button ->
            val obj = JSONObject()
                .put("name", button.name)
                .put("number", button.number)
                .put("type", button.type.name)
            button.http?.let { obj.put("http", it.toJson()) }
            array.put(obj)
        }
        prefs(context).edit().putString(KEY_BUTTONS, array.toString()).apply()
    }
}
