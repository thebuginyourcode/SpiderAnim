package com.spideranim

import com.spideranim.model.Clip
import com.spideranim.model.Ease
import com.spideranim.model.Edits
import com.spideranim.model.Exposure
import com.spideranim.model.Layer
import com.spideranim.model.LayerType
import com.spideranim.model.Marker
import com.spideranim.model.Project
import com.spideranim.model.ProjectJson
import com.spideranim.model.Prop
import com.spideranim.model.Pt
import com.spideranim.model.Stroke
import com.spideranim.model.StrokeKind
import com.spideranim.model.V2
import com.spideranim.model.clipBounds
import com.spideranim.model.Xf
import com.spideranim.model.eraseStrokes
import com.spideranim.model.rectPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {

    private fun dot(x: Float) = Stroke(StrokeKind.Pencil, listOf(Pt(x, 0f), Pt(x + 1, 0f)), 0, 2f)
    private fun drawing() = Layer(name = "D", type = LayerType.Drawing)

    @Test
    fun easesHitEndpoints() {
        for ((_, e) in Ease.presets) {
            assertEquals(0f, e.apply(0f), 1e-4f)
            assertEquals(1f, e.apply(1f), 1e-4f)
        }
        assertTrue(Ease.In.apply(0.5f) < 0.5f)
        assertTrue(Ease.Out.apply(0.5f) > 0.5f)
    }

    @Test
    fun drawingOnEmptyFrameCreatesOneFrameDrawing() {
        var l = drawing()
        l = Edits.addStroke(l, 3, dot(0f))
        l = Edits.addStroke(l, 3, dot(5f))
        l = Edits.addStroke(l, 0, dot(9f))
        assertEquals(listOf(0, 3), l.exposures.map { it.start })
        assertEquals(2, l.exposureAt(3)!!.strokes.size)
        assertNull(l.exposureAt(1))
    }

    @Test
    fun addDrawingInsertsAfterCurrentAndPushesLaterOnes() {
        var l = drawing().copy(exposures = listOf(Exposure(start = 0, length = 2, strokes = listOf(dot(0f))), Exposure(start = 2, strokes = listOf(dot(1f)))))
        val (l2, at) = Edits.addDrawing(l, 1, copy = false)
        assertEquals(2, at)
        assertEquals(listOf(0 to 2, 2 to 1, 3 to 1), l2.exposures.map { it.start to it.length })
        assertTrue(l2.exposureAt(2)!!.strokes.isEmpty())

        val (l3, at3) = Edits.addDrawing(l2, 0, copy = true)
        assertEquals(2, at3)
        assertEquals(1, l3.exposureAt(2)!!.strokes.size)
        l = l3
        assertEquals(listOf(0, 2, 3, 4), l.exposures.map { it.start })
    }

    @Test
    fun extendAndShortenRipple() {
        var l = drawing().copy(exposures = listOf(Exposure(start = 0), Exposure(start = 1), Exposure(start = 5)))
        l = Edits.extend(l, 0)
        assertEquals(listOf(0 to 2, 2 to 1, 6 to 1), l.exposures.map { it.start to it.length })
        l = Edits.extend(l, 4) // empty frame: previous drawing stretches to it
        assertEquals(2 to 3, l.exposures[1].start to l.exposures[1].length)
        l = Edits.shorten(l, 0)
        assertEquals(listOf(0 to 1, 1 to 3, 5 to 1), l.exposures.map { it.start to it.length })
    }

    @Test
    fun moveExposureRefusesOverlap() {
        val a = Exposure(start = 0, length = 2)
        val b = Exposure(start = 4)
        val l = drawing().copy(exposures = listOf(a, b))
        assertEquals(0, Edits.moveExposure(l, a.id, 3).exposures.first { it.id == a.id }.start)
        assertEquals(1, Edits.moveExposure(l, a.id, 1).exposures.first { it.id == a.id }.start)
    }

    @Test
    fun propertiesKeyOnlyWhenAnimated() {
        var l = drawing()
        l = l.set(Prop.Position, 5, V2(10f, 20f), autoKey = false)
        assertNull(l.track(Prop.Position))
        assertEquals(10f, l.xfAt(99).x, 0f)
        l = l.withKey(Prop.Position, 0, V2(0f, 0f)).withKey(Prop.Position, 10, V2(100f, 0f))
        assertEquals(50f, l.xfAt(5).x, 1e-3f)
        l = l.set(Prop.Position, 5, V2(70f, 0f), autoKey = false)
        assertEquals(3, l.track(Prop.Position)!!.keys.size)
        l = l.withoutTrack(Prop.Position, 5)
        assertNull(l.track(Prop.Position))
        assertEquals(70f, l.x, 1e-3f)
    }

    @Test
    fun timeIsHeldAndLoopsWithoutKeys() {
        val sym = Clip(name = "Mouth", length = 4)
        val layer = Layer(name = "M", type = LayerType.Symbol, symbolId = sym.id)
        val p = Project(name = "P", scene = Clip(name = "S", length = 24, layers = listOf(layer)), symbols = listOf(sym))
        assertEquals(1, p.symbolFrame(layer, 5))
        val keyed = layer.withKey(Prop.Time, 0, V2(2f)).withKey(Prop.Time, 10, V2(0f))
        assertEquals(2, p.symbolFrame(keyed, 9))
        assertEquals(0, p.symbolFrame(keyed, 10))
    }

    @Test
    fun convertToSymbolKeepsPlacement() {
        val art = Stroke(StrokeKind.Rect, rectPoints(100f, 100f, 200f, 300f), 0, 0f, closed = true)
        val layer = Layer(name = "Box", type = LayerType.Drawing, exposures = listOf(Exposure(start = 0, strokes = listOf(art))))
        val scene = Clip(name = "S", length = 10, layers = listOf(layer))
        val p = Project(name = "P", scene = scene)
        val (p2, sym) = Edits.convertToSymbol(p, scene.id, layer.id, 0)!!
        val outer = p2.scene.layers.single()
        assertEquals(LayerType.Symbol, outer.type)
        assertEquals(150f, outer.x, 1e-3f)
        assertEquals(200f, outer.y, 1e-3f)
        val b = p2.clipBounds(p2.scene, 0)!!
        assertEquals(100f, b.left, 1e-3f)
        assertEquals(300f, b.bottom, 1e-3f)
        assertTrue(Edits.wouldNest(p2, sym.id, sym.id))
        assertFalse(Edits.wouldNest(p2, p2.scene.id, sym.id))
    }

    @Test
    fun xfRoundTripsWithAnchor() {
        val xf = Xf(120f, -40f, 33f, 1.5f, 0.75f, 1f, 960f, 540f)
        val p = Pt(12f, -7f)
        val back = xf.toLocal(xf.toStage(p))
        assertEquals(p.x, back.x, 1e-2f)
        assertEquals(p.y, back.y, 1e-2f)
        assertEquals(Pt(120f, -40f), Xf(120f, -40f, 10f, 2f, 2f, 1f, 960f, 540f).toStage(Pt(960f, 540f)))
    }

    @Test
    fun eraserSplitsOpenStrokes() {
        val line = Stroke(StrokeKind.Pencil, List(21) { Pt(it * 10f, 0f) }, 0, 2f)
        val split = eraseStrokes(listOf(line), 100f, 0f, 15f)
        assertNotNull(split)
        assertEquals(2, split!!.size)
    }

    @Test
    fun jsonRoundTrip() {
        val sym = Clip(name = "Mouth", length = 3, markers = listOf(Marker(from = 0, to = 0, comment = "A,E,I")),
            layers = listOf(Layer(name = "D", type = LayerType.Drawing, exposures = listOf(Exposure(start = 1, length = 2, strokes = listOf(dot(3f)))))))
        val layer = Layer(name = "M", type = LayerType.Symbol, symbolId = sym.id, x = 5f)
            .withKey(Prop.Time, 4, V2(2f)).withKey(Prop.Position, 0, V2(1f, 2f))
        val p = Project(name = "P", fps = 30, scene = Clip(name = "Scene 1", length = 48, layers = listOf(layer)), symbols = listOf(sym))
        val back = ProjectJson.read(ProjectJson.write(p))
        assertEquals(p.copy(symbols = emptyList(), scene = p.scene.copy(layers = emptyList())), back.copy(symbols = emptyList(), scene = back.scene.copy(layers = emptyList())))
        assertEquals(p.scene.layers, back.scene.layers)
        assertEquals(p.symbols[0].markers, back.symbols[0].markers)
        val e = back.symbols[0].layers[0].exposures.single()
        assertEquals(1 to 2, e.start to e.length)
        assertEquals(3f, e.strokes.single().points[0].x, 1e-3f)
    }

    @Test
    fun demoProjectIsConsistent() {
        val p = com.spideranim.model.Demo.project()
        val mouth = p.scene.layers.first { it.type == LayerType.Symbol }
        val sym = p.clip(mouth.symbolId)!!
        assertEquals(sym.length, sym.markers.size)
        assertEquals(2, p.symbolFrame(mouth, 9)) // "O" picked at frame 8
        assertNotNull(p.clipBounds(p.scene, 0))
        val back = ProjectJson.read(ProjectJson.write(p))
        assertEquals(p.scene.layers.map { it.tracks }, back.scene.layers.map { it.tracks })
        assertEquals(p.scene.layers[0].exposures.single().strokes.size, back.scene.layers[0].exposures.single().strokes.size)
        assertEquals(sym.markers, back.clip(sym.id)!!.markers)
    }
}
