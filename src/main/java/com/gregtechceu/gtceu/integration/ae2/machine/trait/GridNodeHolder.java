package com.gregtechceu.gtceu.integration.ae2.machine.trait;

import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import com.gregtechceu.gtceu.integration.ae2.utils.SerializableManagedGridNode;

import net.minecraft.core.Direction;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.me.helpers.BlockEntityNodeListener;
import appeng.me.helpers.IGridConnectedBlockEntity;
import com.gto.datasynclib.annotations.Access;
import com.gto.datasynclib.annotations.Codec;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.NullData;
import lombok.Getter;

import java.util.EnumSet;

/**
 * A MachineTrait that is only used for hosting grid node and does not provide grid node capability.
 * Because IGridConnectedMachine has already extended IInWorldGridNodeHost.
 */
@Getter
public class GridNodeHolder extends MachineTrait {

    @SaveToDisk
    @Access(createInstance = true)
    @Codec(writeToData = "serializeGridNode", readFromData = "deserializeGridNode")
    protected final SerializableManagedGridNode mainNode;

    public GridNodeHolder(IGridConnectedMachine machine) {
        super(machine.self());
        this.mainNode = createManagedNode();
    }

    protected SerializableManagedGridNode createManagedNode() {
        return (SerializableManagedGridNode) new SerializableManagedGridNode((IGridConnectedBlockEntity) machine, BlockEntityNodeListener.INSTANCE).setFlags(GridFlags.DENSE_CAPACITY, GridFlags.REQUIRE_CHANNEL).setVisualRepresentation(machine.getDefinition().asItem()).setIdlePowerUsage(ConfigHolder.INSTANCE.compat.ae2.meHatchEnergyUsage).setInWorldNode(true).setExposedOnSides(machine.hasFrontFacing() ? EnumSet.of(machine.getFrontFacing()) : EnumSet.allOf(Direction.class)).setTagName("proxy");
    }

    protected void createMainNode() {
        this.mainNode.create(machine.getLevel(), machine.getPos());
    }

    @Override
    public void onMachineRotated(Direction oldFacing, Direction newFacing) {
        mainNode.setExposedOnSides(EnumSet.of(newFacing));
    }

    @Override
    public void onMachineLoad() {
        super.onMachineLoad();
        GridHelper.onFirstTick(machine.holder, b -> createMainNode());
    }

    @Override
    public void onMachineUnLoad() {
        super.onMachineUnLoad();
        mainNode.destroy();
    }

    @SuppressWarnings("unused")
    public boolean onGridNodeDirty(SerializableManagedGridNode node) {
        return node != null && node.isActive() && node.isOnline();
    }

    @SuppressWarnings("unused")
    public Data serializeGridNode(SerializableManagedGridNode node) {
        return NullData.INSTANCE;
    }

    @SuppressWarnings("unused")
    public SerializableManagedGridNode deserializeGridNode(Data data, int v) {
        return this.mainNode;
    }
}
