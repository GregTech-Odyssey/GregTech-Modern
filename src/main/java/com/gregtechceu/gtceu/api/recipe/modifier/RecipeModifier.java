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

@SuppressWarnings("unused")
@FunctionalInterface
@ParametersAreNonnullByDefault
public interface RecipeModifier extends IRecipeInfo {

    int COIL_EUT_DISCOUNT_TEMPERATURE = 900;

    RecipeModifier NO_MODIFIER = (h, u, r) -> r;

    @Nullable
    GTRecipe applyModifier(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe);

    RecipeModifier GENERATOR_OVERCLOCKING = RecipeModifier::generatorOverclocking;
    RecipeModifier PERFECT_OVERCLOCKING = RecipeModifier::perfectOverclocking;
    RecipeModifier OVERCLOCKING = RecipeModifier::overclocking;
    RecipeModifier BATCH_PROCESSING = RecipeModifier::batchProcessing;

    RecipeModifier CRACKER_OVERCLOCK = (holder, unit, recipe) -> {
        if (holder instanceof ICoilMachine coilMachine) {
            return overclocking(holder, unit, recipe, false, Math.max(0.2, 1.0 - (coilMachine.getCoilTier() * 0.1)), 1, 0.5);
        }
        return null;
    };

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

    static RecipeModifier overclocking(double durationFactor, double euMultiplier, double durationMultiplier) {
        return (holder, unit, recipe) -> overclocking(holder, unit, recipe, false, euMultiplier, durationMultiplier, durationFactor);
    }

    static RecipeModifier accurateParallel(long parallel) {
        return (holder, unit, recipe) -> ParallelLogic.accurateParallel(holder, unit, recipe, parallel);
    }

    static RecipeModifier multiplier(double euMultiplier, double durationMultiplier) {
        return (holder, unit, recipe) -> multiplier(recipe, euMultiplier, durationMultiplier);
    }

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
     * 用电机器（比例 < 2）恒不触顶，该夹取实际只对 parallelFactor = 5 的发电机生效。
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

    static int getCoilDiscountAmount(int recipeTemp, int machineTemp) {
        return Math.max(0, (machineTemp - recipeTemp) / COIL_EUT_DISCOUNT_TEMPERATURE);
    }

    static double getCoilEUtDiscount(int recipeTemp, int machineTemp) {
        if (recipeTemp < COIL_EUT_DISCOUNT_TEMPERATURE) return 1;
        int amountEUtDiscount = getCoilDiscountAmount(recipeTemp, machineTemp);
        if (amountEUtDiscount < 1) return 1;
        return Math.min(1, Math.pow(0.95, amountEUtDiscount));
    }
}
