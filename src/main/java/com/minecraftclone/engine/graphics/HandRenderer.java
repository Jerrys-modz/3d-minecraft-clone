package com.minecraftclone.engine.graphics;

import com.minecraftclone.engine.Shader;
import com.minecraftclone.util.FloatArray;
import com.minecraftclone.util.IntArray;
import com.minecraftclone.world.BlockType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL11.*;

/**
 * Draws the block or item currently held in the player's hand, Minecraft-style:
 * a block shows as a small 3D cube (top/side/bottom atlas tiles, with the same
 * baked directional shading as chunk meshes) and an item (tool/food) as a flat
 * sprite, both anchored to the camera at the lower-right of the view.
 * <p>
 * The mesh is built in <em>view space</em> (the chunk shader gets an identity
 * view), so the held thing is glued to the camera like a real first-person arm:
 * it stays put as you look around but sways with the walk-bob. Every vertex is
 * fully lit (blockLight = 1), so the hand is readable even at midnight, and the
 * near position keeps it out of the fog. Rebuilt each frame into scratch
 * buffers - one tiny cube is far cheaper than a chunk remesh.
 */
public class HandRenderer {

    // Held-block geometry in view space: the camera looks down -Z, +X is right,
    // +Y is up. A slightly-rotated cube sitting in front and to the right.
    private static final float HAND_X = 0.62f;
    private static final float HAND_Y = -0.48f;
    private static final float HAND_Z = -0.75f;
    private static final float BLOCK_HALF = 0.21f;   // local cube spans -1..1, scaled by this
    private static final float YAW = 0.65f;          // turn so the front + right faces show
    private static final float PITCH = -0.55f;       // tilt the top toward the camera

    private static final float ITEM_HALF = 0.26f;    // flat-sprite half-size
    private static final float ITEM_TILT = 0.35f;    // in-plane rotation of the held sprite

    // Swing animation (place/use/break): the hand punches toward the crosshair
    // and back over SWING_DURATION seconds.
    private static final float SWING_DURATION = 0.3f;
    private static final float SWING_MOVE_X = 0.28f; // left toward center at the peak
    private static final float SWING_MOVE_Y = 0.20f; // up toward center at the peak
    private static final float SWING_MOVE_Z = 0.32f; // toward the camera at the peak
    private static final float SWING_ROTATE = 0.9f;  // extra pitch (radians) at the peak

    private static final float LIGHT_TOP = 1.0f;
    private static final float LIGHT_BOTTOM = 0.4f;
    private static final float LIGHT_NORTH_SOUTH = 0.75f;
    private static final float LIGHT_EAST_WEST = 0.6f;

    private final Mesh mesh = new Mesh();
    private final FloatArray verts = new FloatArray(512);
    private final IntArray inds = new IntArray(128);
    private final Matrix4f identity = new Matrix4f();
    private final Matrix4f model = new Matrix4f();
    private final Vector3f corner = new Vector3f();
    private int[] counter = {0};
    private float swingTime = 0f; // seconds left in the current swing (0 = idle)

    /** Starts the place/use/break punch animation. Safe to call mid-swing (restarts it). */
    public void triggerSwing() {
        swingTime = SWING_DURATION;
    }

    /**
     * Renders the held {@code type} (or nothing if empty). The caller must have
     * the chunk shader bound with its projection uniform set; this method swaps
     * the view to identity for the hand, draws it, and unbinds.
     *
     * @param bobPhase  walk-bob phase (radians) from the player, drives the sway
     * @param animTime  free-running clock for a gentle idle sway when standing still
     * @param dt        frame time (seconds) - advances the swing animation
     */
    public void render(Shader shader, TextureAtlas atlas, ItemTextures itemTextures,
                       BlockType type, float bobPhase, float animTime, float dt, Matrix4f projection) {
        if (type == null || type == BlockType.AIR) return;

        float bobX = (float) Math.cos(bobPhase) * 0.02f;
        float bobY = (float) Math.sin(bobPhase) * 0.035f;
        float idleX = (float) Math.sin(animTime * 1.5f) * 0.006f;
        float idleY = (float) Math.cos(animTime * 2.1f) * 0.006f;

        // Swing progress 0..1 across the animation; sin() peaks mid-way so the
        // hand reaches out and returns instead of just sliding one way.
        float swingT = 0f;
        if (swingTime > 0f) {
            swingTime = Math.max(0f, swingTime - dt);
            swingT = 1f - swingTime / SWING_DURATION;
        }
        float s = (float) Math.sin(swingT * Math.PI); // 0 -> 1 -> 0
        float swingX = s * -SWING_MOVE_X;
        float swingY = s * SWING_MOVE_Y;
        float swingZ = s * -SWING_MOVE_Z;
        float swingPitch = s * SWING_ROTATE;

        verts.clear();
        inds.clear();
        counter[0] = 0;

        // The hand is drawn on top of everything, like Minecraft: no depth test
        // (a wall right against the camera must never occlude it). Culling stays
        // on so back faces never paint over front ones - without it the cube's
        // faces overlap in screen space and the top face ends up hidden.
        boolean depth = glIsEnabled(GL_DEPTH_TEST);
        glDisable(GL_DEPTH_TEST);

        shader.bind();
        shader.setUniform("view", identity);
        shader.setUniform("projection", projection);
        shader.setUniform("atlas", 0);

        if (type.isItem) {
            addItemSprite(type, itemTextures, bobX + idleX, bobY + idleY, swingX, swingY, swingZ, swingPitch);
        } else {
            addBlockCube(type, atlas, bobX + idleX, bobY + idleY, swingX, swingY, swingZ, swingPitch);
        }

        mesh.upload(verts.toArray(), inds.toArray());
        if (type.isItem) {
            itemTextures.bind(type);
        } else {
            atlas.bind();
        }
        mesh.render();
        shader.unbind();

        if (depth) glEnable(GL_DEPTH_TEST);
    }

    /** Builds a held 3D block cube: six faces with top/side/bottom tiles and baked face lighting. */
    private void addBlockCube(BlockType block, TextureAtlas atlas, float bobX, float bobY,
                              float swingX, float swingY, float swingZ, float swingPitch) {
        model.identity()
                .translate(HAND_X + bobX + swingX, HAND_Y + bobY + swingY, HAND_Z + swingZ)
                .rotateY(YAW)
                .rotateX(PITCH + swingPitch)
                .scale(BLOCK_HALF);

        float[] topUv = atlas.getUV(block.topTile);
        float[] sideUv = atlas.getUV(block.sideTile);
        float[] botUv = atlas.getUV(block.bottomTile);

        // Same winding as Chunk.emitFace, so backface culling still works after the rotation.
        addFace(topUv, LIGHT_TOP, new float[][]{{-1, 1, 1}, {1, 1, 1}, {1, 1, -1}, {-1, 1, -1}});
        addFace(botUv, LIGHT_BOTTOM, new float[][]{{-1, -1, -1}, {1, -1, -1}, {1, -1, 1}, {-1, -1, 1}});
        addFace(sideUv, LIGHT_NORTH_SOUTH, new float[][]{{1, -1, -1}, {-1, -1, -1}, {-1, 1, -1}, {1, 1, -1}});
        addFace(sideUv, LIGHT_NORTH_SOUTH, new float[][]{{-1, -1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1}});
        addFace(sideUv, LIGHT_EAST_WEST, new float[][]{{1, -1, 1}, {1, -1, -1}, {1, 1, -1}, {1, 1, 1}});
        addFace(sideUv, LIGHT_EAST_WEST, new float[][]{{-1, -1, -1}, {-1, -1, 1}, {-1, 1, 1}, {-1, 1, -1}});
    }

    /** Builds a held item as a flat camera-facing sprite, slightly tilted. */
    private void addItemSprite(BlockType item, ItemTextures itemTextures, float bobX, float bobY,
                               float swingX, float swingY, float swingZ, float swingPitch) {
        model.identity()
                .translate(HAND_X + bobX + swingX, HAND_Y + bobY + swingY, HAND_Z + swingZ)
                .rotateZ(ITEM_TILT)
                .rotateX(swingPitch)
                .scale(ITEM_HALF);
        // Full-tile quad facing the camera (+Z); uv v-flipped like the hotbar icons.
        addFace(new float[]{0f, 0f, 1f, 1f}, 1f,
                new float[][]{{-1, -1, 0}, {1, -1, 0}, {1, 1, 0}, {-1, 1, 0}}, true);
    }

    /** Emits one transformed quad. {@code uv} is {u0, v0, u1, v1} (matching atlas.getUV). */
    private void addFace(float[] uv, float light, float[][] positions) {
        addFace(uv, light, positions, false);
    }

    private void addFace(float[] uv, float light, float[][] positions, boolean flipV) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        float[][] uvs = flipV
                ? new float[][]{{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}}
                : new float[][]{{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
        int base = counter[0];
        for (int i = 0; i < 4; i++) {
            corner.set(positions[i][0], positions[i][1], positions[i][2]).mulPosition(model);
            verts.add(corner.x);
            verts.add(corner.y);
            verts.add(corner.z);
            verts.add(uvs[i][0]);
            verts.add(uvs[i][1]);
            verts.add(light);
            verts.add(1f); // blockLight: always fully lit
            verts.add(0f); // fluidFlow
            verts.add(0f); // flowDir.x
            verts.add(0f); // flowDir.y
        }
        inds.add(base); inds.add(base + 1); inds.add(base + 2);
        inds.add(base); inds.add(base + 2); inds.add(base + 3);
        counter[0] += 4;
    }

    public void destroy() {
        mesh.destroy();
    }
}
