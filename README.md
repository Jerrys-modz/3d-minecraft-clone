# 3D Minecraft Clone

A survival voxel game written in Java on top of [LWJGL 3](https://www.lwjgl.org/) (GLFW + modern OpenGL) — closer to a "survival craft" than to vanilla Minecraft's creative-friendly loop: you gather everything by hand, hunger and lava and falling and drowning can all kill you, and the day/night cycle actually matters. Fully self-contained — no external textures or assets to download; the texture atlas is generated procedurally at startup.

![Screenshot](docs/screenshot.png)

## Features

- **Survival stats**: health, hunger and stamina, all visible as HUD bars. Falling too far, standing in lava, staying underwater too long past your breath limit, and letting hunger hit zero all deal real damage; health quietly regenerates on its own once you're well-fed. Sprinting costs stamina (and a little extra hunger) and locks out until it recovers. Death clears your inventory and respawns you at world spawn.
- **Foraging**: apples occasionally drop from broken leaves, and rare berry bushes dotted across grassy biomes yield berries when harvested. Select a food item and right-click to eat it and restore hunger — the same button placing blocks uses, since the game treats "use the selected item" as one contextual action.
- **A day/night cycle**: a 10-minute real-time cycle dims the world and shifts the sky toward dark blue at night, driven by a global ambient-brightness uniform in the chunk shader — there's no dynamic light propagation, but darkness is a real, visible signal, not just cosmetic.
- **Crafting**: a small fixed recipe table (logs → planks, sand → glass) - press `C` with the output selected in your hotbar and, if you have the input, it's crafted directly into your inventory. No crafting-grid UI needed.
- **Infinite, persistent voxel world**: chunks (16×128×16) generate on demand from the seed as you explore in any direction with no boundary, streamed in/out around the player and loaded/meshed incrementally so the game never freezes. Memory stays bounded to render distance — only chunks you've actually edited are written to disk, and reloading one restores your edits instead of regenerating pristine terrain, even across a restart.
- **Procedural terrain generation**: layered Perlin/fBm noise for rolling hills and mountains, a second noise channel for rough biomes (desert / plains / forest / snowy peaks), winding rivers carved from a dedicated noise channel, 3D-noise cave systems with lava pooling in the deepest pockets, and four depth-gated ore veins (coal, iron, gold, diamond - rarer ones deeper and sparser, mirroring vanilla Minecraft's progression).
- **Biome-varied vegetation**: dense oak forests where it's wet, sparser oak on plains, conical pine trees at higher/colder elevations, cacti in deserts, and tall grass/flowers/berry bushes scattered across grassy ground.
- **Fast chunk meshing**: per-block face culling (only exposed faces are emitted) with baked fixed-direction shading (top/side/bottom), distance fog, and cross-shaped "billboard" geometry for non-cube decoration (grass/flowers/bushes) with alpha-cutout transparency.
- **First-person player controller**: WASD walking with gravity and AABB-vs-voxel collision resolved per axis, jumping, stamina-gated sprinting, and a no-clip flight mode.
- **Block interaction**: raycast-based block breaking/placing (reach limited to 6 blocks) with a wireframe outline on the targeted block, gated by a real inventory - breaking a block adds it to your count, placing spends one, and bedrock is unbreakable. Starts empty, so you gather before you build.
- **On-screen HUD**: a hotbar (21 slots) with block icons sampled straight from the game's own texture atlas, live inventory counts drawn with a tiny procedural pixel font (no external font/text-rendering library), a highlight border on the selected slot, and health/hunger/stamina bars above it.
- **Procedural texture atlas**: grass, dirt, stone, sand, water, wood/planks, leaves, bedrock, snow, gravel, cactus, lava, glass, four ores, apples/berries/berry bushes, alpha-cutout grass/flower tiles, and a 0-9 digit font, all generated at runtime.

## Requirements

- JDK 17+ (only to run a pre-built jar; also need Maven 3.6+ to build from source)
- A GPU with OpenGL 3.3 support (or a software rasterizer like Mesa `llvmpipe`)

## Getting a jar

Every [GitHub Release](../../releases) has `minecraft-clone.jar` attached as a build artifact - just download it and run:

```bash
java -jar minecraft-clone.jar
```

That jar is built automatically by [`.github/workflows/release.yml`](.github/workflows/release.yml) whenever a release is published, so there's no manual packaging step to keep in sync.

## Building & Running from source

```bash
mvn compile exec:java
```

or build a runnable fat jar yourself:

```bash
mvn package
java -jar target/minecraft-clone.jar
```

The packaged jar bundles LWJGL natives for Linux, Windows and macOS (Intel + Apple Silicon), so the one build works on any of them out of the box — just run `java -jar target/minecraft-clone.jar` (JDK 17+ required) wherever you copy it.

## Controls

| Input | Action |
|---|---|
| `W A S D` | Move |
| Mouse | Look around |
| `Space` | Jump (or fly up, in flight mode) |
| `Left Shift` | Fly down (flight mode only) |
| `Left Ctrl` | Sprint (costs stamina) |
| `F` | Toggle flight mode |
| Left click | Break the targeted block |
| Right click | Place the selected block - or eat it, if it's food |
| `C` | Craft the selected item from its recipe, if you have the ingredients |
| `1`-`9` / mouse wheel | Select hotbar block |
| `F2` | Save a screenshot to `screenshot.png` |
| `Esc` | Release/recapture the mouse cursor |

You start with an empty inventory - break blocks to collect them (they show up with a count on the hotbar) before you can place them elsewhere. Bedrock can't be broken.

## Survival

- **Health** (red bar) drops from fall damage (landing too hard - a couple of blocks is safe), standing in lava, staying underwater past your ~6-second breath limit, or starving. It regenerates slowly on its own whenever hunger is above half and you aren't actively taking damage that same instant. At 0 you die: your inventory is cleared and you respawn at world spawn with everything reset.
- **Hunger** (orange bar) drains slowly over time (faster while sprinting) and doesn't regenerate on its own - eat food to refill it. Forage apples (a chance from breaking leaves) and berries (harvest a berry bush, found rarely in plains/forest). Select the food in your hotbar and right-click to eat it.
- **Stamina** (yellow bar) drains while sprinting and regenerates while you aren't. Run out and you're locked to walking speed until it recovers.
- **Day/night**: a full cycle is 10 real-time minutes. Night dims the world noticeably - useful to know when it's time to hole up.

## Saving

The world is saved to `saves/world/` next to wherever you run the jar from (override with the `MCCLONE_SAVE_DIR` environment variable). The world seed is written there on first launch and reused on every subsequent launch, so it's the same world each time you start the game. Only chunks you've actually broken/placed blocks in are ever written to disk — untouched terrain is cheap to regenerate deterministically from the seed, which is what keeps disk and memory usage bounded no matter how far you explore. Edits autosave every 60 seconds and on a clean exit.

## Project Layout

```
src/main/java/com/minecraftclone/
├── Main.java                 # Entry point & game loop
├── engine/                   # Window, input, camera, shaders, HUD, DayNightCycle
│   └── graphics/              # TextureAtlas, Mesh, LineMesh, IconMesh (GL wrappers)
├── world/                    # Chunk, World (streaming/meshing), BlockType
│   └── gen/                   # TerrainGenerator (noise-based world gen)
├── player/                    # Player controller, PlayerStats, Inventory, Crafting
└── util/                      # Noise, AABB, Raycaster, ResourceLoader
src/main/resources/shaders/    # GLSL vertex/fragment shaders (chunk, line, hud)
```

## Notes & Simplifications

This is a compact, from-scratch clone meant to be readable end-to-end, not a feature-complete recreation. Some deliberate simplifications:

- Text rendering is limited to the digits 0-9 (inventory counts) via a hand-drawn pixel font baked into the same texture atlas as the blocks - there's no general text renderer, so FPS/debug info still only goes to the console, and there's no on-screen death/damage messaging beyond the bars themselves (check the console for a death notice).
- Crafting is a small fixed table (2 recipes right now), not a grid - there's no way to combine arbitrary items, and no tools/weapons or their durability yet.
- No mobs (hostile or otherwise) - night is darker and a real signal, but nothing actually comes looking for you yet. No sleeping/beds to skip it either.
- Water and leaves are rendered as solid (opaque) blocks rather than alpha-blended, keeping the renderer single-pass. (Grass/flowers/berry bushes are the exception - they're cross-shaped and alpha-cutout, not alpha-blended. Lava is opaque too, despite being a hazard.)
- Day/night affects a single global ambient-brightness multiplier, not real light propagation - there's no torch/light-source placement, and caves are exactly as dark as the surface at the same time of day (no separate "underground is always dark" rule).
- Chunk meshing runs on the main thread with a per-frame budget, so there's no multithreading complexity, at the cost of a brief pause when flying very fast into unloaded terrain.
- World height is capped (128 blocks, same idea as vanilla Minecraft's build limit) — it's the horizontal extent that's unbounded.
- Chunk vertex positions are baked in absolute world-space `float`s rather than being camera-relative, so precision (and therefore visual stability) very gradually degrades if you travel extremely far (hundreds of thousands of blocks) from spawn — not something you'll hit in normal play.
- A crash (as opposed to closing the game normally) can lose up to the last 60 seconds of edits, since that's the autosave interval.

## Automated smoke testing

The game supports a headless self-test mode (used to verify rendering in CI/sandboxes without a real display):

```bash
MCCLONE_AUTOTEST=1 MCCLONE_AUTOTEST_FRAMES=90 MCCLONE_AUTOTEST_PATH=out.png \
  xvfb-run -a java -jar target/minecraft-clone.jar
```

Add `MCCLONE_AUTOTEST_TIME=0.5` (0=midnight, 0.5=noon, etc.) to pin the day/night cycle to a specific moment for a reproducible screenshot instead of whatever the default start time renders.

This runs the given number of frames, saves a screenshot, and exits.
