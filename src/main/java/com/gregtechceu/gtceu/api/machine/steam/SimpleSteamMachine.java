package com.gregtechceu.gtceu.api.machine.steam;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.widget.PredicatedImageWidget;
import com.gregtechceu.gtceu.api.machine.feature.IDummyEnergyMachine;
import com.gregtechceu.gtceu.api.machine.feature.IExhaustVentMachine;
import com.gregtechceu.gtceu.api.machine.feature.IUIMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.common.recipe.condition.VentCondition;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.utils.Position;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fluids.FluidType;

import com.google.common.collect.Tables;
import com.gto.datasynclib.datasream.DataComponentMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceLinkedOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class SimpleSteamMachine extends SteamWorkableMachine implements IExhaustVentMachine, IUIMachine, IDummyEnergyMachine {

    @Persisted
    public final NotifiableItemStackHandler importItems;
    @Persisted
    public final NotifiableItemStackHandler exportItems;
    @Setter
    @Persisted
    private boolean needsVenting;

    @Getter
    private final SteamEnergyContainer energyContainer;

    public SimpleSteamMachine(MetaMachineBlockEntity holder, boolean isHighPressure, Object... args) {
        super(holder, isHighPressure, args);
        this.importItems = createImportItemHandler(args);
        this.exportItems = createExportItemHandler(args);
        this.energyContainer = new SteamEnergyContainer(getConversionRate(), steamTank);
    }

    @Override
    protected NotifiableFluidTank createSteamTank(Object... args) {
        return new NotifiableFluidTank(this, 1, 16 * FluidType.BUCKET_VOLUME, IO.NONE, IO.IN);
    }

    protected NotifiableItemStackHandler createImportItemHandler(Object... args) {
        var handler = new NotifiableItemStackHandler(this, getRecipeType().getMaxInputs(ItemRecipeCapability.CAP), IO.IN);
        if (handler.storage.size == 0) handler.setAvailable(false);
        return handler;
    }

    protected NotifiableItemStackHandler createExportItemHandler(Object... args) {
        var handler = new NotifiableItemStackHandler(this, getRecipeType().getMaxOutputs(ItemRecipeCapability.CAP), IO.OUT);
        if (handler.storage.size == 0) handler.setAvailable(false);
        return handler;
    }

    @Override
    public void onMachineRemoved() {
        clearInventory(importItems.storage);
        clearInventory(exportItems.storage);
    }

    //////////////////////////////////////
    // ****** Venting Logic ******//
    //////////////////////////////////////
    @Override
    public float getVentingDamage() {
        return isHighPressure() ? 12.0F : 6.0F;
    }

    @Override
    public Direction getVentingDirection() {
        return getOutputFacing();
    }

    @Override
    public boolean isNeedsVenting() {
        return this.needsVenting;
    }

    @Override
    public void markVentingComplete() {
        this.needsVenting = false;
    }

    public double getConversionRate() {
        return isHighPressure() ? 2.0 : 1.0;
    }

    @Override
    public void afterWorking() {
        super.afterWorking();
        needsVenting = true;
        checkVenting();
    }

    @Nullable
    public static GTRecipe recipeModifier(IRecipeHandlerHolder machine, RecipeHandlerUnit unit, GTRecipe recipe) {
        if (!(machine instanceof SimpleSteamMachine steamMachine)) {
            return null;
        }
        if (!steamMachine.checkVenting()) {
            return null;
        }
        if (!VentCondition.INSTANCE.testCondition(machine, unit, recipe.definition)) return null;
        if (!steamMachine.isHighPressure) recipe.durationMultiplier(2);
        return recipe;
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////
    @Override
    public ModularUI createUI(Player entityPlayer) {
        var storages = Tables.newCustomTable(new EnumMap<>(IO.class), Reference2ReferenceLinkedOpenHashMap<RecipeCapability<?>, Object>::new);
        storages.put(IO.IN, ItemRecipeCapability.CAP, importItems.storage);
        storages.put(IO.OUT, ItemRecipeCapability.CAP, exportItems.storage);
        var group = getRecipeType().getRecipeUI().createUITemplate(recipeLogic::getProgressPercent, storages, new DataComponentMap(), Collections.emptyList(), true, isHighPressure);
        Position pos = new Position((Math.max(group.getSize().width + 4 + 8, 176) - 4 - group.getSize().width) / 2 + 4, 32);
        group.setSelfPosition(pos);
        return new ModularUI(176, 166, this, entityPlayer).background(GuiTextures.BACKGROUND_STEAM.get(isHighPressure)).widget(group).widget(new LabelWidget(5, 5, getBlockState().getBlock().getDescriptionId())).widget(new PredicatedImageWidget(pos.x + group.getSize().width / 2 - 9, pos.y + group.getSize().height / 2 - 9, 18, 18, GuiTextures.INDICATOR_NO_STEAM.get(isHighPressure)).setPredicate(recipeLogic::isWaiting)).widget(UITemplate.bindPlayerInventory(entityPlayer.getInventory(), GuiTextures.SLOT_STEAM.get(isHighPressure), 7, 84, true));
    }
}
