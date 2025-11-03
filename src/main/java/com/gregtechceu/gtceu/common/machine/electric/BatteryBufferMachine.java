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
import com.gregtechceu.gtceu.api.transfer.item.SingleCustomItemStackHandler;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.utils.Position;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandlerModifiable;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class BatteryBufferMachine extends TieredEnergyMachine implements IControllable, IFancyUIMachine, IMachineLife {

    public static final long AMPS_PER_BATTERY = 2L;
    @Getter
    @Persisted
    private boolean isWorkingEnabled;
    @Getter
    private final int inventorySize;
    @Getter
    @Persisted
    protected final CustomItemStackHandler batteryInventory;

    protected long buffer;
    private ObjectArrayList<Object> allBatteries;
    private ObjectArrayList<IElectricItem> nonEmptyBatteries;
    private ObjectArrayList<Object> nonFullBatteries;

    public BatteryBufferMachine(MetaMachineBlockEntity holder, int tier, int inventorySize, Object... args) {
        super(holder, tier, inventorySize);
        this.isWorkingEnabled = true;
        this.inventorySize = inventorySize;
        this.batteryInventory = createBatteryInventory(args);
        this.batteryInventory.setOnContentsChanged(() -> {
            allBatteries = null;
            nonEmptyBatteries = null;
            nonFullBatteries = null;
            energyContainer.checkOutputSubscription();
        });
    }

    @Override
    protected NotifiableEnergyContainer createEnergyContainer(Object... args) {
        return new EnergyBatteryTrait((int) args[0]);
    }

    protected CustomItemStackHandler createBatteryInventory(Object... ignoredArgs) {
        var handler = new SingleCustomItemStackHandler(this.inventorySize);
        handler.setFilter(item -> {
            var electric = GTCapabilityHelper.getElectricItem(item);
            if (electric != null) return electric.getTier() <= getTier();
            return ConfigHolder.INSTANCE.compat.energy.nativeEUToFE && GTCapabilityHelper.getForgeEnergyItem(item) != null;
        });
        return handler;
    }

    @Override
    public @Nullable IItemHandlerModifiable getItemHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        return batteryInventory;
    }

    @Override
    public int tintColor(int index) {
        if (index == 2) {
            return GTValues.VC[getTier()];
        }
        return super.tintColor(index);
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
                template.addWidget(new SlotWidget(batteryInventory, index++, 4 + x * 18, 4 + y * 18, true, true).setBackgroundTexture(new GuiTextureGroup(GuiTextures.SLOT, GuiTextures.BATTERY_OVERLAY)));
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
    // ****** Battery Logic ******//
    //////////////////////////////////////
    @Override
    public void setWorkingEnabled(boolean workingEnabled) {
        isWorkingEnabled = workingEnabled;
        energyContainer.checkOutputSubscription();
    }

    private List<Object> getNonFullBatteries() {
        if (nonFullBatteries == null) {
            nonFullBatteries = new ObjectArrayList<>();
            for (int i = 0; i < batteryInventory.getSlots(); i++) {
                var batteryStack = batteryInventory.getStackInSlot(i);
                var electricItem = GTCapabilityHelper.getElectricItem(batteryStack);
                if (electricItem != null) {
                    if (electricItem.getCharge() < electricItem.getMaxCharge()) {
                        nonFullBatteries.add(electricItem);
                    }
                } else if (ConfigHolder.INSTANCE.compat.energy.nativeEUToFE) {
                    IEnergyStorage energyStorage = GTCapabilityHelper.getForgeEnergyItem(batteryStack);
                    if (energyStorage != null) {
                        if (energyStorage.getEnergyStored() < energyStorage.getMaxEnergyStored()) {
                            nonFullBatteries.add(energyStorage);
                        }
                    }
                }
            }
        }
        return nonFullBatteries;
    }

    private List<IElectricItem> getNonEmptyBatteries() {
        if (nonEmptyBatteries == null) {
            nonEmptyBatteries = new ObjectArrayList<>();
            for (int i = 0; i < batteryInventory.getSlots(); i++) {
                var batteryStack = batteryInventory.getStackInSlot(i);
                var electricItem = GTCapabilityHelper.getElectricItem(batteryStack);
                if (electricItem != null) {
                    if (electricItem.canProvideChargeExternally() && electricItem.getCharge() > 0) {
                        nonEmptyBatteries.add(electricItem);
                    }
                }
            }
        }
        return nonEmptyBatteries;
    }

    private List<Object> getAllBatteries() {
        if (allBatteries == null) {
            allBatteries = new ObjectArrayList<>();
            for (int i = 0; i < batteryInventory.getSlots(); i++) {
                var batteryStack = batteryInventory.getStackInSlot(i);
                var electricItem = GTCapabilityHelper.getElectricItem(batteryStack);
                if (electricItem != null) {
                    allBatteries.add(electricItem);
                } else if (ConfigHolder.INSTANCE.compat.energy.nativeEUToFE) {
                    IEnergyStorage energyStorage = GTCapabilityHelper.getForgeEnergyItem(batteryStack);
                    if (energyStorage != null) {
                        allBatteries.add(energyStorage);
                    }
                }
            }
        }
        return allBatteries;
    }

    @Override
    public void onMachineRemoved() {
        clearInventory(batteryInventory);
    }

    protected class EnergyBatteryTrait extends NotifiableEnergyContainer {

        protected EnergyBatteryTrait(int inventorySize) {
            super(BatteryBufferMachine.this, GTValues.V[tier] * inventorySize * 32L, GTValues.V[tier], inventorySize * AMPS_PER_BATTERY, GTValues.V[tier], inventorySize);
            this.setSideInputCondition(side -> side != getFrontFacing() && isWorkingEnabled());
            this.setSideOutputCondition(side -> isWorkingEnabled());
        }

        @Override
        public void checkOutputSubscription() {
            if (isWorkingEnabled()) {
                super.checkOutputSubscription();
            } else if (outputSubs != null) {
                outputSubs.unsubscribe();
                outputSubs = null;
            }
        }

        @Override
        public void serverTick() {
            var outFacing = getFrontFacing();
            var energyContainer = GTCapabilityHelper.getEnergyContainer(getNeighbor(outFacing), outFacing.getOpposite());
            if (energyContainer == null) {
                return;
            }
            var batteries = getNonEmptyBatteries();
            if (!batteries.isEmpty()) {
                double out = 0;
                long stored = getEnergyStored();
                if (stored > 0) {
                    var voltage = getOutputVoltage();
                    var canOutput = Math.min(stored, batteries.size() * voltage);
                    out = energyContainer.acceptEnergyFromNetwork(this, outFacing.getOpposite(), voltage, canOutput);
                    if (out == 0) return;

                }
                long distributed = (long) ((out / batteries.size()) + 1);
                boolean changed = false;
                for (IElectricItem electricItem : batteries) {
                    var charged = electricItem.discharge(distributed, getTier(), false, true, false);
                    if (charged > 0) {
                        changed = true;
                    }
                }
                if (changed) {
                    BatteryBufferMachine.this.onChanged();
                    checkOutput = true;
                }
            }
        }

        @Override
        public long acceptEnergyFromNetwork(Object o, @Nullable Direction side, long voltage, long energyToAdd) {
            if (side == null || inputsEnergy(side)) {
                long canAccept = Math.min(energyToAdd, getEnergyCapacity() - getEnergyStored());
                if (canAccept == 0) return 0;
                if (voltage > getInputVoltage()) {
                    doExplosion(GTUtil.getExplosionPower(voltage));
                    return 0;
                }
                var batteries = getNonFullBatteries();
                var size = batteries.size();
                long energyAdded = 0;
                long distributed = canAccept / size;
                if (distributed == 0) {
                    buffer += canAccept % size;
                    if (buffer > size) {
                        distributed = buffer / size;
                        buffer %= size;
                    }
                }
                if (distributed == 0) return 0;
                boolean changed = false;
                for (Object item : batteries) {
                    long charged = 0;
                    if (item instanceof IElectricItem electricItem) {
                        charged = electricItem.charge(Math.min(distributed, GTValues.V[electricItem.getTier()] * AMPS_PER_BATTERY), getTier(), true, false);
                    } else if (item instanceof IEnergyStorage energyStorage) {
                        charged = FeCompat.insertEu(energyStorage, Math.min(distributed, GTValues.V[getTier()] * AMPS_PER_BATTERY), false);
                    }
                    if (charged > 0) {
                        changed = true;
                    }
                    energyAdded += charged;
                    if (energyAdded == canAccept) break;
                }
                if (changed) {
                    BatteryBufferMachine.this.onChanged();
                    checkOutput = true;
                }
                return energyAdded;
            }
            return 0;
        }

        @Override
        public long getEnergyCapacity() {
            long energyCapacity = 0L;
            for (Object battery : getAllBatteries()) {
                if (battery instanceof IElectricItem electricItem) {
                    energyCapacity += electricItem.getMaxCharge();
                } else if (battery instanceof IEnergyStorage energyStorage) {
                    energyCapacity += FeCompat.toEu(energyStorage.getMaxEnergyStored(), FeCompat.ratio(false));
                }
            }
            return energyCapacity;
        }

        @Override
        public long getEnergyStored() {
            long energyStored = 0L;
            for (Object battery : getAllBatteries()) {
                if (battery instanceof IElectricItem electricItem) {
                    energyStored += electricItem.getCharge();
                } else if (battery instanceof IEnergyStorage energyStorage) {
                    energyStored += FeCompat.toEu(energyStorage.getEnergyStored(), FeCompat.ratio(false));
                }
            }
            return energyStored;
        }
    }
}
