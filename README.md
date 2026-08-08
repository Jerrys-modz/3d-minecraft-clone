# 3D Minecraft Clone

A voxel sandbox game inspired by Minecraft, written in Java on top of [LWJGL 3](https://www.lwjgl.org/) (GLFW + modern OpenGL). Fully self-contained — no external textures or assets to download; the texture atlas is generated procedurally at startup.

![Screenshot](docs/screenshot.png)

## Features

- **Chunked, infinite-ish voxel world** streamed around the player (16×128×16 chunks, configurable render distance), loaded/meshed incrementally so the game never freezes while exploring.
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

## Automated smoke testing

The game supports a headless self-test mode (used to verify rendering in CI/sandboxes without a real display):

```bash
MCCLONE_AUTOTEST=1 MCCLONE_AUTOTEST_FRAMES=90 MCCLONE_AUTOTEST_PATH=out.png \
  xvfb-run -a java -jar target/minecraft-clone.jar
```

This runs the given number of frames, saves a screenshot, and exits.
