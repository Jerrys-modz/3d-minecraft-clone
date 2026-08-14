package com.minecraftclone.player;

import com.minecraftclone.GameMode;
import com.minecraftclone.player.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerGameModeTest {

    @Test
    void survivalAndAdventureStayGrounded() {
        Player p = new Player();
        assertFalse(p.isFlying());
        p.setGameMode(GameMode.SURVIVAL);
        assertFalse(p.isFlying());
        p.setGameMode(GameMode.ADVENTURE);
        assertFalse(p.isFlying());
    }

    @Test
    void spectatorIsAlwaysFlying() {
        Player p = new Player();
        p.setGameMode(GameMode.SPECTATOR);
        assertTrue(p.isFlying());
    }

    @Test
    void leavingCreativeOrSpectatorDropsFlight() {
        Player p = new Player();
        p.setGameMode(GameMode.SPECTATOR);
        p.setGameMode(GameMode.ADVENTURE);
        assertFalse(p.isFlying());
        p.setGameMode(GameMode.CREATIVE);
        p.setGameMode(GameMode.SURVIVAL);
        assertFalse(p.isFlying());
    }
}
