package com.gregtechceu.gtceu.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import com.gto.fastcollection.fastutil.O2OOpenCacheHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;

/**
 * 方块掉落缓存。凡是要反复计算同一个方块掉落的机器（采矿机、破坏机……）都该一台机器持有一个实例。
 *
 * <p>
 * 世界与取样参数在构造时给定，之后只需 {@link #getTemplate} / {@link #forEach}。
 * 取样参数默认不带工具，矿机用 {@link LootParamsFactory#withTool} 带上自己的镐。
 * 参数在实例内固定，所以缓存键只需要 {@link BlockState}，不存在互相污染。
 *
 * <h2>使用前提</h2>
 * <ul>
 * <li><b>只适用于「掉落只由方块状态决定」的场合</b>。依赖坐标 / 生物群系 / 结构的战利品表会被
 * 缓存住，这类方块不要用本类。</li>
 * <li>带方块实体的方块不走缓存（掉落可能读实体数据）。</li>
 * <li>只在服务端主线程使用。</li>
 * <li>取样用的工具 / 实体在实例生命周期内应保持固定。</li>
 * </ul>
 *
 * <p>
 * 战利品表随数据包重载变化，重载时会调 {@link #invalidateAll()}，各实例下次读取会自动作废旧缓存。
 *
 * <h2>取出的东西是共享的</h2>
 * <p>
 * {@link #getTemplate} / {@link #forEach} 给出的是缓存里那份 {@link ItemStack}
 * <b>本体，不会复制</b>，所以不要改它。GT 的输出仓不会改写传进去的栈，取用即可；
 * 确实要改（例如叠时运）就自己 {@code copy()} 一份。
 *
 * <p>
 * 缓存容器用 {@link O2OOpenCacheHashMap}：哈希与键存在一起，探测先用存的哈希短路，
 * 且它的 {@code computeIfAbsent} 是单次查找实现。
 */
public final class BlockDropCache {

    private static volatile int generation;

    /** 数据包重载后调用，让所有实例的缓存作废。 */
    public static void invalidateAll() {
        generation++;
    }

    private final Reference2ReferenceOpenHashMap<BlockState, List<ItemStack>> cache = new Reference2ReferenceOpenHashMap<>();
    private final LootParamsFactory paramsFactory;
    private int cachedGeneration = generation;

    public BlockDropCache() {
        this(LootParamsFactory.DEFAULT);
    }

    public BlockDropCache(@NotNull LootParamsFactory paramsFactory) {
        this.paramsFactory = paramsFactory;
    }

    /**
     * 取该方块的掉落模板。
     *
     * <p>
     * <b>返回的是缓存本体，不可修改</b>：改了会污染所有后续取用同一方块的调用。
     *
     * @return 掉落模板（元素同样不可修改）；带方块实体的方块每次都是新算的
     */
    @NotNull
    public List<ItemStack> getTemplate(@NotNull ServerLevel level, @NotNull BlockState state, @NotNull BlockPos pos) {
        if (state.hasBlockEntity()) return sample(level, state, pos);
        if (cachedGeneration != generation) {
            cache.clear();
            cachedGeneration = generation;
        }
        // computeIfAbsent：命中只做一次哈希查找，未命中才取样
        return cache.computeIfAbsent(state, s -> sample(level, state, pos));
    }

    /**
     * 把该方块的掉落喂给 {@code consumer}。
     *
     * <p>
     * <b>喂过去的是缓存里那几份 {@link ItemStack} 本身，不会复制</b>，所以 {@code consumer}
     * 里不要改它们；要改就自己 {@code copy()}。
     */
    public void forEach(@NotNull ServerLevel level, @NotNull BlockState state, @NotNull BlockPos pos,
                        @NotNull Consumer<ItemStack> consumer) {
        getTemplate(level, state, pos).forEach(consumer);
    }

    public void clear() {
        cache.clear();
    }

    private List<ItemStack> sample(ServerLevel level, BlockState state, BlockPos pos) {
        return state.getDrops(paramsFactory.create(level, state, pos));
    }

    /** 取样参数工厂：往基础参数上再补自己的东西。 */
    @FunctionalInterface
    public interface LootParamsFactory {

        @NotNull
        LootParams.Builder create(ServerLevel level, BlockState state, BlockPos pos);

        /** 不带工具，等价于 {@code Block.getDrops(state, level, pos, null)}。 */
        LootParamsFactory DEFAULT = (level, state, pos) -> base(level, state, pos)
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY);

        /** 带上工具：矿机、破坏机这类"用什么挖"的机器用这个。 */
        static LootParamsFactory withTool(ItemStack tool) {
            return (level, state, pos) -> base(level, state, pos)
                    .withParameter(LootContextParams.TOOL, tool);
        }

        /** 带上实体：战利品表要玩家/生物参与时用这个。 */
        static LootParamsFactory withEntity(Entity entity, ItemStack tool) {
            return (level, state, pos) -> base(level, state, pos)
                    .withParameter(LootContextParams.TOOL, tool)
                    .withOptionalParameter(LootContextParams.THIS_ENTITY, entity);
        }

        /** 只含 {@code BLOCK_STATE} 与 {@code ORIGIN} 的基础参数，需要什么自己补。 */
        static LootParams.Builder base(ServerLevel level, BlockState state, BlockPos pos) {
            return new LootParams.Builder(level)
                    .withParameter(LootContextParams.BLOCK_STATE, state)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atLowerCornerOf(pos));
        }
    }
}
