# AGENTS.md

Project: 3D Minecraft Clone — a Java 17 + LWJGL 3 voxel game built with Maven.

## Workflow

- Do all work on a **feature branch** and open a **pull request** against `main` — never push directly to `main`.
- Every PR must have a **title** and a **description** (what changed, why, and how it was tested).
- Update the **README** in the same PR whenever behavior, controls, features, the roadmap, or notes change.
- Use the `gh` CLI (`C:\Program Files\GitHub CLI\gh.exe`) for PRs; return the PR URL when done.

## Build & test

- Build: `mvn compile` (or `mvn package` for the shaded fat jar).
- Headless smoke test (no display needed):
  `MCCLONE_AUTOTEST=1 MCCLONE_AUTOTEST_FRAMES=90 MCCLONE_AUTOTEST_PATH=out.png java -cp "target/classes;<deps>" com.minecraftclone.Main`

## Code conventions

- No external text/font/JSON/image-loading libraries — the game is self-contained.
- Block textures are generated procedurally into a shared atlas at startup; inventory items are committed PNGs; HUD text uses a procedural ASCII pixel font.
- HUD geometry uses a "logical square" (-1..1) space scaled by 1/aspect at draw time.
- Meshing/batching uses primitive growable buffers (`util.FloatArray` / `util.IntArray`) rather than boxed `ArrayList<Float>`.
