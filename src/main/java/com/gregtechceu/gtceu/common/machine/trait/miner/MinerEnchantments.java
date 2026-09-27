package com.gregtechceu.gtceu.common.machine.trait.miner;

import com.gregtechceu.gtceu.api.machine.trait.EnchantmentSlotHandler;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * 矿机附魔的数学部分，与机器类型无关的静态工具。
 *
 * <p>
 * 抽成静态方法是为了让别的矿机实现（例如数字采矿机）能直接复用同一套公式，
 * 而不必继承 {@code MinerLogic}——它们只需要提供一个 {@link EnchantmentSlotHandler}
 * 和「是否精准」的开关即可。
 *
 * <h2>公式</h2>
 * <ul>
 * <li><b>时运</b>：产出倍率取原版「每堆额外 +rand(0..L)」的期望值 {@code 1 + L/2}，
 * 只用于计费；实际掉落走 {@link #applyFortune} 现场骰点。</li>
 * <li><b>效率</b>：按原版「挖掘速度 += L² + 1」折算，{@code (基准 + L² + 1) / 基准}。</li>
 * <li><b>耐久</b>：按原版「L/(L+1) 概率不掉耐久」折算，耗电乘以 {@code 1/(L+1)}。</li>
 * <li><b>精准</b>：与时运互斥——只要走精准，时运等级一律按 0 处理。</li>
 * </ul>
 */
public final class MinerEnchantments {

    private MinerEnchantments() {}

    /**
     * 是否走精准采集掉落。
     *
     * <p>
     * 机器自带的精准模式优先；机器没有精准模式时，看槽里有没有精准采集书。
     *
     * @param machineSilkTouch 机器自身的精准模式是否开启
     */
    public static boolean isSilkTouchActive(@Nullable EnchantmentSlotHandler slot, boolean machineSilkTouch) {
        if (machineSilkTouch) return true;
        return slot != null && slot.getSilkTouchLevel() > 0;
    }

    /** @return 时运等级；走精准时返回 {@code 0} */
    public static int getFortuneLevel(@Nullable EnchantmentSlotHandler slot, boolean silkTouchActive) {
        if (silkTouchActive || slot == null) return 0;
        return slot.getFortuneLevel();
    }

    /** @return 效率等级，没有时为 {@code 0} */
    public static int getEfficiencyLevel(@Nullable EnchantmentSlotHandler slot) {
        return slot == null ? 0 : slot.getEfficiencyLevel();
    }

    /** @return 耐久等级，没有时为 {@code 0} */
    public static int getUnbreakingLevel(@Nullable EnchantmentSlotHandler slot) {
        return slot == null ? 0 : slot.getUnbreakingLevel();
    }

    /** 时运带来的产出倍率（原版骰点的期望值 {@code 1 + L/2}），只用于计费。 */
    public static double getFortuneMultiplier(int fortuneLevel) {
        return 1.0D + fortuneLevel / 2.0D;
    }

    /**
     * 效率带来的速度倍率：{@code (baseToolSpeed + L² + 1) / baseToolSpeed}。
     *
     * @param baseToolSpeed 所用镐的挖掘速度基准，必须大于 0
     */
    public static double getSpeedMultiplier(int efficiencyLevel, double baseToolSpeed) {
        if (efficiencyLevel <= 0) return 1.0D;
        double base = baseToolSpeed > 0 ? baseToolSpeed : 1.0D;
        return (base + (double) efficiencyLevel * efficiencyLevel + 1.0D) / base;
    }

    /**
     * 耐久带来的省电倍率 {@code 1/(L+1)}（L=3 时省到四分之一）。
     *
     * @return 不大于 1 的倍率；没有耐久时为 {@code 1}
     */
    public static double getPowerSavingMultiplier(int unbreakingLevel) {
        return unbreakingLevel <= 0 ? 1.0D : 1.0D / (unbreakingLevel + 1);
    }

    /**
     * 把基础间隔按速度倍率折算成实际间隔。
     *
     * @param baseSpeed 不带附魔时的间隔（tick / 方块）
     * @return 至少为 1
     */
    public static int getActiveSpeed(int baseSpeed, double activeSpeedMultiplier) {
        if (activeSpeedMultiplier <= 1.0D) return baseSpeed;
        return Math.max(1, (int) Math.round(baseSpeed / activeSpeedMultiplier));
    }

    /**
     * 按可用资源解析本次实际生效的附魔强度。
     *
     * <p>
     * 结算顺序是「先省电、再按加成涨价」：耐久先把基础代价打成 {@code 基础 × 省电倍率}
     * （始终全额生效，不参与缩放），时运与效率再各自按倍率抬高它。
     *
     * <p>
     * 资源不够时求一个 {@code k ∈ [0,1]}，把时运与效率的加成等比缩成 {@code 1 + (倍率-1)·k}，
     * 使实际代价落在可用量内——也就是「电力不够自动削弱效果」。连打完折的基础代价都付不起时，
     * 返回值仍是最低代价，由调用方比对可用量后判定停机。
     *
     * @param baseCost          不带附魔时的单 tick 代价
     * @param available         当前可用资源
     * @param fortuneMultiplier 时运倍率，见 {@link #getFortuneMultiplier}
     * @param speedMultiplier   效率倍率，见 {@link #getSpeedMultiplier}
     * @param powerSaving       耐久省电倍率，见 {@link #getPowerSavingMultiplier}
     */
    @NotNull
    public static Resolved resolveCost(long baseCost, long available, double fortuneMultiplier,
                                       double speedMultiplier, double powerSaving) {
        if (baseCost <= 0) {
            return new Resolved(baseCost, 1.0D, 1.0D);
        }
        double discountedBase = baseCost * powerSaving;
        long minCost = Math.max(1L, (long) Math.ceil(discountedBase));
        if (fortuneMultiplier <= 1.0D && speedMultiplier <= 1.0D) {
            return new Resolved(minCost, 1.0D, 1.0D);
        }
        double k;
        double full = discountedBase * fortuneMultiplier * speedMultiplier;
        if (available >= full) {
            k = 1.0D;
        } else if (available <= discountedBase) {
            k = 0.0D;
        } else {
            // 解 discountedBase · (1 + (f-1)k) · (1 + (s-1)k) = available
            double a = (fortuneMultiplier - 1.0D) * (speedMultiplier - 1.0D);
            double b = (fortuneMultiplier - 1.0D) + (speedMultiplier - 1.0D);
            double c = 1.0D - available / discountedBase;
            k = a > 1.0E-9D ? (-b + Math.sqrt(b * b - 4.0D * a * c)) / (2.0D * a) : -c / b;
            k = Math.max(0.0D, Math.min(1.0D, k));
        }
        double activeFortune = 1.0D + (fortuneMultiplier - 1.0D) * k;
        double activeSpeed = 1.0D + (speedMultiplier - 1.0D) * k;
        long cost = (long) Math.ceil(discountedBase * activeFortune * activeSpeed);
        if (available >= minCost) {
            cost = Math.min(cost, available);
        }
        return new Resolved(Math.max(cost, minCost), activeFortune, activeSpeed);
    }

    /**
     * 按原版时运语义给掉落加成：每堆额外增加 {@code rand(0..L)} 个。
     *
     * <p>
     * 不使用原版的 {@code ApplyBonusCount} / 战利品上下文，直接掷点，
     * 因此对任何掉落物都生效，不依赖方块是否走战利品表。
     *
     * <p>
     * {@code drops} 里可能是 {@code BlockDropCache} 的共享掉落模板，<b>不能就地改</b>，
     * 所以只有真正吃到加成的那几堆才换成副本；没加成时一份都不复制。
     *
     * @param filter 只给满足条件的掉落加成（例如大型采矿机只加粉碎矿）；全部加成就传 {@code s -> true}
     */
    public static void applyFortune(@NotNull List<ItemStack> drops, int fortuneLevel,
                                    @NotNull RandomSource random, @NotNull Predicate<ItemStack> filter) {
        if (fortuneLevel <= 0) return;
        for (int i = 0; i < drops.size(); i++) {
            var stack = drops.get(i);
            if (stack.isEmpty() || !filter.test(stack)) continue;
            int bonus = random.nextInt(fortuneLevel + 1);
            if (bonus <= 0) continue;
            var boosted = stack.copy();
            boosted.setCount(Math.min(boosted.getMaxStackSize(), boosted.getCount() + bonus));
            drops.set(i, boosted);
        }
    }

    /**
     * {@link #resolveCost} 的结果。
     *
     * @param cost              实际应扣除的代价
     * @param fortuneMultiplier 实际生效的时运倍率（已含等比削弱）
     * @param speedMultiplier   实际生效的效率倍率（已含等比削弱）
     */
    public record Resolved(long cost, double fortuneMultiplier, double speedMultiplier) {}
}
