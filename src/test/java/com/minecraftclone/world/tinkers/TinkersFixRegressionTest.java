package com.minecraftclone.world.tinkers;

import com.minecraftclone.player.ItemStack;
import com.minecraftclone.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for bugs fixed per CodeRabbit review on PR #74.
 *
 * <ul>
 *   <li>{@code TinkersItem.Tool.use()} must not decrement below zero or fire
 *       a false "broke" signal after the tool is already broken.</li>
 *   <li>{@code TinkersItem.Tool.fraction()} must clamp to [0, 1].</li>
 *   <li>{@code TinkersRegistry.register()} must reject null, non-positive
 *       durability, non-positive/non-finite mining speed, and negative tier.</li>
 * </ul>
 */
class TinkersFixRegressionTest {

    // -----------------------------------------------------------------------
    // TinkersItem.Tool.use() — below-zero regression
    // -----------------------------------------------------------------------

    private static TinkersItem.Tool ironPickaxe() {
        return new TinkersItem.Tool(
                com.minecraftclone.world.Mining.ToolKind.PICKAXE,
                java.util.List.of(
                        new TinkersItem.ToolLayer(ToolPartType.PICK_HEAD, BlockType.IRON_INGOT),
                        new TinkersItem.ToolLayer(ToolPartType.TOOL_ROD,  BlockType.PLANKS)
                )
        );
    }

    @Test
    void useReturnsTrueExactlyWhenDurabilityReachesZero() {
        TinkersItem.Tool tool = ironPickaxe();
        int max = tool.maxDurability;
        assertTrue(max > 0, "maxDurability must be positive");

        boolean brokeSignal = false;
        for (int i = 0; i < max; i++) {
            boolean result = tool.use();
            if (result) {
                brokeSignal = true;
                assertEquals(max - 1, i, "Broke signal must fire on the last use (index max-1)");
            }
        }
        assertTrue(brokeSignal, "use() must have returned true exactly once");
    }

    @Test
    void useReturnsFalseWhenAlreadyBroken() {
        TinkersItem.Tool tool = ironPickaxe();
        for (int i = 0; i < tool.maxDurability; i++) tool.use();

        // Calling use() on a broken tool must return false, not true again
        assertFalse(tool.use(), "use() on a broken tool must return false");
        assertFalse(tool.use(), "use() on a broken tool must always return false");
    }

    @Test
    void remainingNeverGoesNegative() {
        TinkersItem.Tool tool = ironPickaxe();
        for (int i = 0; i < tool.maxDurability + 5; i++) tool.use();
        assertEquals(0, tool.remaining(), "remaining() must not go below 0");
    }

    @Test
    void fractionIsClampedToZeroWhenBroken() {
        TinkersItem.Tool tool = ironPickaxe();
        for (int i = 0; i < tool.maxDurability; i++) tool.use();
        assertEquals(0.0f, tool.fraction(), 0.001f, "fraction() must be 0 when fully broken");
    }

    @Test
    void fractionIsOneWhenFresh() {
        TinkersItem.Tool tool = ironPickaxe();
        assertEquals(1.0f, tool.fraction(), 0.001f, "fraction() must be 1.0 for a fresh tool");
    }

    // -----------------------------------------------------------------------
    // TinkersRegistry.register() — validation regression
    // -----------------------------------------------------------------------

    @Test
    void registerRejectsNullStats() {
        assertThrows(IllegalArgumentException.class,
                () -> TinkersRegistry.register(BlockType.COAL, null),
                "null stats must be rejected");
    }

    @Test
    void registerRejectsZeroDurability() {
        assertThrows(IllegalArgumentException.class,
                () -> TinkersRegistry.register(BlockType.COAL,
                        new TinkersMaterialStats(0, 1.0f, 1, 0)),
                "zero durability must be rejected");
    }

    @Test
    void registerRejectsNegativeDurability() {
        assertThrows(IllegalArgumentException.class,
                () -> TinkersRegistry.register(BlockType.COAL,
                        new TinkersMaterialStats(-1, 1.0f, 1, 0)),
                "negative durability must be rejected");
    }

    @Test
    void registerRejectsZeroMiningSpeed() {
        assertThrows(IllegalArgumentException.class,
                () -> TinkersRegistry.register(BlockType.COAL,
                        new TinkersMaterialStats(100, 0f, 1, 0)),
                "zero miningSpeed must be rejected");
    }

    @Test
    void registerRejectsNegativeMiningSpeed() {
        assertThrows(IllegalArgumentException.class,
                () -> TinkersRegistry.register(BlockType.COAL,
                        new TinkersMaterialStats(100, -1f, 1, 0)),
                "negative miningSpeed must be rejected");
    }

    @Test
    void registerRejectsInfiniteMiningSpeed() {
        assertThrows(IllegalArgumentException.class,
                () -> TinkersRegistry.register(BlockType.COAL,
                        new TinkersMaterialStats(100, Float.POSITIVE_INFINITY, 1, 0)),
                "infinite miningSpeed must be rejected");
    }

    @Test
    void registerRejectsNaNMiningSpeed() {
        assertThrows(IllegalArgumentException.class,
                () -> TinkersRegistry.register(BlockType.COAL,
                        new TinkersMaterialStats(100, Float.NaN, 1, 0)),
                "NaN miningSpeed must be rejected");
    }

    @Test
    void registerRejectsNegativeMiningTier() {
        assertThrows(IllegalArgumentException.class,
                () -> TinkersRegistry.register(BlockType.COAL,
                        new TinkersMaterialStats(100, 1f, -1, 0)),
                "negative miningTier must be rejected");
    }
}
