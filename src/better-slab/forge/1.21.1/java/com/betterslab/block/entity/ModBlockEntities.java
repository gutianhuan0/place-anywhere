package com.betterslab.block.entity;

import com.betterslab.BetterSlab;
import com.betterslab.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BetterSlab.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GenericVerticalSlabEntity>> GENERIC_VERTICAL_SLAB_ENTITY =
            BLOCK_ENTITIES.register("generic_vertical_slab",
                    () -> BlockEntityType.Builder.of(GenericVerticalSlabEntity::new, ModBlocks.GENERIC_VERTICAL_SLAB_RO.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MergedSlabEntity>> MERGED_SLAB_ENTITY =
            BLOCK_ENTITIES.register("merged_slab",
                    () -> BlockEntityType.Builder.of(MergedSlabEntity::new, ModBlocks.MERGED_SLAB_RO.get()).build(null));

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
