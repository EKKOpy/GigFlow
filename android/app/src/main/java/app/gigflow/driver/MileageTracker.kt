package app.gigflow.driver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import org.json.JSONObject

/**
 * Rastreador de kilometraje de turnos — seguimiento de activación/desactivación al estilo Gridwise. Se inicia desde la
 * pestaña Rastrear, se ejecuta como un servicio en primer plano de tipo ubicación para que siga funcionando
 * mientras el conductor está en una aplicación de conductor. Las ubicaciones GPS acumulan la distancia localmente.
 *
 * Todo se mantiene en el dispositivo — nada sale del teléfono.
 */
class MileageTracker : Service(), LocationListener {

    companion object {
        const val ACTION_START = "app.gigflow.driver.action.START_TRACKING"
        const val ACTION_STOP = "app.gigflow.driver.action.STOP_TRACKING"
        private const val CHANNEL = "mileage_tracking"
        private const val NOTIF_ID = 42

        /** Estado casi en tiempo real legible por la interfaz. */
        @Volatile var running = false
            private set
        @Volatile var kmSoFar = 0.0
            private set
        @Volatile var startedAt = 0L
            private set

        fun startIntent(ctx: Context) =
            Intent(ctx, MileageTracker::class.java).setAction(ACTION_START)
        fun stopIntent(ctx: Context) =
            Intent(ctx, MileageTracker::class.java).setAction(ACTION_STOP)

        fun hasLocationPermission(ctx: Context): Boolean =
            ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    private var lm: LocationManager? = null
    private var last: Location? = null
    private var db: LocalDb? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTracking()
            else -> if (running) stopTracking() else stopSelf()
        }
        return START_STICKY
    }

    private fun startTracking() {
        if (running) return
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            mgr.createNotificationChannel(NotificationChannel(
                CHANNEL, "Rastreo de kilometraje", NotificationManager.IMPORTANCE_LOW))
        }
        startForeground(NOTIF_ID, buildNotification("Turno activo — toca para abrir"))

        running = true
        kmSoFar = 0.0
        startedAt = System.currentTimeMillis()
        db = LocalDb(this)

        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            lm?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 2_000L, 5f, this,
            )
            // El proveedor de red cubre los vacíos donde falla el GPS (túneles, cañones urbanos).
            lm?.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, 8_000L, 25f, this,
            )
        } catch (_: SecurityException) {
            stopTracking()
        }
    }

    override fun onLocationChanged(location: Location) {
        if (!running) return
        // Descartar lecturas con ruido — una lectura de "movimiento" con mala precisión o un desplazamiento
        // demasiado pequeño inflaría el cuentakilómetros.
        if (location.accuracy > 30f) return
        val prev = last
        if (prev != null) {
            val d = prev.distanceTo(location)
            if (d < 5f || d > 500f) return // fluctuación estacionaria o teletransporte
            kmSoFar += d / 1000.0
            updateNotification()
        }
        last = location
    }

    /** Misma forma de notificación siempre — una notificación reconstruida debe
     *  volver a adjuntar el intento de tocar para abrir y la acción de Detener o desaparecerán
     *  después de la primera actualización del cuentakilómetros. */
    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, stopIntent(this), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("GigFlow está rastreando el kilometraje")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Detener turno", stop).build())
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIF_ID, buildNotification("%.1f km recorridos".format(kmSoFar)))
    }

    private fun stopTracking() {
        if (!running) { stopSelf(); return }
        running = false
        try { lm?.removeUpdates(this) } catch (_: SecurityException) {}
        lm = null
        val km = kmSoFar
        val started = startedAt
        kmSoFar = 0.0
        startedAt = 0L
        if (km >= 0.05 && db != null) {
            db!!.insert("mileage", JSONObject()
                .put("distanceKm", km)
                .put("purpose", "WORK")
                .put("description", "Turno rastreado automáticamente")
                .put("date", started))
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (running) stopTracking()
        super.onDestroy()
    }
}