package com.betterslab.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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
            ChunkPos chunkPos = new ChunkPos(getBlockPos());
            for (ServerPlayer player : sl.getChunkSource().chunkMap.getPlayers(chunkPos, false)) {
                player.connection.send(packet);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider provider) {
        super.saveAdditional(nbt, provider);
        nbt.putString("slab_a", BuiltInRegistries.BLOCK.getKey(slabA).toString());
        nbt.putString("slab_b", BuiltInRegistries.BLOCK.getKey(slabB).toString());
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider provider) {
        super.loadAdditional(nbt, provider);
        String strA = nbt.getString("slab_a");
        String strB = nbt.getString("slab_b");
        if (!strA.isEmpty()) {
            slabA = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(strA)).orElse(Blocks.STONE_SLAB);
        }
        if (!strB.isEmpty()) {
            slabB = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(strB)).orElse(Blocks.STONE_SLAB);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag nbt = new CompoundTag();
        saveAdditional(nbt, provider);
        return nbt;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
