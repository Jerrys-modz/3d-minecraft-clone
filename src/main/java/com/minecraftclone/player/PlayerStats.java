package com.minecraftclone.player;

/**
 * Health, hunger and stamina: the core survival loop. Damage comes from
 * falling too far, standing in lava, staying submerged too long, or letting
 * hunger hit zero; health slowly regenerates when hunger is high enough.
 * Sprinting costs both stamina (immediate, regenerates fast) and a little
 * extra hunger (permanent until you eat).
 */
public class PlayerStats {

    public static final float MAX_HEALTH = 100f;
    public static final float MAX_HUNGER = 100f;
    public static final float MAX_STAMINA = 100f;

    private static final float SAFE_FALL_SPEED = 10f;          // blocks/sec you can land at with no damage
    private static final float FALL_DAMAGE_PER_SPEED = 3.5f;   // damage per (blocks/sec) over the safe speed
    private static final float LAVA_DAMAGE_PER_SECOND = 20f;
    private static final float FIRE_DAMAGE_PER_SECOND = 6f;   // standing in lightning-lit fire burns
    private static final float COLD_HUNGER_DRAIN_PER_SECOND = 100f / 300f; // ~5 min to empty at full exposure
    private static final float COLD_DAMAGE_PER_SECOND = 2f;   // freezing once hunger is gone
    private static final float DROWN_GRACE_SECONDS = 6f;       // how long you can hold your breath
    private static final float DROWN_DAMAGE_PER_SECOND = 5f;
    private static final float STARVE_DAMAGE_PER_SECOND = 2f;
    private static final float REGEN_HUNGER_THRESHOLD = 50f;   // need at least this much hunger to regenerate health
    private static final float REGEN_PER_SECOND = 1f;
    private static final float HUNGER_DRAIN_PER_SECOND = 100f / (20f * 60f); // empties passively over ~20 minutes
    private static final float HUNGER_SPRINT_EXTRA_DRAIN_PER_SECOND = 0.5f;
    private static final float STAMINA_SPRINT_DRAIN_PER_SECOND = 25f; // ~4s of sprinting from full
    private static final float STAMINA_REGEN_PER_SECOND = 15f;
    private static final float STAMINA_SPRINT_MIN = 10f; // must regen back above this before sprinting resumes once exhausted

    private float health = MAX_HEALTH;
    private float hunger = MAX_HUNGER;
    private float stamina = MAX_STAMINA;
    private float submergedTime = 0f;
    private float coldness = 0f; // 0 (warm) .. 1 (freezing out in a blizzard), set by Player each frame
    private boolean staminaExhausted = false;
    private boolean dead = false;

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

    public float getStamina() {
        return stamina;
    }

    public boolean isDead() {
        return dead;
    }

    public void reset() {
        health = MAX_HEALTH;
        hunger = MAX_HUNGER;
        stamina = MAX_STAMINA;
        submergedTime = 0f;
        coldness = 0f;
        staminaExhausted = false;
        dead = false;
    }

    /** Keeps every stat topped up and the player alive - used in creative/spectator modes. */
    public void forceFull() {
        health = MAX_HEALTH;
        hunger = MAX_HUNGER;
        stamina = MAX_STAMINA;
        submergedTime = 0f;
        coldness = 0f;
        staminaExhausted = false;
        dead = false;
    }

    public boolean canSprint() {
        return !staminaExhausted && stamina > 0.5f;
    }

    /**
     * Advances all three stats by one frame.
     *
     * @param fallImpactSpeed the speed (blocks/sec) the player just landed at this frame, or 0 if not landing.
     */
    public void update(float dt, boolean inLava, boolean inFire, boolean submerged, boolean sprintingAndMoving, float fallImpactSpeed, float coldness) {
        if (dead) return;
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

        float hungerDrain = HUNGER_DRAIN_PER_SECOND + (sprintingAndMoving ? HUNGER_SPRINT_EXTRA_DRAIN_PER_SECOND : 0f);
        hunger = Math.max(0f, hunger - hungerDrain * dt);

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
            submergedTime += dt;
            if (submergedTime > DROWN_GRACE_SECONDS) {
                damage(DROWN_DAMAGE_PER_SECOND * dt);
                tookDamage = true;
            }
        } else {
            submergedTime = 0f;
        }

        if (coldness > 0f) {
            // Freezing out in a storm: the cold burns through hunger first, then
            // health once you've run out of food. Shelter or a warm fire reduces
            // the exposure (see Player). tookDamage stays clear while food lasts
            // so the regen below keeps working.
            hunger = Math.max(0f, hunger - COLD_HUNGER_DRAIN_PER_SECOND * coldness * dt);
            if (hunger <= 0f) {
                damage(COLD_DAMAGE_PER_SECOND * coldness * dt);
                tookDamage = true;
            }
        }

        if (hunger <= 0f) {
            damage(STARVE_DAMAGE_PER_SECOND * dt);
        } else if (!tookDamage && hunger >= REGEN_HUNGER_THRESHOLD && health < MAX_HEALTH) {
            health = Math.min(MAX_HEALTH, health + REGEN_PER_SECOND * dt);
        }
    }

    public void damage(float amount) {
        if (dead || amount <= 0f) return;
        health -= amount;
        if (health <= 0f) {
            health = 0f;
            dead = true;
        }
    }

    public void eat(int foodValue) {
        hunger = Math.min(MAX_HUNGER, hunger + foodValue);
    }
}
