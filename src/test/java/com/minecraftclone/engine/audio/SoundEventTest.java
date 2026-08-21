package com.minecraftclone.engine.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SoundEventTest {

    @Test
    void everyEventHasACategory() {
        for (SoundEvent e : SoundEvent.values()) {
            assertNotNull(e.category(), e + " has no category");
        }
    }

    @Test
    void categoriesMatchTheExpectedGrouping() {
        assertEquals(SoundCategory.UI, SoundEvent.UI_CLICK.category());
        assertEquals(SoundCategory.UI, SoundEvent.UI_OPEN.category());
        assertEquals(SoundCategory.UI, SoundEvent.UI_CLOSE.category());
        assertEquals(SoundCategory.PLAYER, SoundEvent.JUMP.category());
        assertEquals(SoundCategory.PLAYER, SoundEvent.LAND.category());
        assertEquals(SoundCategory.PLAYER, SoundEvent.SPLASH.category());
        assertEquals(SoundCategory.PLAYER, SoundEvent.EAT.category());
        assertEquals(SoundCategory.PLAYER, SoundEvent.HURT.category());
        assertEquals(SoundCategory.PLAYER, SoundEvent.DEATH.category());
        assertEquals(SoundCategory.PLAYER, SoundEvent.ITEM_PICKUP.category());
        assertEquals(SoundCategory.MOBS, SoundEvent.ATTACK.category());
        assertEquals(SoundCategory.MOBS, SoundEvent.MOB_DEATH.category());
        assertEquals(SoundCategory.MACHINES, SoundEvent.TOOL_BREAK.category());
        assertEquals(SoundCategory.MACHINES, SoundEvent.DOOR.category());
        assertEquals(SoundCategory.MACHINES, SoundEvent.CHEST_OPEN.category());
        assertEquals(SoundCategory.MACHINES, SoundEvent.CHEST_CLOSE.category());
        assertEquals(SoundCategory.MACHINES, SoundEvent.CRAFT.category());
    }
}
