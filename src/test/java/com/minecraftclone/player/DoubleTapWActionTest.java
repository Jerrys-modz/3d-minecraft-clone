package com.minecraftclone.player;

import org.junit.jupiter.api.Test;

import static com.minecraftclone.player.Player.WTapAction.*;
import static com.minecraftclone.player.Player.decideDoubleTapWAction;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DoubleTapWActionTest {

    @Test
    void noPressIsNeverAnAction() {
        assertEquals(NONE, decideDoubleTapWAction(false, false, true));
        assertEquals(NONE, decideDoubleTapWAction(false, false, false));
    }

    @Test
    void creativeDoubleTapStartsFlyingWhenGrounded() {
        assertEquals(START_FLYING, decideDoubleTapWAction(true, false, true));
    }

    @Test
    void survivalDoubleTapSprintsWhenNotFlying() {
        assertEquals(SPRINT, decideDoubleTapWAction(true, false, false));
    }

    @Test
    void alreadyFlyingSpeedsUpInsteadOfTakingOffAgain() {
        // Regression: this used to toggle flight off (flying = !flying),
        // dropping the player straight out of the sky on any stray
        // double-tap while airborne. It must never yield START_FLYING once
        // already flying - SPRINT is reused as the flight-speed-boost latch.
        assertEquals(SPRINT, decideDoubleTapWAction(true, true, true));
    }

    @Test
    void spectatorDoubleTapAlsoSpeedsUpFlight() {
        // Non-creative "flying" only happens in spectator - still a speed boost.
        assertEquals(SPRINT, decideDoubleTapWAction(true, true, false));
    }
}
