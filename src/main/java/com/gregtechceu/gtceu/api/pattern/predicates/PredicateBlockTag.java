package com.gregtechceu.gtceu.api.pattern.predicates;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

public class PredicateBlockTag extends SimplePredicate {

    private static final AtomicInteger GENERATION = new AtomicInteger();

    protected final TagKey<Block> tag;
    @Nullable
    private volatile Members members;

    public PredicateBlockTag(TagKey<Block> tag) {
        this.tag = tag;
        buildPredicate();
    }

    public static PredicateBlockTag of(TagKey<Block> tag) {
        return new PredicateBlockTag(tag);
    }

    public static int generation() {
        return GENERATION.get();
    }

    @Override
    public SimplePredicate buildPredicate() {
        if (tag == null) {
            predicate = GTUtil.NEGATIVE;
            blockInfo = () -> BlockInfo.EMPTY;
            candidates = () -> new Block[] { Blocks.AIR };
            return this;
        }
        predicate = state -> state.getBlockState().is(tag);
        candidates = () -> members().blocks;
        blockInfo = () -> members().info;
        return this;
    }

    private Members members() {
        int generation = GENERATION.get();
        var cached = members;
        if (cached != null && cached.generation == generation) return cached;
        var blocks = BuiltInRegistries.BLOCK.getTag(tag)
                .stream()
                .flatMap(HolderSet.Named::stream)
                .map(Holder::value)
                .toArray(Block[]::new);
        if (blocks.length == 0) blocks = new Block[] { Blocks.BARRIER };
        var resolved = new Members(generation, blocks, BlockInfo.fromBlock(blocks[0]));
        if (generation > 0) members = resolved;
        return resolved;
    }

    private record Members(int generation, Block[] blocks, BlockInfo info) {}

    @Mod.EventBusSubscriber(modid = GTCEu.MOD_ID)
    public static final class TagReloads {

        private TagReloads() {}

        @SubscribeEvent
        public static void onTagsUpdated(TagsUpdatedEvent event) {
            GENERATION.incrementAndGet();
        }
    }
}
