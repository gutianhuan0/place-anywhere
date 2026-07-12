package com.betterslab.block.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;

/**
 * 通用竖半砖的方块实体：存储来源半砖方块，用于 BER 渲染与破坏掉落。
 * setter 不自动 sync，调用方必须在 setBlockState 之后手动调用 sync()。
 */
public class GenericVerticalSlabEntity extends BlockEntity {
    private Block sourceSlab = Blocks.STONE_SLAB;

    public GenericVerticalSlabEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GENERIC_VERTICAL_SLAB_ENTITY, pos, state);
    }

    public Block getSourceSlab() { return sourceSlab; }

    public BlockState getSourceSlabState() {
        if (sourceSlab instanceof SlabBlock) {
            return sourceSlab.getDefaultState().with(SlabBlock.TYPE, net.minecraft.block.enums.SlabType.BOTTOM);
        }
        return sourceSlab.getDefaultState();
    }

    public void setSourceSlab(Block block) {
        this.sourceSlab = block;
        markDirty();
    }

    /** 手动触发客户端同步：在 setBlockState 之后调用。 */
    public void sync() {
        if (world != null && !world.isClient && world instanceof ServerWorld sw) {
            markDirty();
            sw.getChunkManager().markForUpdate(pos);
            BlockEntityUpdateS2CPacket bePacket = BlockEntityUpdateS2CPacket.create(this);
            for (ServerPlayerEntity player : PlayerLookup.tracking(this)) {
                player.networkHandler.sendPacket(bePacket);
            }
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putString("source_slab", Registries.BLOCK.getId(sourceSlab).toString());
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        String str = nbt.getString("source_slab");
        if (!str.isEmpty()) {
            sourceSlab = Registries.BLOCK.getOrEmpty(new Identifier(str)).orElse(Blocks.STONE_SLAB);
        }
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        return createComponentlessNbtWithIdentifyingData(registryLookup);
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
