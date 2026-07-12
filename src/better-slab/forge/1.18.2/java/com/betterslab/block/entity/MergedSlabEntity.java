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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.network.PacketDistributor;

/**
 * 合并半砖方块实体：存储两种来源半砖，用于 BER 渲染与破坏掉落。
 * setter 不自动 sync，调用方必须在 setBlockState 之后手动调用 sync()。
 */
public class MergedSlabEntity extends BlockEntity {
    private Block slabA = Blocks.STONE_SLAB;
    private Block slabB = Blocks.STONE_SLAB;

    public MergedSlabEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MERGED_SLAB_ENTITY.get(), pos, state);
    }

    public Block getSlabA() { return slabA; }
    public Block getSlabB() { return slabB; }

    public void setSlabA(Block block) {
        this.slabA = block;
        setChanged();
    }

    public void setSlabB(Block block) {
        this.slabB = block;
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
        nbt.putString("slab_a", Registry.BLOCK.getKey(slabA).toString());
        nbt.putString("slab_b", Registry.BLOCK.getKey(slabB).toString());
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        String strA = nbt.getString("slab_a");
        String strB = nbt.getString("slab_b");
        if (!strA.isEmpty()) {
            slabA = Registry.BLOCK.getOptional(new ResourceLocation(strA)).orElse(Blocks.STONE_SLAB);
        }
        if (!strB.isEmpty()) {
            slabB = Registry.BLOCK.getOptional(new ResourceLocation(strB)).orElse(Blocks.STONE_SLAB);
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
