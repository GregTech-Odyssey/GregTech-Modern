package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.GTCapability;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.IEnergyInfoProvider;
import com.gregtechceu.gtceu.api.capability.ILaserContainer;
import com.gregtechceu.gtceu.api.machine.TieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.IDropSaveMachine;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.uipro.elements.Form;
import com.gregtechceu.gtceu.uipro.elements.NumberField;
import com.gregtechceu.gtceu.uipro.elements.StatusPanel;
import com.gregtechceu.gtceu.uipro.elements.Stepper;
import com.gregtechceu.gtceu.uipro.elements.Switch;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uiwidgets.multiblock.MultiblockPage;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CreativeEnergyContainerMachine extends TieredMachine implements ILaserContainer, IFancyUIMachine, IDropSaveMachine, IEnergyInfoProvider {

    @Getter
    @SaveToDisk(defaultValue = "0")
    @SyncToClient
    private long voltage = 0;
    @Getter
    @SaveToDisk(defaultValue = "1")
    @SyncToClient
    private int amps = 1;
    @Getter
    @SaveToDisk(defaultValue = "0")
    @SyncToClient
    private int setTier = 0;
    @Getter
    @SaveToDisk(defaultValue = "false")
    @SyncToClient
    private boolean active = false;
    @Getter
    @SaveToDisk(defaultValue = "true")
    @SyncToClient
    private boolean source = true;
    @SaveToDisk(defaultValue = "0")
    private long energyIOPerSec = 0;
    @SyncToClient
    private long lastAverageEnergyIOPerTick = 0;
    private boolean doExplosion = false;

    public CreativeEnergyContainerMachine(MetaMachineBlockEntity holder) {
        super(holder, GTValues.MAX);
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    @Override
    public @Nullable <T> Object getGTCapability(Class<T> cap, @Nullable Direction side) {
        if (cap == GTCapability.ENERGY_CONTAINER) {
            return this;
        } else if (cap == GTCapability.LASER) {
            return this;
        }
        return super.getGTCapability(cap, side);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        subscribeServerTick(this::updateEnergyTick, 0);
    }

    //////////////////////////////////////
    // ********** MISC ***********//
    //////////////////////////////////////

    protected void updateEnergyTick() {
        if (getOffsetTimer() % 20 == 0) {
            this.setIOSpeed(energyIOPerSec / 20);
            energyIOPerSec = 0;
            if (doExplosion) {
                getLevel().explode(null, getPos().getX() + 0.5, getPos().getY() + 0.5, getPos().getZ() + 0.5,
                        1, Level.ExplosionInteraction.NONE);
                doExplosion = false;
            }
        }
        if (!active || !source || voltage <= 0 || amps <= 0) return;
        long energyUsed = 0;
        long energy = getMaxEnergyPerTick();
        for (var facing : GTUtil.DIRECTIONS) {
            var opposite = facing.getOpposite();
            IEnergyContainer container = GTCapabilityHelper.getEnergyContainer(holder.getNeighborBlockEntity(facing), opposite);
            // Try to get laser capability
            if (container == null)
                container = GTCapabilityHelper.getLaser(holder.getNeighborBlockEntity(facing), opposite);

            if (container != null && container.inputsEnergy(opposite)) {
                energyUsed += container.acceptEnergyFromNetwork(this, opposite, voltage, energy - energyUsed);
                if (energyUsed >= energy) {
                    break;
                }
            }
        }
        energyIOPerSec += energyUsed;
    }

    public long getMaxEnergyPerTick() {
        if (voltage <= 0 || amps <= 0) return 0;
        if (Long.MAX_VALUE / amps < voltage) return Long.MAX_VALUE;
        return voltage * amps;
    }

    @Override
    public long acceptEnergyFromNetwork(Object o, Direction side, long v, long e) {
        if (source || !active) return 0;
        if (voltage > 0 && v > voltage) {
            doExplosion = true;
            return 0;
        }
        long maxEnergy = getMaxEnergyPerTick();
        long accepted = maxEnergy > 0 ? Math.min(e, maxEnergy) : e;
        energyIOPerSec += accepted;
        return accepted;
    }

    @Override
    public boolean inputsEnergy(Direction side) {
        return !source;
    }

    @Override
    public boolean outputsEnergy(Direction side) {
        return source;
    }

    @Override
    public long changeEnergy(long differenceAmount) {
        if (source || !active) {
            return 0;
        }
        energyIOPerSec += differenceAmount;
        return differenceAmount;
    }

    @Override
    public long getEnergyStored() {
        if (!active) return 0;
        return source ? getMaxEnergyPerTick() : 0;
    }

    @Override
    public long getEnergyCapacity() {
        return getMaxEnergyPerTick();
    }

    @Override
    public EnergyInfo getEnergyInfo() {
        if (voltage <= 0 || amps <= 0) {
            return new EnergyInfo(BigInteger.ZERO, BigInteger.ZERO);
        }
        BigInteger cap = BigInteger.valueOf(voltage).multiply(BigInteger.valueOf(amps));
        BigInteger stored = (active && source) ? cap : BigInteger.ZERO;
        return new EnergyInfo(cap, stored);
    }

    @Override
    public long getInputPerSec() {
        return (!source && active) ? lastAverageEnergyIOPerTick * 20 : 0;
    }

    @Override
    public long getOutputPerSec() {
        return (source && active) ? lastAverageEnergyIOPerTick * 20 : 0;
    }

    @Override
    public boolean supportsBigIntEnergyValues() {
        return true;
    }

    @Override
    public long getInputAmperage() {
        return source ? 0 : amps;
    }

    @Override
    public long getInputVoltage() {
        return source ? 0 : voltage;
    }

    @Override
    public long getOutputVoltage() {
        return source ? voltage : 0;
    }

    @Override
    public long getOutputAmperage() {
        return source ? amps : 0;
    }

    public void setIOSpeed(long energyIOPerSec) {
        if (this.lastAverageEnergyIOPerTick != energyIOPerSec) {
            this.lastAverageEnergyIOPerTick = energyIOPerSec;
        }
    }

    @Override
    public void saveToItem(CompoundTag tag) {
        saveCustomPersistedData(tag, true);
    }

    @Override
    public void loadFromItem(CompoundTag tag) {
        loadCustomPersistedData(tag);
    }

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        if (forDrop) {
            tag.putLong("voltage", voltage);
            tag.putInt("amps", amps);
            tag.putInt("setTier", setTier);
            tag.putBoolean("active", active);
            tag.putBoolean("source", source);
        }
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        if (tag.contains("voltage")) {
            voltage = tag.getLong("voltage");
        }
        if (tag.contains("amps")) {
            amps = tag.getInt("amps");
        }
        if (tag.contains("setTier")) {
            setTier = tag.getInt("setTier");
        }
        if (tag.contains("active")) {
            active = tag.getBoolean("active");
        }
        if (tag.contains("source")) {
            source = tag.getBoolean("source");
        }
    }

    public void setVoltage(long voltage) {
        this.voltage = voltage;
        this.setTier = GTUtil.getTierByVoltage(voltage);
        onChanged();
    }

    public void setAmps(int amps) {
        this.amps = amps;
        onChanged();
    }

    public void setSetTier(int tier) {
        this.setTier = tier;
        this.voltage = GTValues.VEX[tier];
        onChanged();
    }

    public void setActive(boolean active) {
        this.active = active;
        onChanged();
    }

    public void setSource(boolean source) {
        this.source = source;
        onChanged();
    }

    @Override
    public boolean hasPlayerInventory() {
        return false;
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////

    @Override
    public Widget createUIWidget() {
        var status = new StatusPanel();
        status.addLine("gtceu.creative.energy.average", MultiblockPage.numberText(() -> lastAverageEnergyIOPerTick, "EU/t"));

        var page = Form.page();
        page.addChild(status);
        page.addChild(Form.controlRow("gtceu.creative.energy.tier",
                Stepper.of(50, this::getSetTier, this::setSetTier, 0, GTValues.VNF.length - 1)
                        .setFormatter(idx -> GTValues.VNF[idx])));
        page.addChild(Form.numberRow("gtceu.creative.energy.voltage",
                NumberField.ofLong(UISizes.CONTENT_WIDTH, this::getVoltage, this::setVoltage, 0L, Long.MAX_VALUE)));
        page.addChild(Form.numberRow("gtceu.creative.energy.amperage",
                NumberField.ofInt(UISizes.CONTENT_WIDTH, this::getAmps, this::setAmps, 0, Integer.MAX_VALUE)));
        page.addChild(Form.segmentRow("gtceu.creative.energy.mode", 2,
                i -> Component.translatable(i == 0 ? "gtceu.creative.energy.sink" : "gtceu.creative.energy.source"),
                () -> source ? 1 : 0,
                i -> setSource(i == 1)));
        page.addChild(Form.controlRow("gtceu.creative.activity.on", Switch.of(this::isActive, this::setActive)));
        return page;
    }
}
