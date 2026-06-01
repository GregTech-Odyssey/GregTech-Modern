package com.gregtechceu.gtceu.common.machine.electric;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.IElectricItem;
import com.gregtechceu.gtceu.api.capability.compat.FeCompat;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.machine.TieredEnergyMachine;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.api.transfer.item.ICustomItemStackHandler;
import com.gregtechceu.gtceu.api.transfer.item.SingleCustomItemStackHandler;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraftforge.energy.IEnergyStorage;

import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@Getter
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class ChargerMachine extends TieredEnergyMachine implements IControllable, IFancyUIMachine, IMachineLife {

    public static final long AMPS_PER_ITEM = 4L;

    public enum State {
        IDLE,
        RUNNING,
        FINISHED
    }

    @Setter
    @SaveToDisk
    private boolean isWorkingEnabled;
    private final int inventorySize;
    @SaveToDisk
    protected final CustomItemStackHandler chargerInventory;
    @SyncToClient(notifyUpdate = true)
    private State state;

    public ChargerMachine(MetaMachineBlockEntity holder, int tier, int inventorySize, Object... args) {
        super(holder, tier, inventorySize);
        this.isWorkingEnabled = true;
        this.inventorySize = inventorySize;
        this.chargerInventory = createChargerInventory(args);
        this.state = State.IDLE;
    }

    @Override
    protected NotifiableEnergyContainer createEnergyContainer(Object... args) {
        return new EnergyBatteryTrait((int) args[0]);
    }

    protected CustomItemStackHandler createChargerInventory(Object... args) {
        var handler = new SingleCustomItemStackHandler(this.inventorySize);
        handler.setFilter(item -> {
            var electric = GTCapabilityHelper.getElectricItem(item);
            if (electric != null) return electric.getTier() <= getTier();
            return ConfigHolder.INSTANCE.compat.energy.nativeEUToFE && GTCapabilityHelper.getForgeEnergyItem(item) != null;
        });
        return handler;
    }

    @Override
    public @Nullable ICustomItemStackHandler getItemHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        return chargerInventory;
    }

    @Override
    public int tintColor(int index) {
        if (index == 2) {
            return GTValues.VC[getTier()];
        }
        return super.tintColor(index);
    }

    @Override
    public void onMachineRemoved() {
        clearInventory(chargerInventory);
    }

    //////////////////////////////////////
    // ********** GUI ***********//
    //////////////////////////////////////
    @Override
    public Widget createUIWidget() {
        int rowSize = (int) Math.sqrt(inventorySize);
        int colSize = rowSize;
        if (inventorySize == 8) {
            rowSize = 4;
            colSize = 2;
        }
        var template = new WidgetGroup(0, 0, 18 * rowSize + 8, 18 * colSize + 8);
        template.setBackground(GuiTextures.BACKGROUND_INVERSE);
        int index = 0;
        for (int y = 0; y < colSize; y++) {
            for (int x = 0; x < rowSize; x++) {
                template.addWidget(new SlotWidget(chargerInventory, index++, 4 + x * 18, 4 + y * 18, true, true).setBackgroundTexture(new GuiTextureGroup(GuiTextures.SLOT, GuiTextures.CHARGER_OVERLAY)));
            }
        }
        var editableUI = createEnergyBar();
        var energyBar = editableUI.createDefault();
        var group = new WidgetGroup(0, 0, Math.max(energyBar.getSize().width + template.getSize().width + 4 + 8, 172), Math.max(template.getSize().height + 8, energyBar.getSize().height + 8));
        var size = group.getSize();
        energyBar.setSelfPosition(new Position(3, (size.height - energyBar.getSize().height) / 2));
        template.setSelfPosition(new Position((size.width - energyBar.getSize().width - 4 - template.getSize().width) / 2 + 2 + energyBar.getSize().width + 2, (size.height - template.getSize().height) / 2));
        group.addWidget(energyBar);
        group.addWidget(template);
        editableUI.setupUI(group, this);
        return group;
    }

    //////////////////////////////////////
    // ****** Charger Logic ******//
    //////////////////////////////////////
    private List<Object> getNonFullElectricItem() {
        List<Object> electricItems = new ArrayList<>();
        for (int i = 0; i < chargerInventory.getSlots(); i++) {
            var electricItemStack = chargerInventory.getStackInSlot(i);
            var electricItem = GTCapabilityHelper.getElectricItem(electricItemStack);
            if (electricItem != null) {
                if (electricItem.getCharge() < electricItem.getMaxCharge()) {
                    electricItems.add(electricItem);
                }
            } else if (ConfigHolder.INSTANCE.compat.energy.nativeEUToFE) {
                var energyStorage = GTCapabilityHelper.getForgeEnergyItem(electricItemStack);
                if (energyStorage != null) {
                    if (energyStorage.getEnergyStored() < energyStorage.getMaxEnergyStored()) {
                        electricItems.add(energyStorage);
                    }
                }
            }
        }
        return electricItems;
    }

    private void changeState(State newState) {
        if (state != newState) {
            state = newState;
        }
    }

    protected class EnergyBatteryTrait extends NotifiableEnergyContainer {

        protected EnergyBatteryTrait(int inventorySize) {
            super(ChargerMachine.this, GTValues.V[tier] * inventorySize * 32L, GTValues.V[tier], inventorySize * AMPS_PER_ITEM, 0L, 0L);
            this.setSideInputCondition(side -> isWorkingEnabled());
            this.setSideOutputCondition(GTUtil.NEGATIVE);
        }

        @Override
        public long acceptEnergyFromNetwork(Object o, @Nullable Direction side, long voltage, long energyToAdd) {
            if (side == null || inputsEnergy(side)) {
                long canAccept = Math.min(energyToAdd, getEnergyCapacity() - getEnergyStored());
                if (canAccept == 0) return 0;
                var electricItems = getNonFullElectricItem();
                long energyAdded = 0;
                long distributed = canAccept / electricItems.size();
                boolean changed = false;
                for (var electricItem : electricItems) {
                    long charged = 0;
                    if (electricItem instanceof IElectricItem item) {
                        charged = item.charge(Math.min(distributed, GTValues.V[item.getTier()] * AMPS_PER_ITEM), getTier(), true, false);
                    } else if (electricItem instanceof IEnergyStorage energyStorage) {
                        charged = FeCompat.insertEu(energyStorage, Math.min(distributed, GTValues.V[getTier()] * AMPS_PER_ITEM), false);
                    }
                    if (charged > 0) {
                        changed = true;
                    }
                    energyAdded += charged;
                }
                if (changed) {
                    ChargerMachine.this.onChanged();
                    changeState(State.RUNNING);
                }
                return energyAdded;
            }
            return 0;
        }

        @Override
        public long getEnergyCapacity() {
            long energyCapacity = 0L;
            for (int i = 0; i < chargerInventory.getSlots(); i++) {
                var electricItemStack = chargerInventory.getStackInSlot(i);
                var electricItem = GTCapabilityHelper.getElectricItem(electricItemStack);
                if (electricItem != null) {
                    energyCapacity += electricItem.getMaxCharge();
                } else if (ConfigHolder.INSTANCE.compat.energy.nativeEUToFE) {
                    var energyStorage = GTCapabilityHelper.getForgeEnergyItem(electricItemStack);
                    if (energyStorage != null) {
                        energyCapacity += FeCompat.toEu(energyStorage.getMaxEnergyStored(), FeCompat.ratio(false));
                    }
                }
            }
            if (energyCapacity == 0) {
                changeState(State.IDLE);
            }
            return energyCapacity;
        }

        @Override
        public long getEnergyStored() {
            long energyStored = 0L;
            for (int i = 0; i < chargerInventory.getSlots(); i++) {
                var electricItemStack = chargerInventory.getStackInSlot(i);
                var electricItem = GTCapabilityHelper.getElectricItem(electricItemStack);
                if (electricItem != null) {
                    energyStored += electricItem.getCharge();
                } else if (ConfigHolder.INSTANCE.compat.energy.nativeEUToFE) {
                    var energyStorage = GTCapabilityHelper.getForgeEnergyItem(electricItemStack);
                    if (energyStorage != null) {
                        energyStored += FeCompat.toEu(energyStorage.getEnergyStored(), FeCompat.ratio(false));
                    }
                }
            }
            var capacity = getEnergyCapacity();
            if (capacity != 0 && capacity == energyStored) {
                changeState(State.FINISHED);
            }
            return energyStored;
        }
    }
}
