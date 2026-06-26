package com.gregtechceu.gtceu.api.recipe.modifier;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.feature.IOverclockMachine;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.ICoilMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IWorkableMultiController;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.common.data.GTRecipeDataKeys;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.GTMath;

import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

@SuppressWarnings("unused")
@FunctionalInterface
@ParametersAreNonnullByDefault
public interface RecipeModifier {

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
                            contentMultiplier = maxContentMultiplier;
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

    static RecipeModifier overclocking(double durationFactor, double reductionEUt, double reductionDuration) {
        return (holder, unit, recipe) -> overclocking(holder, unit, recipe, false, reductionEUt, reductionDuration, durationFactor);
    }

    static RecipeModifier accurateParallel(long parallel) {
        return (holder, unit, recipe) -> ParallelLogic.accurateParallel(holder, unit, recipe, parallel);
    }

    static RecipeModifier multiplier(double euMultiplier, double durationMultiplier) {
        return (holder, unit, recipe) -> multiplier(recipe, euMultiplier, durationMultiplier);
    }

    static GTRecipe multiplier(GTRecipe recipe, double euMultiplier, double durationMultiplier) {
        if (euMultiplier != 1) {
            recipe.eut = (long) (recipe.eut * euMultiplier);
        }
        if (durationMultiplier != 1) {
            recipe.duration = Math.max(1, (int) (recipe.duration * durationMultiplier));
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

    static @Nullable GTRecipe overclocking(final IRecipeHandlerHolder holder, RecipeHandlerUnit unit, final GTRecipe recipe, boolean generator, double reductionEUt, double reductionDuration, double durationFactor) {
        if (holder instanceof IOverclockMachine overclockMachine) {
            return overclocking(holder, unit, recipe, holder instanceof IWorkableMultiController controller && controller.isBatchEnabled(), overclockMachine.getOverclockLimit(), (long) ((generator ? recipe.getOutputEUt() : recipe.getInputEUt()) * reductionEUt), overclockMachine.getOverclockVoltage(), generator, reductionDuration, durationFactor);
        }
        return recipe;
    }

    static @Nullable GTRecipe overclocking(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe, boolean batchEnabled, int limitDuration, long recipeVoltage, long maxVoltage, boolean generator, double reductionDuration, double durationFactor) {
        long maxContentMultiplier = 0;
        long contentMultiplier = 1;
        double duration = recipe.duration * reductionDuration;
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
                        contentMultiplier = maxContentMultiplier;
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
