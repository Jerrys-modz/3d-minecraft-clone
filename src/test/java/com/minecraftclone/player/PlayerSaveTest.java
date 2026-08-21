package com.minecraftclone.player;

import com.minecraftclone.GameMode;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.DimensionType;
import com.minecraftclone.world.Mining;
import com.minecraftclone.world.tinkers.TinkersItem;
import com.minecraftclone.world.tinkers.ToolPartType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlayerSaveTest {

    @TempDir
    Path dir;

    @Test
    void missingFileLoadsAsNull() {
        assertNull(PlayerSave.load(dir));
    }

    @Test
    void positionLookInventoryAndBedSpawnRoundTrip() {
        Player p = new Player();
        p.setGameMode(GameMode.CREATIVE);
        p.teleportTo(42.5f, 68.25f, -11.5f);
        p.getCamera().setYaw(45f);
        p.getCamera().setPitch(-12f);
        p.setFlying(true);
        p.setSpawnPoint(10, 64, 20);
        p.getInventory().setStack(0, ItemStack.of(BlockType.DIRT, 32));
        p.getInventory().setStack(3, ItemStack.of(BlockType.DIAMOND_PICKAXE, 1));
        p.getInventory().setArmor(Inventory.ARMOR_SLOT_HELMET, BlockType.IRON_HELMET);
        p.getStats().restore(70f, 40f, 55f, 80f);
        p.getDurability().wear(BlockType.DIAMOND_PICKAXE, 10);

        PlayerSave.capture(p, DimensionType.NETHER, 3).save(dir);

        Player loaded = new Player();
        loaded.setGameMode(GameMode.CREATIVE);
        PlayerSave save = PlayerSave.load(dir);
        assertNotNull(save);
        int slot = save.applyTo(loaded);

        assertEquals(42.5f, loaded.getPosition().x, 0.001f);
        assertEquals(68.25f, loaded.getPosition().y, 0.001f);
        assertEquals(-11.5f, loaded.getPosition().z, 0.001f);
        assertEquals(45f, loaded.getCamera().getYaw(), 0.01f);
        assertEquals(-12f, loaded.getCamera().getPitch(), 0.01f);
        assertEquals(DimensionType.NETHER, save.dimension());
        assertEquals(3, slot);
        assertTrue(loaded.isFlying());
        assertTrue(loaded.hasSpawnPoint());
        assertEquals(10, loaded.spawnX());
        assertEquals(64, loaded.spawnY());
        assertEquals(20, loaded.spawnZ());
        assertEquals(BlockType.DIRT, loaded.getInventory().typeOf(0));
        assertEquals(32, loaded.getInventory().countOf(0));
        assertEquals(BlockType.DIAMOND_PICKAXE, loaded.getInventory().typeOf(3));
        assertEquals(BlockType.IRON_HELMET, loaded.getInventory().armorType(Inventory.ARMOR_SLOT_HELMET));
        assertEquals(70f, loaded.getStats().getHealth(), 0.01f);
        assertEquals(40f, loaded.getStats().getHunger(), 0.01f);
        int max = Mining.toolStats(BlockType.DIAMOND_PICKAXE).maxUses();
        assertEquals(max - 10, loaded.getDurability().remaining(BlockType.DIAMOND_PICKAXE));
    }

    @Test
    void tinkersPartAndToolRoundTripThroughText() {
        ItemStack part = ItemStack.tinkersPart(new TinkersItem.Part(ToolPartType.PICK_HEAD, BlockType.IRON_INGOT));
        ItemStack tool = ItemStack.tinkersTool(new TinkersItem.Tool(Mining.ToolKind.PICKAXE, List.of(
                new TinkersItem.ToolLayer(ToolPartType.PICK_HEAD, BlockType.IRON_INGOT),
                new TinkersItem.ToolLayer(ToolPartType.TOOL_ROD, BlockType.PLANKS)
        )));
        tool.tinkersTool().setRemaining(17);

        assertEquals(part.tinkersPart().shape, PlayerSave.decodeStack(PlayerSave.encodeStack(part)).tinkersPart().shape);
        ItemStack restored = PlayerSave.decodeStack(PlayerSave.encodeStack(tool));
        assertTrue(restored.isTinkersTool());
        assertEquals(Mining.ToolKind.PICKAXE, restored.tinkersTool().kind);
        assertEquals(17, restored.tinkersTool().remaining());
        assertEquals(BlockType.IRON_INGOT, restored.tinkersTool().headMaterial());
    }

    @Test
    void survivalDoesNotKeepFlightFromSave() {
        Player p = new Player();
        p.setGameMode(GameMode.CREATIVE);
        p.setFlying(true);
        PlayerSave.capture(p, DimensionType.OVERWORLD, 0).save(dir);

        Player loaded = new Player();
        loaded.setGameMode(GameMode.SURVIVAL);
        PlayerSave.load(dir).applyTo(loaded);
        assertFalse(loaded.isFlying(), "survival ignores a creative flight flag");
    }

    @Test
    void resetSessionClearsBedSpawn() {
        Player p = new Player();
        p.setSpawnPoint(1, 2, 3);
        p.resetSession();
        assertFalse(p.hasSpawnPoint());
    }
}
