package com.gregtechceu.gtceu.common.machine.multiblock.generator;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.fluids.store.FluidStorageKeys;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.gui.fancy.TooltipsPanel;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTMath;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraftforge.fluids.FluidStack;

import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class LargeCombustionEngineMachine extends WorkableElectricMultiblockMachine {

    private static final FluidStack OXYGEN_STACK = GTMaterials.Oxygen.getFluid(1);
    private static final FluidStack LIQUID_OXYGEN_STACK = GTMaterials.Oxygen.getFluid(FluidStorageKeys.LIQUID, 4);
    private static final FluidStack LUBRICANT_STACK = GTMaterials.Lubricant.getFluid(1);
    @Getter
    private final int tier;
    // runtime
    @SyncToClient
    private boolean isOxygenBoosted = false;
    private int runningTimer = 0;

    public LargeCombustionEngineMachine(MetaMachineBlockEntity holder, int tier) {
        super(holder);
        this.tier = tier;
    }

    private boolean isIntakesObstructed() {
        for (int i = -1; i < 2; i++) {
            for (int j = -1; j < 2; j++) {
                // Skip the controller block itself
                if (i == 0 && j == 0) continue;
                var blockPos = RelativeDirection.offsetPos(getPos(), getFrontFacing(), getUpwardsFacing(), isFlipped(), i, j, 1);
                var blockState = this.getLevel().getBlockState(blockPos);
                if (!blockState.isAir()) return true;
            }
        }
        return false;
    }

    private boolean isExtreme() {
        return getTier() > GTValues.EV;
    }

    public boolean isBoostAllowed() {
        return getMaxVoltage() >= GTValues.V[getTier() + 1];
    }

    //////////////////////////////////////
    // ****** Recipe Logic *******//
    //////////////////////////////////////
    @Override
    public long getOverclockVoltage() {
        if (isOxygenBoosted) return GTValues.V[tier] * 2;
        else return GTValues.V[tier];
    }

    /**
     * @return EUt multiplier that should be applied to the engine's output
     */
    protected double getProductionBoost() {
        if (!isOxygenBoosted) return 1;
        return isExtreme() ? 2.0 : 1.5;
    }

    /**
     * Recipe Modifier for <b>Combustion Engine Multiblocks</b> - can be used as a valid {@link RecipeModifier}
     * <p>
     * Recipe is rejected if the machine's intakes are obstructed or if it doesn't have lubricant<br>
     * Recipe is parallelized up to {@code desiredEUt / recipeEUt} times.
     * EUt is further multiplied by the production boost of the engine.
     *
     * @param machine a {@link LargeCombustionEngineMachine}
     * @param recipe  recipe
     */
    @Nullable
    public static GTRecipe recipeModifier(IRecipeHandlerHolder machine, RecipeHandlerUnit unit, GTRecipe recipe) {
        if (!(machine instanceof LargeCombustionEngineMachine engineMachine)) {
            return null;
        }
        long EUt = recipe.getOutputEUt();
        // has lubricant
        if (EUt > 0 && !engineMachine.isIntakesObstructed() && engineMachine.inputFluid(LUBRICANT_STACK)) {
            var maxParallel = engineMachine.getOverclockVoltage() / EUt; // get maximum parallel
            var actualParallel = ParallelLogic.getMaxParallelAmount(engineMachine, unit, recipe, maxParallel);
            if (actualParallel == 0) return null;
            double eutMultiplier = actualParallel * engineMachine.getProductionBoost();
            recipe.modifier(actualParallel, false);
            recipe.euMultiplier(eutMultiplier);
            return recipe;
        }
        return null;
    }

    @Override
    public boolean onWorking() {
        boolean value = super.onWorking();
        // check lubricant
        if (runningTimer % 72 == 0) {
            // insufficient lubricant
            if (!inputFluid(LUBRICANT_STACK)) {
                recipeLogic.interruptRecipe();
                return false;
            }
        }
        // check boost fluid
        if (isBoostAllowed()) {
            this.isOxygenBoosted = inputFluid(isExtreme() ? LIQUID_OXYGEN_STACK : OXYGEN_STACK);
        }
        runningTimer++;
        if (runningTimer > 72000) runningTimer %= 72000; // reset once every hour of running
        return value;
    }

    @Override
    public boolean regressWhenWaiting() {
        return false;
    }

    //////////////////////////////////////
    // ******* GUI ********//
    //////////////////////////////////////
    @Override
    public void addDisplayText(List<Component> textList) {
        MultiblockDisplayText.Builder builder = MultiblockDisplayText.builder(textList, isFormed()).setWorkingStatus(recipeLogic.isWorkingEnabled(), recipeLogic.isActive());
        if (isExtreme()) {
            builder.addEnergyProductionLine(GTValues.V[tier + 1], recipeLogic.getLastRecipe() != null ? recipeLogic.getLastRecipe().getOutputEUt() : 0);
        } else {
            builder.addEnergyProductionAmpsLine(GTValues.V[tier] * 3, 3);
        }
        if (isActive() && isWorkingEnabled()) {
            builder.addCurrentEnergyProductionLine(recipeLogic.getLastRecipe() != null ? recipeLogic.getLastRecipe().getOutputEUt() : 0);
        }
        builder.addFuelNeededLine(getRecipeFluidInputInfo(), recipeLogic.getDuration());
        if (isFormed && isOxygenBoosted) {
            final var key = isExtreme() ? "gtceu.multiblock.large_combustion_engine.liquid_oxygen_boosted" : "gtceu.multiblock.large_combustion_engine.oxygen_boosted";
            builder.addCustom(tl -> tl.add(Component.translatable(key).withStyle(ChatFormatting.AQUA)));
        }
        builder.addWorkingStatusLine();
    }

    @Nullable
    public String getRecipeFluidInputInfo() {
        // Previous Recipe is always null on first world load, so try to acquire a new recipe
        AtomicReference<GTRecipe> recipe = new AtomicReference<>(recipeLogic.getLastRecipe());
        if (recipe.get() == null) {
            getRecipeType().findRecipe(this, (u, r) -> {
                var re = r.toRuntime();
                if (matchRecipeInput(u, re)) {
                    recipe.set(re);
                    return true;
                }
                return false;
            });
        }
        var requiredFluidInput = recipe.get().fluidInputs.getFirst();
        long ocAmount = getMaxVoltage() / recipe.get().getOutputEUt();
        int neededAmount = GTMath.saturatedCast(ocAmount * requiredFluidInput.amount);
        return ChatFormatting.RED + FormattingUtil.formatNumbers(neededAmount) + "mB";
    }

    @Override
    public void attachTooltips(TooltipsPanel tooltipsPanel) {
        super.attachTooltips(tooltipsPanel);
        tooltipsPanel.attachTooltips(new IFancyTooltip.Basic(() -> GuiTextures.INDICATOR_NO_STEAM.get(false), () -> List.of(Component.translatable("gtceu.multiblock.large_combustion_engine.obstructed").setStyle(Style.EMPTY.withColor(ChatFormatting.RED))), this::isIntakesObstructed, () -> null));
    }
}
