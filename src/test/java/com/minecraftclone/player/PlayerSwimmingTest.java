package com.minecraftclone.player;

import org.junit.jupiter.api.Test;

import static com.minecraftclone.player.Player.computeSwimming;
import static com.minecraftclone.player.Player.landingImpactSpeed;
import static com.minecraftclone.player.Player.swimVerticalVelocity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerSwimmingTest {

    @Test
    void swimsWhenInWaterAirborneAndNotFlying() {
        assertTrue(computeSwimming(false, false, false, true));
    }

    @Test
    void flyingAlwaysOverridesSwimming() {
        assertFalse(computeSwimming(true, false, false, true));
    }

    @Test
    void spectatorNeverSwims() {
        assertFalse(computeSwimming(false, true, false, true));
    }

    @Test
    void standingOnTheBottomIsWadingNotSwimming() {
        // Feet touching a solid floor under shallow water - just walk, don't swim.
        assertFalse(computeSwimming(false, false, true, true));
    }

    @Test
    void dryLandIsNeverSwimming() {
        assertFalse(computeSwimming(false, false, false, false));
    }

    @Test
    void strokingUpMovesAtAFixedUpwardSpeedRegardlessOfCurrentVelocity() {
        float fromRest = swimVerticalVelocity(true, false, 0f, 0.1f);
        float fromFalling = swimVerticalVelocity(true, false, -20f, 0.1f);
        assertTrue(fromRest > 0f);
        assertEquals(fromRest, fromFalling, 1e-6f,
                "a stroke snaps to a fixed speed, it doesn't accelerate from wherever you were");
    }

    @Test
    void strokingDownMovesAtTheMirroredDownwardSpeed() {
        float up = swimVerticalVelocity(true, false, 0f, 0.1f);
        float down = swimVerticalVelocity(false, true, 0f, 0.1f);
        assertEquals(-up, down, 1e-6f);
    }

    @Test
    void strokingUpWinsIfBothDirectionsAreHeld() {
        float up = swimVerticalVelocity(true, false, 0f, 0.1f);
        float both = swimVerticalVelocity(true, true, 0f, 0.1f);
        assertEquals(up, both, 1e-6f);
    }

    @Test
    void lettingGoSinksGraduallyRatherThanFreeFalling() {
        float afterOneStep = swimVerticalVelocity(false, false, 0f, 0.1f);
        assertTrue(afterOneStep < 0f, "buoyancy still pulls you down a little");
        assertTrue(afterOneStep > -2f, "but nowhere near a full-gravity free-fall speed after just 0.1s: " + afterOneStep);
    }

    @Test
    void sinkingIsCappedAndNeverAccelaratesPastIt() {
        float cap = swimVerticalVelocity(false, false, 0f, 1000f); // one huge step finds the floor
        assertTrue(cap < 0f);
        // Sinking from well past the cap should clamp back up to it, not overshoot further down.
        float fromWayBelow = swimVerticalVelocity(false, false, cap - 5f, 0.1f);
        assertEquals(cap, fromWayBelow, 1e-6f);
        // And a normal small step starting exactly at the cap stays exactly at the cap.
        float steadyState = swimVerticalVelocity(false, false, cap, 0.1f);
        assertEquals(cap, steadyState, 1e-6f);
    }

    @Test
    void divingInDeceleratesFastEnoughToAvoidFallDamageBeforeHittingBottom() {
        // A hard fall (e.g. off a cliff into a lake) enters the water at a large
        // negative velocity - one physics step of swim buoyancy must immediately
        // clamp that down toward the sink cap rather than carrying the fall speed
        // through, since that's what keeps a dive from still registering as a
        // damaging impact once the player reaches a real lakebed.
        float enteredAt = -30f; // a lethal fall speed on dry land
        float afterOneStep = swimVerticalVelocity(false, false, enteredAt, 0.1f);
        assertTrue(afterOneStep > enteredAt, "buoyancy must slow the fall immediately: " + afterOneStep);
        assertTrue(afterOneStep > -5f, "and land well below a damaging speed after just one step: " + afterOneStep);
    }

    @Test
    void landingOnDryGroundRecordsTheRawImpactSpeed() {
        assertEquals(40f, landingImpactSpeed(40f, false), 1e-6f);
    }

    @Test
    void highSpeedEntryIntoOneBlockDeepWaterIsCappedLikeANormalSwimLanding() {
        // Regression: a fast fall can cross a shallow puddle and hit the solid
        // floor beneath it within a single physics step, never getting a full
        // frame of swim buoyancy first (updateMovement's swimming check runs
        // against the position *before* that same step) - moveAndCollide must
        // still cap the recorded impact when the landing spot itself is water,
        // exactly as if buoyancy had gotten a chance to slow the fall down.
        float lethalFallSpeed = 40f;
        float capped = landingImpactSpeed(lethalFallSpeed, true);
        assertTrue(capped < lethalFallSpeed, "must not record the raw fall speed: " + capped);
        // The cap must match what a genuine frame of buoyancy would have produced,
        // so a shallow-puddle landing and a deep-water landing feel consistent.
        float buoyancyCap = -swimVerticalVelocity(false, false, 0f, 1000f);
        assertEquals(buoyancyCap, capped, 1e-6f);
    }

    @Test
    void landingInWaterNeverMakesAGentleFallWorse() {
        // A landing speed already below the water cap (e.g. stepping off a low
        // ledge into a puddle) should be recorded as-is, not raised up to the cap.
        float gentle = 0.2f;
        assertEquals(gentle, landingImpactSpeed(gentle, true), 1e-6f);
    }
}
