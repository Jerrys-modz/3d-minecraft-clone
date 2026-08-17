package com.minecraftclone.player;

import com.minecraftclone.GameMode;
import com.minecraftclone.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Player#takeDamage} and {@link Player#finalizeDamage}: the external-damage
 * path used by mobs, lightning, etc. (see Main), as opposed to the environmental
 * hazards {@link Player#update} applies internally.
 */
class PlayerDamageTest {

    @Test
    void takeDamageIsANoOpWhenInvulnerable() {
        Player p = new Player();
        p.setGameMode(GameMode.CREATIVE);
        float health = p.getStats().getHealth();
        p.takeDamage(50f);
        assertEquals(health, p.getStats().getHealth(), 0.001f, "creative is invulnerable");
    }

    @Test
    void takeDamageRefreshesTheArmorMultiplierFromTheCurrentInventory() {
        // Regression: takeDamage can be called before Player.update() has had a
        // chance to refresh the armor multiplier this frame (e.g. lightning, which
        // runs earlier in Main's loop) - it must not rely on a stale value.
        Player p = new Player();
        p.setGameMode(GameMode.SURVIVAL);
        p.getInventory().setArmor(Inventory.ARMOR_SLOT_CHESTPLATE, BlockType.DIAMOND_CHESTPLATE);
        float health = p.getStats().getHealth();
        p.takeDamage(20f);
        float damageDealt = health - p.getStats().getHealth();
        assertTrue(damageDealt < 20f, "armor mitigation applied even without a prior update() call: " + damageDealt);
        assertTrue(damageDealt > 0f);
    }

    @Test
    void finalizeDamageWearsArmorForEverythingAccumulatedSinceTheLastClear() {
        Player p = new Player();
        p.setGameMode(GameMode.SURVIVAL);
        p.getInventory().setArmor(Inventory.ARMOR_SLOT_HELMET, BlockType.WOOD_HELMET);
        // Two hits in the same "frame" (no finalizeDamage between them) should both
        // count toward wear, not just the last one.
        p.takeDamage(20f);
        p.takeDamage(20f);
        assertTrue(p.getStats().frameDamageAccumulator() > 0f, "damage accumulated across both hits");
        p.finalizeDamage();
        assertEquals(0f, p.getStats().frameDamageAccumulator(), 0.001f, "consumed and cleared");
        assertTrue(p.getDurability().remaining(BlockType.WOOD_HELMET) < 55, "the helmet wore down");
    }

    @Test
    void finalizeDamageIsASafeNoOpWithNothingAccumulated() {
        Player p = new Player();
        assertEquals(0f, p.getStats().frameDamageAccumulator());
        p.finalizeDamage(); // must not throw with an empty accumulator and no armor equipped
        assertNull(p.getInventory().armorType(Inventory.ARMOR_SLOT_HELMET));
    }
}
