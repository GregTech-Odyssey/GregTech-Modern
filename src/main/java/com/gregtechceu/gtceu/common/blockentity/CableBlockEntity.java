package com.gregtechceu.gtceu.common.blockentity;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.ITickSubscription;
import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.IWailaDisplayProvider;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.WireProperties;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IDataInfoProvider;
import com.gregtechceu.gtceu.common.block.CableBlock;
import com.gregtechceu.gtceu.common.data.GTMaterialBlocks;
import com.gregtechceu.gtceu.common.item.PortableScannerBehavior;
import com.gregtechceu.gtceu.common.pipelike.cable.*;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.lang.ref.WeakReference;
import java.util.EnumMap;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import static com.gregtechceu.gtceu.utils.FormattingUtil.DECIMAL_FORMAT_1F;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CableBlockEntity extends PipeBlockEntity<Insulation, WireProperties> implements IDataInfoProvider, IWailaDisplayProvider {

    protected WeakReference<EnergyNet> currentEnergyNet = new WeakReference<>(null);
    private static final int meltTemp = 3000;
    private final EnumMap<Direction, LazyOptional<EnergyNetHandler>> handlers = new EnumMap<>(Direction.class);
    private final PerTickLongCounter voltageCounter = new PerTickLongCounter(true);
    private final PerTickLongCounter amperageCounter = new PerTickLongCounter(false);
    private LazyOptional<EnergyNetHandler> defaultHandler;
    private int heatQueue;
    @Getter
    @Persisted
    @DescSynced
    private int temperature = getDefaultTemp();
    private TickableSubscription heatSubs;

    public CableBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public static CableBlockEntity create(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        return new CableBlockEntity(type, pos, blockState);
    }

    @Override
    @NotNull
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == GTCapability.CAPABILITY_ENERGY_CONTAINER) {
            var container = getEnergyContainer(side);
            if (container != null) {
                return container;
            }
            return LazyOptional.empty();
        } else if (cap == GTCapability.CAPABILITY_COVERABLE) {
            return GTCapability.CAPABILITY_COVERABLE.orEmpty(cap, LazyOptional.of(this::getCoverContainer));
        }
        return super.getCapability(cap, side);
    }

    @Nullable
    private EnergyNet getEnergyNet() {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        EnergyNet currentEnergyNet = this.currentEnergyNet.get();
        if (currentEnergyNet != null && currentEnergyNet.isValid() && currentEnergyNet.containsNode(getPipePosLong())) return currentEnergyNet;
        LevelEnergyNet worldENet = LevelEnergyNet.getOrCreate(serverLevel);
        currentEnergyNet = worldENet.getNetFromPos(getBlockPos(), getPipePosLong());
        if (currentEnergyNet != null) {
            this.currentEnergyNet = new WeakReference<>(currentEnergyNet);
        }
        return currentEnergyNet;
    }

    public void checkNetwork() {
        if (defaultHandler != null) {
            EnergyNet current = getEnergyNet();
            if (defaultHandler.orElse(null).getNet() != current) {
                defaultHandler.orElse(null).updateNetwork(current);
                for (var handler : handlers.values()) {
                    handler.orElse(null).updateNetwork(current);
                }
            }
        }
    }

    @Nullable
    public LazyOptional getEnergyContainer(@Nullable Direction side) {
        if (side != null && !isConnected(side)) return null;
        // the EnergyNetHandler can only be created on the server, so we have an empty placeholder for the client
        if (isRemote()) return LazyOptional.of(() -> IEnergyContainer.DEFAULT);
        if (handlers.isEmpty()) initHandlers();
        checkNetwork();
        var container = handlers.getOrDefault(side, defaultHandler);
        if (container == null) return LazyOptional.of(() -> IEnergyContainer.DEFAULT);
        return container;
    }

    @Override
    public boolean canHaveBlockedFaces() {
        return false;
    }

    private void initHandlers() {
        EnergyNet net = getEnergyNet();
        if (net == null) {
            return;
        }
        for (Direction facing : GTUtil.DIRECTIONS) {
            handlers.put(facing, LazyOptional.of(() -> new EnergyNetHandler(net, this, facing)));
        }
        defaultHandler = LazyOptional.of(() -> new EnergyNetHandler(net, this, null));
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (!level.isClientSide) {
            setTemperature(temperature);
            if (temperature > getDefaultTemp()) {
                subscribeHeat();
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        unsubscribeHeat();
    }

    private void subscribeHeat() {
        this.heatSubs = subscribeServerTick(heatSubs, this::update);
    }

    private void unsubscribeHeat() {
        this.heatSubs = ITickSubscription.unsubscribe(this.heatSubs);
    }

    public CableBlock getPipeBlock() {
        return (CableBlock) super.getPipeBlock();
    }

    public long getCurrentVoltage() {
        return voltageCounter.get(getOffsetTimer());
    }

    public double getCurrentAmperage() {
        return (double) amperageCounter.get(getOffsetTimer()) / 100;
    }

    public long getMaxAmperage() {
        return getNodeData().getAmperage();
    }

    public long getMaxVoltage() {
        return getNodeData().getVoltage();
    }

    public int getDefaultTemp() {
        return 293;
    }

    /**
     * Should only be called internally
     */
    public void incrementAmperage(long voltage, long amperage) {
        var time = getOffsetTimer();
        voltageCounter.set(time, voltage);
        amperageCounter.set(time, amperage);
        if ((amperageCounter.get(time) > getMaxAmperage() * 100) || voltageCounter.get(time) > getMaxVoltage()) {
            applyHeat((int) Math.sqrt(amperageCounter.get(time)));
        }
    }

    private void applyHeat(int amount) {
        heatQueue += amount;
        if (!level.isClientSide && heatSubs == null && temperature + heatQueue > getDefaultTemp()) {
            subscribeHeat();
        }
    }

    private void update() {
        if (heatQueue > 0) {
            // if received heat from overvolting or overamping, add heat
            setTemperature(temperature + heatQueue);
        }
        if (temperature >= meltTemp) {
            // cable melted
            level.setBlockAndUpdate(worldPosition, Blocks.FIRE.defaultBlockState());
            return;
        }
        if (temperature <= getDefaultTemp()) {
            unsubscribeHeat();
            return;
        }
        if (getPipeType().insulationLevel >= 0 && temperature >= 1500 && GTValues.RNG.nextFloat() < 0.1) {
            // insulation melted
            uninsulate();
            return;
        }
        if (heatQueue == 0) {
            // otherwise cool down
            setTemperature((int) (temperature - Math.pow(temperature - getDefaultTemp(), 0.35)));
        } else {
            heatQueue = 0;
        }
    }

    private void uninsulate() {
        int temp = temperature;
        setTemperature(getDefaultTemp());
        int index = getPipeType().insulationLevel;
        CableBlock newBlock = GTMaterialBlocks.CABLE_BLOCKS.get(Insulation.values()[index].tagPrefix, getPipeBlock().material).get();
        level.setBlockAndUpdate(getBlockPos(), newBlock.defaultBlockState());
        CableBlockEntity newCable = (CableBlockEntity) level.getBlockEntity(getBlockPos());
        if (newCable != null) {
            // should never be null
            newCable.setTemperature(temp);
            newCable.subscribeHeat();
            for (Direction facing : GTUtil.DIRECTIONS) {
                if (isConnected(facing)) {
                    newCable.setConnection(facing, true, true);
                }
            }
            newCable.setChanged();
            // force a block rerender
            newCable.scheduleRenderUpdate();
        }
    }

    public void setTemperature(int temperature) {
        this.temperature = temperature;
        level.getLightEngine().checkBlock(worldPosition);
        if (!level.isClientSide && temperature >= meltTemp) {
            var facing = Direction.UP;
            float xPos = facing.getStepX() * 0.76F + worldPosition.getX() + 0.25F;
            float yPos = facing.getStepY() * 0.76F + worldPosition.getY() + 0.25F;
            float zPos = facing.getStepZ() * 0.76F + worldPosition.getZ() + 0.25F;
            float ySpd = facing.getStepY() * 0.1F + 0.2F + 0.1F * GTValues.RNG.nextFloat();
            float temp = GTValues.RNG.nextFloat() * 2 * (float) Math.PI;
            float xSpd = (float) Math.sin(temp) * 0.1F;
            float zSpd = (float) Math.cos(temp) * 0.1F;
            ((ServerLevel) level).sendParticles(ParticleTypes.SMOKE, xPos + GTValues.RNG.nextFloat() * 0.5F, yPos + GTValues.RNG.nextFloat() * 0.5F, zPos + GTValues.RNG.nextFloat() * 0.5F, 0, xSpd, ySpd, zSpd, 1);
        }
    }

    //////////////////////////////////////
    // ******* Interaction *******//
    //////////////////////////////////////
    @Override
    public ResourceTexture getPipeTexture(boolean isBlock) {
        return isBlock ? GuiTextures.TOOL_WIRE_CONNECT : GuiTextures.TOOL_WIRE_BLOCK;
    }

    @Override
    public GTToolType getPipeTuneTool() {
        return GTToolType.WIRE_CUTTER;
    }

    @Override
    @NotNull
    public List<Component> getDataInfo(PortableScannerBehavior.DisplayMode mode) {
        List<Component> list = new ObjectArrayList<>();
        if (mode == PortableScannerBehavior.DisplayMode.SHOW_ALL || mode == PortableScannerBehavior.DisplayMode.SHOW_ELECTRICAL_INFO) {
            list.add(Component.translatable("behavior.portable_scanner.eu_per_sec", Component.translatable(FormattingUtil.formatNumbers(getCurrentVoltage())).withStyle(ChatFormatting.RED)));
            list.add(Component.translatable("behavior.portable_scanner.amp_per_sec", Component.translatable(FormattingUtil.formatNumbers(getCurrentAmperage())).withStyle(ChatFormatting.RED)));
            list.add(Component.translatable("gtceu.recipe.temperature", temperature).withStyle(ChatFormatting.RED));
        }
        return list;
    }

    @Override
    public void appendWailaTooltip(CompoundTag data, ITooltip iTooltip, BlockAccessor blockAccessor, IPluginConfig iPluginConfig) {
        if (data.contains("cableData", Tag.TAG_COMPOUND)) {
            var tag = data.getCompound("cableData");
            long voltage = tag.getLong("currentVoltage");
            double amperage = tag.getDouble("currentAmperage");
            iTooltip.add(Component.translatable("gtceu.top.cable_voltage"));
            if (voltage != 0) {
                iTooltip.append(Component.literal(GTValues.VNF[GTUtil.getTierByVoltage(voltage)]));
                iTooltip.append(Component.literal(" / "));
            }
            iTooltip.append(Component.literal(GTValues.VNF[GTUtil.getTierByVoltage(tag.getLong("maxVoltage"))]));

            iTooltip.add(Component.translatable("gtceu.top.cable_amperage"));
            if (amperage != 0) {
                iTooltip.append(Component.literal(DECIMAL_FORMAT_1F.format(amperage) + "A / "));
            }
            iTooltip.append(Component.literal(DECIMAL_FORMAT_1F.format(tag.getDouble("maxAmperage")) + "A"));
        }
    }

    @Override
    public void appendWailaData(CompoundTag data, BlockAccessor blockAccessor) {
        var cableData = new CompoundTag();
        cableData.putLong("maxVoltage", getMaxVoltage());
        cableData.putLong("currentVoltage", getCurrentVoltage());
        cableData.putDouble("maxAmperage", getMaxAmperage());
        cableData.putDouble("currentAmperage", getCurrentAmperage());
        data.put("cableData", cableData);
    }
}
