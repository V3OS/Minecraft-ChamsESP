# Chams ESP

A **Fabric** mod for **Minecraft 1.21.11** that renders players (skin, armor, held items) visibly through walls, plus a skeleton wireframe, hitbox outline, and glow silhouette.

Built on the modern deferred rendering pipeline (`SubmitNodeCollector` / `RenderPipeline`) — no legacy rendering hacks, no mixins into render logic.

---

## Features

| Feature | Description | Default Key |
|---------|-------------|-------------|
| **True Chams** | See players through walls with skin + armor + items | `G` |
| **Skeleton ESP** | Stick-figure wireframe, health-based coloring | `H` |
| **Hitbox ESP** | Bounding box around each player | `J` |
| **Glow** | Full-player color tint silhouette | `K` |
| **Tracers** | Line from your view to each player | `Y` |
| **ESP Master Toggle** | Kill-switch for all ESP features at once | `Right Ctrl` |
| **Settings Menu** | In-game configuration screen | `Right Shift` |

### Coloring options (per-feature in settings)

- **Chroma** — animated HSV rainbow cycle (skeleton / hitbox / glow / tracer independently)
- **Team color** — use the player's scoreboard team color
- **Distance color** — lerp between a near and a far color based on range
- Fallback fixed hex color or (skeleton only) health-based red→green

### Filters

- **Range limit** — only render players within N blocks
- **FOV limit** — only render players within a configurable view-cone angle

All feature keys can be rebound in Minecraft's standard **Options → Controls → Chams ESP** section.

## Settings Menu

Open with `Right Shift`. Lets you configure:

- Individual feature toggles (Skin / Skeleton / Hitbox / Glow)
- Skin-Chams sub-toggles: show armor, show capes
- Skeleton color mode: `Health (red→green)` or `Fixed hex color`
- Per-feature hex color: Skeleton, Glow, Hitbox

Changes are persisted immediately to `.minecraft/config/chams-esp.json`.

## Installation

1. Install **Fabric Loader** ≥ 0.15 for Minecraft 1.21.11
2. Install **Fabric API**
3. Drop the built `chams-esp-X.X.X.jar` into `.minecraft/mods/`

## Build from source

```bash
git clone https://github.com/V3OS/Minecraft-ChamsESP.git
cd Minecraft-ChamsESP
./gradlew build
```

Output `.jar` lands in `build/libs/`.

Requirements: Java 21+, Fabric Loom 1.16+.

## Architecture

```
dev.chams
├── ChamsMod                      Entry point, registers keybinds + tick events
├── config
│   └── ChamsConfig               Gson-persisted config POJO
├── gui
│   └── ChamsConfigScreen         Settings GUI (Screen subclass)
└── render
    ├── ChamsRenderer             WorldRenderEvents.AFTER_ENTITIES hook
    ├── ChamsRenderTypes          Custom RenderType/Pipeline factory
    ├── ChamsRenderTypeTransform  Reflection-based pipeline cloner
    └── ChamsSubmitNodeCollector  Custom SubmitNodeCollector
```

### The core trick

`ChamsSubmitNodeCollector` intercepts Mojang's deferred render pipeline. When the entity dispatcher submits a model, part, item, or custom geometry, the collector:

1. Extracts the `Sampler0` texture from the incoming `RenderType` via reflection on `RenderSetup`.
2. Clones the `RenderPipeline` via `RenderPipeline.Snippet` with all 15 original properties (shader, vertex format, blend, samplers, …) intact — only `DepthTestFunction` is overridden to `NO_DEPTH_TEST` and `cull` is forced to `true`.
3. Re-creates the `RenderSetup` with the same texture bindings, `useLightmap`, `useOverlay`.
4. Redirects the render to the cloned pipeline.

Because shader and vertex format are preserved from the original pipeline, armor (`armor_cutout_no_cull`), block-atlas items (`entity_cutout`), and body (`entity_translucent`) all render correctly — no vertex-format mismatches, no white textures, no black leggings.

`cull=true` is force-enabled to prevent back-face bleeding when depth test is disabled (otherwise the far wall of a cube would overdraw the near one).

## Roadmap

Ordered roughly by priority / impact.

### Coloring

- [x] **Distance color** — lerp between near/far color by player distance, with configurable radius
- [x] **Chroma / Rainbow** — time-animated hue cycle, per-feature toggle (skeleton, hitbox, glow, tracer)
- [x] **Team color** — use scoreboard team color if player is on a team
- [ ] **Per-player override** — friend list with custom color, priority list

### Chams variants

- [ ] **Solid color chams** — single-color silhouette ignoring texture (looks cleaner than tint)
- [ ] **Wireframe chams** — render model edges only
- [ ] **Outline glow** — slightly enlarged back-face shell for rim highlight
- [ ] **Dual-pass visibility** — different color for "behind wall" vs "visible"
- [ ] **Proper cape fix** — two-pass render with per-player depth prime so cape orders correctly behind body

### More ESP types

- [ ] **2D box ESP** — screen-space rectangle around each player
- [x] **Tracers** — line from crosshair (or screen bottom) to each player
- [ ] **Health bar** — 2D (overlay) or 3D (billboard)
- [ ] **Distance / Name text** — floating text above player
- [ ] **Item ESP** — highlight dropped items through walls
- [ ] **Mob ESP** — extend to hostile mobs / entities

### Filters & Quality-of-life

- [x] **Range limit** — only render within N blocks
- [x] **FOV limit** — only render players in front of camera
- [ ] **Friend list** — exclude or color-tag specific usernames
- [ ] **Team-mate filter** — hide same-team players
- [ ] **Config profiles** — save/load named presets (pvp / exploration / spectator)
- [x] **Master toggle** — single key to disable all features at once
- [ ] **Fade in/out** — smooth transitions when toggling features

### Known issues

- **Cape overdraws skin** when `chamsShowCapes = true`. Root cause: with `NO_DEPTH_TEST` + `depthWrite=false`, submit order decides overdraw. Mojang submits cape after body, so cape always wins. Proper fix needs a two-pass render with per-player depth priming — listed in roadmap.
- Glint overlay is skipped entirely. The violet procedural pattern requires a special scroll shader we don't replicate.

## Disclaimer

This mod modifies client-side rendering to reveal player positions that would otherwise be hidden by terrain. **Using it on multiplayer servers that forbid rendering modifications is a violation of those servers' terms of service** and can get you banned.

Intended use: private servers, single-player, LAN sessions, or educational exploration of Minecraft's rendering pipeline.

Use at your own risk.

## License

MIT — see `LICENSE` (if missing, treat as MIT per `fabric.mod.json`).
