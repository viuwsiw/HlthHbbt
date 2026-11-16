package com.tshk.hlthhbbty.ui.walk

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tshk.hlthhbbty.walk.TrackPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay

/**
 * Карта OpenStreetMap (без ключей): трек прогулки линией и маячок текущего положения.
 * [follow] — держать маячок в центре; иначе карта подстраивается под весь маршрут.
 */
@Composable
fun OsmMap(
    points: List<TrackPoint>,
    current: TrackPoint?,
    follow: Boolean,
    lineColor: Color,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val map = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = context.cacheDir.resolve("osmdroid")
            osmdroidTileCache = context.cacheDir.resolve("osmdroid/tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(16.5)
            controller.setCenter(GeoPoint(55.7558, 37.6173))
            isTilesScaledToDpi = true
        }
    }
    val line = remember { Polyline(map).apply { outlinePaint.strokeWidth = 14f; outlinePaint.strokeCap = Paint.Cap.ROUND } }
    val beacon = remember { Marker(map).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); setInfoWindow(null) } }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); map.onDetach() }
    }

    AndroidView(factory = { map }, modifier = modifier, update = { mv ->
        mv.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
        line.outlinePaint.color = lineColor.toArgb()
        line.setPoints(points.map { GeoPoint(it.lat, it.lon) })
        if (!mv.overlays.contains(line)) mv.overlays.add(line)
        if (current != null) {
            beacon.position = GeoPoint(current.lat, current.lon)
            beacon.icon = beaconIcon(mv, lineColor.toArgb())
            if (!mv.overlays.contains(beacon)) mv.overlays.add(beacon)
        } else mv.overlays.remove(beacon)

        when {
            follow && current != null -> mv.controller.animateTo(GeoPoint(current.lat, current.lon))
            !follow && points.size >= 2 -> mv.post {
                runCatching {
                    mv.zoomToBoundingBox(BoundingBox.fromGeoPoints(points.map { GeoPoint(it.lat, it.lon) }).increaseByScale(1.3f), false)
                }
            }
            !follow && points.size == 1 -> mv.controller.setCenter(GeoPoint(points[0].lat, points[0].lon))
        }
        mv.invalidate()
    })
}

/** Маячок: точка с белой обводкой и полупрозрачным ореолом. */
private fun beaconIcon(map: MapView, color: Int): BitmapDrawable {
    val d = map.resources.displayMetrics.density
    val size = (34 * d).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = size / 2f
    p.color = color; p.alpha = 60; c.drawCircle(cx, cx, size / 2f, p)
    p.color = android.graphics.Color.WHITE; p.alpha = 255; c.drawCircle(cx, cx, 8 * d, p)
    p.color = color; c.drawCircle(cx, cx, 5.5f * d, p)
    return BitmapDrawable(map.resources, bmp)
}
