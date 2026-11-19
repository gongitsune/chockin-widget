package com.example.chockin_widget

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.widget.ListView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.view.descendants
import androidx.glance.appwidget.compose
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chockin_widget.widget.AppWidget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppWidgetTest {
    @Test
    @SdkSuppress(minSdkVersion = 31)
    fun rendersMemberInLauncherCollection() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = object : ContextWrapper(instrumentation.targetContext) {
            override fun getSharedPreferences(name: String, mode: Int) =
                baseContext.getSharedPreferences("widget-test-" + name, mode)
        }
        val prefs = context.getSharedPreferences("room", Context.MODE_PRIVATE)
        val json = """{"serverTime":"2026-11-19T10:00:00Z","occupied":true,"presentCount":1,
            "members":[{"displayName":"テスト部員","checkedInAt":"2026-11-19T09:00:00Z",
            "statusMessage":"アプリだけに表示","avatarUrl":null}]}"""
        try {
            prefs.edit().putString("json", json).apply()
            val views = runBlocking { AppWidget().compose(context, size = DpSize(250.dp, 160.dp)) }
            instrumentation.runOnMainSync {
                val host = AppWidgetHostView(context)
                host.addView(views.apply(context, host))
                val list = host.descendants.filterIsInstance<ListView>().single()
                assertEquals(1, list.adapter.count)
                val row = list.adapter.getView(0, null, list)
                val result = arrayListOf<View>()
                row.findViewsWithText(result, "テスト部員", View.FIND_VIEWS_WITH_TEXT)
                assertTrue(result.isNotEmpty())
                result.clear()
                row.findViewsWithText(result, "アプリだけに表示", View.FIND_VIEWS_WITH_TEXT)
                assertTrue(result.isEmpty())
            }
        } finally {
            prefs.edit().clear().apply()
        }
    }
}
