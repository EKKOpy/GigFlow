package app.gigflow.driver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect

/** Realiza toques dentro de la aplicación de destino mediante la API de gestos de accesibilidad. */
object GesturePerformer {

    fun tap(service: AccessibilityService, bounds: Rect, done: (Boolean) -> Unit = {}) {
        val path = Path().apply {
            moveTo(bounds.exactCenterX(), bounds.exactCenterY())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        // dispatchGesture devuelve falso cuando no puede enviar el gesto en absoluto —
        // por lo tanto, ningún callback se ejecuta, así que informamos del fallo nosotros mismos.
        val dispatched = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) = done(true)
                override fun onCancelled(g: GestureDescription?) = done(false)
            },
            null,
        )
        if (!dispatched) done(false)
    }

    /** Deslizamiento de izquierda a derecha sobre [bounds] — Amazon Flex utiliza un control deslizante de
     *  "deslizar para aceptar" donde un simple toque no hace nada. */
    fun swipeRight(service: AccessibilityService, bounds: Rect, done: (Boolean) -> Unit = {}) {
        val path = Path().apply {
            moveTo(bounds.left + bounds.width() * 0.15f, bounds.exactCenterY())
            lineTo(bounds.right - bounds.width() * 0.10f, bounds.exactCenterY())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 350))
            .build()
        val dispatched = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) = done(true)
                override fun onCancelled(g: GestureDescription?) = done(false)
            },
            null,
        )
        if (!dispatched) done(false)
    }
}