package com.k0ara.chockinwidget

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.view.View
import android.widget.ListView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.view.descendants
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.composeForPreview
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.k0ara.chockinwidget.widget.AppWidget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AppWidgetTest {
    @Test
    @SdkSuppress(minSdkVersion = 35)
    fun previewShowsCountInSelectedLanguage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        for ((locale, count) in listOf(Locale.ENGLISH to "2 people", Locale.JAPANESE to "2 人")) {
            val config = Configuration(target.resources.configuration).apply { setLocale(locale) }
            val context = target.createConfigurationContext(config)
            val views =
                runBlocking { AppWidget().composeForPreview(context, WIDGET_CATEGORY_HOME_SCREEN) }
            instrumentation.runOnMainSync {
                val host = AppWidgetHostView(context)
                val preview = views.apply(context, host)
                val matches = arrayListOf<View>()
                preview.findViewsWithText(matches, count, View.FIND_VIEWS_WITH_TEXT)
                assertTrue("Missing count: $count", matches.isNotEmpty())
            }
        }
    }

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
