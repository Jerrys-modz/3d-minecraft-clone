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
    void alreadyFlyingNeverGetsToggledOffByADoubleTap() {
        // Regression: this used to toggle flight off, dropping the player
        // straight out of the sky on any stray double-tap while airborne.
        assertEquals(NONE, decideDoubleTapWAction(true, true, true));
    }

    @Test
    void alreadyFlyingDoesNotLatchSprintEither() {
        // Non-creative "flying" only happens in spectator, where sprint is
        // meaningless anyway - still shouldn't do anything.
        assertEquals(NONE, decideDoubleTapWAction(true, true, false));
    }
}
