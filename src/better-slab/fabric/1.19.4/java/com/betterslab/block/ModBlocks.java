package com.betterslab.block;

import com.betterslab.BetterSlab;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.HashMap;

public class ModBlocks {

    /** 原版半砖 → 专属竖半砖（仅原版半砖有）。 */
    private static final HashMap<Block, Block> VANILLA_TO_VERTICAL = new HashMap<>();

    /** 专属竖半砖 → 原版半砖（反向映射，用于破坏时还原）。 */
    private static final HashMap<Block, Block> VERTICAL_TO_VANILLA = new HashMap<>();

    /** 通用竖半砖方块（用于模组半砖，运行时存储来源半砖外观）。 */
    public static Block GENERIC_VERTICAL_SLAB;

    /** 合并半砖方块（不同种类半砖合并到同一格，由 BlockEntity 记录两种来源）。 */
    public static Block MERGED_SLAB;

    private static final Block[] VANILLA_SLABS = {
            Blocks.OAK_SLAB, Blocks.SPRUCE_SLAB, Blocks.BIRCH_SLAB, Blocks.JUNGLE_SLAB,
            Blocks.ACACIA_SLAB, Blocks.DARK_OAK_SLAB, Blocks.MANGROVE_SLAB,
            Blocks.CRIMSON_SLAB, Blocks.WARPED_SLAB,
            Blocks.STONE_SLAB, Blocks.COBBLESTONE_SLAB, Blocks.MOSSY_COBBLESTONE_SLAB, Blocks.STONE_BRICK_SLAB,
            Blocks.MOSSY_STONE_BRICK_SLAB, Blocks.GRANITE_SLAB, Blocks.POLISHED_GRANITE_SLAB, Blocks.DIORITE_SLAB,
            Blocks.POLISHED_DIORITE_SLAB, Blocks.ANDESITE_SLAB, Blocks.POLISHED_ANDESITE_SLAB, Blocks.SANDSTONE_SLAB,
            Blocks.CUT_SANDSTONE_SLAB, Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.RED_SANDSTONE_SLAB, Blocks.CUT_RED_SANDSTONE_SLAB,
            Blocks.SMOOTH_RED_SANDSTONE_SLAB, Blocks.BRICK_SLAB, Blocks.NETHER_BRICK_SLAB, Blocks.RED_NETHER_BRICK_SLAB,
            Blocks.QUARTZ_SLAB, Blocks.SMOOTH_QUARTZ_SLAB, Blocks.PURPUR_SLAB, Blocks.PRISMARINE_SLAB,
            Blocks.PRISMARINE_BRICK_SLAB, Blocks.DARK_PRISMARINE_SLAB, Blocks.END_STONE_BRICK_SLAB, Blocks.BLACKSTONE_SLAB,
            Blocks.POLISHED_BLACKSTONE_SLAB, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, Blocks.DEEPSLATE_BRICK_SLAB, Blocks.DEEPSLATE_TILE_SLAB,
            Blocks.COBBLED_DEEPSLATE_SLAB, Blocks.POLISHED_DEEPSLATE_SLAB, Blocks.MUD_BRICK_SLAB
    };

    private static final String SLAB_SUFFIX = "_slab";
    private static final String VERTICAL_SUFFIX = "_vertical_slab";

    public static void registerModBlocks() {
        // 注册原版半砖的专属竖半砖
        for (Block vanillaSlab : VANILLA_SLABS) {
            Identifier vanillaId = Registries.BLOCK.getId(vanillaSlab);
            String path = vanillaId.getPath();
            String baseName = path.substring(0, path.length() - SLAB_SUFFIX.length());
            Identifier id = new Identifier(BetterSlab.MOD_ID, baseName + VERTICAL_SUFFIX);

            Block verticalSlab = Registry.register(
                    Registries.BLOCK,
                    id,
                    new VerticalSlabBlock(AbstractBlock.Settings.copy(vanillaSlab))
            );

            Registry.register(
                    Registries.ITEM,
                    id,
                    new BlockItem(verticalSlab, new Item.Settings())
            );

            VANILLA_TO_VERTICAL.put(vanillaSlab, verticalSlab);
            VERTICAL_TO_VANILLA.put(verticalSlab, vanillaSlab);
        }

        // 注册通用竖半砖方块（无物品，仅运行时由模组半砖放置逻辑生成）
        GENERIC_VERTICAL_SLAB = Registry.register(
                Registries.BLOCK,
                new Identifier(BetterSlab.MOD_ID, "generic_vertical_slab"),
                new GenericVerticalSlabBlock(AbstractBlock.Settings.copy(Blocks.STONE).nonOpaque())
        );

        // 注册合并半砖方块（无物品，仅运行时由合并逻辑生成）
        MERGED_SLAB = Registry.register(
                Registries.BLOCK,
                new Identifier(BetterSlab.MOD_ID, "merged_slab"),
                new MergedSlabBlock(AbstractBlock.Settings.copy(Blocks.STONE).nonOpaque())
        );
    }

    /** 返回原版半砖对应的专属竖半砖；模组半砖返回 null（调用方应改用通用竖半砖）。 */
    public static Block getVerticalSlab(Block vanillaSlab) {
        return VANILLA_TO_VERTICAL.get(vanillaSlab);
    }

    /** 返回专属竖半砖对应的原版半砖；非专属竖半砖返回 null。 */
    public static Block getVanillaSlab(Block verticalSlab) {
        return VERTICAL_TO_VANILLA.get(verticalSlab);
    }

    /** 该半砖是否有专属竖半砖（原版半砖为 true，模组半砖为 false）。 */
    public static boolean hasDedicatedVertical(Block slab) {
        return VANILLA_TO_VERTICAL.containsKey(slab);
    }
}
