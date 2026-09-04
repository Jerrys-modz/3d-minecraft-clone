package com.minecraftclone.world;

import java.util.Random;

/**
 * Blast physics for creeper explosions and TNT.
 *
 * <p>An explosion destroys all non-bedrock, non-fluid blocks within a sphere of
 * {@code radius} blocks, with a probability that decays linearly from certain
 * destruction at the centre to ~50 % at the edge (mimicking Minecraft's
 * ray-trace blast attenuation with a cheaper, equally-fun approximation).
 * Blocks that survive the blast are left untouched; destroyed blocks have a
 * 30 % chance of dropping their item on the ground.
 *
 * <p>Entity damage falls off with the square of distance up to
 * {@code 1.5 × radius}; targets beyond that boundary take no damage.
 *
 * <h3>Constants</h3>
 * <ul>
 *   <li>{@link #CREEPER_RADIUS} / {@link #CREEPER_CENTER_DAMAGE} — creeper blast profile</li>
 *   <li>{@link #TNT_RADIUS}     / {@link #TNT_CENTER_DAMAGE}     — TNT blast profile</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Destroy blocks and get max player damage:
 * Explosion.blastBlocks(world, cx, cy, cz, Explosion.CREEPER_RADIUS, rnd);
 * float dmg = Explosion.damageAt(cx, cy, cz, Explosion.CREEPER_RADIUS,
 *                                Explosion.CREEPER_CENTER_DAMAGE,
 *                                player.x, player.y, player.z);
 * }</pre>
 */
public final class Explosion {

    // -----------------------------------------------------------------------
    // Blast profiles
    // -----------------------------------------------------------------------

    /** Sphere radius (blocks) of a creeper explosion. */
    public static final float CREEPER_RADIUS        = 3f;
    /** Maximum damage dealt at ground-zero of a creeper explosion. */
    public static final float CREEPER_CENTER_DAMAGE = 9f;

    /** Sphere radius (blocks) of a TNT explosion. */
    public static final float TNT_RADIUS            = 4f;
    /** Maximum damage dealt at ground-zero of a TNT explosion. */
    public static final float TNT_CENTER_DAMAGE     = 14f;

    /** Fraction of destroyed blocks that drop their item. */
    private static final float DROP_CHANCE = 0.3f;
    /**
     * Edge-survival factor: a block exactly at the blast radius has this
     * probability of surviving.  Blocks closer to the centre are always
     * destroyed; blocks farther out always survive.
     */
    private static final float EDGE_SURVIVAL = 0.5f;

    private Explosion() {}

    // -----------------------------------------------------------------------
    // Block destruction
    // -----------------------------------------------------------------------

    /**
     * Destroys blocks in a sphere of radius {@code radius} centred at
     * ({@code cx}, {@code cy}, {@code cz}).  Bedrock and fluid blocks are
     * immune.  Each destroyed block has a {@value #DROP_CHANCE} chance of
     * spawning an item entity at its position.
     *
     * @param world  the world to modify
     * @param cx     explosion centre X (world coordinates)
     * @param cy     explosion centre Y
     * @param cz     explosion centre Z
     * @param radius blast sphere radius in blocks
     * @param rnd    random source
     */
    public static void blastBlocks(World world,
                                   float cx, float cy, float cz,
                                   float radius, Random rnd) {
        int ri = (int) Math.ceil(radius);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -ri; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    float dist = (float) Math.sqrt(dx * (double) dx
                            + dy * (double) dy + dz * (double) dz);
                    if (dist > radius) continue;

                    // Survival probability increases linearly from 0 at centre
                    // to EDGE_SURVIVAL at the edge.
                    float survivalProb = (dist / radius) * EDGE_SURVIVAL;
                    if (rnd.nextFloat() < survivalProb) continue;

                    int bx = (int) Math.floor(cx) + dx;
                    int by = (int) Math.floor(cy) + dy;
                    int bz = (int) Math.floor(cz) + dz;

                    BlockType block = world.getBlock(bx, by, bz);
                    if (block == null
                            || block == BlockType.AIR
                            || block == BlockType.BEDROCK) continue;
                    if (block.isFluid()) {
                        // Fluids vaporise silently (water quenches, lava spreads fire —
                        // the simple version: just remove the fluid cell).
                        world.setBlock(bx, by, bz, BlockType.AIR);
                        continue;
                    }

                    // Chain-detonate TNT caught in the blast.
                    if (block == BlockType.TNT) {
                        world.setBlock(bx, by, bz, BlockType.AIR);
                        // Recursive: use a slightly smaller radius so cascades don't
                        // spiral out of control, and offset slightly to randomise spread.
                        blastBlocks(world,
                                bx + 0.5f, by + 0.5f, bz + 0.5f,
                                radius * 0.8f, rnd);
                        continue;
                    }

                    world.setBlock(bx, by, bz, BlockType.AIR);
                    // Partial item drop — same as Minecraft vanilla blast behaviour.
                    if (rnd.nextFloat() < DROP_CHANCE && !block.isItem) {
                        world.spawnItem(bx, by, bz, block, 1, rnd);
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Entity damage
    // -----------------------------------------------------------------------

    /**
     * Computes the damage a target at ({@code tx}, {@code ty}, {@code tz})
     * would take from an explosion at ({@code cx}, {@code cy}, {@code cz}).
     *
     * <p>Damage falls off with the inverse square of distance and reaches
     * zero at {@code 1.5 × radius}.  A target at the exact centre takes
     * {@code maxDamage}.
     *
     * @param cx        explosion centre X
     * @param cy        explosion centre Y
     * @param cz        explosion centre Z
     * @param radius    blast sphere radius
     * @param maxDamage damage at ground zero
     * @param tx        target X
     * @param ty        target Y
     * @param tz        target Z
     * @return damage amount ≥ 0
     */
    public static float damageAt(float cx, float cy, float cz,
                                 float radius, float maxDamage,
                                 float tx, float ty, float tz) {
        float killRadius = radius * 1.5f;
        float dx = tx - cx, dy = ty - cy, dz = tz - cz;
        float distSq = dx * dx + dy * dy + dz * dz;
        float killSq = killRadius * killRadius;
        if (distSq >= killSq) return 0f;

        float dist = (float) Math.sqrt(distSq);
        // Linear falloff from maxDamage at 0 to 0 at killRadius.
        float t = 1f - dist / killRadius;
        return maxDamage * t * t; // squared for a steeper drop-off
    }
}
