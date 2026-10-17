# SpiderAnim

A 2D animation app for Android phones and tablets that combines Toonsquid's calm, touch-first
feel with Adobe Animate's tools and vocabulary. Native Kotlin + Jetpack Compose. Everything
is vector.

## Concepts

| SpiderAnim | Like in Animate | Like in Toonsquid |
| --- | --- | --- |
| **Symbol**: a reusable drawing with one or more drawings inside | Graphic symbol | Object |
| **Symbols** panel: create, place, swap, duplicate, rename | Library | Objects list |
| **Symbol Picker**: choose which of a symbol's drawings shows on each keyframe | Frame Picker | Frame picker |
| **Layer**: one symbol instance with its own keyframes | Layer + instance | Object track |
| **Keyframe**: position, scale, rotation, opacity and the picked drawing | Classic tween keyframe | Keyframe |
| **Curves**: cubic-bezier ease on each tween, with presets | Ease / Motion Editor | Curves |

Edits to a symbol show up in every instance of it. Moving or transforming a layer between keys
adds a key on the current frame (auto-key).

## Tools

Grouped like Animate's Tools panel. The letter is the Animate shortcut each one mirrors.

- **Selection (V)**: tap to select, drag to move.
- **Free Transform (Q)**: corner handles scale, side handles stretch, the top knob rotates.
- **Brush (B)**: pressure-sensitive with a stylus, tapered ends, adjustable smoothing.
- **Pencil (Y)**: constant-width line.
- **Pen (P)**: tap to place points; tap the first point to close the shape, or the last point to finish.
- **Line (N)**, **Rectangle (R)**, **Oval (O)**: with optional fill.
- **Paint Bucket (K)**: fills closed shapes, and strokes whose ends nearly meet.
- **Eraser (E)**: vector eraser that cuts strokes where it touches them.
- **Eyedropper (I)**: picks up stroke or fill colour.
- **Hand (H)**, **Zoom (Z)**.

Gestures: two fingers pan and pinch-zoom; a two-finger tap undoes; a three-finger tap redoes.

## Timeline

Play/pause, previous/next keyframe, onion skin, loop, fps and length. Tap a cell to jump there,
drag the ruler to scrub, drag a keyframe dot to retime it. Tween spans are tinted. A white pip on a
keyframe means the Symbol Picker changes drawing there. The selected layer's motion path is drawn
on stage.

## Layouts

- **Tablet** (≥ 720 × 520 dp): floating tool rail, a right-hand panel with tabs (Properties,
  Symbols, Picker, Curves) and a full-width timeline.
- **Phone**: a scrollable tool strip, a compact timeline that collapses, and the panels in a
  bottom sheet.

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
