package com.betterslab.block.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;

/**
 * 合并半砖方块实体：存储两种来源半砖，用于 BER 渲染与破坏掉落。
 * setter 不自动 sync，调用方必须在 setBlockState 之后手动调用 sync()。
 */
public class MergedSlabEntity extends BlockEntity {
    private Block slabA = Blocks.STONE_SLAB;
    private Block slabB = Blocks.STONE_SLAB;

    public MergedSlabEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MERGED_SLAB_ENTITY, pos, state);
    }

    public Block getSlabA() { return slabA; }
    public Block getSlabB() { return slabB; }

    public void setSlabA(Block block) {
        this.slabA = block;
        markDirty();
    }

    public void setSlabB(Block block) {
        this.slabB = block;
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
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putString("slab_a", Registry.BLOCK.getId(slabA).toString());
        nbt.putString("slab_b", Registry.BLOCK.getId(slabB).toString());
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        String strA = nbt.getString("slab_a");
        String strB = nbt.getString("slab_b");
        if (!strA.isEmpty()) {
            slabA = Registry.BLOCK.getOrEmpty(new Identifier(strA)).orElse(Blocks.STONE_SLAB);
        }
        if (!strB.isEmpty()) {
            slabB = Registry.BLOCK.getOrEmpty(new Identifier(strB)).orElse(Blocks.STONE_SLAB);
        }
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        NbtCompound nbt = new NbtCompound();
        writeNbt(nbt);
        return nbt;
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
