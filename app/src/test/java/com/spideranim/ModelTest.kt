package com.spideranim

import com.spideranim.model.Ease
import com.spideranim.model.Keyframe
import com.spideranim.model.Layer
import com.spideranim.model.Pick
import com.spideranim.model.Pt
import com.spideranim.model.Stroke
import com.spideranim.model.StrokeKind
import com.spideranim.model.Xform
import com.spideranim.model.eraseStrokes
import com.spideranim.model.insidePolygon
import com.spideranim.model.rectPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {

    @Test
    fun linearEaseIsIdentity() {
        for (i in 0..10) assertEquals(i / 10f, Ease.Linear.apply(i / 10f), 1e-4f)
    }

    @Test
    fun easesHitEndpointsAndBend() {
        for ((_, e) in Ease.presets) {
            assertEquals(0f, e.apply(0f), 1e-4f)
            assertEquals(1f, e.apply(1f), 1e-4f)
        }
        assertTrue(Ease.In.apply(0.5f) < 0.5f)
        assertTrue(Ease.Out.apply(0.5f) > 0.5f)
        assertEquals(0.5f, Ease.InOut.apply(0.5f), 1e-3f)
        // Back overshoots past 1 before settling.
        assertTrue((1..99).any { Ease.Back.apply(it / 100f) > 1f })
    }

    @Test
    fun poseInterpolatesAndHoldsPickedDrawing() {
        val layer = Layer(
            name = "L", symbolId = 1,
            keyframes = listOf(Keyframe(0, Xform(0f, 0f)), Keyframe(10, Xform(100f, 50f))),
            picks = listOf(Pick(0, 2), Pick(10, 0)),
        )
        assertEquals(50f, layer.poseAt(5).xf.x, 1e-3f)
        assertEquals(25f, layer.poseAt(5).xf.y, 1e-3f)
        assertEquals(2, layer.poseAt(9).symbolFrame)
        assertEquals(0, layer.poseAt(10).symbolFrame)
        assertEquals(100f, layer.poseAt(30).xf.x, 1e-3f)

        val held = layer.withKey(0) { it.copy(tween = false) }
        assertEquals(0f, held.poseAt(9).xf.x, 1e-3f)
    }

    @Test
    fun picksReplaceAtSameFrameAndStaySorted() {
        val layer = Layer(name = "L", symbolId = 1, keyframes = listOf(Keyframe(0, Xform())))
        assertEquals(0, layer.drawingAt(5))
        val l2 = layer.withPick(8, 3).withPick(2, 1).withPick(8, 4)
        assertEquals(listOf(Pick(2, 1), Pick(8, 4)), l2.picks)
        assertEquals(1, l2.drawingAt(0)) // before the first pick, the first pick's drawing shows
        assertEquals(1, l2.drawingAt(7))
        assertEquals(4, l2.drawingAt(30))
    }

    @Test
    fun withKeyCreatesKeyFromInterpolatedPose() {
        val layer = Layer(
            name = "L", symbolId = 1,
            keyframes = listOf(Keyframe(0, Xform(0f, 0f)), Keyframe(10, Xform(100f, 0f))),
        )
        val keyed = layer.withKey(4) { it }
        assertEquals(3, keyed.keyframes.size)
        assertEquals(listOf(0, 4, 10), keyed.keyframes.map { it.frame })
        assertEquals(40f, keyed.keyAt(4)!!.xf.x, 1e-3f)
        // Motion is unchanged by inserting a key on the path.
        assertEquals(layer.poseAt(7).xf.x, keyed.poseAt(7).xf.x, 1e-3f)
    }

    @Test
    fun xformRoundTrips() {
        val xf = Xform(120f, -40f, 1.5f, 0.75f, 33f)
        val p = Pt(12f, -7f)
        val back = xf.toLocal(xf.toStage(p))
        assertEquals(p.x, back.x, 1e-3f)
        assertEquals(p.y, back.y, 1e-3f)
    }

    @Test
    fun eraserSplitsOpenStrokesAndRemovesShapes() {
        val line = Stroke(StrokeKind.Pencil, List(21) { Pt(it * 10f, 0f) }, 0, 2f)
        val split = eraseStrokes(listOf(line), 100f, 0f, 15f)
        assertNotNull(split)
        assertEquals(2, split!!.size)
        assertTrue(split.all { s -> s.points.none { kotlin.math.abs(it.x - 100f) < 15f } })

        val box = Stroke(StrokeKind.Rect, rectPoints(0f, 0f, 50f, 50f), 0, 2f, fill = 1, closed = true)
        assertEquals(0, eraseStrokes(listOf(box), 25f, 25f, 5f)!!.size)
        assertNull(eraseStrokes(listOf(box), 200f, 200f, 5f))
    }

    @Test
    fun polygonContainment() {
        val sq = rectPoints(0f, 0f, 10f, 10f)
        assertTrue(insidePolygon(sq, 5f, 5f))
        assertFalse(insidePolygon(sq, 15f, 5f))
    }
}
