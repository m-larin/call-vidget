package com.callwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import kotlin.math.ceil
import kotlin.math.min

class CallWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { manager.updateAppWidget(it, buildViews(context, manager, it)) }
    }

    /** Размер виджета изменился: пересчитываем сетку под новые пропорции. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        manager.updateAppWidget(appWidgetId, buildViews(context, manager, appWidgetId))
    }

    companion object {
        /** Желаемое соотношение ширины кнопки к высоте при выборе числа колонок. */
        private const val BUTTON_ASPECT = 2f
        private const val DEFAULT_WIDTH_DP = 250
        private const val DEFAULT_HEIGHT_DP = 110

        /** Перерисовать все виджеты после изменения списка кнопок. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CallWidgetProvider::class.java))
            ids.forEach { manager.updateAppWidget(it, buildViews(context, manager, it)) }
        }

        private fun buildViews(context: Context, manager: AppWidgetManager, widgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_call)
            views.removeAllViews(R.id.grid)

            val buttons = ButtonStore.load(context)
            views.setViewVisibility(R.id.grid, if (buttons.isEmpty()) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.empty, if (buttons.isEmpty()) View.VISIBLE else View.GONE)
            if (buttons.isEmpty()) {
                val openApp = PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.empty, openApp)
                return views
            }

            // Строки и кнопки растягиваются весами, поэтому заполняют виджет при любом размере
            val (width, height) = widgetSizeDp(context, manager.getAppWidgetOptions(widgetId))
            val columns = chooseColumns(buttons.size, width, height)
            buttons.withIndex().chunked(columns).forEach { rowButtons ->
                val row = RemoteViews(context.packageName, R.layout.widget_row)
                rowButtons.forEach { (index, button) ->
                    val item = RemoteViews(context.packageName, R.layout.widget_item)
                    item.setTextViewText(R.id.item_button, button.name)
                    item.setOnClickPendingIntent(R.id.item_button, callIntent(context, index, button))
                    row.addView(R.id.row, item)
                }
                views.addView(R.id.grid, row)
            }
            return views
        }

        private fun callIntent(context: Context, index: Int, button: CallButton): PendingIntent {
            val intent = Intent(context, CallActivity::class.java)
                .putExtra(CallActivity.EXTRA_NUMBER, button.number)
                .putExtra(CallActivity.EXTRA_NAME, button.name)
            // requestCode = позиция кнопки, чтобы у каждой был свой PendingIntent
            return PendingIntent.getActivity(
                context, index, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** Текущий размер виджета в dp: в портрете лаунчер отдаёт minWidth x maxHeight, в ландшафте наоборот. */
        private fun widgetSizeDp(context: Context, options: Bundle): Pair<Int, Int> {
            val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val width = options.getInt(
                if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
            )
            val height = options.getInt(
                if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
            )
            if (width <= 0 || height <= 0) return DEFAULT_WIDTH_DP to DEFAULT_HEIGHT_DP
            return width to height
        }

        /** Число колонок, при котором кнопки получаются крупнее всего (с поправкой на вытянутую форму кнопки). */
        private fun chooseColumns(count: Int, width: Int, height: Int): Int =
            (1..count).maxBy { columns ->
                val rows = ceil(count / columns.toFloat())
                min(width / columns / BUTTON_ASPECT, height / rows)
            }
    }
}
