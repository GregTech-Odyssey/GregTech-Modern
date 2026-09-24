package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.gui.editor.EditableMachineUI;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.info.FluidRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.RecipeInfo;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI;
import com.gregtechceu.gtceu.uiwidgets.recipe.RecipeMachinePage;

import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;

import com.google.common.collect.Tables;
import com.gto.datasynclib.datastream.DataComponentMap;
import com.mojang.blaze3d.MethodsReturnNonnullByDefault;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceLinkedOpenHashMap;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.function.BiFunction;

import javax.annotation.ParametersAreNonnullByDefault;

@Getter
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class SimpleGeneratorMachine extends WorkableTieredMachine {

    private final float hazardStrengthPerOperation;

    public SimpleGeneratorMachine(MetaMachineBlockEntity holder, int tier, float hazardStrengthPerOperation, Int2IntFunction tankScalingFunction, Object... args) {
        super(holder, tier, tankScalingFunction, args);
        this.hazardStrengthPerOperation = hazardStrengthPerOperation;
    }

    public SimpleGeneratorMachine(MetaMachineBlockEntity holder, int tier, Int2IntFunction tankScalingFunction, Object... args) {
        this(holder, tier, 0.25F, tankScalingFunction, args);
    }

    @Override
    public boolean nextTickSearch() {
        return false;
    }

    @Override
    protected boolean isEnergyEmitter() {
        return true;
    }

    @Override
    protected long getMaxInputOutputAmperage() {
        return 1L;
    }

    @Override
    public int tintColor(int index) {
        if (index == 2) {
            return GTValues.VC[getTier()];
        }
        return super.tintColor(index);
    }

    @Override
    public boolean regressWhenWaiting() {
        return false;
    }

    @Override
    public boolean canVoidRecipeOutputs(RecipeInfo capability) {
        return true;
    }

    @Nullable
    public static GTRecipe recipeModifier(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe) {
        if (holder instanceof SimpleGeneratorMachine generator) {
            var EUt = recipe.getOutputEUt();
            if (EUt > 0) {
                recipe = ParallelLogic.accurateParallel(holder, unit, recipe, (generator.getOverclockVoltage() / EUt));
            }
            return recipe;
        }
        return null;
    }

    @Override
    protected NotifiableFluidTank createExportFluidHandler(Object... args) {
        return new NotifiableFluidTank(this, 0, 0, IO.OUT).setAvailable(false);
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////
    @SuppressWarnings("UnstableApiUsage")
    /** 发电机主页：只显示输入的配方槽位区，放在标准尺寸外框（{@link RecipeMachinePage}）里居中。 */
    public static BiFunction<ResourceLocation, GTRecipeType, EditableMachineUI> EDITABLE_UI_CREATOR = Util.memoize((path, recipeType) -> new EditableMachineUI("generator", path, () -> RecipeMachinePage.page(
            recipeType.getRecipeUI().createEditableUITemplate(false, false, (output, key) -> !output).createDefault()), (template, machine) -> {
                if (machine instanceof SimpleGeneratorMachine generatorMachine) {
                    var storages = Tables.newCustomTable(new EnumMap<>(IO.class), Reference2ReferenceLinkedOpenHashMap<RecipeInfo, Object>::new);
                    storages.put(IO.IN, ItemRecipeInfo.INSTANCE, generatorMachine.importItems.storage);
                    storages.put(IO.OUT, ItemRecipeInfo.INSTANCE, generatorMachine.exportItems.storage);
                    storages.put(IO.IN, FluidRecipeInfo.INSTANCE, generatorMachine.importFluids);
                    storages.put(IO.OUT, FluidRecipeInfo.INSTANCE, generatorMachine.exportFluids);
                    generatorMachine.getRecipeType().getRecipeUI().createEditableUITemplate(false, false, (output, key) -> !output).setupUI(template, new GTRecipeTypeUI.RecipeHolder(generatorMachine.recipeLogic::getProgressPercent, storages, new DataComponentMap(), Collections.emptyList(), false, false));
                    createEnergyBar().setupUI(template, generatorMachine);
                }
            }));
}
