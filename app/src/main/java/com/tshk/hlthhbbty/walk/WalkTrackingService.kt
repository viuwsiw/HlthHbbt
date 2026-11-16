package com.tshk.hlthhbbty.walk

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.tshk.hlthhbbty.HlthApp
import com.tshk.hlthhbbty.MainActivity
import com.tshk.hlthhbbty.R
import com.tshk.hlthhbbty.reminders.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Служба прогулки: GPS-трек и шаги при выключенном экране, уведомление с секундомером. */
class WalkTrackingService : Service(), LocationListener, SensorEventListener {
    private val tracker get() = (application as HlthApp).container.walks
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var locationManager: LocationManager? = null
    private var sensorManager: SensorManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)

        locationManager = getSystemService(LocationManager::class.java)?.also { lm ->
            runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0f, this, Looper.getMainLooper()) }
            runCatching { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 0f, this, Looper.getMainLooper()) }
            runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()?.let(::onLocationChanged)
        }
        sensorManager = getSystemService(SensorManager::class.java)
        val counter = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (counter == null || !runCatching { sensorManager?.registerListener(this, counter, SensorManager.SENSOR_DELAY_NORMAL) }.getOrDefault(false)!!) {
            tracker.setNoStepSensor()
        }

        // Обновляем уведомление при паузе/продолжении и раз в ~100 м.
        scope.launch {
            tracker.session.map { it?.let { s -> s.paused to (s.distanceM / 100).toInt() } }
                .distinctUntilChanged()
                .collectLatest { if (it == null) stopSelf() else updateNotification() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> tracker.pause()
            ACTION_RESUME -> tracker.resume()
        }
        return START_NOT_STICKY
    }

    override fun onLocationChanged(location: Location) = tracker.onLocation(location)
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) tracker.onStepCounter(event.values[0])
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    @SuppressLint("MissingPermission")
    private fun updateNotification() {
        if (Notifications.canPost(this)) NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification())
    }

    private fun notification() = run {
        val s = tracker.session.value
        val b = NotificationCompat.Builder(this, Notifications.CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setContentTitle("🚶 ${s?.title ?: "Прогулка"}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 40,
                    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_OPEN_WALK, true),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
        val km = "%.2f км".format((s?.distanceM ?: 0.0) / 1000)
        if (s != null && !s.paused) {
            b.setContentText("Идёт запись маршрута · $km")
                .setUsesChronometer(true)
                .setWhen(System.currentTimeMillis() - s.elapsedMs())
                .setShowWhen(true)
                .addAction(0, "Пауза", action(ACTION_PAUSE, 41))
        } else {
            b.setContentText("Пауза · $km").setShowWhen(false).addAction(0, "Продолжить", action(ACTION_RESUME, 42))
        }
        b.build()
    }

    private fun action(a: String, code: Int) = PendingIntent.getService(
        this, code, Intent(this, WalkTrackingService::class.java).setAction(a),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun onDestroy() {
        runCatching { locationManager?.removeUpdates(this) }
        runCatching { sensorManager?.unregisterListener(this) }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 4343
        private const val ACTION_PAUSE = "walk.pause"
        private const val ACTION_RESUME = "walk.resume"
    }
}
