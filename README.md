# 3D Minecraft Clone

A voxel sandbox game inspired by Minecraft, written in Java on top of [LWJGL 3](https://www.lwjgl.org/) (GLFW + modern OpenGL). Fully self-contained — no external textures or assets to download; the texture atlas is generated procedurally at startup.

![Screenshot](docs/screenshot.png)

## Features

- **Infinite, persistent voxel world**: chunks (16×128×16) generate on demand from the seed as you explore in any direction with no boundary, streamed in/out around the player and loaded/meshed incrementally so the game never freezes. Memory stays bounded to render distance — only chunks you've actually edited are written to disk, and reloading one restores your edits instead of regenerating pristine terrain, even across a restart.
- **Procedural terrain generation**: layered Perlin/fBm noise for rolling hills and mountains, a second noise channel for rough biomes (plains / desert / snowy peaks), cave carving, trees, and cacti.
- **Fast chunk meshing**: per-block face culling (only exposed faces are emitted) with baked fixed-direction shading (top/side/bottom) and distance fog.
- **First-person player controller**: WASD walking with gravity and AABB-vs-voxel collision resolved per axis, jumping, sprinting, and a no-clip flight mode.
- **Block interaction**: raycast-based block breaking/placing with a 9-slot hotbar, reach limited to 6 blocks, and a wireframe outline on the targeted block.
- **Procedural texture atlas**: grass, dirt, stone, sand, water, wood/planks, leaves, bedrock, snow, gravel and cactus tiles, all generated at runtime.

## Requirements

- JDK 17+
- Maven 3.6+
- A GPU with OpenGL 3.3 support (or a software rasterizer like Mesa `llvmpipe`)

## Building & Running

```bash
mvn compile exec:java
```

or build a runnable fat jar:

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
| `Left Ctrl` | Sprint |
| `F` | Toggle flight mode |
| Left click | Break the targeted block |
| Right click | Place the selected block |
| `1`-`9` / mouse wheel | Select hotbar block |
| `F2` | Save a screenshot to `screenshot.png` |
| `Esc` | Release/recapture the mouse cursor |

## Saving

The world is saved to `saves/world/` next to wherever you run the jar from (override with the `MCCLONE_SAVE_DIR` environment variable). The world seed is written there on first launch and reused on every subsequent launch, so it's the same world each time you start the game. Only chunks you've actually broken/placed blocks in are ever written to disk — untouched terrain is cheap to regenerate deterministically from the seed, which is what keeps disk and memory usage bounded no matter how far you explore. Edits autosave every 60 seconds and on a clean exit.

## Project Layout

```
src/main/java/com/minecraftclone/
├── Main.java                 # Entry point & game loop
├── engine/                   # Window, input, camera, shaders, HUD
│   └── graphics/              # TextureAtlas, Mesh, LineMesh (GL wrappers)
├── world/                    # Chunk, World (streaming/meshing), BlockType
│   └── gen/                   # TerrainGenerator (noise-based world gen)
├── player/                    # Player controller (physics & collision)
└── util/                      # Noise, AABB, Raycaster, ResourceLoader
src/main/resources/shaders/    # GLSL vertex/fragment shaders
```

## Notes & Simplifications

This is a compact, from-scratch clone meant to be readable end-to-end, not a feature-complete recreation. Some deliberate simplifications:

- No text/font rendering — the hotbar/FPS aren't drawn on screen (see console output for the world seed and controls).
- No inventory/crafting/mobs — it's a "creative mode" walk-and-build sandbox.
- Water and leaves are rendered as solid (opaque) blocks rather than alpha-blended, keeping the renderer single-pass.
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

This runs the given number of frames, saves a screenshot, and exits.
