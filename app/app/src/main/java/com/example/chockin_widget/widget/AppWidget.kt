package com.example.chockin_widget.widget

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import com.example.chockin_widget.MainActivity

class AppWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId
    ) {
        provideContent {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Content()
            } else {
                Text(text = "This widget requires Android 12 or higher.")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @Composable
    private fun Content() {
        Column(
            modifier = GlanceModifier.fillMaxSize().cornerRadius(android.R.dimen.system_app_widget_background_radius),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Where to?", modifier = GlanceModifier.padding(12.dp))
            Row(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    text = "Home",
                    onClick = actionStartActivity<MainActivity>()
                )
                Button(
                    text = "Work",
                    onClick = actionStartActivity<MainActivity>()
                )
            }
        }
    }
}