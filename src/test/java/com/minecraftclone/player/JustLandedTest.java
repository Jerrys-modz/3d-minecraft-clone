package com.minecraftclone.player;

import org.junit.jupiter.api.Test;

import static com.minecraftclone.player.Player.computeJustLanded;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JustLandedTest {

    private static final float HARD = 10f;   // well above the min landing speed
    private static final float SOFT = 0.5f;  // a trivial step-down, not a real fall

    @Test
    void notArmedYetNeverLands() {
        // Still falling from spawn, hasn't touched ground once: not armed.
        assertFalse(computeJustLanded(false, true, false, HARD));
    }

    @Test
    void firstTouchdownAfterSpawnIsNotAJustLandedEvent() {
        // Regression: an earlier fix armed landing detection *before*
        // checking it on the same frame, which meant the very touchdown that
        // arms it (falling in from spawn) still counted as a "just landed"
        // thump - exactly the spurious spawn-landing sound this gate exists
        // to suppress. computeJustLanded must be checked against the frame's
        // *incoming* armed state, so this call - armed=false, the state
        // before Player arms it for next frame - must stay false regardless
        // of fall speed.
        assertFalse(computeJustLanded(false, true, false, HARD));
    }

    @Test
    void aRealLandingAfterBeingArmedTriggersTheSound() {
        assertTrue(computeJustLanded(true, true, false, HARD));
    }

    @Test
    void restingOnTheGroundEveryFrameAfterIsNotRepeatedLanding() {
        // wasOnGround=true here means onGround didn't just flip - already resting.
        assertFalse(computeJustLanded(true, true, true, HARD));
    }

    @Test
    void aTrivialStepDownDoesNotCountAsLanding() {
        assertFalse(computeJustLanded(true, true, false, SOFT));
    }

    @Test
    void stillAirborneIsNeverALanding() {
        assertFalse(computeJustLanded(true, false, false, HARD));
    }
}
