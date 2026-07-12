package com.betterslab.block;

import com.betterslab.BetterSlab;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashMap;
import java.util.Map;

public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, BetterSlab.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, BetterSlab.MOD_ID);

    /** 原版半砖 → 专属竖半砖的 RegistryObject（仅原版半砖有）。 */
    private static final Map<Block, RegistryObject<Block>> VANILLA_TO_VERTICAL_RO = new HashMap<>();
    /** 反向缓存：专属竖半砖 → 原版半砖（运行时惰性构建）。 */
    private static Map<Block, Block> verticalToVanillaCache;

    /** 通用竖半砖方块（用于模组半砖，运行时存储来源半砖外观）。 */
    public static final RegistryObject<Block> GENERIC_VERTICAL_SLAB_RO = BLOCKS.register("generic_vertical_slab",
            () -> new GenericVerticalSlabBlock(BlockBehaviour.Properties.copy(Blocks.STONE).noOcclusion()));

    /** 合并半砖方块（不同种类半砖合并到同一格，由 BlockEntity 记录两种来源）。 */
    public static final RegistryObject<Block> MERGED_SLAB_RO = BLOCKS.register("merged_slab",
            () -> new MergedSlabBlock(BlockBehaviour.Properties.copy(Blocks.STONE).noOcclusion()));

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

    public static void register(IEventBus bus) {
        // 注册原版半砖的专属竖半砖
        for (Block vanillaSlab : VANILLA_SLABS) {
            ResourceLocation vanillaId = Registry.BLOCK.getKey(vanillaSlab);
            String path = vanillaId.getPath();
            String baseName = path.substring(0, path.length() - SLAB_SUFFIX.length());
            String name = baseName + VERTICAL_SUFFIX;

            RegistryObject<Block> ro = BLOCKS.register(name,
                    () -> new VerticalSlabBlock(BlockBehaviour.Properties.copy(vanillaSlab)));
            ITEMS.register(name, () -> new BlockItem(ro.get(), new Item.Properties()));
            VANILLA_TO_VERTICAL_RO.put(vanillaSlab, ro);
        }

        BLOCKS.register(bus);
        ITEMS.register(bus);
    }

    public static Block getGenericVerticalSlab() { return GENERIC_VERTICAL_SLAB_RO.get(); }
    public static Block getMergedSlab() { return MERGED_SLAB_RO.get(); }

    /** 返回原版半砖对应的专属竖半砖；模组半砖返回 null（调用方应改用通用竖半砖）。 */
    public static Block getVerticalSlab(Block vanillaSlab) {
        RegistryObject<Block> ro = VANILLA_TO_VERTICAL_RO.get(vanillaSlab);
        return ro == null ? null : ro.get();
    }

    /** 返回专属竖半砖对应的原版半砖；非专属竖半砖返回 null。 */
    public static Block getVanillaSlab(Block verticalSlab) {
        if (verticalToVanillaCache == null) {
            verticalToVanillaCache = new HashMap<>();
            for (Map.Entry<Block, RegistryObject<Block>> e : VANILLA_TO_VERTICAL_RO.entrySet()) {
                verticalToVanillaCache.put(e.getValue().get(), e.getKey());
            }
        }
        return verticalToVanillaCache.get(verticalSlab);
    }

    /** 该半砖是否有专属竖半砖（原版半砖为 true，模组半砖为 false）。 */
    public static boolean hasDedicatedVertical(Block slab) {
        return VANILLA_TO_VERTICAL_RO.containsKey(slab);
    }
}
