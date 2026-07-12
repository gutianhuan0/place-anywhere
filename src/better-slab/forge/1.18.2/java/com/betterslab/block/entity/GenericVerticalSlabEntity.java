package com.betterslab.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.network.PacketDistributor;

/**
 * 通用竖半砖的方块实体：存储来源半砖方块，用于 BER 渲染与破坏掉落。
 * setter 不自动 sync，调用方必须在 setBlockState 之后手动调用 sync()。
 */
public class GenericVerticalSlabEntity extends BlockEntity {
    private Block sourceSlab = Blocks.STONE_SLAB;

    public GenericVerticalSlabEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GENERIC_VERTICAL_SLAB_ENTITY.get(), pos, state);
    }

    public Block getSourceSlab() { return sourceSlab; }

    public BlockState getSourceSlabState() {
        if (sourceSlab instanceof SlabBlock) {
            return sourceSlab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        }
        return sourceSlab.defaultBlockState();
    }

    public void setSourceSlab(Block block) {
        this.sourceSlab = block;
        setChanged();
    }

    /** 手动触发客户端同步：在 setBlockState 之后调用。 */
    public void sync() {
        if (level != null && !level.isClientSide && level instanceof ServerLevel sl) {
            setChanged();
            ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(this);
            LevelChunk chunk = sl.getChunkAt(getBlockPos());
            PacketDistributor.TRACKING_CHUNK.with(() -> chunk).send(packet);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.putString("source_slab", Registry.BLOCK.getKey(sourceSlab).toString());
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        String str = nbt.getString("source_slab");
        if (!str.isEmpty()) {
            sourceSlab = Registry.BLOCK.getOptional(new ResourceLocation(str)).orElse(Blocks.STONE_SLAB);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag nbt = new CompoundTag();
        saveAdditional(nbt);
        return nbt;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
