# SpiderAnim

A 2D animation app for Android phones and tablets, modelled on ToonSquid's layout and
workflow. Native Kotlin + Jetpack Compose. Everything is vector.

## Layout

- **Sidebar** (right edge): colour, the six tools, size and opacity bars, and the inspector
  toggle. Tap the active tool again to open its options.
- **Inspector**: switches between **Layer** (transform, easing curve, track colour, symbol)
  and **Drawing** (the symbol's drawings and their markers).
- **Frame picker**: a strip of chips above the timeline, one per drawing of the selected layer,
  each labelled with its marker. Tap one to show that drawing from the playhead on. Long-press
  a chip to name its marker, change its colour, duplicate it or delete it.
- **Timeline**: a playback bar on top, timeline markers above the frame numbers, and one row per
  layer. Each row shows keyframe diamonds, and below them the frame picks as coloured blocks
  named by marker. Drag a diamond or a block to retime it. The chevron collapses the timeline.

On phones the sidebar floats over the stage and the inspector opens as a bottom sheet.

## Concepts

- **Symbol**: a reusable drawing made of one or more drawings. Every layer shows a symbol.
- **Marker on a drawing**: a name such as "A", "O" or "Blink". The frame picker picks drawings
  by these names, which makes lip sync a matter of scrubbing and tapping.
- **Frame pick**: a key on the layer's frame track that says which drawing shows from that frame on.
- **Keyframe**: position, scale, rotation and opacity. Moving something between keys adds a key.
- **Timeline marker**: a comment, a colour and a From/To frame range, shown above the frame numbers.

## Tools

Transform, Brush (Ink or Pencil, stylus pressure, smoothing), Eraser (vector, cuts strokes),
Fill (closes small gaps), Shape (line, rectangle, ellipse, polygon) and Eyedropper.

Gestures: two fingers pan and pinch-zoom; a two-finger tap undoes; a three-finger tap redoes.

## Saving and export

The project autosaves to app storage. **Export GIF** renders every frame and opens the share sheet.

## Building

Needs JDK 17 and the Android SDK (API 35).

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # model, JSON and GIF encoder tests
```

CI builds debug and release APKs on every push. They are attached to the workflow run as the
`SpiderAnim-apk` artifact.

## Code map

- `model/`: immutable project model (symbols, drawings, layers, keyframes, eases), geometry and JSON.
- `editor/EditorState.kt`: the ViewModel. Undo/redo, tools, every edit operation, autosave, export.
- `ui/Stage.kt`: stage rendering, gestures and tool behaviour.
- `ui/Timeline.kt`, `ui/Panels.kt`, `ui/Tools.kt`, `ui/EditorScreen.kt`: the interface.
- `ui/icons/Ic.kt`: the line icon set, drawn on a 24-unit grid.
- `export/`: GIF89a writer, LZW encoder and palette quantiser.
