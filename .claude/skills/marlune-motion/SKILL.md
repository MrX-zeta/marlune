---
name: marlune-motion
description: Motion and animation guidance for Marlune, a native Android music player built in Kotlin + Jetpack Compose (minimalist, ad-free, offline-first). Use this skill WHENEVER you write, review, or edit any UI motion in Marlune: transitions, enter/exit, gestures, the "marea" progress wave, play/pause, toggle states, list entrances, screen and shared-element transitions, haptics, or dynamic color changes. Trigger it even when the request only implies motion (e.g. "build the player screen", "add the now-playing bar", "make the library feel smoother") — do not hand-roll animations for Marlune without consulting this file first. It translates web motion principles (Emil Kowalski) into correct Compose APIs and says exactly where each animation belongs.
---

# Marlune Motion

Marlune is a native Android music player: minimalist, ad-free, offline-first. Kotlin + Jetpack Compose, Material 3. The brand signature is the **marea** — a discreet tide-wave that lives in the progress bar and echoes across the app. Motion here is calm, purposeful, and cheap on battery. When in doubt, animate less.

This skill assumes you may also have Emil Kowalski's skills installed (`emil-design-eng`, `review-animations`). Their *principles* are the source of truth for taste. Their *code* is web (CSS / Framer Motion) and must NOT be copied literally — always translate to the Compose APIs below.

## Non-negotiable principles (Emil, translated to Compose)

1. **Duration ceiling 300ms.** Micro-interactions 100–180ms, standard transitions 200–250ms, large/hero transitions ≤300ms. Never longer for UI.
2. **Easing has direction.** Elements entering *decelerate*; elements leaving *accelerate*. Never `LinearEasing` for UI (reserve linear for the looping marea and determinate progress only).
3. **Do not animate high-frequency actions.** Anything a user triggers dozens of times a day (skip track, tab switches on tap, list scrolling) should feel instant. Motion there reads as lag.
4. **Every animation answers "why".** Feedback, state indication, spatial continuity, or preventing a jarring pop. "It looks cool" is not a reason if the user sees it often.
5. **Animate transform + alpha, never layout.** Use `Modifier.graphicsLayer { }` (scaleX/Y, translationX/Y, alpha, rotationZ) — GPU-composited, no relayout. Never animate `size`, `padding`, `width`, or `height` in hot paths (they trigger measure/layout every frame).
6. **Respect reduced motion.** If `Settings.Global.ANIMATOR_DURATION_SCALE == 0f`, skip or shorten animations to near-instant. Provide a `LocalReducedMotion` composition local and gate non-essential motion on it. This is Android's equivalent of `prefers-reduced-motion`.
7. **Offline-first = battery-first.** Infinite animations (the marea) must stop when not needed: pause when playback is paused, and never run while off-screen. Drive them with a single `rememberInfiniteTransition` or a paused `Animatable`, not per-item timers.

## Easing & spec reference (use these exact tokens)

| Intent | Compose spec |
| --- | --- |
| Enter / appear (decelerate) | `tween(220, easing = LinearOutSlowInEasing)` |
| Exit / disappear (accelerate) | `tween(180, easing = FastOutLinearInEasing)` |
| Move / standard | `tween(240, easing = FastOutSlowInEasing)` |
| Emphasis / press feedback | `spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)` |
| Custom "Emil" curve | `CubicBezierEasing(0.32f, 0.72f, 0f, 1f)` for expressive enters |
| Gesture follow (drag) | `Animatable` + `spring(stiffness = Spring.StiffnessLow)` on release |

Scale rules: enter from `0.96f`, never `0f` (starting from nothing looks cheap). Fade + rise for content appearing (`alpha 0→1`, `translationY 8.dp→0`). Scale for emphasis, translate for navigation.

## The marea (brand signature)

The tide-wave is the one place Marlune animates continuously. Spec:

- Progress bar: unplayed portion is a flat thin line at ~30% alpha (`#3A3357` on track). Played portion is a low-amplitude sine (1–2 dp) tinted with the accent (`#6FD8C6` aqua, or the dynamic accent when available).
- Draw it in a `Canvas` from a single phase float on a `rememberInfiniteTransition` (loop 3–4s, `LinearEasing` — this is the only allowed linear loop).
- **On pause, the amplitude animates to ~0 (calm) via `animateFloatAsState`; on play it returns.** The tide responds to playback state. This is the emotional core — get it right.
- Playhead: a small filled dot at the played/unplayed boundary, with a subtle halo so it reads over the wave.
- Battery: suspend the infinite transition when `!isPlaying` and when the composable leaves composition. Never animate the wave off-screen.

Reuse the *same* marea, at reduced amplitude, in the "Continuar escuchando" card on Home so the signature is a system, not a one-off.

## Where each animation goes (by view)

### Reproductor (Now Playing)
- **Play/pause**: morph the icon (play↔pause) with a 150ms crossfade/scale; button itself scales to `0.94f` on press (`spring`). Do NOT add an open/close animation to frequent controls.
- **Marea**: as above — the hero. Calms on pause, flows on play.
- **Skip prev/next**: instant icon feedback (press scale only). No track-change animation on the buttons themselves — skipping is high-frequency.
- **Album art gestures**: swipe horizontal = change track (art follows finger via `Animatable`, settles with spring, cross-slides to next art). Swipe down = minimize to mini-player (shared-element transition, see transversal).
- **Toggle shuffle/repeat**: animate *color* from muted `#7C7791` to accent `#8E7DF0` over 180ms `tween` when activated, plus a tiny 1.1f scale pop. The state change must be visible through color, not just icon swap.
- **Like (corazón)**: outline→filled with a spring scale pop (`0→1.2→1`) on tap. Classic, allowed — it's low-frequency and it's feedback.
- **Album art enter** (screen open): fade + scale from `0.96f`, 240ms decelerate.

### Inicio ("Tu música")
- **List entrance ("Escuchado hace poco")**: stagger the rows fade+rise, 30–40ms apart, groups of 3–7 only, first item leads. Do this once on first load, not on every scroll.
- **"Continuar escuchando" card**: carries the reduced-amplitude marea (see above).
- **Dynamic color**: when album-art palette resolves, cross-animate the accent (`animateColorAsState`, 240ms) — never hard-cut. Backgrounds/text stay in the neutral scale; only the accent tweens.
- **Grid cards (Me gusta / Descargas / …)**: press scale `0.97f` feedback only. No entrance choreography beyond the shared list stagger.

### Biblioteca
- **Filter chips (Listas/Álbumes/…)**: selected chip animates background + text color 180ms; the selection indicator slides between chips (`animateDpAsState` on offset, or a shared underline). Tab content swap: fast fade (150ms), no slide — switching is frequent.
- **Playlist rows**: entrance stagger on first load; press feedback on tap.
- **"3 puntos" menu**: standard Material menu enter (scale 0.92f + fade from the corner, correct `transformOrigin`). Ensure 48dp touch target.

### Buscar
- **Search field focus**: subtle 150ms — border/elevation tint to accent, no bouncy scale. Typing must feel instant.
- **Recent chips**: horizontal scroll; new chips fade+rise in. Fix any clipped-chip overflow (that's layout, not motion).
- **Category cards**: press scale `0.97f`. On tap-through, use a shared-element/container transform into the category screen if feasible; otherwise fast fade. Keep these in the unified accent/dynamic system, not multicolor.

### Transversal
- **Bottom nav**: tab switch is instant content swap (fade 150ms). Animate only the *active icon* (tiny scale/color), never a full page slide — this is the most frequent action in the app.
- **Mini-player ↔ full player**: shared-element transition on the album art (Compose `SharedTransitionLayout` / `LookaheadScope`). Art travels; surrounding controls fade. This is the signature spatial move — spend your motion budget here.
- **Haptics**: light `HapticFeedbackConstants.CLOCK_TICK` / `performHapticFeedback` on play/pause and track change. Cheap, makes it feel premium. Not on every scroll.
- **Screen transitions**: container transform or shared axis (Material motion). Keep ≤300ms.

## Decision table (does it animate?)

| Element / action | Animate? | Spec |
| --- | --- | --- |
| Play/pause toggle | Yes (feedback) | icon morph 150ms + press spring |
| Skip prev/next | Press only | high-frequency, no transition |
| Marea progress | Yes (signature) | infinite loop; amplitude calms on pause |
| Shuffle/repeat state | Yes (state) | color tween 180ms + small pop |
| Like button | Yes (feedback) | spring scale pop, outline→filled |
| Bottom-nav tab | Minimal | 150ms fade; active icon only |
| List rows (first load) | Yes | stagger fade+rise, 3–7 groups |
| List scroll | No | must feel instant |
| Album art (mini↔full) | Yes (hero) | shared-element transform |
| Dynamic accent change | Yes | animateColorAsState 240ms |
| Search typing | No | instant |

## Boundaries (never do)

- Never animate `size`/`padding`/`width`/`height` in per-frame or per-item paths — use `graphicsLayer`.
- Never use `LinearEasing` for UI motion (only the marea loop and determinate progress).
- Never start scale/opacity from `0f` for enters — start at `0.96f` / small offset.
- Never animate keyboard- or high-frequency-triggered actions (skip, tab tap, scroll).
- Never leave an infinite animation running while paused or off-screen (battery).
- Never hard-cut a dynamic color change — always tween the accent.
- Never copy CSS/Framer Motion code from web animation skills verbatim; translate to the Compose specs above.
- Never let motion override a user's reduced-motion setting.

## Palette tokens (for tinting motion; source of truth for color)

Neutral scaffold: bg `#0A0910`, surface `#15131E`, elevated `#1F1C2B`, divider `#2B2839`.
Text: primary `#F3F1F8`, secondary `#ABA6BC`, tertiary `#7C7791`.
Accent (marca + fallback): base `#8E7DF0`, vivo/play `#A99BFF`, tenue/track `#3A3357`.
Marea (single second hue): aqua `#6FD8C6`.
Rule: dynamic color (Material You / Palette) moves ONLY the accent (play, active toggles, marea). Backgrounds and text always stay in the neutral scale for guaranteed contrast.