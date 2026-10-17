package com.spideranim.model

/**
 * First-launch project: a face whose Mouth symbol holds one drawing per mouth shape, each
 * labelled by a marker. The scene keys the Mouth's Time from those markers, the way lip sync
 * is done in ToonSquid.
 */
object Demo {
    private const val INK = 0xFF2B1A14.toInt()
    private const val SKIN = 0xFFF2BFA0.toInt()
    private const val LIP = 0xFF8C2F39.toInt()
    private const val TOOTH = 0xFFFFFFFF.toInt()

    private fun oval(x0: Float, y0: Float, x1: Float, y1: Float, fill: Int?, w: Float = 6f, color: Int = INK) =
        Stroke(StrokeKind.Oval, ovalPoints(x0, y0, x1, y1), color, w, fill, true)

    fun project(): Project {
        val shapes = listOf(
            "B,M,P" to listOf(Stroke(StrokeKind.Line, listOf(Pt(-70f, 0f), Pt(70f, 0f)), INK, 8f)),
            "A,E,I" to listOf(oval(-80f, -30f, 80f, 40f, LIP), Stroke(StrokeKind.Rect, rectPoints(-62f, -24f, 62f, -6f), TOOTH, 1f, TOOTH, true)),
            "O" to listOf(oval(-38f, -46f, 38f, 46f, LIP)),
            "E" to listOf(oval(-86f, -18f, 86f, 22f, LIP), Stroke(StrokeKind.Rect, rectPoints(-70f, -14f, 70f, 0f), TOOTH, 1f, TOOTH, true)),
            "U" to listOf(oval(-26f, -22f, 26f, 22f, LIP)),
        )
        val mouthLayer = Layer(
            name = "Drawing 1",
            type = LayerType.Drawing,
            exposures = shapes.mapIndexed { i, (_, s) -> Exposure(start = i, strokes = s) },
        )
        val mouth = Clip(
            name = "Mouth",
            length = shapes.size,
            layers = listOf(mouthLayer),
            markers = shapes.mapIndexed { i, (name, _) -> Marker(from = i, to = i, comment = name, color = MarkerColors[i % MarkerColors.size]) },
        )
        val head = Layer(
            name = "Head",
            type = LayerType.Drawing,
            x = 960f, y = 540f, ax = 960f, ay = 540f,
            exposures = listOf(Exposure(start = 0, length = 48, strokes = listOf(
                oval(700f, 160f, 1220f, 900f, SKIN),
                oval(820f, 400f, 880f, 480f, INK),
                oval(1040f, 400f, 1100f, 480f, INK),
                Stroke(StrokeKind.Ink, listOf(Pt(800f, 360f, 0.6f), Pt(840f, 340f), Pt(890f, 350f, 0.7f)), INK, 14f),
                Stroke(StrokeKind.Ink, listOf(Pt(1030f, 350f, 0.7f), Pt(1080f, 340f), Pt(1120f, 360f, 0.6f)), INK, 14f),
            ))),
        )
        // Pick a mouth shape every few frames, as if following dialogue.
        val script = listOf(0 to 0, 4 to 1, 8 to 2, 12 to 3, 16 to 0, 20 to 4, 26 to 1, 32 to 2, 38 to 0)
        var mouthOnStage = Layer(name = "Mouth", type = LayerType.Symbol, symbolId = mouth.id, x = 960f, y = 700f)
        for ((f, i) in script) mouthOnStage = mouthOnStage.withKey(Prop.Time, f, V2(i.toFloat()))
        val scene = Clip(name = "Scene 1", length = 48, layers = listOf(head, mouthOnStage))
        return Project(name = "Lip Sync Demo", scene = scene, symbols = listOf(mouth))
    }
}
