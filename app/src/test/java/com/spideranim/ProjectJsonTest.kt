package com.spideranim

import com.spideranim.model.Drawing
import com.spideranim.model.Ease
import com.spideranim.model.Keyframe
import com.spideranim.model.Layer
import com.spideranim.model.Marker
import com.spideranim.model.Pick
import com.spideranim.model.Project
import com.spideranim.model.ProjectJson
import com.spideranim.model.Pt
import com.spideranim.model.Stroke
import com.spideranim.model.StrokeKind
import com.spideranim.model.Symbol
import com.spideranim.model.Xform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectJsonTest {

    @Test
    fun roundTripsProject() {
        val sym = Symbol(
            name = "Mouth",
            frames = listOf(
                Drawing(strokes = listOf(Stroke(StrokeKind.Brush, listOf(Pt(1f, 2f, 0.5f), Pt(3.25f, 4f)), 0xFF112233.toInt(), 8f)), marker = "A", markerColor = 7),
                Drawing(strokes = listOf(Stroke(StrokeKind.Rect, listOf(Pt(0f, 0f), Pt(9f, 0f), Pt(9f, 9f)), -1, 2f, fill = 0xFFFF0000.toInt(), closed = true))),
            ),
        )
        val layer = Layer(
            name = "Face", symbolId = sym.id, locked = true,
            keyframes = listOf(
                Keyframe(0, Xform(10f, 20f, 1.5f, 0.5f, 45f, 0.8f), ease = Ease.Back),
                Keyframe(12, Xform(), tween = false),
            ),
            picks = listOf(Pick(0, 1), Pick(6, 0)),
            color = 42,
        )
        val p = Project(
            name = "Test", fps = 12, length = 36, symbols = listOf(sym), layers = listOf(layer),
            markers = listOf(Marker(from = 3, to = 5, comment = "Hit", color = 9)),
        )

        val back = ProjectJson.read(ProjectJson.write(p))

        assertEquals("Test", back.name)
        assertEquals(12, back.fps)
        assertEquals(36, back.length)
        assertEquals(p.layers, back.layers)
        assertEquals(p.markers, back.markers)
        assertEquals("A", back.symbols.single().frames[0].marker)
        assertEquals(7, back.symbols.single().frames[0].markerColor)
        assertNull(back.symbols.single().frames[1].marker)
        val s = back.symbols.single()
        assertEquals("Mouth", s.name)
        assertEquals(2, s.frames.size)
        val brush = s.frames[0].strokes.single()
        assertEquals(StrokeKind.Brush, brush.kind)
        assertEquals(0.5f, brush.points[0].p, 1e-3f)
        assertEquals(3.25f, brush.points[1].x, 1e-3f)
        assertNull(brush.fill)
        val rect = s.frames[1].strokes.single()
        assertEquals(0xFFFF0000.toInt(), rect.fill)
        assertEquals(true, rect.closed)
    }

    @Test
    fun migratesVersion1DrawingKeys() {
        val v1 = """{"name":"Old","symbols":[{"id":1,"name":"S","frames":[{"id":2,"strokes":[]},{"id":3,"strokes":[]}]}],
            "layers":[{"id":4,"name":"L","symbol":1,"keys":[
              {"f":0,"x":0,"y":0,"sx":1,"sy":1,"r":0,"a":1,"sf":0},
              {"f":5,"x":0,"y":0,"sx":1,"sy":1,"r":0,"a":1,"sf":1},
              {"f":9,"x":0,"y":0,"sx":1,"sy":1,"r":0,"a":1,"sf":1}]}]}"""
        val l = ProjectJson.read(v1).layers.single()
        assertEquals(listOf(Pick(0, 0), Pick(5, 1)), l.picks)
        assertEquals(0, l.drawingAt(2))
        assertEquals(1, l.drawingAt(9))
    }
}
