package com.example.chockin_widget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.chockin_widget.RoomRepository

class AppWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = AppWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WorkManager.getInstance(context).enqueueUniqueWork(
            "room-update", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<RoomWorker>().build()
        )
    }
}

class RoomWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val success = RoomRepository.refresh(applicationContext)
        AppWidget().updateAll(applicationContext)
        return if (!success && runAttemptCount < 2) Result.retry() else Result.success()
    }
}
