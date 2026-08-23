package com.minecraftclone.world;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Registry of known block-entity types, mapping the stable string a block
 * entity persists as ({@link BlockEntity#type()}) to a factory that builds a
 * fresh instance for loading. {@link ChunkStorage} consults this when restoring
 * a chunk: a registered type is rebuilt and fed its saved data, while an
 * unknown type is skipped so removing an entity type from the game never
 * breaks an old save.
 * <p>
 * Adding a machine with an inventory is one line here (plus implementing
 * {@link BlockEntity}) - everything else (saving, loading, ticking, pruning)
 * is driven generically off this registry.
 */
public final class BlockEntities {

    private static final Map<String, Supplier<BlockEntity>> REGISTRY = new HashMap<>();

    static {
        register(Furnace.TYPE, Furnace::new);
        register(Chest.TYPE, Chest::new);
        register(Barrel.TYPE, Barrel::new);
        // Phase 0.5: Tinkers' Construct multi-block entities
        register(com.minecraftclone.world.multiblock.SmelteryEntity.TYPE,
                 com.minecraftclone.world.multiblock.SmelteryEntity::new);
        // Phase 0.5: Tinkers' Construct crafting stations
        register(com.minecraftclone.world.tinkers.PartBuilderEntity.TYPE,
                 com.minecraftclone.world.tinkers.PartBuilderEntity::new);
        register(com.minecraftclone.world.tinkers.ToolStationEntity.TYPE,
                 com.minecraftclone.world.tinkers.ToolStationEntity::new);
        // Casting stations: one entity class, two block variants.
        register(CastingEntity.TABLE_TYPE, () -> new CastingEntity(BlockType.CASTING_TABLE, false));
        register(CastingEntity.BASIN_TYPE, () -> new CastingEntity(BlockType.CASTING_BASIN, true));
    }

    private BlockEntities() {
    }

    /** Registers a block-entity type so saved instances can be rebuilt on load. */
    public static void register(String type, Supplier<BlockEntity> factory) {
        if (REGISTRY.containsKey(type)) {
            throw new IllegalArgumentException("Block entity type already registered: " + type);
        }
        REGISTRY.put(type, factory);
    }

    /** True if {@code type} is a registered block-entity type. */
    public static boolean isRegistered(String type) {
        return REGISTRY.containsKey(type);
    }

    /** Creates a fresh, empty instance of {@code type}, or null if the type isn't registered. */
    public static BlockEntity create(String type) {
        Supplier<BlockEntity> factory = REGISTRY.get(type);
        return factory == null ? null : factory.get();
    }
}
