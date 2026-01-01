package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMufflerMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.part.WorkableTieredPartMachine;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.items.ItemHandlerHelper;

import lombok.Getter;

import javax.annotation.ParametersAreNonnullByDefault;

@Getter
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MufflerPartMachine extends WorkableTieredPartMachine implements IMufflerMachine {

    private final int recoveryChance;
    @Persisted
    private final CustomItemStackHandler inventory;

    public MufflerPartMachine(MetaMachineBlockEntity holder, int tier) {
        super(holder, tier);
        this.recoveryChance = Math.max(1, tier * 10);
        this.inventory = new CustomItemStackHandler((int) Math.pow(tier + 1, 2));
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
            ItemHandlerHelper.insertItemStacked(inventory, recoveryItems.copy(), false);
        }
    }

    private boolean calculateChance() {
        return recoveryChance >= 100 || recoveryChance >= GTValues.RNG.nextInt(100);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void clientTick() {
        super.clientTick();
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
    public ModularUI createUI(Player entityPlayer) {
        int rowSize = (int) Math.sqrt(inventory.getSlots());
        int xOffset = rowSize == 10 ? 9 : 0;
        var modular = new ModularUI(176 + xOffset * 2, 18 + 18 * rowSize + 94, this, entityPlayer).background(GuiTextures.BACKGROUND).widget(new LabelWidget(10, 5, getBlockState().getBlock().getDescriptionId())).widget(UITemplate.bindPlayerInventory(entityPlayer.getInventory(), GuiTextures.SLOT, 7 + xOffset, 18 + 18 * rowSize + 12, true));
        for (int y = 0; y < rowSize; y++) {
            for (int x = 0; x < rowSize; x++) {
                int index = y * rowSize + x;
                modular.widget(new SlotWidget(inventory, index, (88 - rowSize * 9 + x * 18) + xOffset, 18 + y * 18, true, false).setBackgroundTexture(GuiTextures.SLOT));
            }
        }
        return modular;
    }
}
