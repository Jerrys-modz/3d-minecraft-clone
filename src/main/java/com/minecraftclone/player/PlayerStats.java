package com.minecraftclone.player;

import com.minecraftclone.Difficulty;

/**
 * Health, hunger, thirst and stamina: the core survival loop. Damage comes
 * from falling too far, standing in lava, staying submerged too long, letting
 * hunger hit zero, or letting thirst hit zero; health slowly regenerates when
 * both hunger and thirst are sufficient. Sprinting costs both stamina
 * (immediate, regenerates fast) and a little extra hunger (permanent until you
 * eat). Fill a clay canteen at a water source and drink it to restore thirst.
 * <p>
 * Hunger drain, starvation, and regen rate follow the world's
 * {@link Difficulty}: Peaceful doesn't drain hunger and regenerates quickly;
 * Easy/Normal stop starvation short of death; Hard can starve you out.
 */
public class PlayerStats {

    public static final float MAX_HEALTH  = 100f;
    public static final float MAX_HUNGER  = 100f;
    public static final float MAX_THIRST  = 100f;
    public static final float MAX_STAMINA = 100f;
    /** Seconds of breath you start a dive with - same value as {@link #DROWN_GRACE_SECONDS}, just public for the HUD's breath meter. */
    public static final float MAX_BREATH  = 6f;

    private static final float SAFE_FALL_SPEED = 10f;          // blocks/sec you can land at with no damage
    private static final float FALL_DAMAGE_PER_SPEED = 3.5f;   // damage per (blocks/sec) over the safe speed
    private static final float LAVA_DAMAGE_PER_SECOND = 20f;
    private static final float FIRE_DAMAGE_PER_SECOND = 6f;   // standing in lightning-lit fire burns
    private static final float COLD_HUNGER_DRAIN_PER_SECOND = 100f / 300f; // ~5 min to empty at full exposure
    private static final float COLD_DAMAGE_PER_SECOND = 2f;   // freezing once hunger is gone
    private static final float DROWN_GRACE_SECONDS = MAX_BREATH; // how long you can hold your breath
    private static final float DROWN_DAMAGE_PER_SECOND = 5f;
    private static final float STARVE_DAMAGE_PER_SECOND = 2f;
    /** Dehydration kicks in once thirst hits zero - similar rate to starvation. */
    private static final float DEHYDRATE_DAMAGE_PER_SECOND = 2f;
    private static final float REGEN_HUNGER_THRESHOLD = 50f;   // need at least this much hunger to regenerate health
    private static final float REGEN_THIRST_THRESHOLD = 50f;   // need at least this much thirst to regenerate health
    private static final float REGEN_PER_SECOND = 1f;
    private static final float HUNGER_DRAIN_PER_SECOND = 100f / (20f * 60f); // empties passively over ~20 minutes
    private static final float THIRST_DRAIN_PER_SECOND = 100f / (15f * 60f); // empties faster than hunger (~15 min)
    private static final float HUNGER_SPRINT_EXTRA_DRAIN_PER_SECOND = 0.5f;
    private static final float STAMINA_SPRINT_DRAIN_PER_SECOND = 25f; // ~4s of sprinting from full
    private static final float STAMINA_REGEN_PER_SECOND = 15f;
    private static final float STAMINA_SPRINT_MIN = 10f; // must regen back above this before sprinting resumes once exhausted

    private float health  = MAX_HEALTH;
    private float hunger  = MAX_HUNGER;
    private float thirst  = MAX_THIRST;
    private float stamina = MAX_STAMINA;
    private float submergedTime = 0f;
    private float coldness = 0f; // 0 (warm) .. 1 (freezing out in a blizzard), set by Player each frame
    private boolean staminaExhausted = false;
    private boolean dead = false;
    /** Damage multiplier from equipped armor (1 = no armor); applied by {@link #damage}. */
    private float armorMultiplier = 1f;
    /** Total damage accumulated this frame (after armor), cleared after armor wear is applied. */
    private float frameDamageAccumulator = 0f;

    /** Sets the damage multiplier from the player's equipped armor (1 = no armor, lower = more protection). */
    public void setArmorMultiplier(float multiplier) {
        this.armorMultiplier = multiplier;
    }

    /** Total damage accumulated this frame (after armor) - cleared after armor wear is applied. */
    public float frameDamageAccumulator() {
        return frameDamageAccumulator;
    }

    /** Clears the per-frame damage accumulator after armor wear has been consumed. */
    public void clearFrameDamage() {
        frameDamageAccumulator = 0f;
    }

    /** How exposed to the cold the player is this frame, 0 (warm) to 1 (freezing). */
    public float getColdness() {
        return coldness;
    }

    public float getHealth() {
        return health;
    }

    public float getHunger() {
        return hunger;
    }

    public float getThirst() {
        return thirst;
    }

    public float getStamina() {
        return stamina;
    }

    /** Seconds of breath left before you start drowning (0..{@link #MAX_BREATH}) - only counts down while submerged. */
    public float getBreath() {
        return Math.max(0f, DROWN_GRACE_SECONDS - submergedTime);
    }

    public boolean isDead() {
        return dead;
    }

    public void reset() {
        health  = MAX_HEALTH;
        hunger  = MAX_HUNGER;
        thirst  = MAX_THIRST;
        stamina = MAX_STAMINA;
        submergedTime = 0f;
        coldness = 0f;
        staminaExhausted = false;
        dead = false;
        armorMultiplier = 1f;
        frameDamageAccumulator = 0f;
    }

    /** Restores the four survival bars from a save (clamped). A 0-health restore leaves the player dead. */
    public void restore(float health, float hunger, float thirst, float stamina) {
        this.health  = clamp(health,  0f, MAX_HEALTH);
        this.hunger  = clamp(hunger,  0f, MAX_HUNGER);
        this.thirst  = clamp(thirst,  0f, MAX_THIRST);
        this.stamina = clamp(stamina, 0f, MAX_STAMINA);
        this.dead = this.health <= 0f;
        this.submergedTime = 0f;
        this.coldness = 0f;
        this.staminaExhausted = this.stamina <= 0f;
        this.armorMultiplier = 1f;
        this.frameDamageAccumulator = 0f;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Keeps every stat topped up and the player alive - used in creative/spectator modes. */
    public void forceFull() {
        health  = MAX_HEALTH;
        hunger  = MAX_HUNGER;
        thirst  = MAX_THIRST;
        stamina = MAX_STAMINA;
        submergedTime = 0f;
        coldness = 0f;
        staminaExhausted = false;
        dead = false;
        // Also drop any damage a stray hit accumulated right before switching into an
        // invulnerable mode - otherwise it lingers unconsumed (this branch skips
        // Player.finalizeDamage's wear step) and gets wrongly applied to whatever armor
        // is equipped much later, if the player switches back to a mortal mode.
        armorMultiplier = 1f;
        frameDamageAccumulator = 0f;
    }

    /**
     * Drinking a full clay canteen restores {@code amount} thirst points (clamped
     * to MAX_THIRST). Call from Main when the player right-clicks a CLAY_CANTEEN_FULL.
     */
    public void drink(float amount) {
        thirst = Math.min(MAX_THIRST, thirst + amount);
    }

    public boolean canSprint() {
        return !staminaExhausted && stamina > 0.5f;
    }

    /**
     * Advances all four stats (health, hunger, thirst, stamina) by one frame.
     *
     * @param fallImpactSpeed the speed (blocks/sec) the player just landed at this frame, or 0 if not landing.
     */
    public void update(float dt, boolean inLava, boolean inFire, boolean submerged, boolean sprintingAndMoving, float fallImpactSpeed, float coldness) {
        update(dt, inLava, inFire, submerged, sprintingAndMoving, fallImpactSpeed, coldness, Difficulty.NORMAL);
    }

    /**
     * Advances all four stats (health, hunger, thirst, stamina) by one frame
     * under {@code difficulty}.
     */
    public void update(float dt, boolean inLava, boolean inFire, boolean submerged, boolean sprintingAndMoving,
                       float fallImpactSpeed, float coldness, Difficulty difficulty) {
        if (dead) return;
        if (difficulty == null) difficulty = Difficulty.NORMAL;
        this.coldness = coldness;
        if (sprintingAndMoving) {
            stamina = Math.max(0f, stamina - STAMINA_SPRINT_DRAIN_PER_SECOND * dt);
            if (stamina <= 0f) staminaExhausted = true;
        } else {
            stamina = Math.min(MAX_STAMINA, stamina + STAMINA_REGEN_PER_SECOND * dt);
        }
        if (staminaExhausted && stamina >= STAMINA_SPRINT_MIN) {
            staminaExhausted = false;
        }

        float drainMul = difficulty.hungerDrainMultiplier();
        float hungerDrain = (HUNGER_DRAIN_PER_SECOND + (sprintingAndMoving ? HUNGER_SPRINT_EXTRA_DRAIN_PER_SECOND : 0f)) * drainMul;
        hunger = Math.max(0f, hunger - hungerDrain * dt);
        thirst = Math.max(0f, thirst - THIRST_DRAIN_PER_SECOND * drainMul * dt);

        // Track whether anything hurt the player this tick, so regen (below) doesn't
        // silently cancel out damage taken in the same update - e.g. standing in lava
        // while well-fed shouldn't net out to "no visible damage".
        boolean tookDamage = false;

        if (fallImpactSpeed > SAFE_FALL_SPEED) {
            damage((fallImpactSpeed - SAFE_FALL_SPEED) * FALL_DAMAGE_PER_SPEED);
            tookDamage = true;
        }

        if (inLava) {
            damage(LAVA_DAMAGE_PER_SECOND * dt);
            tookDamage = true;
        }

        if (inFire) {
            damage(FIRE_DAMAGE_PER_SECOND * dt);
            tookDamage = true;
        }

        if (submerged) {
            float submergedBefore = submergedTime;
            submergedTime += dt;
            // Damage only the portion of this dt that's actually past the grace
            // period - the update straddling the boundary (submergedBefore below
            // it, submergedTime past it) would otherwise get charged for the
            // *entire* dt, not just the fraction of it spent drowning. At a normal
            // frame rate that sliver is negligible, but a large dt (a stutter, or
            // a coarse test step) makes the overcharge obvious.
            float drowningSeconds = submergedTime - Math.max(submergedBefore, DROWN_GRACE_SECONDS);
            if (drowningSeconds > 0f) {
                damage(DROWN_DAMAGE_PER_SECOND * drowningSeconds);
                tookDamage = true;
            }
        } else {
            submergedTime = 0f;
        }

        if (coldness > 0f) {
            // Freezing out in a storm: the cold burns through hunger first, then
            // health once you've run out of food. Shelter or a warm fire reduces
            // the exposure (see Player). tookDamage stays clear while food lasts
            // so the regen below keeps working. Peaceful skips the hunger burn.
            hunger = Math.max(0f, hunger - COLD_HUNGER_DRAIN_PER_SECOND * coldness * drainMul * dt);
            if (hunger <= 0f && drainMul > 0f) {
                damage(COLD_DAMAGE_PER_SECOND * coldness * dt);
                tookDamage = true;
            }
        }

        float starveFloor = difficulty.starvationHealthFloor();
        if (hunger <= 0f && drainMul > 0f && health > starveFloor) {
            float applied = Math.min(STARVE_DAMAGE_PER_SECOND * dt, health - starveFloor);
            if (applied > 0f) {
                damage(applied);
                tookDamage = true;
            }
        }
        if (thirst <= 0f && drainMul > 0f && health > starveFloor) {
            float applied = Math.min(DEHYDRATE_DAMAGE_PER_SECOND * dt, health - starveFloor);
            if (applied > 0f) {
                damage(applied);
                tookDamage = true;
            }
        }
        // Peaceful always regenerates; other difficulties only when well-fed
        // and nothing else hurt you this tick.
        if (!dead && health < MAX_HEALTH && (difficulty == Difficulty.PEACEFUL
                || (!tookDamage && hunger >= REGEN_HUNGER_THRESHOLD && thirst >= REGEN_THIRST_THRESHOLD))) {
            health = Math.min(MAX_HEALTH, health + REGEN_PER_SECOND * difficulty.healthRegenMultiplier() * dt);
        }
    }

    public void damage(float amount) {
        if (dead || amount <= 0f) return;
        float damageDealt = amount * armorMultiplier;
        frameDamageAccumulator += damageDealt;
        health -= damageDealt;
        if (health <= 0f) {
            health = 0f;
            dead = true;
        }
    }

    public void eat(int foodValue) {
        hunger = Math.min(MAX_HUNGER, hunger + foodValue);
    }
}
