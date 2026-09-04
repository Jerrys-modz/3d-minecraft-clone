package com.minecraftclone.world;

import org.joml.Vector3f;

import java.util.Random;

/**
 * A fishing bobber thrown by the player when they right-click with a
 * {@link BlockType#FISHING_ROD}.  The bobber follows three phases:
 *
 * <ol>
 *   <li><b>In-flight</b> — arcs through the air under gravity until it hits a
 *       water block or the ground.</li>
 *   <li><b>Floating</b> — rests on the water surface and counts down a random
 *       bite timer ({@link #BITE_MIN}–{@link #BITE_MAX} seconds).</li>
 *   <li><b>Biting</b> — the bite timer has expired; a fish is on the line.
 *       The player must reel in before {@link #BITE_WINDOW} seconds pass or
 *       the fish escapes and the bobber returns to floating.</li>
 * </ol>
 *
 * <p>Call {@link #tick(float, World, Random)} every game tick to advance the
 * bobber's physics and state machine.  Call {@link #reelIn(Random)} to
 * retrieve the catch; it returns the {@link BlockType} of the item caught, or
 * {@code null} if the bobber was not biting (no catch).
 */
public class FishingBobber {

    // -----------------------------------------------------------------------
    // Timing constants — package-private so unit tests can reference them.
    // -----------------------------------------------------------------------

    /** Minimum seconds between the bobber landing and a bite. */
    static final float BITE_MIN    = 5f;
    /** Maximum seconds between the bobber landing and a bite. */
    static final float BITE_MAX    = 30f;
    /**
     * Seconds the fish stays on the line before escaping if the player
     * doesn't reel in.
     */
    static final float BITE_WINDOW = 5f;
    /** Gravity applied while the bobber is in flight (blocks/s²). */
    static final float GRAVITY     = 18f;

    /** Raw probability of catching a treasure item instead of a fish (1 in N). */
    private static final int TREASURE_ODDS = 20;

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    /** Current world position of the bobber. */
    public final Vector3f position = new Vector3f();
    /** Velocity while in-flight. */
    private final Vector3f velocity = new Vector3f();

    private boolean inFlight   = true;
    private boolean floating   = false;
    private boolean biting     = false;

    /**
     * Seconds until the next bite (counts down while floating).
     * Randomised when the bobber first lands.
     */
    private float biteTimer    = 0f;
    /** Seconds remaining in the bite window before the fish escapes. */
    private float biteWindow   = 0f;

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    /**
     * Creates a bobber thrown from {@code (x, y, z)} with the given initial
     * velocity vector.
     *
     * @param x  world X of the throw origin
     * @param y  world Y of the throw origin
     * @param z  world Z of the throw origin
     * @param vx horizontal velocity component along X
     * @param vy vertical velocity component (positive = upward)
     * @param vz horizontal velocity component along Z
     */
    public FishingBobber(float x, float y, float z, float vx, float vy, float vz) {
        position.set(x, y, z);
        velocity.set(vx, vy, vz);
    }

    // -----------------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------------

    /** {@code true} while the bobber is still arcing through the air. */
    public boolean isInFlight() {
        return inFlight;
    }

    /** {@code true} while the bobber is resting on water, waiting for a bite. */
    public boolean isFloating() {
        return floating;
    }

    /**
     * {@code true} when a fish is on the line; the player should reel in
     * quickly before the fish escapes.
     */
    public boolean isBiting() {
        return biting;
    }

    // -----------------------------------------------------------------------
    // Update
    // -----------------------------------------------------------------------

    /**
     * Advances the bobber's physics and state machine by {@code dt} seconds.
     * Should be called once per game tick from {@link World#updateMobs}.
     *
     * @param dt    elapsed seconds since the last tick
     * @param world used to test whether the bobber has entered a water block
     * @param rnd   random source for bite-timer initialisation
     */
    void tick(float dt, World world, Random rnd) {
        if (inFlight) {
            tickFlight(dt, world, rnd);
        } else if (biting) {
            tickBiting(dt, rnd);
        } else if (floating) {
            tickFloating(dt, rnd);
        }
    }

    private void tickFlight(float dt, World world, Random rnd) {
        // Apply gravity.
        velocity.y -= GRAVITY * dt;

        // Move the bobber.
        position.x += velocity.x * dt;
        position.y += velocity.y * dt;
        position.z += velocity.z * dt;

        // Check for water landing.
        int bx = (int) Math.floor(position.x);
        int by = (int) Math.floor(position.y);
        int bz = (int) Math.floor(position.z);
        BlockType block = world.getBlock(bx, by, bz);
        if (block != null && block.isFluid()) {
            landInWater(rnd);
            return;
        }

        // Fell below the world or hit solid ground — retrieve.
        if (position.y < -64f || (block != null && block != BlockType.AIR && !block.isFluid() && !block.isItem)) {
            // Bobber missed water; treat as a retrieve with nothing caught.
            floating = false;
            inFlight = false;
        }
    }

    private void landInWater(Random rnd) {
        inFlight = false;
        floating = true;
        velocity.zero();
        biteTimer = BITE_MIN + rnd.nextFloat() * (BITE_MAX - BITE_MIN);
    }

    private void tickFloating(float dt, Random rnd) {
        biteTimer -= dt;
        if (biteTimer <= 0f) {
            floating = false;
            biting   = true;
            biteWindow = BITE_WINDOW;
        }
    }

    private void tickBiting(float dt, Random rnd) {
        biteWindow -= dt;
        if (biteWindow <= 0f) {
            // Fish escaped; go back to floating with a fresh timer.
            biting   = false;
            floating = true;
            biteTimer = BITE_MIN + rnd.nextFloat() * (BITE_MAX - BITE_MIN);
        }
    }

    // -----------------------------------------------------------------------
    // Reel-in
    // -----------------------------------------------------------------------

    /**
     * Reels in the fishing line and returns any catch.
     *
     * <ul>
     *   <li>If the bobber is {@link #isBiting() biting}, a {@link BlockType#RAW_FISH}
     *       is returned (with a small chance of a rarer catch).</li>
     *   <li>Otherwise {@code null} is returned — the player just retrieves the
     *       empty bobber.</li>
     * </ul>
     *
     * <p>The bobber is invalidated after this call; the caller should discard it.
     *
     * @param rnd random source for treasure rolls
     * @return the caught item, or {@code null}
     */
    public BlockType reelIn(Random rnd) {
        if (!biting) {
            return null;
        }
        // Small chance of a bonus catch (string, bone, etc.) in place of a fish.
        if (rnd.nextInt(TREASURE_ODDS) == 0) {
            // Treasure table: string or bones as occasional junk/treasure
            return (rnd.nextBoolean()) ? BlockType.STRING : BlockType.BONES;
        }
        return BlockType.RAW_FISH;
    }
}
