package com.k0ara.chockinwidget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.collection.intSetOf
import androidx.core.content.edit
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class WidgetLocaleWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val language = context.resources.configuration.locales.toLanguageTags()
        val prefs = context.getSharedPreferences("widget_locales", Context.MODE_PRIVATE)
        if (prefs.getString("widget", null) != language) {
            AppWidget().updateAll(context)
            prefs.edit { putString("widget", language) }
        }
        if (Build.VERSION.SDK_INT < 35) return Result.success()

        val component = ComponentName(context, AppWidgetReceiver::class.java)
        val provider = AppWidgetManager.getInstance(context).installedProviders
            .firstOrNull { it.provider == component } ?: return Result.success()
        if (provider.generatedPreviewCategories and WIDGET_CATEGORY_HOME_SCREEN != 0 &&
            prefs.getString("preview", null) == language
        ) return Result.success()

        val result = GlanceAppWidgetManager(context).setWidgetPreviews(
            AppWidgetReceiver::class, intSetOf(WIDGET_CATEGORY_HOME_SCREEN)
        )
        if (result == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_RATE_LIMITED) {
            return Result.retry()
        }
        prefs.edit { putString("preview", language) }
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetLocaleWorker>()
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("widget-locale", ExistingWorkPolicy.KEEP, request)
        }
    }
}
