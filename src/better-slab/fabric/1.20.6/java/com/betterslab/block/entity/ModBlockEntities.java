package com.betterslab.block.entity;

import com.betterslab.BetterSlab;
import com.betterslab.block.GenericVerticalSlabBlock;
import com.betterslab.block.MergedSlabBlock;
import com.betterslab.block.ModBlocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlockEntities {
    public static BlockEntityType<GenericVerticalSlabEntity> GENERIC_VERTICAL_SLAB_ENTITY;
    public static BlockEntityType<MergedSlabEntity> MERGED_SLAB_ENTITY;

    public static void register() {
        GENERIC_VERTICAL_SLAB_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(BetterSlab.MOD_ID, "generic_vertical_slab"),
                BlockEntityType.Builder.create(GenericVerticalSlabEntity::new, ModBlocks.GENERIC_VERTICAL_SLAB).build()
        );
        MERGED_SLAB_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(BetterSlab.MOD_ID, "merged_slab"),
                BlockEntityType.Builder.create(MergedSlabEntity::new, ModBlocks.MERGED_SLAB).build()
        );
    }
}
