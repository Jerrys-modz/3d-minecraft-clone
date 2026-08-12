# 3D Minecraft Clone

A survival voxel game written in Java on top of [LWJGL 3](https://www.lwjgl.org/) (GLFW + modern OpenGL) — closer to a "survival craft" than to vanilla Minecraft's creative-friendly loop: you gather everything by hand, hunger and lava and falling and drowning can all kill you, and the day/night cycle actually matters. Fully self-contained — no assets to download at runtime. Block textures are generated procedurally into one shared atlas at startup (so chunk meshing can batch many blocks into a single draw call); each inventory-only item (tools, food) instead has its own individual 16×16 PNG committed in the repo under `src/main/resources/items/`, produced once by a small offline generator tool - see [Textures](#textures) below.

![Screenshot](docs/screenshot.png)

## Features

- **Survival stats**: health, hunger and stamina, all visible as HUD bars. Falling too far, standing in lava, staying underwater too long past your breath limit, and letting hunger hit zero all deal real damage; health quietly regenerates on its own once you're well-fed. Sprinting costs stamina (and a little extra hunger) and locks out until it recovers. Death clears your inventory and respawns you at world spawn.
- **Tools & mining**: breaking is hold-to-break, not instant - how long depends on the block's hardness and whatever tool you're holding. Wood/stone/iron/diamond pickaxes, axes and swords (crafted from raw materials + sticks) each mine faster than the tier below at their specialty, and some blocks (ores, in increasing order of rarity) flatly require a minimum tool tier - you can't punch out a diamond. Tools wear out with use (higher tiers last much longer) and show a HUD wear bar once damaged. Swords are quickest through leaves and cacti; combat is on the roadmap once there's something to fight. The block outline glows redder and thickens as you get closer to breaking it.
- **Foraging**: apples occasionally drop from broken leaves, and rare berry bushes dotted across grassy biomes yield berries when harvested. Select a food item and right-click to eat it and restore hunger — the same button placing blocks uses, since the game treats "use the selected item" as one contextual action.
- **A day/night cycle**: a 10-minute real-time cycle dims the world and shifts the sky toward dark blue at night, driven by a global ambient-brightness uniform in the chunk shader — darkness is a real, visible signal, not just cosmetic.
- **Torches**: a real (if simplified) local light source, craftable from a stick + coal. Placed like any other block, a torch keeps the blocks around it lit at a brightness floor that doesn't dim at night, fading out smoothly with distance - see [Notes & Simplifications](#notes--simplifications) for how this differs from true light propagation.
- **Crafting**: a small fixed recipe table (logs → planks → sticks, sand → glass, planks/stone/ore + sticks → tools, stick + coal → torches) - press `C` with the output selected in your hotbar and, if you have the ingredients, it's crafted directly into your inventory. No crafting-grid UI needed.
- **Infinite, persistent voxel world**: chunks (16×128×16) generate on demand from the seed as you explore in any direction with no boundary, streamed in/out around the player and loaded/meshed incrementally so the game never freezes. Memory stays bounded to render distance — only chunks you've actually edited are written to disk, and reloading one restores your edits instead of regenerating pristine terrain, even across a restart.
- **Procedural terrain generation**: layered Perlin/fBm noise for rolling hills and mountains, a second noise channel for rough biomes (desert / plains / forest / snowy peaks), winding rivers carved from a dedicated noise channel, 3D-noise cave systems with lava pooling in the deepest pockets, and four depth-gated ore veins (coal, iron, gold, diamond - rarer ones deeper and sparser, mirroring vanilla Minecraft's progression).
- **Biome-varied vegetation**: dense oak forests where it's wet, sparser oak on plains, conical pine trees at higher/colder elevations, cacti in deserts, and tall grass/flowers/berry bushes scattered across grassy ground.
- **Fast chunk meshing**: per-block face culling (only exposed faces are emitted) with baked fixed-direction shading (top/side/bottom), distance fog, and cross-shaped "billboard" geometry for non-cube decoration (grass/flowers/bushes) with alpha-cutout transparency.
- **First-person player controller**: WASD walking with gravity and AABB-vs-voxel collision resolved per axis, jumping, stamina-gated sprinting, and a no-clip flight mode.
- **Block interaction**: raycast-based block breaking/placing (reach limited to 6 blocks) with a wireframe outline on the targeted block, gated by a real inventory - breaking a block adds it to your count, placing spends one, and bedrock is unbreakable. Starts empty, so you gather before you build.
- **On-screen HUD**: a hotbar (35 slots) with block icons batched from the game's shared texture atlas and item icons (food/tools) drawn from their own individual PNGs, live inventory counts drawn with a tiny procedural pixel font (its own small atlas, no external font/text-rendering library), a highlight border on the selected slot, health/hunger/stamina bars above it, transient on-screen messages (death, crafting, tool breakage), and an `F3`-toggled debug overlay (FPS, position, selected item).
- **Procedural block texture atlas**: grass, dirt, stone, sand, water, wood/planks, leaves, bedrock, snow, gravel, cactus, lava, glass, four ores, berry bushes, torches, and alpha-cutout grass/flower tiles, all generated at runtime into one shared sheet.

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
| Hold Left click | Mine the targeted block (speed/possibility depends on your tool) |
| Right click | Place the selected block - or eat it, if it's food |
| `C` | Craft the selected item from its recipe, if you have the ingredients |
| `1`-`9` / mouse wheel | Select hotbar block |
| `F2` | Save a screenshot to `screenshot.png` |
| `F3` | Toggle the debug overlay (FPS / position / selected item) |
| `Esc` | Release/recapture the mouse cursor |

You start with an empty inventory - break blocks to collect them (they show up with a count on the hotbar) before you can place them elsewhere. Bedrock can't be broken.

## Survival

- **Health** (red bar) drops from fall damage (landing too hard - a couple of blocks is safe), standing in lava, staying underwater past your ~6-second breath limit, or starving. It regenerates slowly on its own whenever hunger is above half and you aren't actively taking damage that same instant. At 0 you die: your inventory is cleared and you respawn at world spawn with everything reset.
- **Hunger** (orange bar) drains slowly over time (faster while sprinting) and doesn't regenerate on its own - eat food to refill it. Forage apples (a chance from breaking leaves) and berries (harvest a berry bush, found rarely in plains/forest). Select the food in your hotbar and right-click to eat it.
- **Stamina** (yellow bar) drains while sprinting and regenerates while you aren't. Run out and you're locked to walking speed until it recovers.
- **Day/night**: a full cycle is 10 real-time minutes. Night dims the world noticeably - useful to know when it's time to hole up. A **torch** (craft with `C`: 1 stick + 1 coal ore → 4 torches, then place it like any block) keeps its immediate surroundings lit regardless of the time of day, so you don't have to hole up in the dark.

## Mining & tools

Breaking a block takes time, not a click - hold the mouse button down and watch the targeted block's outline glow redder (and thicken) as you get closer to breaking it. How long it takes depends on:

- **The block's hardness** - dirt and sand are quick, stone slower, ores slower still.
- **Your tool.** Each tier (wood → stone → iron → diamond) roughly doubles mining speed for its matching tool kind (pickaxe, axe, or sword) over the tier below; the wrong tool kind (or bare hands) gets no bonus. Some blocks are gated harder than that: coal needs at least a wood pickaxe, iron needs stone, gold and diamond need iron - anything less and it simply won't break, full stop, no matter how long you hold the button. Swords are the odd one out - no block requires one, but they're the fastest thing through leaves and cacti.
- Craft pickaxes/axes with `C`: 3 of the tool's base material (planks, stone, or raw ore) + 2 sticks. Swords are lighter - 2 material + 1 stick. Craft sticks from planks first (2 planks → 4 sticks). See `Mining.java` for the exact hardness/tier table.

**Durability**: every tool wears out - each block it finishes breaking costs it one use, tracked per tool type (wood/stone/iron/diamond each wear down independently, and different kinds like a pickaxe vs. an axe of the same tier don't share wear). Higher tiers last far longer per use (wood ~60 uses, stone ~130, iron ~250, diamond ~1500+), on top of already being faster - so upgrading pays off twice. A hotbar slot shows a thin green→yellow→red wear bar under its icon once a tool has taken any damage; when it finally runs out, that tool is consumed from your inventory (the next one in the stack, if you have any, starts back at full durability). Bare-handed mining never wears anything down, obviously, and swords wear down the same way from cutting leaves/cacti even though they have nothing to fight yet.

## Saving

The world is saved to `saves/world/` next to wherever you run the jar from (override with the `MCCLONE_SAVE_DIR` environment variable). The world seed is written there on first launch and reused on every subsequent launch, so it's the same world each time you start the game. Only chunks you've actually broken/placed blocks in are ever written to disk — untouched terrain is cheap to regenerate deterministically from the seed, which is what keeps disk and memory usage bounded no matter how far you explore. Edits autosave every 60 seconds and on a clean exit.

## Textures

Two different asset strategies, chosen per what actually benefits from each:

- **Blocks** (`TextureAtlas`): generated procedurally into one shared 8×8 tile sheet at startup. Chunk meshing batches many blocks into a single draw call, so sharing one sheet (and one texture bind) across all of them matters for performance.
- **Items** (`ItemTextures`): food and tools are inventory-only and never batch together the way block faces do, so each one is a real, individual 16×16 PNG file committed under `src/main/resources/items/`, loaded from the classpath at runtime. They were produced once by [`GenerateItemTextures`](src/main/java/com/minecraftclone/tools/GenerateItemTextures.java) - a small offline tool, not something the game runs itself - and are checked into the repo like any other asset; re-run that tool and commit the results if an item's art ever needs to change.
- **HUD font** (`FontAtlas` + `TextRenderer`): a full printable-ASCII (32-126) 5x7 pixel font, procedurally generated into its own sheet, is used for every piece of on-screen text: inventory counts, transient messages (death/craft/tool-break), and the `F3` debug overlay. Like the block atlas, it has its own tiny atlas separate from blocks and items since it's neither.

All three share one GL upload helper (`GLTexture`) and the same nearest-neighbor filtering, so the blocky pixel-art look is consistent everywhere.

## Project Layout

```
src/main/java/com/minecraftclone/
├── Main.java                 # Entry point & game loop
├── engine/                   # Window, input, camera, shaders, HUD, DayNightCycle
│   └── graphics/              # TextureAtlas, ItemTextures, FontAtlas, GLTexture, Mesh, LineMesh, IconMesh
├── world/                    # Chunk, World (streaming/meshing), BlockType, Mining
│   └── gen/                   # TerrainGenerator (noise-based world gen)
├── player/                    # Player controller, PlayerStats, Inventory, Crafting, MiningController
├── tools/                     # GenerateItemTextures (offline, not run by the game itself)
└── util/                      # Noise, AABB, Raycaster, ResourceLoader
src/main/resources/shaders/    # GLSL vertex/fragment shaders (chunk, line, hud)
src/main/resources/items/      # Individual item PNGs (see Textures below)
```

## Notes & Simplifications

This is a compact, from-scratch clone meant to be readable end-to-end, not a feature-complete recreation. Some deliberate simplifications:

- Text rendering now covers the full printable ASCII set via a single 5x7 pixel font (see Textures) - inventory counts, on-screen death/craft/tool-break messages, and the F3 debug overlay (FPS/position/selected item) all flow through it. It's still just lines of text, though: there's no layout engine or multi-line UI, so a real recipe book, chat-style log, or death/damage scrolling message list would need building on top of it rather than existing yet.
- Crafting is a small fixed table, not a grid - there's no way to combine arbitrary items. Pickaxes, axes and swords exist with four material tiers each and now wear out with use (see Mining & tools), but there's no repairing/anvil yet - a worn-out tool is just gone - and there's nothing for a sword to fight (no mobs) or a shovel to dig faster (no shovel).
- No mobs (hostile or otherwise) - night is darker and a real signal, but nothing actually comes looking for you yet. No sleeping/beds to skip it either.
- Water and leaves are rendered as solid (opaque) blocks rather than alpha-blended, keeping the renderer single-pass. (Grass/flowers/berry bushes are the exception - they're cross-shaped and alpha-cutout, not alpha-blended. Lava is opaque too, despite being a hazard.)
- Day/night affects a single global ambient-brightness multiplier, and caves are exactly as dark as the surface at the same time of day (no separate "underground is always dark" rule). Torches punch a local hole in that multiplier rather than doing real light propagation: each one bakes a static glow into nearby chunk meshes at mesh-build time, falling off with straight-line distance (Minecraft-style, 1/15 per block) rather than being flood-filled and blocked by walls - so a torch on the other side of a thin wall will still show a faint glow through it. There's also no light bouncing or color tinting, just a brightness floor.
- Chunk meshing runs on the main thread with a per-frame budget, so there's no multithreading complexity, at the cost of a brief pause when flying very fast into unloaded terrain.
- World height is capped (128 blocks, same idea as vanilla Minecraft's build limit) — it's the horizontal extent that's unbounded.
- Chunk vertex positions are baked in absolute world-space `float`s rather than being camera-relative, so precision (and therefore visual stability) very gradually degrades if you travel extremely far (hundreds of thousands of blocks) from spawn — not something you'll hit in normal play.
- A crash (as opposed to closing the game normally) can lose up to the last 60 seconds of edits, since that's the autosave interval.

## Roadmap

This project is being grown incrementally, loosely following [Survivalcraft](https://survivalcraft.net/)'s own real-world update history as a backlog of features to work through - not chasing parity with any specific version, just using it as a source of "what's next" in roughly the order the genre itself matured. Roughly where things stand against that list:

**Done (in some form):** screenshots (well, `F2`)/tools/recipaedia (this README) → **1.1**; snow → **1.2**; furnace-free ore tools → partial **1.3**; food/eating → **1.5**; buckets/water physics/magma → partial **1.8**; diamonds/flat-ish terrain → partial **1.12**; saplings-ish (trees regrow via world-gen, not planting) → partial **1.13**; cacti → **1.15**; rain-free weather is still open but thunderstorms/pumpkins are on the list → **1.18** (partial); creative-adjacent options via the hotbar → partial **1.20**; survival/farming-adjacent (no crops yet) → partial **1.22**. Also done outside that list: tool **durability** (uses-based wear per tool, with a HUD wear indicator - no repair/anvil yet); **torches** (a real, if distance-based rather than flood-filled, local light source that overrides the night-time dimmer - see Notes & Simplifications); an on-screen **text renderer** (full-ASCII pixel font powering inventory counts, transient messages and the F3 debug overlay - see Notes & Simplifications for what it doesn't yet do).

**Not yet, roughly in the order they'd naturally build on what exists:**
- A brighter full-cube **lamp** to go with torches, for lighting up a whole room without a forest of sticks poking out of the walls.
- A **furnace** and smelting (ore → ingot) ahead of raw-ore tools, plus **stairs/slabs/doors/fences/trapdoors** (partial-cube geometry the mesher doesn't support yet - everything is either a full cube or a cross-shaped sprite right now).
- **Animals & mobs** - passive (cows, wolves, fish, birds...) and eventually hostile, with the AI/pathfinding that implies. Also compass/thermometer-style instruments, creature spawners. This is what actually gives swords (done) a job beyond leaves/cacti.
- **Combat** - attacking, damage, bows/arrows, thrown items, explosives/fire.
- **Farming** - crops, planting/growing, not just foraging what world-gen placed.
- **Boats**, **horse/animal riding**.
- **Electricity**, **clothes/armor**, **temperature effects**.
- A real **recipe book / on-screen UI** now that a text renderer exists - crafting/message logs, death/damage messaging, and debug info are in-game (see Features), but there's no scrollable/laid-out panel yet, and no map/inventory screen beyond the hotbar.

If you've got a specific one of these in mind, just say which and it jumps the queue.

## Automated smoke testing

The game supports a headless self-test mode (used to verify rendering in CI/sandboxes without a real display):

```bash
MCCLONE_AUTOTEST=1 MCCLONE_AUTOTEST_FRAMES=90 MCCLONE_AUTOTEST_PATH=out.png \
  xvfb-run -a java -jar target/minecraft-clone.jar
```

Add `MCCLONE_AUTOTEST_TIME=0.5` (0=midnight, 0.5=noon, etc.) to pin the day/night cycle to a specific moment for a reproducible screenshot instead of whatever the default start time renders.

This runs the given number of frames, saves a screenshot, and exits.
