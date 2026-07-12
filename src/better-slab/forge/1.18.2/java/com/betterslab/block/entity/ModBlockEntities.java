package com.betterslab.block.entity;

import com.betterslab.BetterSlab;
import com.betterslab.block.ModBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITIES, BetterSlab.MOD_ID);

    public static final RegistryObject<BlockEntityType<GenericVerticalSlabEntity>> GENERIC_VERTICAL_SLAB_ENTITY =
            BLOCK_ENTITIES.register("generic_vertical_slab",
                    () -> BlockEntityType.Builder.of(GenericVerticalSlabEntity::new, ModBlocks.GENERIC_VERTICAL_SLAB_RO.get()).build(null));

    public static final RegistryObject<BlockEntityType<MergedSlabEntity>> MERGED_SLAB_ENTITY =
            BLOCK_ENTITIES.register("merged_slab",
                    () -> BlockEntityType.Builder.of(MergedSlabEntity::new, ModBlocks.MERGED_SLAB_RO.get()).build(null));

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
