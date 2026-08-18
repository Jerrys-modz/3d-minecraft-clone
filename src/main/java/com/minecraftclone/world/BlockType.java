package com.minecraftclone.world;

import com.minecraftclone.engine.graphics.TextureAtlas;
import com.minecraftclone.util.AABB;

/**
 * All placeable/generatable block types. Each entry defines which tile of
 * the procedural texture atlas is used for the top, side and bottom faces,
 * plus a few gameplay flags (solidity, transparency, cross-shape, food value).
 * <p>
 * Atlas tile indices refer to {@link com.minecraftclone.engine.graphics.TextureAtlas}.
 * <p>
 * <b>Adding a new block</b> (5 steps, all one-liners except the texture):
 * <ol>
 *   <li>Add an enum constant here with a <b>unique, never-reused id</b> - ids are
 *       persisted in save files, so reusing an old id would corrupt existing worlds
 *       (a duplicate id fails fast at startup).</li>
 *   <li>Paint its texture in {@code TextureAtlas.buildImage()} (or point it at an
 *       existing tile index).</li>
 *   <li>Give it break times in {@link com.minecraftclone.world.Mining}: {@code put(type, hardness, tool, tier)}.</li>
 *   <li>Add a {@link com.minecraftclone.player.Crafting#shaped} / {@code #shapeless}
 *       recipe, or a {@code Smelting#outputFor} smelting entry, if it's made from ingredients.</li>
 *   <li>Add it to a tab in {@link com.minecraftclone.player.CreativeCatalog} so creative mode offers it.</li>
 * </ol>
 */
public enum BlockType {
    AIR(0, false, true, 0, 0, 0),
    GRASS(1, true, false, 1, 2, 3),
    DIRT(2, true, false, 3, 3, 3),
    STONE(3, true, false, 4, 4, 4),
    SAND(4, true, false, 5, 5, 5),
    WATER(5, false, true, 6, 6, 6),
    WOOD_LOG(6, true, false, 8, 7, 8),
    LEAVES(7, true, true, 9, 9, 9),
    BEDROCK(8, true, false, 10, 10, 10),
    PLANKS(9, true, false, 11, 11, 11),
    SNOW(10, true, false, 12, 13, 3),
    GRAVEL(11, true, false, 14, 14, 14),
    CACTUS(12, true, false, 15, 15, 15),
    COAL_ORE(13, true, false, 16, 16, 16),
    IRON_ORE(14, true, false, 17, 17, 17),
    GOLD_ORE(15, true, false, 18, 18, 18),
    DIAMOND_ORE(16, true, false, 19, 19, 19),
    LAVA(17, false, true, 20, 20, 20),
    TALL_GRASS(18, false, true, 21),
    FLOWER_RED(19, false, true, 22),
    FLOWER_YELLOW(20, false, true, 23),
    GLASS(21, true, false, 34, 34, 34),
    BERRY_BUSH(22, false, true, 37),
    TORCH(38, false, true, 38, 8), // cross-shaped, non-collidable, and a light source (see lightLevel)
    FIRE(91, false, true, TextureAtlas.FIRE_TILE, 14), // transient burning block lit by lightning; never placeable
    LAMP(39, true, false, 25, 25, 25, 0, 15), // full-cube light source, brighter than a torch
    FURNACE(40, true, false, 4, 4, 4, 26, TextureAtlas.FURNACE_LIT_TILE, 0, 0), // smelting station: stone top/bottom/sides, furnace-face front (tile 26) that glows when burning
    STONE_SLAB(44, true, false, true, 4),   // bottom-half slab, stone texture
    PLANKS_SLAB(45, true, false, true, 11), // bottom-half slab, planks texture
    // Fluids: SOURCE variants are placeable and flow (see FluidSim); the WATER/LAVA
    // above are the static terrain fills that don't move. FLOW variants are the
    // transient flowing cells the simulation fills in and dries up.
    WATER_SOURCE(46, false, true, 6, 6, 6),
    WATER_FLOW(47, false, true, 6, 6, 6),
    LAVA_SOURCE(48, false, true, 20, 20, 20),
    LAVA_FLOW(49, false, true, 20, 20, 20),
    // Biome-specific surface blocks and decorations.
    SWAMP_GRASS(50, true, false, 27, 28, 3),
    RED_CLAY(51, true, false, 29, 29, 29),
    MYCELIUM(52, true, false, 30, 31, 3),
    ICE(53, true, true, 32, 32, 32),
    DEAD_BUSH(54, false, true, 33),
    MUSHROOM_RED(55, false, true, 35),
    MUSHROOM_BROWN(56, false, true, 36),
    VINE(57, false, true, 39),
    CHERRY_LEAVES(58, true, true, 40, 40, 40),
    PACKED_ICE(59, true, false, 41),
    BAMBOO(60, false, true, 42),
    LILY_PAD(61, false, true, 43),
    PUMPKIN(62, true, false, 44, 44, 44),
    SEAWEED(63, false, true, 45),
    DOOR(64, true, false, 46, 46, 46),
    DOOR_OPEN(70, false, true, 46),
    TRAPDOOR(71, true, false, 47, 47, 47),
    TRAPDOOR_OPEN(72, false, true, 47),
    CRAFTING_TABLE(73, true, false, 48, 48, 48), // workbench: right-click opens the 3x3 crafting GUI
    ADVANCED_CRAFTING_TABLE(137, true, false, 48, 48, 48), // advanced workbench: right-click opens the 5x5 crafting GUI
    CHEST(87, true, false, 50, 50, 50),          // storage: right-click opens a 27-slot container GUI (54 when doubled)
    BARREL(88, true, false, 51, 51, 51),         // storage: cheaper single 27-slot container, never doubles

    // Dimension blocks: Nether terrain, End terrain, and the portal blocks that
    // link the dimensions (see DimensionType.portalDestination). Portals are
    // non-solid glowing swirls you walk into to teleport.
    NETHERRACK(96, true, false, 52, 52, 52),
    SOUL_SAND(97, true, false, 53, 53, 53),
    GLOWSTONE(98, true, false, 54, 54, 54, 0, 15), // dim self-light source
    NETHER_PORTAL(92, false, true, 55, 55, 55, 0, 15),
    END_STONE(93, true, false, 56, 56, 56),
    OBSIDIAN(94, true, false, 57, 57, 57),
    END_PORTAL(95, false, true, 58, 58, 58, 0, 15),

    // Inventory-only items: food and tools. Never placed as a world block,
    // so they have no atlas tile - each gets its own procedurally generated
    // texture instead, see com.minecraftclone.engine.graphics.ItemTextures.
    // Mining stats for
    // tools (kind/tier/durability) live in Mining.java, not here, to keep
    // this enum focused on rendering/collision.
    APPLE(23, 20),   // food: restores 20 hunger
    BERRIES(24, 10), // food: restores 10 hunger
    STICK(25, 0),
    WOOD_PICKAXE(26, 0),
    STONE_PICKAXE(27, 0),
    IRON_PICKAXE(28, 0),
    DIAMOND_PICKAXE(29, 0),
    WOOD_AXE(30, 0),
    STONE_AXE(31, 0),
    IRON_AXE(32, 0),
    DIAMOND_AXE(33, 0),
    WOOD_SWORD(34, 0),
    STONE_SWORD(35, 0),
    IRON_SWORD(36, 0),
    DIAMOND_SWORD(37, 0),
    // Shovels, hammers and broadaxes - the rest of the tool set (see Mining):
    // shovel = soft ground, hammer = stone/building, broadaxe = wood (faster than an axe).
    WOOD_SHOVEL(75, 0),
    STONE_SHOVEL(76, 0),
    IRON_SHOVEL(77, 0),
    DIAMOND_SHOVEL(78, 0),
    WOOD_HAMMER(79, 0),
    STONE_HAMMER(80, 0),
    IRON_HAMMER(81, 0),
    DIAMOND_HAMMER(82, 0),
    WOOD_BROADAXE(83, 0),
    STONE_BROADAXE(84, 0),
    IRON_BROADAXE(85, 0),
    DIAMOND_BROADAXE(86, 0),
    IRON_INGOT(41, 0), // smelted from iron ore (see Smelting)
    GOLD_INGOT(42, 0), // smelted from gold ore
    DIAMOND(43, 0),    // smelted from diamond ore
    COAL(74, 0),       // mined from coal ore, the furnace fuel (see Smelting)
    // Raw meat - dropped by passive mobs when killed (see World.damageMob), edible.
    RAW_PORKCHOP(65, 16),
    RAW_BEEF(66, 16),
    MUTTON(67, 12),
    // Hostile-mob loot - see World.damageMob. Rotten flesh is barely edible.
    ROTTEN_FLESH(68, 4),
    BONES(69, 0),
    // Wool - sheared from sheep (see World.damageMob). The material fur armor is
    // made from, and a warm clothing resource in its own right. Placeable as a block.
    WOOL(115, true, false, 59, 59, 59),
    // Snow-capped slabs: a bottom-half slab that's been covered by accumulating
    // snow (see World.tryAddSnow). Full-height solid blocks that MESH as a slab
    // under a snow cap, so the snow sits flush rather than floating above the
    // slab's half-height top. Melting restores the plain slab underneath.
    SNOWY_STONE_SLAB(89, true, false, 12, 4, 4),
    SNOWY_PLANKS_SLAB(90, true, false, 12, 11, 11),

    // Armor pieces (inventory-only items, like tools): one per slot, per tier.
    // Stats (defense points, durability) live in Armor.java, not here, to keep
    // this enum focused on rendering/collision.
    WOOD_HELMET(99, 0),
    WOOD_CHESTPLATE(100, 0),
    WOOD_LEGGINGS(101, 0),
    WOOD_BOOTS(102, 0),
    STONE_HELMET(103, 0),
    STONE_CHESTPLATE(104, 0),
    STONE_LEGGINGS(105, 0),
    STONE_BOOTS(106, 0),
    IRON_HELMET(107, 0),
    IRON_CHESTPLATE(108, 0),
    IRON_LEGGINGS(109, 0),
    IRON_BOOTS(110, 0),
    DIAMOND_HELMET(111, 0),
    DIAMOND_CHESTPLATE(112, 0),
    DIAMOND_LEGGINGS(113, 0),
    DIAMOND_BOOTS(114, 0),
    // Fur armor - the warmest tier (see Armor.java warmth), crafted from wool
    // (sheep). The least defensive but by far the best against the cold, so
    // winter survival wants a set even though combat prefers metal.
    FUR_HELMET(116, 0),
    FUR_CHESTPLATE(117, 0),
    FUR_LEGGINGS(118, 0),
    FUR_BOOTS(119, 0),
    // Wolf-pelt armor - the second fur tier (see Armor.java warmth), from the
    // hostile wolves that stalk forests. Warmer and tougher than sheep wool.
    WOLF_PELT(120, 0),
    WOLF_HELMET(121, 0),
    WOLF_CHESTPLATE(122, 0),
    WOLF_LEGGINGS(123, 0),
    WOLF_BOOTS(124, 0),
    // Polar-bear armor - the rarest, warmest, toughest fur tier, from the huge
    // hostile bears that roam the frozen wastes. A full set is warm enough to
    // shrug off even the deepest blizzard.
    BEAR_HIDE(125, 0),
    BEAR_HELMET(126, 0),
    BEAR_CHESTPLATE(127, 0),
    BEAR_LEGGINGS(128, 0),
    BEAR_BOOTS(129, 0),

    // Stairs: partial-cube blocks with a stepped profile, facing the direction
    // they were placed (their orientation byte). Stone and planks match their
    // Stairs: partial-cube blocks with a stepped profile, facing the direction
    // they were placed (their orientation byte). Stone and planks match their
    // full-cube materials' tiles. Collision is stepped: the low front step and
    // the raised back step each get their own box, so a player can walk up
    // stairs block by block.
    STONE_STAIRS(130, 4, true, false, 1.0f),
    PLANKS_STAIRS(131, 11, true, false, 1.0f),
    // Fences: thin posts with rails that auto-connect to neighbouring fences
    // and solid blocks. One tile (planks) for the whole mesh. Collision is a
    // 1.5-block-tall box (the post is taller than a block), so neither the
    // player nor mobs can jump over a fence (jump height is ~1.4 blocks).
    WOODEN_FENCE(132, 11, false, true, 1.5f),

    // Beds: a 1x2 sleeping surface (like Minecraft). BED is the foot end, BED_HEAD
    // is the pillow/head end. Both halves are solid, directional, and half-height.
    // Beds cannot be placed in the Nether or End (they explode in nether like vanilla).
    BED(133, 60, 61, 60, 62, 62, 0.5f),
    BED_HEAD(134, 63, 63, 63, 63, 63, 0.5f),
    BED_OCCUPIED(135, 60, 61, 60, 62, 62, 0.5f),
    BED_HEAD_OCCUPIED(136, 63, 63, 63, 63, 63, 0.5f),

    // --- GTNH Ores: 20 ore types × 4 stages (ore, crushed, dust, ingot) ---
    // Early game ores (stone tier) - tiles 80-85
    COPPER_ORE(138, true, false, 80, 80, 80),
    CRUSHED_COPPER(139, 0),
    COPPER_DUST(140, 0),
    COPPER_INGOT(141, 0),

    TIN_ORE(142, true, false, 81, 81, 81),
    CRUSHED_TIN(143, 0),
    TIN_DUST(144, 0),
    TIN_INGOT(145, 0),

    BAUXITE_ORE(146, true, false, 82, 82, 82),
    CRUSHED_BAUXITE(147, 0),
    BAUXITE_DUST(148, 0),
    ALUMINUM_INGOT(149, 0),

    ZINC_ORE(150, true, false, 83, 83, 83),
    CRUSHED_ZINC(151, 0),
    ZINC_DUST(152, 0),
    ZINC_INGOT(153, 0),

    LEAD_ORE(154, true, false, 84, 84, 84),
    CRUSHED_LEAD(155, 0),
    LEAD_DUST(156, 0),
    LEAD_INGOT(157, 0),

    SILVER_ORE(158, true, false, 85, 85, 85),
    CRUSHED_SILVER(159, 0),
    SILVER_DUST(160, 0),
    SILVER_INGOT(161, 0),

    // Mid-game ores (iron tier) - tiles 86-90
    NICKEL_ORE(162, true, false, 86, 86, 86),
    CRUSHED_NICKEL(163, 0),
    NICKEL_DUST(164, 0),
    NICKEL_INGOT(165, 0),

    COBALT_ORE(166, true, false, 87, 87, 87),
    CRUSHED_COBALT(167, 0),
    COBALT_DUST(168, 0),
    COBALT_INGOT(169, 0),

    TUNGSTEN_ORE(170, true, false, 88, 88, 88),
    CRUSHED_TUNGSTEN(171, 0),
    TUNGSTEN_DUST(172, 0),
    TUNGSTEN_INGOT(173, 0),

    MOLYBDENUM_ORE(174, true, false, 89, 89, 89),
    CRUSHED_MOLYBDENUM(175, 0),
    MOLYBDENUM_DUST(176, 0),
    MOLYBDENUM_INGOT(177, 0),

    PLATINUM_ORE(178, true, false, 90, 90, 90),
    CRUSHED_PLATINUM(179, 0),
    PLATINUM_DUST(180, 0),
    PLATINUM_INGOT(181, 0),

    // Advanced ores (diamond tier) - tiles 91-95
    CHROMIUM_ORE(182, true, false, 91, 91, 91),
    CRUSHED_CHROMIUM(183, 0),
    CHROMIUM_DUST(184, 0),
    CHROMIUM_INGOT(185, 0),

    MANGANESE_ORE(186, true, false, 92, 92, 92),
    CRUSHED_MANGANESE(187, 0),
    MANGANESE_DUST(188, 0),
    MANGANESE_INGOT(189, 0),

    VANADIUM_ORE(190, true, false, 93, 93, 93),
    CRUSHED_VANADIUM(191, 0),
    VANADIUM_DUST(192, 0),
    VANADIUM_INGOT(193, 0),

    BERYLLIUM_ORE(194, true, false, 94, 94, 94),
    CRUSHED_BERYLLIUM(195, 0),
    BERYLLIUM_DUST(196, 0),
    BERYLLIUM_INGOT(197, 0),

    TITANIUM_ORE(198, true, false, 95, 95, 95),
    CRUSHED_TITANIUM(199, 0),
    TITANIUM_DUST(200, 0),
    TITANIUM_INGOT(201, 0),

    // Late-game ores (endgame tier) - tiles 96-99
    URANIUM_ORE(202, true, false, 96, 96, 96),
    CRUSHED_URANIUM(203, 0),
    URANIUM_DUST(204, 0),
    URANIUM_INGOT(205, 0),

    THORIUM_ORE(206, true, false, 97, 97, 97),
    CRUSHED_THORIUM(207, 0),
    THORIUM_DUST(208, 0),
    THORIUM_INGOT(209, 0),

    PLUTONIUM_ORE(210, true, false, 98, 98, 98),
    CRUSHED_PLUTONIUM(211, 0),
    PLUTONIUM_DUST(212, 0),
    PLUTONIUM_INGOT(213, 0),

    IRIDIUM_ORE(214, true, false, 99, 99, 99),
    CRUSHED_IRIDIUM(215, 0),
    IRIDIUM_DUST(216, 0),
    IRIDIUM_INGOT(217, 0),

    // --- Small Ores: Indicator ores mineable at -1 tier (tiles 100-119) ---
    // Early-game small ores (striped appearance, mineable with stone tools)
    SMALL_COPPER_ORE(218, true, false, 100, 100, 100),
    SMALL_TIN_ORE(219, true, false, 101, 101, 101),
    SMALL_BAUXITE_ORE(220, true, false, 102, 102, 102),
    SMALL_ZINC_ORE(221, true, false, 103, 103, 103),
    SMALL_LEAD_ORE(222, true, false, 104, 104, 104),
    SMALL_SILVER_ORE(223, true, false, 105, 105, 105),

    // Mid-game small ores (mineable with iron tools)
    SMALL_NICKEL_ORE(224, true, false, 106, 106, 106),
    SMALL_COBALT_ORE(225, true, false, 107, 107, 107),
    SMALL_TUNGSTEN_ORE(226, true, false, 108, 108, 108),
    SMALL_MOLYBDENUM_ORE(227, true, false, 109, 109, 109),
    SMALL_PLATINUM_ORE(228, true, false, 110, 110, 110),

    // Advanced small ores (mineable with diamond tools)
    SMALL_CHROMIUM_ORE(229, true, false, 111, 111, 111),
    SMALL_MANGANESE_ORE(230, true, false, 112, 112, 112),
    SMALL_VANADIUM_ORE(231, true, false, 113, 113, 113),
    SMALL_BERYLLIUM_ORE(232, true, false, 114, 114, 114),
    SMALL_TITANIUM_ORE(233, true, false, 115, 115, 115),

    // Late-game small ores (mineable with diamond tools)
    SMALL_URANIUM_ORE(234, true, false, 116, 116, 116),
    SMALL_THORIUM_ORE(235, true, false, 117, 117, 117),
    SMALL_PLUTONIUM_ORE(236, true, false, 118, 118, 118),
    SMALL_IRIDIUM_ORE(237, true, false, 119, 119, 119),

    // --- Impure Ore Piles: Secondary drop from small ores (partially processed) ---
    // Note: Limited to 18 items to fit within byte ID range (238-255).
    // Early-game impure ores (6 ores)
    IMPURE_COPPER(238, 0),
    IMPURE_TIN(239, 0),
    IMPURE_BAUXITE(240, 0),
    IMPURE_ZINC(241, 0),
    IMPURE_LEAD(242, 0),
    IMPURE_SILVER(243, 0),

    // Mid-game impure ores (5 ores)
    IMPURE_NICKEL(244, 0),
    IMPURE_COBALT(245, 0),
    IMPURE_TUNGSTEN(246, 0),
    IMPURE_MOLYBDENUM(247, 0),
    IMPURE_PLATINUM(248, 0),

    // Advanced impure ores (4 ores - skipping VANADIUM)
    IMPURE_CHROMIUM(249, 0),
    IMPURE_MANGANESE(250, 0),
    IMPURE_BERYLLIUM(251, 0),
    IMPURE_TITANIUM(252, 0),

    // Late-game impure ores (3 ores - skipping THORIUM)
    IMPURE_URANIUM(253, 0),
    IMPURE_PLUTONIUM(254, 0),
    IMPURE_IRIDIUM(255, 0),

    // --- New GTNH Compound Ores (Phase 2): 60 ore types ---
    // Block IDs 256+, atlas tiles 120-179 for ore blocks, 180-219 for small ores
    // Ores 1-22 and 24-40 have SMALL_ variants; Ferberite (#23) and Naquadah Enriched (#36) do not.

    MAGNETITE_ORE(256, true, false, 120, 120, 120),
    CRUSHED_MAGNETITE(257, 0),
    SMALL_MAGNETITE_ORE(258, true, false, 180, 180, 180),

    HEMATITE_ORE(259, true, false, 121, 121, 121),
    CRUSHED_HEMATITE(260, 0),
    SMALL_HEMATITE_ORE(261, true, false, 181, 181, 181),

    BROWN_LIMONITE_ORE(262, true, false, 122, 122, 122),
    CRUSHED_BROWN_LIMONITE(263, 0),
    SMALL_BROWN_LIMONITE_ORE(264, true, false, 182, 182, 182),

    YELLOW_LIMONITE_ORE(265, true, false, 123, 123, 123),
    CRUSHED_YELLOW_LIMONITE(266, 0),
    SMALL_YELLOW_LIMONITE_ORE(267, true, false, 183, 183, 183),

    BANDED_IRON_ORE(268, true, false, 124, 124, 124),
    CRUSHED_BANDED_IRON(269, 0),
    SMALL_BANDED_IRON_ORE(270, true, false, 184, 184, 184),

    VANADIUM_MAGNETITE_ORE(271, true, false, 125, 125, 125),
    CRUSHED_VANADIUM_MAGNETITE(272, 0),
    SMALL_VANADIUM_MAGNETITE_ORE(273, true, false, 185, 185, 185),

    CHALCOPYRITE_ORE(274, true, false, 126, 126, 126),
    CRUSHED_CHALCOPYRITE(275, 0),
    SMALL_CHALCOPYRITE_ORE(276, true, false, 186, 186, 186),

    TETRAHEDRITE_ORE(277, true, false, 127, 127, 127),
    CRUSHED_TETRAHEDRITE(278, 0),
    SMALL_TETRAHEDRITE_ORE(279, true, false, 187, 187, 187),

    MALACHITE_ORE(280, true, false, 128, 128, 128),
    CRUSHED_MALACHITE(281, 0),
    SMALL_MALACHITE_ORE(282, true, false, 188, 188, 188),

    GALENA_ORE(283, true, false, 129, 129, 129),
    CRUSHED_GALENA(284, 0),
    SMALL_GALENA_ORE(285, true, false, 189, 189, 189),

    SPHALERITE_ORE(286, true, false, 130, 130, 130),
    CRUSHED_SPHALERITE(287, 0),
    SMALL_SPHALERITE_ORE(288, true, false, 190, 190, 190),

    GARNIERITE_ORE(289, true, false, 131, 131, 131),
    CRUSHED_GARNIERITE(290, 0),
    SMALL_GARNIERITE_ORE(291, true, false, 191, 191, 191),

    PENTLANDITE_ORE(292, true, false, 132, 132, 132),
    CRUSHED_PENTLANDITE(293, 0),
    SMALL_PENTLANDITE_ORE(294, true, false, 192, 192, 192),

    COBALTITE_ORE(295, true, false, 133, 133, 133),
    CRUSHED_COBALTITE(296, 0),
    SMALL_COBALTITE_ORE(297, true, false, 193, 193, 193),

    PYRITE_ORE(298, true, false, 134, 134, 134),
    CRUSHED_PYRITE(299, 0),
    SMALL_PYRITE_ORE(300, true, false, 194, 194, 194),

    ARSENOPYRITE_ORE(301, true, false, 135, 135, 135),
    CRUSHED_ARSENOPYRITE(302, 0),
    SMALL_ARSENOPYRITE_ORE(303, true, false, 195, 195, 195),

    SULFUR_ORE(304, true, false, 136, 136, 136),
    CRUSHED_SULFUR(305, 0),
    SMALL_SULFUR_ORE(306, true, false, 196, 196, 196),

    CINNABAR_ORE(307, true, false, 137, 137, 137),
    CRUSHED_CINNABAR(308, 0),
    SMALL_CINNABAR_ORE(309, true, false, 197, 197, 197),

    CASSITERITE_ORE(310, true, false, 138, 138, 138),
    CRUSHED_CASSITERITE(311, 0),
    SMALL_CASSITERITE_ORE(312, true, false, 198, 198, 198),

    SCHEELITE_ORE(313, true, false, 139, 139, 139),
    CRUSHED_SCHEELITE(314, 0),
    SMALL_SCHEELITE_ORE(315, true, false, 199, 199, 199),

    WOLFRAMITE_ORE(316, true, false, 140, 140, 140),
    CRUSHED_WOLFRAMITE(317, 0),
    SMALL_WOLFRAMITE_ORE(318, true, false, 200, 200, 200),

    MOLYBDENITE_ORE(319, true, false, 141, 141, 141),
    CRUSHED_MOLYBDENITE(320, 0),
    SMALL_MOLYBDENITE_ORE(321, true, false, 201, 201, 201),

    CHROMITE_ORE(322, true, false, 143, 143, 143),
    CRUSHED_CHROMITE(323, 0),
    SMALL_CHROMITE_ORE(324, true, false, 202, 202, 202),

    ILMENITE_ORE(325, true, false, 144, 144, 144),
    CRUSHED_ILMENITE(326, 0),
    SMALL_ILMENITE_ORE(327, true, false, 203, 203, 203),

    RUTILE_ORE(328, true, false, 145, 145, 145),
    CRUSHED_RUTILE(329, 0),
    SMALL_RUTILE_ORE(330, true, false, 204, 204, 204),

    URANINITE_ORE(331, true, false, 146, 146, 146),
    CRUSHED_URANINITE(332, 0),
    SMALL_URANINITE_ORE(333, true, false, 205, 205, 205),

    PITCHBLENDE_ORE(334, true, false, 147, 147, 147),
    CRUSHED_PITCHBLENDE(335, 0),
    SMALL_PITCHBLENDE_ORE(336, true, false, 206, 206, 206),

    MONAZITE_ORE(337, true, false, 148, 148, 148),
    CRUSHED_MONAZITE(338, 0),
    SMALL_MONAZITE_ORE(339, true, false, 207, 207, 207),

    BASTNASITE_ORE(340, true, false, 149, 149, 149),
    CRUSHED_BASTNASITE(341, 0),
    SMALL_BASTNASITE_ORE(342, true, false, 208, 208, 208),

    VANADINITE_ORE(343, true, false, 150, 150, 150),
    CRUSHED_VANADINITE(344, 0),
    SMALL_VANADINITE_ORE(345, true, false, 209, 209, 209),

    PYROLUSITE_ORE(346, true, false, 151, 151, 151),
    CRUSHED_PYROLUSITE(347, 0),
    SMALL_PYROLUSITE_ORE(348, true, false, 210, 210, 210),

    GRAPHITE_ORE(349, true, false, 152, 152, 152),
    CRUSHED_GRAPHITE(350, 0),
    SMALL_GRAPHITE_ORE(351, true, false, 211, 211, 211),

    LITHIUM_ORE(352, true, false, 153, 153, 153),
    CRUSHED_LITHIUM(353, 0),
    SMALL_LITHIUM_ORE(354, true, false, 212, 212, 212),

    NAQUADAH_ORE(355, true, false, 154, 154, 154),
    CRUSHED_NAQUADAH(356, 0),
    SMALL_NAQUADAH_ORE(357, true, false, 213, 213, 213),

    NAQUADAH_ENRICHED_ORE(358, true, false, 155, 155, 155),
    CRUSHED_NAQUADAH_ENRICHED(359, 0),

    TRINIUM_ORE(360, true, false, 156, 156, 156),
    CRUSHED_TRINIUM(361, 0),
    SMALL_TRINIUM_ORE(362, true, false, 214, 214, 214),

    NEODYMIUM_ORE(363, true, false, 157, 157, 157),
    CRUSHED_NEODYMIUM(364, 0),
    SMALL_NEODYMIUM_ORE(365, true, false, 215, 215, 215),

    CERIUM_ORE(366, true, false, 158, 158, 158),
    CRUSHED_CERIUM(367, 0),
    SMALL_CERIUM_ORE(368, true, false, 216, 216, 216),

    OSMIUM_ORE(369, true, false, 159, 159, 159),
    CRUSHED_OSMIUM(370, 0),
    SMALL_OSMIUM_ORE(371, true, false, 217, 217, 217),

    PALLADIUM_ORE(372, true, false, 160, 160, 160),
    CRUSHED_PALLADIUM(373, 0),
    SMALL_PALLADIUM_ORE(374, true, false, 218, 218, 218),

    CALCITE_ORE(375, true, false, 161, 161, 161),
    CRUSHED_CALCITE(376, 0),

    OLIVINE_ORE(377, true, false, 162, 162, 162),
    CRUSHED_OLIVINE(378, 0),

    TALC_ORE(379, true, false, 163, 163, 163),
    CRUSHED_TALC(380, 0),

    BENTONITE_ORE(381, true, false, 164, 164, 164),
    CRUSHED_BENTONITE(382, 0),

    SODALITE_ORE(383, true, false, 165, 165, 165),
    CRUSHED_SODALITE(384, 0),

    LAZURITE_ORE(385, true, false, 166, 166, 166),
    CRUSHED_LAZURITE(386, 0),

    SALT_ORE(387, true, false, 167, 167, 167),
    CRUSHED_SALT(388, 0),

    ROCK_SALT_ORE(389, true, false, 168, 168, 168),
    CRUSHED_ROCK_SALT(390, 0),

    SALTPETER_ORE(391, true, false, 169, 169, 169),
    CRUSHED_SALTPETER(392, 0),

    BORAX_ORE(393, true, false, 170, 170, 170),
    CRUSHED_BORAX(394, 0),

    APATITE_ORE(395, true, false, 171, 171, 171),
    CRUSHED_APATITE(396, 0),

    PHOSPHATE_ORE(397, true, false, 172, 172, 172),
    CRUSHED_PHOSPHATE(398, 0),

    PYROCHLORE_ORE(399, true, false, 173, 173, 173),
    CRUSHED_PYROCHLORE(400, 0),

    LEPIDOLITE_ORE(401, true, false, 174, 174, 174),
    CRUSHED_LEPIDOLITE(402, 0),

    RUBY_ORE(403, true, false, 175, 175, 175),
    CRUSHED_RUBY(404, 0),
    SMALL_RUBY_ORE(405, true, false, 219, 219, 219),

    SAPPHIRE_ORE(406, true, false, 176, 176, 176),
    CRUSHED_SAPPHIRE(407, 0),

    GREEN_SAPPHIRE_ORE(408, true, false, 177, 177, 177),
    CRUSHED_GREEN_SAPPHIRE(409, 0),

    PYROPE_ORE(410, true, false, 178, 178, 178),
    CRUSHED_PYROPE(411, 0),

    SPESSARTINE_ORE(412, true, false, 179, 179, 179),
    CRUSHED_SPESSARTINE(413, 0),

    // Ferberite: ore #23 in the table (atlas tile 142), added after the main sequence
    FERBERITE_ORE(414, true, false, 142, 142, 142),
    CRUSHED_FERBERITE(415, 0),

    // Gem items: smelted outputs for the gemstone ore chain (inventory-only, no atlas tile).
    RUBY(416, 0),          // smelted from ruby ore / crushed ruby
    SAPPHIRE(417, 0),      // smelted from sapphire ore / crushed sapphire
    GREEN_SAPPHIRE(418, 0); // smelted from green sapphire ore / crushed green sapphire

    public final short id;
    public final boolean solid;
    public final boolean transparent;
    public final boolean cross;
    public final int topTile;
    public final int sideTile;
    public final int bottomTile;
    /** Atlas tile for the block's front face (the face it "faces" toward via its orientation), when directional. */
    public final int frontTile;
    /** Atlas tile used for the front face while the block is "active" (e.g. a burning furnace's glowing mouth); equals {@link #frontTile} for blocks that don't change. */
    public final int litFrontTile;
    public final int foodValue;
    /** True for inventory-only items (food/tools): no atlas tile, own procedurally generated texture, never placeable as a world block. */
    public final boolean isItem;
    /** 0-15, Minecraft-style: how brightly this block glows (0 = not a light source). See torch handling in {@link Chunk}. */
    public final int lightLevel;
    /** True if this block is a bottom-half slab (partial cube), which meshes and collides at half height. */
    public final boolean slab;
    /** True for a stepped stair block - meshed with a stair profile and collides like a full cell. */
    public final boolean stair;
    /** True for a fence - a thin post with auto-connecting rails, meshed and colliding as a fence. */
    public final boolean fence;
    /** Vertical extent of this block's collision box in blocks (1.0 for full cubes, 0.5 for slabs). */
    public final float collisionHeight;

    /** Full-cube block: distinct top/side/bottom textures, collides with the player. */
    BlockType(int id, boolean solid, boolean transparent, int topTile, int sideTile, int bottomTile) {
        // Not sideTile - that accidentally landed in the foodValue slot below,
        // making isEdible() (foodValue > 0) true for nearly every ordinary
        // cube block (its own atlas tile index is almost always positive).
        // Right-clicking one in survival then "ate" it via Player.eat()
        // instead of placing it: consumed from the hotbar, no block appeared.
        this(id, solid, transparent, topTile, sideTile, bottomTile, 0);
    }

    /** Full-cube block with a food value (not currently used - cubes aren't eaten - but kept symmetric). */
    BlockType(int id, boolean solid, boolean transparent, int topTile, int sideTile, int bottomTile, int foodValue) {
        this(id, solid, transparent, topTile, sideTile, bottomTile, foodValue, 0);
    }

    /** Full-cube block with a distinct front face (the face it faces via orientation), e.g. a furnace. */
    BlockType(int id, boolean solid, boolean transparent, int topTile, int sideTile, int bottomTile, int frontTile, int foodValue, int lightLevel) {
        this(id, solid, transparent, topTile, sideTile, bottomTile, frontTile, frontTile, foodValue, lightLevel);
    }

    /** Full-cube block with a distinct front face that also changes while active (a lit furnace mouth). */
    BlockType(int id, boolean solid, boolean transparent, int topTile, int sideTile, int bottomTile, int frontTile, int litFrontTile, int foodValue, int lightLevel) {
        this.id = (short) id;
        this.solid = solid;
        this.transparent = transparent;
        this.cross = false;
        this.slab = false;
        this.stair = false;
        this.fence = false;
        this.topTile = topTile;
        this.sideTile = sideTile;
        this.bottomTile = bottomTile;
        this.frontTile = frontTile;
        this.litFrontTile = litFrontTile;
        this.foodValue = foodValue;
        this.isItem = false;
        this.lightLevel = lightLevel;
        this.collisionHeight = 1.0f;
    }

    /** Full-cube block that also emits light (e.g. a lamp), plus an (unused) food value. */
    BlockType(int id, boolean solid, boolean transparent, int topTile, int sideTile, int bottomTile, int foodValue, int lightLevel) {
        this(id, solid, transparent, topTile, sideTile, bottomTile, sideTile, foodValue, lightLevel);
    }

    /** Bottom-half slab: a partial cube, one atlas tile for all faces, colliding only in its lower half. */
    BlockType(int id, boolean solid, boolean transparent, boolean slab, int tile) {
        this.id = (short) id;
        this.solid = solid;
        this.transparent = transparent;
        this.cross = false;
        this.slab = slab;
        this.stair = false;
        this.fence = false;
        this.topTile = tile;
        this.sideTile = tile;
        this.bottomTile = tile;
        this.frontTile = tile;
        this.litFrontTile = tile;
        this.foodValue = 0;
        this.isItem = false;
        this.lightLevel = 0;
        this.collisionHeight = slab ? 0.5f : 1.0f;
    }

    /** Stairs or fence: a partial-cube block with custom meshing and custom collision boxes. */
    BlockType(int id, int tile, boolean stair, boolean fence, float collisionHeight) {
        this.id = (short) id;
        this.solid = true;
        this.transparent = false;
        this.cross = false;
        this.slab = false;
        this.stair = stair;
        this.fence = fence;
        this.topTile = tile;
        this.sideTile = tile;
        this.bottomTile = tile;
        this.frontTile = tile;
        this.litFrontTile = tile;
        this.foodValue = 0;
        this.isItem = false;
        this.lightLevel = 0;
        this.collisionHeight = collisionHeight;
    }

    /** Half-height directional block (like a bed): distinct front face, half collision height. */
    BlockType(int id, int topTile, int sideTile, int bottomTile, int frontTile, int litFrontTile, float collisionHeight) {
        this.id = (short) id;
        this.solid = true;
        this.transparent = false;
        this.cross = false;
        this.slab = false;
        this.stair = false;
        this.fence = false;
        this.topTile = topTile;
        this.sideTile = sideTile;
        this.bottomTile = bottomTile;
        this.frontTile = frontTile;
        this.litFrontTile = litFrontTile;
        this.foodValue = 0;
        this.isItem = false;
        this.lightLevel = 0;
        this.collisionHeight = collisionHeight;
    }

    /** Cross-shaped (billboard-X) world decoration block, e.g. grass/flowers/berry bush: one atlas tile, never collides. */
    BlockType(int id, boolean solid, boolean transparent, int tile) {
        this(id, solid, transparent, tile, 0);
    }

    /** Cross-shaped world decoration that's also a light source, e.g. a torch. */
    BlockType(int id, boolean solid, boolean transparent, int tile, int lightLevel) {
        this.id = (short) id;
        this.solid = solid;
        this.transparent = transparent;
        this.cross = true;
        this.slab = false;
        this.stair = false;
        this.fence = false;
        this.topTile = tile;
        this.sideTile = tile;
        this.bottomTile = tile;
        this.frontTile = tile;
        this.litFrontTile = tile;
        this.foodValue = 0;
        this.isItem = false;
        this.lightLevel = lightLevel;
        this.collisionHeight = 1.0f;
    }

    /** Inventory-only item (tool, or foraged food like apple/berries): no atlas tile, has its own procedurally generated texture, never placeable as a world block. */
    BlockType(int id, int foodValue) {
        this.id = (short) id;
        this.solid = false;
        this.transparent = true;
        this.cross = false;
        this.slab = false;
        this.stair = false;
        this.fence = false;
        this.topTile = -1;
        this.sideTile = -1;
        this.bottomTile = -1;
        this.frontTile = -1;
        this.litFrontTile = -1;
        this.foodValue = foodValue;
        this.isItem = true;
        this.lightLevel = 0;
        this.collisionHeight = 1.0f;
    }

    // Sparse id lookup over the short range (0-4095 used). Block ids are
    // persisted as raw shorts; ids don't have to be a dense 0..N-1 range (e.g.
    // slabs use 44/45 while other features may take 39-43); unused slots stay null.
    private static final BlockType[] BY_ID;

    static {
        BY_ID = new BlockType[4096];
        for (BlockType t : values()) {
            int idx = t.id & 0xFFFF;
            if (idx >= BY_ID.length) {
                throw new IllegalStateException("Block id " + t.id + " out of range for " + t);
            }
            if (BY_ID[idx] != null) {
                // Fail fast on a duplicate id - a re-used id would silently corrupt save files.
                throw new IllegalStateException("Duplicate block id " + t.id + ": " + t + " and " + BY_ID[idx]);
            }
            BY_ID[idx] = t;
        }
    }

    public static BlockType byId(int id) {
        int idx = id & 0xFFFF;
        BlockType type = idx < BY_ID.length ? BY_ID[idx] : null;
        return type == null ? AIR : type;
    }

    /** True if this block stops the player / blocks a raycast. */
    public boolean isCollidable() {
        return solid;
    }

    /** True if this block has a distinct front face that faces its orientation (e.g. a furnace). */
    public boolean isDirectional() {
        return frontTile != sideTile;
    }

    public boolean isEdible() {
        return foodValue > 0;
    }

    public boolean isLightSource() {
        return lightLevel > 0;
    }

    /** True for any water-family block (static, source, or flow). */
    public boolean isWater() {
        return this == WATER || this == WATER_SOURCE || this == WATER_FLOW;
    }    /** True for the portal blocks that teleport the player between dimensions. */
    public boolean isPortal() {
        return this == NETHER_PORTAL || this == END_PORTAL;
    }

    /** True for any lava-family block (static, source, or flow). */
    public boolean isLava() {
        return this == LAVA || this == LAVA_SOURCE || this == LAVA_FLOW;
    }

    /**
     * True if this block radiates heat on its own - a fire, a torch or lamp, or
     * lava. A furnace only warms a room while it's actively burning (see
     * {@link com.minecraftclone.player.Player#hasHeatSource}).
     */
    public boolean isHeatSource() {
        return this == FIRE || this == TORCH || this == LAMP || isLava();
    }

    /** True for any fluid (water or lava), including static and flowing variants. */
    public boolean isFluid() {
        return isWater() || isLava();
    }

    /**
     * True if this block's top face can support an accumulated snow layer:
     * an opaque full cube (or a bottom-half slab, which snow caps flush - see
     * {@link #isSnowCappedSlab()}) that isn't a cross-shaped plant, so snow
     * piles up on ground, stone, wood, roofs, sand and snow itself, but not on
     * leaves, glass, ice, fluids, torches or flowers.
     */
    public boolean canHoldSnow() {
        return solid && !transparent && !cross && !isTranslucent();
    }

    /** True for a bottom-half slab that's been covered by a flush snow cap (see the snowy slab entries above). */
    public boolean isSnowCappedSlab() {
        return this == SNOWY_STONE_SLAB || this == SNOWY_PLANKS_SLAB;
    }

    /** True for the placeable, flowing fluid source blocks. */
    public boolean isFluidSource() {
        return this == WATER_SOURCE || this == LAVA_SOURCE;
    }

    /** True for the transient flowing cells the fluid simulation fills and dries. */
    public boolean isFluidFlow() {
        return this == WATER_FLOW || this == LAVA_FLOW;
    }

    /** True for source or flow fluid (i.e. the flowing kinds, not the static terrain fills). */
    public boolean isFlowingFluid() {
        return isFluidSource() || isFluidFlow();
    }

    /**
     * True if a ray should pass straight through this block: open air, or any
     * fluid (water/lava, static/source/flow alike - they're all rendered the
     * same translucent, non-solid way, so there's no reason a source block
     * should raycast any differently than the terrain-fill or flow forms
     * sitting right next to it).
     * <p>
     * Missing WATER_SOURCE here used to be a real bug, not just an
     * inconsistency: breaking a block next to natural ocean water promotes
     * the boundary cell from WATER to WATER_SOURCE (see World#promoteIfStaticFluid),
     * which is extremely common near any player-touched shoreline or
     * underwater dig. The instant the camera's eye position entered one of
     * those cells while swimming, Raycaster.cast's "already inside a solid
     * block" branch fired every frame - water_source wasn't pass-through,
     * so the game treated the player as embedded in solid ground - aiming
     * the block-outline highlight at their own eye position. With the
     * camera essentially inside the outlined cube, its edges radiated out
     * to the screen corners: a glitchy wireframe-in-the-water look that's
     * exactly what "x-ray in the ocean" describes.
     */
    public boolean isPassThrough() {
        return this == AIR || isFluid();
    }

    /** True if this block is drawn in the see-through translucent render pass (glass, ice). */
    public boolean isTranslucent() {
        return this == GLASS || this == ICE;
    }

    /**
     * True for decoration that grows/sits <em>inside</em> a fluid cell rather
     * than needing to replace it - Minecraft's "waterlogged" plants (seagrass,
     * kelp) work the same way. World-gen and manual placement both route this
     * into the target cell's overlay slot instead of overwriting the water
     * there - see {@link Chunk#setOverlay} and {@link BlockAccessor#getOverlay}.
     */
    public boolean isSubmersible() {
        return this == SEAWEED || this == LILY_PAD;
    }

    /** True for either half of a functional door (closed solid, or open walk-through). */
    public boolean isDoor() {
        return this == DOOR || this == DOOR_OPEN;
    }

    /** True for a functional trapdoor (closed solid panel, or open walk-through). */
    public boolean isTrapdoor() {
        return this == TRAPDOOR || this == TRAPDOOR_OPEN;
    }

    /** True for a stepped stair block (stone or planks). */
    public boolean isStair() {
        return stair;
    }

    /** True for a fence (thin post with auto-connecting rails). */
    public boolean isFence() {
        return fence;
    }

    /** True for either half of a functional door (closed solid, or open walk-through). */
    public boolean isBed() {
        return this == BED || this == BED_HEAD || this == BED_OCCUPIED || this == BED_HEAD_OCCUPIED;
    }

    /** True if this is the foot end of a bed (as opposed to the head/pillow end). */
    public boolean isBedFoot() {
        return this == BED || this == BED_OCCUPIED;
    }

    /** True for any partial-cube block that needs its own meshing (stairs, fences). */
    public boolean isPartialCube() {
        return stair || fence;
    }

    /**
     * The collision boxes of this block occupying the cell at {@code (x,y,z)},
     * given its {@code orientation} byte. Full cubes and slabs return a single
     * box sized by {@link #collisionHeight}; stairs return two stepped boxes
     * (a full-width low step and a raised back-half step, matching their mesh)
     * so a player can climb them; a fence returns a single 1.5-block-tall box
     * so nothing can jump over it.
     */
    public AABB[] collisionBoxes(int x, int y, int z, byte orientation) {
        if (stair) {
            // The raised (back) half sits opposite the facing, same as the mesh.
            float loX = x, hiX = x + 1, loZ = z, hiZ = z + 1;
            if (orientation == 2) hiX = x + 0.5f;        // facing +X: raised on -X half
            else if (orientation == 3) loX = x + 0.5f;   // facing -X: raised on +X half
            else if (orientation == 0) hiZ = z + 0.5f;   // facing +Z: raised on -Z half
            else if (orientation == 1) loZ = z + 0.5f;   // facing -Z: raised on +Z half
            return new AABB[]{
                    new AABB(x, y, z, x + 1, y + 0.5f, z + 1),        // low step
                    new AABB(loX, y + 0.5f, loZ, hiX, y + 1, hiZ),    // raised step
            };
        }
        return new AABB[]{new AABB(x, y, z, x + 1, y + collisionHeight, z + 1)};
    }

    /** A human-readable name for HUD tooltips, e.g. "DIAMOND_PICKAXE" -> "Diamond Pickaxe". */
    public String displayName() {
        StringBuilder sb = new StringBuilder(name().length());
        boolean upper = true;
        for (int i = 0; i < name().length(); i++) {
            char c = name().charAt(i);
            if (c == '_') {
                sb.append(' ');
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(c) : Character.toLowerCase(c));
                upper = false;
            }
        }
        return sb.toString();
    }

    /**
     * The tracked, flowing source variant a static (world-gen) fluid block should be
     * promoted to once it ends up bordering open space - null if this block isn't a
     * static fluid (including if it's already a source/flow). See World#setBlock: an
     * entire ocean/lake is deliberately left untracked for performance, but the one
     * boundary block that actually needs to flow into a freshly-broken opening is
     * promoted right there, so it starts participating in FluidSim.
     */
    public BlockType promotedFluidSource() {
        if (this == WATER) return WATER_SOURCE;
        if (this == LAVA) return LAVA_SOURCE;
        return null;
    }
}
