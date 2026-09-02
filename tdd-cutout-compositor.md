# TDD — 2D Cutout Room Compositor (POC)

**Status:** draft, quick and dirty
**Platform:** Android only
**Goal:** learning project. Ship-quality is not the bar; "I built the hard parts myself" is.

---

## 1. What it does

1. User picks/takes a photo of an object → app cuts the object out.
2. User picks/takes a photo of a room.
3. Cutout is composited into the room photo. Drag, pinch-scale, rotate.
4. User can mark regions of the room as *foreground*, so the cutout sits behind them.
5. Export the flattened result to gallery.

## 2. Non-goals

Explicitly out of scope for the POC. Listed so they stay out.

- Virtual light source / object relighting
- Contact shadows
- Any 3D — no meshes, no depth-based placement, no AR
- Automatic scale correction (see §7)
- iOS, accounts, cloud sync, sharing, a backend of any kind
- Multiple cutouts in one scene (single object only)
- Undo history beyond "reset"

## 3. Stack

| Concern | Choice | Why |
|---|---|---|
| Language | Kotlin | Only sane default |
| UI | Jetpack Compose | Gesture APIs are better than View-land |
| Segmentation (object) | ML Kit Subject Segmentation | On-device, free, ~10 LOC |
| Segmentation (foreground) | Manual brush → MobileSAM later | Phase it, see §5 |
| Compositing | Compose `Canvas` + `drawImage` | No engine needed for 3 layers |
| Persistence | None in phase 1; Room if a saved-cutouts library appears | Don't build it yet |
| Dev env | Android Studio native on Windows, project on NTFS. **Not WSL.** | Toolchain expects it |
| Testing | Physical Android device over USB | Emulator camera is a rendered fake |

## 4. Architecture

Three bitmaps and a transform. That's the whole app.

```
Layer 2 (top)     roomForeground   = room ⊙ fgMask
Layer 1 (middle)  cutout           = objectPhoto ⊙ subjectMask, transformed
Layer 0 (bottom)  roomBackground   = full room photo
```

Draw order is 0 → 1 → 2. Occlusion is a consequence of layering, not a
computation. Two occluders or six is the same single `fgMask` — no
per-object logic anywhere.

### State

```kotlin
data class SceneState(
    val room: Bitmap,
    val fgMask: Bitmap?,        // alpha-only, same dims as room
    val cutout: Bitmap?,        // pre-masked, alpha-cut, cropped to bounds
    val offset: Offset,
    val scale: Float,
    val rotation: Float,
)
```

Single immutable state object in a ViewModel. Transform lives here, not in
the composable — gesture handlers reduce into it.

### Rendering

`Canvas` composable. Room background drawn 1:1 into a `fit`-scaled dest rect.
Cutout drawn inside `withTransform { translate/rotate/scale }` about the
cutout's own center. Foreground drawn last, same rect as background.

Export = same draw calls against an offscreen `Bitmap`-backed Canvas at the
room photo's native resolution, then `MediaStore` insert. **Screen and export
must share one draw function** or they will drift apart within a week.

## 5. Phasing

Each phase runs on a real device before the next starts. Nothing is a
prerequisite for something that doesn't already work.

**Phase 0 — "it moves" (a Saturday)**
Hardcoded chair PNG in `res/drawable`, hardcoded room JPEG. Drag, pinch,
rotate. No pickers, no ML, no export. This makes the transform math real
while it's still cheap to get wrong.

**Phase 1 — real inputs (a few evenings)**
Photo picker + CameraX for both images. ML Kit subject segmentation on the
object photo. Feather the mask alpha (§6). Export to gallery.

**Phase 2 — occlusion, manual (a week)**
Brush tool over the room photo paints `fgMask`. Radius slider, erase mode,
clear-all. Ugly but complete: the three-layer pipeline is now real and
everything after this is a swap-in.

**Phase 3 — occlusion, assisted (two-ish weeks, optional)**
Replace the brush with MobileSAM/EdgeSAM on-device. Tap an object → mask
for that object → OR it into `fgMask`. Keep the brush as a repair tool;
it will still be needed.

## 6. Known sharp edges

- **Halos.** ML Kit masks are hard-aliased. A raw cutout has a 1–2px fringe
  of the original background and looks like a Paint job. Gaussian-blur the
  alpha channel ~2px before compositing. Small fix, disproportionate effect.
- **Gesture feel.** `detectTransformGestures` works in an afternoon and
  feels wrong for a week. Rotate about the gesture centroid, not the bitmap
  origin. Clamp scale. Expect to keep tuning after it "works."
- **Bitmap memory.** Two 12MP photos plus a mask plus an export canvas will
  OOM on a mid-range device. Downsample to ~2048px longest edge on load;
  keep full-res only for the final export pass, and do that off the main
  thread.
- **Coordinate spaces.** Screen px, room-bitmap px, and cutout-local px are
  three different things and the bugs where they get confused are miserable
  to read. Write the screen↔bitmap conversion once, use it everywhere.
- **Orientation.** Camera JPEGs carry EXIF rotation. Read it or everything
  is sideways.

## 7. The thing this POC does not solve

Scale. The user can size the cutout to anything, and there's no ground truth
about how big the object really is. This is fine right up until phase 2 —
once the object sits *behind* the coffee table, the viewer can read its
depth position, and a wrong-sized object now visibly contradicts itself.

Occlusion makes the scale problem more obvious, not less. Accepted for the
POC; manual resize is the answer. If it becomes intolerable, the smallest
real fix is a reference-object tap ("tap a doorway / a power outlet") rather
than depth estimation.

## 8. Done when

A photo of a real chair, taken on the phone, appears in a photo of the real
living room, partially behind the real coffee table, at a size chosen by
hand, and the exported PNG is not obviously fake at a glance.
