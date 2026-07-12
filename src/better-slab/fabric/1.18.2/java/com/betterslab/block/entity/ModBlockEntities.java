package com.betterslab.block.entity;

import com.betterslab.BetterSlab;
import com.betterslab.block.GenericVerticalSlabBlock;
import com.betterslab.block.MergedSlabBlock;
import com.betterslab.block.ModBlocks;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public class ModBlockEntities {
    public static BlockEntityType<GenericVerticalSlabEntity> GENERIC_VERTICAL_SLAB_ENTITY;
    public static BlockEntityType<MergedSlabEntity> MERGED_SLAB_ENTITY;

    public static void register() {
        GENERIC_VERTICAL_SLAB_ENTITY = Registry.register(
                Registry.BLOCK_ENTITY_TYPE,
                new Identifier(BetterSlab.MOD_ID, "generic_vertical_slab"),
                FabricBlockEntityTypeBuilder.create(GenericVerticalSlabEntity::new, ModBlocks.GENERIC_VERTICAL_SLAB).build()
        );
        MERGED_SLAB_ENTITY = Registry.register(
                Registry.BLOCK_ENTITY_TYPE,
                new Identifier(BetterSlab.MOD_ID, "merged_slab"),
                FabricBlockEntityTypeBuilder.create(MergedSlabEntity::new, ModBlocks.MERGED_SLAB).build()
        );
    }
}
