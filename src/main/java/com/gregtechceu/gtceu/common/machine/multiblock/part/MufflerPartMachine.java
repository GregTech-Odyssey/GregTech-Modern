package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.ITickSubscription;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMufflerMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.part.WorkableTieredPartMachine;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.uipro.elements.ItemSlot;
import com.gregtechceu.gtceu.uipro.elements.SlotGrid;
import com.gregtechceu.gtceu.uiwidgets.inventory.HatchViews;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import appeng.api.stacks.AEItemKey;
import com.gto.datasynclib.annotations.SaveToDisk;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

@Getter
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MufflerPartMachine extends WorkableTieredPartMachine implements IMufflerMachine {

    private final int recoveryChance;
    @SaveToDisk
    private final KeyInventory<AEItemKey> inventory;
    @Nullable
    protected TickableSubscription particleSubs;

    public MufflerPartMachine(MetaMachineBlockEntity holder, int tier) {
        super(holder, tier);
        this.recoveryChance = Math.max(1, tier * 10);
        this.inventory = KeyInventory.items((int) Math.pow(tier + 1, 2));
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    //////////////////////////////////////
    // ******** Muffler *********//
    //////////////////////////////////////
    @Override
    public void recoverItemsTable(ItemStack recoveryItems) {
        if (calculateChance()) {
            var key = AEItemKey.of(recoveryItems);
            if (key != null) inventory.insert(key, recoveryItems.getCount(), false);
        }
    }

    private boolean calculateChance() {
        return recoveryChance >= 100 || recoveryChance >= GTValues.RNG.nextInt(100);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (isRemote()) {
            particleSubs = subscribeClientTick(particleSubs, this::particlesTick);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        particleSubs = ITickSubscription.unsubscribe(particleSubs);
    }

    @OnlyIn(Dist.CLIENT)
    private void particlesTick() {
        for (IMultiController controller : this.getControllers()) {
            if (controller instanceof IRecipeLogicMachine recipeLogicMachine && recipeLogicMachine.getRecipeLogic().isWorking()) {
                emitPollutionParticles();
                break;
            }
        }
    }

    //////////////////////////////////////
    // ********** GUI ***********//
    //////////////////////////////////////
    @Override
    public Widget createUIWidget() {
        int rowSize = (int) Math.sqrt(inventory.size());
        return HatchViews.page(SlotGrid.of(rowSize, inventory.size(), i -> ItemSlot.of(inventory, i, true, false)));
    }
}
