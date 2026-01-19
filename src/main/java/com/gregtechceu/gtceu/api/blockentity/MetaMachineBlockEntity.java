package com.gregtechceu.gtceu.api.blockentity;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.capability.*;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.item.tool.IToolGridHighlight;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.misc.EnergyInfoProviderList;
import com.gregtechceu.gtceu.api.misc.LaserContainerList;
import com.gregtechceu.gtceu.client.renderer.GTRendererProvider;
import com.gregtechceu.gtceu.utils.TaskHandler;

import com.lowdragmc.lowdraglib.Platform;
import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.networking.LDLNetworking;
import com.lowdragmc.lowdraglib.networking.s2c.SPacketManagedPayload;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAsyncAutoSyncBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAutoPersistBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IRPCBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.field.FieldManagedStorage;
import com.lowdragmc.lowdraglib.syncdata.managed.IRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

public class MetaMachineBlockEntity extends BlockEntity implements IToolGridHighlight, IAsyncAutoSyncBlockEntity, IRPCBlockEntity, IAutoPersistBlockEntity, IPaintable, IWailaDisplayProvider {

    @Getter
    public final MetaMachine metaMachine;
    public final MachineDefinition definition;
    public final int offset = GTValues.RNG.nextInt(20);
    public final BooleanSupplier isRemove = () -> remove;
    public int tickDelay = 0;
    protected boolean asyncSyncing;
    protected LevelChunk chunk;

    protected MetaMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        this.definition = blockState.getBlock() instanceof MetaMachineBlock machineBlock ? machineBlock.definition : null;
        assert definition != null : "MetaMachineBlockEntity is created for an un available block: +" + blockState.getBlock();
        this.metaMachine = definition.createMetaMachine(this);
    }

    public static MetaMachineBlockEntity createBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        return new MetaMachineBlockEntity(type, pos, blockState);
    }

    public @Nullable LevelChunk getChunk() {
        if (chunk != null) return chunk;
        if (level != null) return chunk = level.getChunkAt(worldPosition);
        return null;
    }

    @Override
    public FieldManagedStorage getRootStorage() {
        return metaMachine.getSyncStorage();
    }

    @Override
    public boolean triggerEvent(int id, int para) {
        if (id == 1) {
            // chunk re render
            if (level != null && level.isClientSide) {
                scheduleRenderUpdate();
            }
            return true;
        }
        return false;
    }

    @Override
    public void setLevel(@NotNull Level level) {
        super.setLevel(level);
        chunk = null;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        metaMachine.onUnload();
        chunk = null;
    }

    @Override
    public void clearRemoved() {
        chunk = null;
        tickDelay = offset;
        super.clearRemoved();
        metaMachine.onLoad();
        if (getLevel() instanceof ServerLevel serverLevel) {
            TaskHandler.enqueueTask(serverLevel, () -> tickDelay = 0, 1);
        }
    }

    @Override
    public boolean shouldRenderGrid(Player player, BlockPos pos, BlockState state, ItemStack held, Set<GTToolType> toolTypes) {
        return metaMachine.shouldRenderGrid(player, pos, state, held, toolTypes);
    }

    @Override
    public ResourceTexture sideTips(Player player, BlockPos pos, BlockState state, Set<GTToolType> toolTypes, Direction side) {
        return metaMachine.sideTips(player, pos, state, toolTypes, side);
    }

    @Override
    @NotNull
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        var result = getCapability(metaMachine, cap, side);
        return result == null ? super.getCapability(cap, side) : result;
    }

    @Override
    public void setChanged() {
        var chunk = getChunk();
        if (chunk != null) {
            chunk.setUnsaved(true);
        }
    }

    @Nullable
    public static <T> LazyOptional<T> getCapability(MetaMachine machine, @NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return machine.itemCapDirectionCache.getOrSet(side, () -> {
                var handler = machine.getItemHandlerCap(side, true);
                if (handler != null) {
                    return LazyOptional.of(() -> handler);
                }
                return LazyOptional.empty();
            }).cast();
        } else if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return machine.fluidCapDirectionCache.getOrSet(side, () -> {
                var handler = machine.getFluidHandlerCap(side, true);
                if (handler != null) {
                    return LazyOptional.of(() -> handler);
                }
                return LazyOptional.empty();
            }).cast();
        } else if (cap == GTCapability.CAPABILITY_COVERABLE) {
            return GTCapability.CAPABILITY_COVERABLE.orEmpty(cap, LazyOptional.of(machine::getCoverContainer));
        } else if (cap == GTCapability.CAPABILITY_ENERGY_CONTAINER) {
            return machine.energyDirectionCache.getOrSet(side, () -> {
                if (machine instanceof IEnergyContainer energyContainer) {
                    return LazyOptional.of(() -> energyContainer);
                }
                var list = getCapabilitiesFromTraits(machine.getTraits(), side, IEnergyContainer.class);
                if (!list.isEmpty()) {
                    return LazyOptional.of(() -> list.size() == 1 ? list.getFirst() : new EnergyContainerList(list));
                }
                return LazyOptional.empty();
            }).cast();
        } else if (cap == GTCapability.CAPABILITY_LASER) {
            if (machine instanceof ILaserContainer energyContainer) {
                return GTCapability.CAPABILITY_LASER.orEmpty(cap, LazyOptional.of(() -> energyContainer));
            }
            var list = getCapabilitiesFromTraits(machine.getTraits(), side, ILaserContainer.class);
            if (!list.isEmpty()) {
                return GTCapability.CAPABILITY_LASER.orEmpty(cap, LazyOptional.of(() -> list.size() == 1 ? list.getFirst() : new LaserContainerList(list)));
            }
            return LazyOptional.empty();
        } else if (cap == GTCapability.CAPABILITY_ENERGY_INFO_PROVIDER) {
            if (machine instanceof IEnergyInfoProvider energyInfoProvider) {
                return GTCapability.CAPABILITY_ENERGY_INFO_PROVIDER.orEmpty(cap, LazyOptional.of(() -> energyInfoProvider));
            }
            var list = getCapabilitiesFromTraits(machine.getTraits(), side, IEnergyInfoProvider.class);
            if (!list.isEmpty()) {
                return GTCapability.CAPABILITY_ENERGY_INFO_PROVIDER.orEmpty(cap, LazyOptional.of(() -> list.size() == 1 ? list.getFirst() : new EnergyInfoProviderList(list)));
            }
            return LazyOptional.empty();
        } else if (cap == GTCapability.CAPABILITY_WORKABLE) {
            if (machine instanceof IWorkable workable) {
                return GTCapability.CAPABILITY_WORKABLE.orEmpty(cap, LazyOptional.of(() -> workable));
            }
            for (MachineTrait trait : machine.getTraits()) {
                if (trait instanceof IWorkable workable) {
                    return GTCapability.CAPABILITY_WORKABLE.orEmpty(cap, LazyOptional.of(() -> workable));
                }
            }
            return LazyOptional.empty();
        } else if (cap == GTCapability.CAPABILITY_CONTROLLABLE) {
            if (machine instanceof IControllable controllable) {
                return GTCapability.CAPABILITY_CONTROLLABLE.orEmpty(cap, LazyOptional.of(() -> controllable));
            }
            for (MachineTrait trait : machine.getTraits()) {
                if (trait instanceof IControllable controllable) {
                    return GTCapability.CAPABILITY_CONTROLLABLE.orEmpty(cap, LazyOptional.of(() -> controllable));
                }
            }
            return LazyOptional.empty();
        }
        return machine.getCapability(cap, side);
    }

    public static <T> List<T> getCapabilitiesFromTraits(List<MachineTrait> traits, Direction accessSide, Class<T> capability) {
        if (traits.isEmpty()) return Collections.emptyList();
        List<T> list = new ArrayList<>();
        for (MachineTrait trait : traits) {
            if (trait.hasCapability(accessSide) && capability.isInstance(trait)) {
                list.add(capability.cast(trait));
            }
        }
        return list;
    }

    /**
     * Why, Forge, Why?
     * Why must you make me add a method for no good reason?
     */
    @OnlyIn(Dist.CLIENT)
    @Override
    public AABB getRenderBoundingBox() {
        GTRendererProvider instance = GTRendererProvider.getInstance();
        if (instance != null) {
            IRenderer renderer = instance.getRenderer(this);
            if (renderer != null) {
                if (renderer.getViewDistance() == 64 /* the default */) {
                    return new AABB(worldPosition.offset(-1, 0, -1), worldPosition.offset(2, 2, 2));
                }
                int viewDistHalf = renderer.getViewDistance() / 2;
                return new AABB(worldPosition).inflate(viewDistHalf);
            }
        }
        return new AABB(worldPosition.offset(-1, 0, -1), worldPosition.offset(2, 2, 2));
    }

    @Override
    public void asyncTick(long periodID) {
        if (Platform.isServerNotSafe()) return;
        if (metaMachine.needSync() || periodID + offset % 20 == 0) {
            if (useAsyncThread() && !isRemoved()) {
                for (IRef field : getNonLazyFields()) {
                    field.update();
                }
                if (metaMachine.getSyncStorage().hasDirtySyncFields() && !asyncSyncing) {
                    asyncSyncing = true;
                    Platform.getMinecraftServer().execute(() -> {
                        if (Platform.isServerNotSafe()) return;
                        var packet = SPacketManagedPayload.of(this, false);
                        LDLNetworking.NETWORK.sendToTrackingChunk(packet, getChunk());
                        asyncSyncing = false;
                    });
                }
            }
        }
    }

    @Override
    public boolean isAsyncSyncing() {
        return asyncSyncing;
    }

    @Override
    public void setAsyncSyncing(boolean syncing) {
        asyncSyncing = syncing;
    }

    @Override
    public MetaMachineBlockEntity getSelf() {
        return this;
    }

    public Level level() {
        return level;
    }

    public BlockPos pos() {
        return worldPosition;
    }

    public void notifyBlockUpdate() {
        if (level != null) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    public void scheduleRenderUpdate() {
        if (level != null) {
            if (level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 1 << 3);
            } else {
                level.blockEvent(worldPosition, getBlockState().getBlock(), 1, 0);
            }
        }
    }

    public int getOffsetTimer() {
        if (level == null) return offset;
        var server = level.getServer();
        if (server != null) return server.getTickCount() + offset;
        return GTValues.CLIENT_TIME + offset;
    }

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        IAutoPersistBlockEntity.super.saveCustomPersistedData(tag, forDrop);
        metaMachine.saveCustomPersistedData(tag, forDrop);
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        IAutoPersistBlockEntity.super.loadCustomPersistedData(tag);
        metaMachine.loadCustomPersistedData(tag);
    }

    @Override
    public int getPaintingColor() {
        return metaMachine.getPaintingColor();
    }

    @Override
    public void setPaintingColor(int color) {
        metaMachine.setPaintingColor(color);
    }

    @Override
    public int getDefaultPaintingColor() {
        return metaMachine.getDefaultPaintingColor();
    }

    @Override
    public void appendWailaTooltip(CompoundTag data, ITooltip iTooltip, BlockAccessor blockAccessor, IPluginConfig iPluginConfig) {
        if (metaMachine instanceof IWailaDisplayProvider provider) {
            provider.appendWailaTooltip(data, iTooltip, blockAccessor, iPluginConfig);
        }
    }

    @Override
    public void appendWailaData(CompoundTag data, BlockAccessor blockAccessor) {
        if (metaMachine instanceof IWailaDisplayProvider provider) {
            provider.appendWailaData(data, blockAccessor);
        }
    }
}
