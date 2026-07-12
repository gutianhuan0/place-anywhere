package com.betterslab.util;

import com.betterslab.BetterSlab;
import com.betterslab.block.MergedSlabBlock;
import net.minecraft.block.Block;
import net.minecraft.block.enums.SlabType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 追踪不同种类半砖合并的位置与层位。
 *
 * <p>当玩家把半砖 B 放在已有半砖 A 上（不同种类）时，方块变为 A 的 DOUBLE，
 * 这里记录 B 的类型与层位（TOP/BOTTOM）。破坏时据此掉落正确的半砖。</p>
 *
 * <p>注意：数据不跨重启持久化，服务器重启后只掉落第一种半砖。</p>
 *
 * <p>新系统使用 {@link MergedSlabBlock}（自带 BlockEntity 持久化），
 * 不再依赖此内存追踪；此方法仅用于旧系统兼容与新系统检测。</p>
 */
public class MergedSlabTracker {
    private static final Map<String, MergedEntry> mergedData = new ConcurrentHashMap<>();

    public record MergedEntry(Block slab, SlabType type) {}

    private static String key(World world, BlockPos pos) {
        return world.getRegistryKey().getValue().toString() + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static void setMerged(World world, BlockPos pos, Block secondSlab, SlabType secondType) {
        mergedData.put(key(world, pos), new MergedEntry(secondSlab, secondType));
        BetterSlab.LOGGER.debug("Merged slab tracked at {} with {} ({})", pos, secondSlab, secondType);
    }

    /** 兼容旧调用：默认层位为 TOP。 */
    public static void setMerged(World world, BlockPos pos, Block secondSlab) {
        setMerged(world, pos, secondSlab, SlabType.TOP);
    }

    public static MergedEntry getMerged(World world, BlockPos pos) {
        return mergedData.get(key(world, pos));
    }

    public static void remove(World world, BlockPos pos) {
        mergedData.remove(key(world, pos));
    }

    public static boolean hasMerged(World world, BlockPos pos) {
        return mergedData.containsKey(key(world, pos));
    }

    /** 检查 pos 处是否为新系统的 MergedSlabBlock（自带 BlockEntity 持久化的合并半砖）。 */
    public static boolean isMergedSlabBlock(World world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() instanceof MergedSlabBlock;
    }
}
