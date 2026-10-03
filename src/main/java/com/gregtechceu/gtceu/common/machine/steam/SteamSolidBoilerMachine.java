package com.gregtechceu.gtceu.common.machine.steam;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.machine.steam.SteamBoilerMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableInventory;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.config.ConfigHolder;

import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidUtil;

import appeng.api.stacks.AEItemKey;
import com.gto.datasynclib.annotations.SaveToDisk;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class SteamSolidBoilerMachine extends SteamBoilerMachine {

    public static final ReferenceOpenHashSet<Item> FUEL_CACHE = new ReferenceOpenHashSet<>();

    @SaveToDisk
    public final NotifiableInventory<AEItemKey> fuelHandler, ashHandler;

    public SteamSolidBoilerMachine(MetaMachineBlockEntity holder, boolean isHighPressure, Object... args) {
        super(holder, isHighPressure, args);
        this.fuelHandler = createFuelHandler(args).setFilter(key -> key instanceof AEItemKey item && FUEL_CACHE.contains(item.getItem()) && FluidUtil.getFluidContained(item.getReadOnlyStack()).isEmpty());
        this.ashHandler = createAshHandler(args);
    }

    //////////////////////////////////////
    // ***** Initialization *****//
    //////////////////////////////////////

    protected NotifiableInventory<AEItemKey> createFuelHandler(Object... args) {
        return NotifiableInventory.items(this, 1, IO.IN, IO.IN);
    }

    protected NotifiableInventory<AEItemKey> createAshHandler(Object... args) {
        return NotifiableInventory.items(this, 1, IO.OUT, IO.OUT);
    }

    @Override
    protected long getBaseSteamOutput() {
        return isHighPressure ? ConfigHolder.INSTANCE.machines.smallBoilers.hpSolidBoilerBaseOutput :
                ConfigHolder.INSTANCE.machines.smallBoilers.solidBoilerBaseOutput;
    }

    @Override
    public void afterWorking() {
        super.afterWorking();
        if (recipeLogic.getLastRecipe() != null) {
            var inputs = recipeLogic.getLastRecipe().itemInputs;
            if (!inputs.isEmpty() && inputs.ingredient(0).displayKey() instanceof AEItemKey input) {
                var remaining = getBurningFuelRemainder(Keys.displayStack(input));
                if (!remaining.isEmpty()) {
                    ashHandler.storage.insert(0, Keys.item(remaining), remaining.getCount(), false);
                }
            }
        }
    }

    public static ItemStack getBurningFuelRemainder(ItemStack fuelStack) {
        float remainderChance;
        ItemStack remainder;
        var materialStack = ChemicalHelper.getMaterialStack(fuelStack);
        if (materialStack.isEmpty()) {
            return ItemStack.EMPTY;
        } else if (materialStack.material() == GTMaterials.Charcoal) {
            remainder = ChemicalHelper.get(TagPrefix.dust, GTMaterials.Ash);
            remainderChance = 0.3f;
        } else if (materialStack.material() == GTMaterials.Coal) {
            remainder = ChemicalHelper.get(TagPrefix.dust, GTMaterials.DarkAsh);
            remainderChance = 0.35f;
        } else if (materialStack.material() == GTMaterials.Coke) {
            remainder = ChemicalHelper.get(TagPrefix.dust, GTMaterials.Ash);
            remainderChance = 0.5f;
        } else {
            return ItemStack.EMPTY;
        }
        return GTValues.RNG.nextFloat() <= remainderChance ? remainder : ItemStack.EMPTY;
    }

    @Override
    protected void addBoilerWidgets(WidgetGroup group) {
        group.addWidget(new SlotWidget(this.fuelHandler.storage, 0, 108, 40)
                .setBackgroundTexture(new GuiTextureGroup(GuiTextures.SLOT_STEAM.get(isHighPressure),
                        GuiTextures.COAL_OVERLAY_STEAM.get(isHighPressure))));
        group.addWidget(new SlotWidget(this.ashHandler.storage, 0, 108, 4, true, false)
                .setBackgroundTexture(new GuiTextureGroup(GuiTextures.SLOT_STEAM.get(isHighPressure),
                        GuiTextures.DUST_OVERLAY_STEAM.get(isHighPressure))));
        group.addWidget(new ProgressWidget(recipeLogic::getProgressPercent, 108, 22, 18, 18)
                .setProgressTexture(
                        GuiTextures.PROGRESS_BAR_BOILER_FUEL.get(isHighPressure).getSubTexture(0, 0, 1, 0.5),
                        GuiTextures.PROGRESS_BAR_BOILER_FUEL.get(isHighPressure).getSubTexture(0, 0.5, 1, 0.5))
                .setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP));
    }

    @Override
    public void onMachineRemoved() {
        clearInventory(fuelHandler.storage);
        clearInventory(ashHandler.storage);
    }
}
