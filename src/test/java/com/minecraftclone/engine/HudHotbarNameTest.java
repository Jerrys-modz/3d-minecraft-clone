package com.minecraftclone.engine;

import com.minecraftclone.player.ItemStack;
import com.minecraftclone.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The selected hotbar item's name sits above the status bars and fades
 * after a hold, like Minecraft — pure timing/layout, no GL.
 */
class HudHotbarNameTest {

    @Test
    void emptySlotHasNoName() {
        assertNull(Hud.hotbarItemName(ItemStack.EMPTY));
        assertNull(Hud.hotbarItemName(null));
    }

    @Test
    void vanillaItemsUseDisplayName() {
        assertEquals("Diamond Pickaxe", Hud.hotbarItemName(ItemStack.of(BlockType.DIAMOND_PICKAXE, 1)));
        assertEquals("Grass", Hud.hotbarItemName(ItemStack.of(BlockType.GRASS, 64)));
        assertEquals("Copper Ore", Hud.hotbarItemName(ItemStack.of(BlockType.COPPER_ORE, 12)));
    }

    @Test
    void fullyVisibleThenFadesThenGone() {
        assertEquals(1f, Hud.hotbarNameAlpha(0f));
        assertEquals(1f, Hud.hotbarNameAlpha(Hud.HOTBAR_NAME_HOLD_SECONDS));
        assertEquals(0.5f, Hud.hotbarNameAlpha(
                Hud.HOTBAR_NAME_HOLD_SECONDS + Hud.HOTBAR_NAME_FADE_SECONDS / 2f), 1e-4f);
        assertEquals(0f, Hud.hotbarNameAlpha(
                Hud.HOTBAR_NAME_HOLD_SECONDS + Hud.HOTBAR_NAME_FADE_SECONDS));
        assertEquals(0f, Hud.hotbarNameAlpha(10f));
    }

    @Test
    void nameSitsAboveTheHotbarAndStatusBars() {
        float y = Hud.hotbarHeldNameY();
        // Hotbar panel top is around -0.835; four bars + gaps push the name up.
        assertTrue(y > -0.80f, "must clear the hotbar, got " + y);
        assertTrue(y < -0.50f, "should stay in the lower third, got " + y);
    }

    @Test
    void titleFromEnumSplitsUnderscores() {
        assertEquals("Pick Head", Hud.titleFromEnum("PICK_HEAD"));
        assertEquals("Pickaxe", Hud.titleFromEnum("PICKAXE"));
    }
}
