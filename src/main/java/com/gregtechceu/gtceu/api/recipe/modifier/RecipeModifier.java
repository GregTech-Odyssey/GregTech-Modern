package com.gregtechceu.gtceu.api.recipe.modifier;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.feature.IOverclockMachine;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.ICoilMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IWorkableMultiController;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.ui.IRecipeInfo;
import com.gregtechceu.gtceu.common.data.GTRecipeDataKeys;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.GTMath;

import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 配方修饰器：在配方匹配成功之后、真正执行之前，对运行时配方 {@link GTRecipe} 做变换。
 *
 * <p>
 * 超频、并行、批处理都是它的实例。它是函数式接口，直接写 lambda 即可；
 * 返回 {@code null} 表示「这条配方在当前机器上不可用」，调用方会跳过它。
 *
 * <p>
 * 施加顺序见 {@code IRecipeLogicMachine#fullModifyRecipe}：
 * <ol>
 * <li>先按序应用配方自带的
 * {@link com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition#recipeModifiers}；</li>
 * <li>再应用机器自身的修饰器（{@code doModifyRecipe}，通常是下面预置的那几个）。</li>
 * </ol>
 * 同一流程里还会把匹配到的输入分组的颜色写进 {@link GTRecipe#outputColor}，
 * 并按机器的输出上限裁剪一次产物。
 *
 * <p>
 * 本接口也继承 {@link IRecipeInfo}，可以顺带在配方界面上补充说明文字。
 */
@SuppressWarnings("unused")
@FunctionalInterface
@ParametersAreNonnullByDefault
public interface RecipeModifier extends IRecipeInfo {

    /** 线圈每比配方温度高这么多度，就多一档电压折扣。 */
    int COIL_EUT_DISCOUNT_TEMPERATURE = 900;

    /** 什么都不改的修饰器。 */
    RecipeModifier NO_MODIFIER = (h, u, r) -> r;

    /**
     * 对配方做变换。
     *
     * @param holder 发起这次配方的机器
     * @param unit   匹配到的处理器分组（并行数计算要用它）
     * @return 变换后的配方，可以就地修改并返回同一个对象；
     *         返回 {@code null} 表示这条配方在当前机器上不可用
     */
    @Nullable
    GTRecipe applyModifier(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe);

    /** 发电机超频：每级时长 ×0.2，并且以发电电压（负数侧）为基准。 */
    RecipeModifier GENERATOR_OVERCLOCKING = RecipeModifier::generatorOverclocking;
    /** 无损超频：每级时长 ×0.25，而不是普通超频的 ×0.5。 */
    RecipeModifier PERFECT_OVERCLOCKING = RecipeModifier::perfectOverclocking;
    /** 普通超频：每级电压 ×4、时长 ×0.5；时长压到机器下限后改为折算并行。 */
    RecipeModifier OVERCLOCKING = RecipeModifier::overclocking;
    /**
     * 批处理：把能并行处理的份数合并成一次运行，时长按份数拉长。
     *
     * <p>
     * 时长上限由配置 {@code machines.batchDuration} 决定，超出部分不会继续合并。
     */
    RecipeModifier BATCH_PROCESSING = RecipeModifier::batchProcessing;

    /** 裂化机：线圈等级越高，超频的时长系数越小（每级 −0.1，最低 0.2）。 */
    RecipeModifier CRACKER_OVERCLOCK = (holder, unit, recipe) -> {
        if (holder instanceof ICoilMachine coilMachine) {
            return overclocking(holder, unit, recipe, false, Math.max(0.2, 1.0 - (coilMachine.getCoilTier() * 0.1)), 1, 0.5);
        }
        return null;
    };

    /** 热解炉：线圈 0 级时时长 ×1.33，之后按线圈等级取 ×2/(等级+1)。 */
    RecipeModifier PYROLYSE_OVEN_OVERCLOCK = (holder, unit, recipe) -> {
        if (holder instanceof ICoilMachine coilMachine) {
            if (coilMachine.getCoilTier() == 0) {
                return overclocking(holder, unit, recipe, false, 1, 1.33, 0.5);
            } else {
                return overclocking(holder, unit, recipe, false, 1, 2.0 / (coilMachine.getCoilTier() + 1), 0.5);
            }
        }
        return null;
    };

    /**
     * 高炉：线圈温度低于配方需求时返回 {@code null}（配方不可用）；够温度时按温差给电压折扣，
     * 温差每满 1800 度还能享受一次无损超频（时长 ÷4 而不是 ÷2）。
     */
    RecipeModifier EBF_OVERCLOCK = (holder, unit, recipe) -> {
        if (holder instanceof ICoilMachine coilMachine && holder instanceof IOverclockMachine overclockMachine) {
            int temperature = coilMachine.getCoilType().getCoilTemperature() + (100 * Math.max(0, ((ITieredMachine) coilMachine).getTier() - GTValues.MV));
            int recipeTemp = recipe.data.getInt(GTRecipeDataKeys.EBF_TEMP);
            if (recipeTemp > temperature) {
                return null;
            }
            long recipeVoltage = (long) (recipe.getInputEUt() * getCoilEUtDiscount(recipeTemp, temperature));
            int duration = recipe.duration;
            long maxContentMultiplier = 0;
            long contentMultiplier = 1;
            if (duration > 1) {
                final long maxVoltage = overclockMachine.getOverclockVoltage();
                int amountPerfectOC = Math.max(0, (temperature - recipeTemp) / 1800);
                final int limit = overclockMachine.getOverclockLimit();
                int ocLevel = 0;
                while (true) {
                    final long overclockVoltage = recipeVoltage << 2;
                    if (overclockVoltage > maxVoltage || overclockVoltage < 0) break;
                    final int d = duration >> (amountPerfectOC > 0 ? 2 : 1);
                    if (d < limit) {
                        if (maxContentMultiplier == 0) {
                            maxContentMultiplier = ParallelLogic.getRemainingMaxParallelAmount(holder, unit, recipe);
                            if (maxContentMultiplier == 0) return null;
                        }
                        final long parallel = contentMultiplier << 1;
                        if (parallel > maxContentMultiplier) {
                            final long target = compensationTarget(recipeVoltage, maxVoltage, contentMultiplier, maxContentMultiplier);
                            if (target > contentMultiplier) {
                                recipeVoltage = scaleCompensationCost(recipeVoltage, contentMultiplier, target);
                                contentMultiplier = target;
                            }
                            break;
                        }
                        contentMultiplier = parallel;
                    } else {
                        duration = d;
                    }
                    amountPerfectOC--;
                    recipeVoltage = overclockVoltage;
                    ocLevel++;
                }
                recipe.ocLevel = ocLevel / 2;
                recipe.duration = duration;
            } else {
                recipe.duration = 1;
            }
            recipe.eut = recipeVoltage;
            if (holder instanceof IWorkableMultiController controller && controller.isBatchEnabled()) return batchProcessing(holder, unit, recipe, maxContentMultiplier, contentMultiplier);
            if (contentMultiplier > 1) {
                recipe.modifier(contentMultiplier, false);
                recipe.batchParallels = contentMultiplier;
            }
        }
        return recipe;
    };

    /** 多核熔炼：先按线圈等级确定并行上限（{@code 32 × 等级}）并折算电压 / 时长，再做普通超频。 */
    RecipeModifier MULTI_SMELTER_OVERCLOCK = (holder, unit, recipe) -> {
        if (holder instanceof ICoilMachine coilMachine) {
            int maxParallel = 32 * coilMachine.getCoilType().getLevel();
            recipe = ParallelLogic.accurateParallel(holder, unit, recipe, maxParallel);
            if (recipe == null) return null;
            recipe.eut = Math.max(1, 4 * (long) (recipe.parallels / (8.0 * coilMachine.getCoilType().getEnergyDiscount())));
            recipe.duration = (int) (128 * 2.0 * recipe.parallels / maxParallel);
            return overclocking(holder, unit, recipe);
        }
        return null;
    };

    /**
     * 生成一个超频修饰器。
     *
     * @param durationFactor     每级超频后剩余的时长比例（越小越快；{@code recipe.perfect} 为真时会被 0.25 覆盖）
     * @param euMultiplier       超频前先对电压乘上的系数
     * @param durationMultiplier 超频前先对时长乘上的系数
     */
    static RecipeModifier overclocking(double durationFactor, double euMultiplier, double durationMultiplier) {
        return (holder, unit, recipe) -> overclocking(holder, unit, recipe, false, euMultiplier, durationMultiplier, durationFactor);
    }

    /** 生成一个「最多并行 {@code parallel} 份」的修饰器，实际份数仍受机器库存限制。 */
    static RecipeModifier accurateParallel(long parallel) {
        return (holder, unit, recipe) -> ParallelLogic.accurateParallel(holder, unit, recipe, parallel);
    }

    /** 生成一个只缩放电压与时长的修饰器，不涉及超频与并行。 */
    static RecipeModifier multiplier(double euMultiplier, double durationMultiplier) {
        return (holder, unit, recipe) -> multiplier(recipe, euMultiplier, durationMultiplier);
    }

    /** {@link #multiplier(double, double)} 的实际实现：乘数等于 1 时对应字段保持不变。 */
    static GTRecipe multiplier(GTRecipe recipe, double euMultiplier, double durationMultiplier) {
        if (euMultiplier != 1) {
            recipe.euMultiplier(euMultiplier);
        }
        if (durationMultiplier != 1) {
            recipe.durationMultiplier(durationMultiplier);
        }
        return recipe;
    }

    static @Nullable GTRecipe laserLossOverclocking(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe) {
        return overclocking(holder, unit, recipe, false, 1, 1, 0.65);
    }

    static @Nullable GTRecipe generatorOverclocking(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe) {
        return overclocking(holder, unit, recipe, true, 1, 1, 0.2);
    }

    static @Nullable GTRecipe perfectOverclocking(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe) {
        return overclocking(holder, unit, recipe, false, 1, 1, 0.25);
    }

    static @Nullable GTRecipe overclocking(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe) {
        return overclocking(holder, unit, recipe, false, 1, 1, 0.5);
    }

    static @Nullable GTRecipe overclocking(final IRecipeHandlerHolder holder, RecipeHandlerUnit unit, final GTRecipe recipe, boolean generator, double euMultiplier, double durationMultiplier, double durationFactor) {
        if (holder instanceof IOverclockMachine overclockMachine) {
            var recipeVoltage = generator ? recipe.getOutputEUt() : recipe.getInputEUt();
            if (recipeVoltage != 0 && euMultiplier != 1) {
                recipeVoltage = Math.max(1, (long) (recipeVoltage * euMultiplier));
            }
            return overclocking(holder, unit, recipe, holder instanceof IWorkableMultiController controller && controller.isBatchEnabled(), overclockMachine.getOverclockLimit(), recipeVoltage, overclockMachine.getOverclockVoltage(), generator, durationMultiplier, durationFactor);
        }
        return recipe;
    }

    static @Nullable GTRecipe overclocking(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe, boolean batchEnabled, int limitDuration, long recipeVoltage, long maxVoltage, boolean generator, double durationMultiplier, double durationFactor) {
        long maxContentMultiplier = 0;
        long contentMultiplier = 1;
        double duration = recipe.duration * durationMultiplier;
        if (duration > 0) {
            durationFactor = recipe.perfect ? 0.25 : durationFactor;
            final int parallelFactor = generator ? 5 : 2;
            int ocLevel = 0;
            while (true) {
                final long overclockVoltage = recipeVoltage << 2;
                if (overclockVoltage > maxVoltage || overclockVoltage < 0) break;
                final double d = duration * durationFactor;
                if (d < limitDuration) {
                    if (maxContentMultiplier == 0) {
                        maxContentMultiplier = ParallelLogic.getRemainingMaxParallelAmount(holder, unit, recipe);
                        if (maxContentMultiplier == 0) return null;
                    }
                    final long parallel = contentMultiplier * parallelFactor;
                    if (parallel > maxContentMultiplier) {
                        final long target = compensationTarget(recipeVoltage, maxVoltage, contentMultiplier, maxContentMultiplier);
                        if (target > contentMultiplier) {
                            recipeVoltage = scaleCompensationCost(recipeVoltage, contentMultiplier, target);
                            contentMultiplier = target;
                        }
                        break;
                    }
                    contentMultiplier = parallel;
                } else {
                    duration = d;
                }
                recipeVoltage = overclockVoltage;
                ocLevel++;
            }
            recipe.ocLevel = ocLevel;
            recipe.duration = Math.max(1, (int) duration);
        } else {
            recipe.duration = 1;
        }
        recipe.eut = generator ? -recipeVoltage : recipeVoltage;
        if (batchEnabled) return batchProcessing(holder, unit, recipe, maxContentMultiplier, contentMultiplier);
        if (contentMultiplier > 1) {
            recipe.modifier(contentMultiplier, false);
            recipe.batchParallels = contentMultiplier;
        }
        return recipe;
    }

    /**
     * 超频补偿截断时可放大到的并行上限。
     * <p>
     * 剩余输入凑不满下一档并行时，仍然把它一次吃完以减少配方轮次，但不得超过代价（电压 / 魔力）
     * 天花板所能支撑的倍数。循环已验证 {@code cost * 4 <= maxCost}，故 parallelFactor = 2 的
     * 用电机器（比例 &lt; 2）恒不触顶，该夹取实际只对 parallelFactor = 5 的发电机生效。
     * <p>
     * 夹取到 {@code current * (maxCost / cost)} 亦保证 {@link #scaleCompensationCost} 的结果
     * 不会超过 {@code maxCost}，也不会溢出。
     */
    static long compensationTarget(long cost, long maxCost, long current, long maxContentMultiplier) {
        if (cost <= 0) return maxContentMultiplier;
        return Math.min(maxContentMultiplier, current * (maxCost / cost));
    }

    /**
     * 把代价按 {@code target / current} 的比例放大，使单位产出的代价与放大前完全相同：
     * 用电机器多处理多少份就多付多少电，发电机多烧多少燃料就多发多少电。
     * <p>
     * 一律向上取整，确保放大后绝不比放大前便宜；{@code cost * target} 这个中间量可能溢出，
     * 故按整数倍与余数拆开计算（结果上界为 {@code maxCost}，必定落在 long 内）。
     */
    static long scaleCompensationCost(long cost, long current, long target) {
        final long q = target / current;
        final long r = target % current;
        long scaled = cost * q;
        if (r != 0) {
            scaled += cost <= Long.MAX_VALUE / r ? Math.ceilDiv(cost * r, current) : Math.ceilDiv(cost, current) * r;
        }
        return scaled;
    }

    /** 批处理：按剩余可并行的份数把配方合并成一次运行，时长按份数拉长。 */
    static @Nullable GTRecipe batchProcessing(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, final GTRecipe recipe) {
        final int maxDurationMultiplier = ConfigHolder.INSTANCE.machines.batchDuration / recipe.duration;
        if (maxDurationMultiplier > 1) {
            long contentMultiplier = ParallelLogic.getRemainingMaxParallelAmount(holder, unit, recipe);
            if (contentMultiplier == 0) return null;
            if (contentMultiplier > 1) {
                if (contentMultiplier > maxDurationMultiplier) {
                    contentMultiplier = maxDurationMultiplier;
                }
                recipe.modifier(contentMultiplier, false);
                recipe.batchParallels = contentMultiplier;
                recipe.duration *= (int) contentMultiplier;
            }
        }
        return recipe;
    }

    /**
     * 批处理的另一个入口：调用方已经算出内容倍率时走这里，省掉重复计算并行数
     * （高炉超频路径就是这样调用的）。
     *
     * @param maxContentMultiplier 当前还能处理的最大份数；传 {@code 0} 表示需要现算
     * @param contentMultiplier    本次已经放大的份数
     */
    static @Nullable GTRecipe batchProcessing(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe, long maxContentMultiplier, long contentMultiplier) {
        final int maxDurationMultiplier = ConfigHolder.INSTANCE.machines.batchDuration / recipe.duration;
        if (maxDurationMultiplier > 1) {
            if (maxContentMultiplier == 0) {
                maxContentMultiplier = ParallelLogic.getRemainingMaxParallelAmount(holder, unit, recipe);
                if (maxContentMultiplier == 0) return null;
            }
            if (maxContentMultiplier > 1) {
                if (contentMultiplier < maxContentMultiplier) {
                    final int multiplier = GTMath.saturatedCast(maxContentMultiplier / contentMultiplier);
                    if (multiplier > maxDurationMultiplier) {
                        recipe.duration *= maxDurationMultiplier;
                        contentMultiplier *= maxDurationMultiplier;
                    } else {
                        recipe.duration *= multiplier;
                        contentMultiplier = maxContentMultiplier;
                    }
                }
                recipe.modifier(contentMultiplier, false);
                recipe.batchParallels = contentMultiplier;
            }
        }
        return recipe;
    }

    /** 机器温度比配方温度每高出 {@link #COIL_EUT_DISCOUNT_TEMPERATURE} 度算一档电压折扣。 */
    static int getCoilDiscountAmount(int recipeTemp, int machineTemp) {
        return Math.max(0, (machineTemp - recipeTemp) / COIL_EUT_DISCOUNT_TEMPERATURE);
    }

    /**
     * 线圈带来的电压折扣系数：按档数取 {@code 0.95^n}，且不会超过 1。
     * 配方温度本身低于 {@link #COIL_EUT_DISCOUNT_TEMPERATURE} 时不打折。
     */
    static double getCoilEUtDiscount(int recipeTemp, int machineTemp) {
        if (recipeTemp < COIL_EUT_DISCOUNT_TEMPERATURE) return 1;
        int amountEUtDiscount = getCoilDiscountAmount(recipeTemp, machineTemp);
        if (amountEUtDiscount < 1) return 1;
        return Math.min(1, Math.pow(0.95, amountEUtDiscount));
    }
}
