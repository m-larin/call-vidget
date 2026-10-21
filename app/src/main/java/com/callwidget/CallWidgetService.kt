package com.callwidget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/** Поставляет кнопки для GridView виджета. */
class CallWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val context: Context) : RemoteViewsFactory {
        private var buttons: List<CallButton> = emptyList()

        override fun onCreate() {}
        override fun onDataSetChanged() {
            buttons = ButtonStore.load(context)
        }
        override fun onDestroy() {}
        override fun getCount() = buttons.size

        override fun getViewAt(position: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_item)
            val button = buttons.getOrNull(position) ?: return views
            views.setTextViewText(R.id.item_button, button.name)
            val fillIn = Intent()
                .putExtra(CallActivity.EXTRA_NUMBER, button.number)
                .putExtra(CallActivity.EXTRA_NAME, button.name)
            views.setOnClickFillInIntent(R.id.item_button, fillIn)
            return views
        }

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false
    }
}
