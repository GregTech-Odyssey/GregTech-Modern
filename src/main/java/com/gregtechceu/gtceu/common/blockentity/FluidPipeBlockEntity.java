package com.gregtechceu.gtceu.common.blockentity;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityWatch;
import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.filter.FluidFilter;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.FluidPipeProperties;
import com.gregtechceu.gtceu.api.transfer.forge.ForgeAdapters;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.KeyTransfer;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.common.block.FluidPipeBlock;
import com.gregtechceu.gtceu.common.cover.FluidFilterCover;
import com.gregtechceu.gtceu.common.pipelike.fluid.FluidNetHandler;
import com.gregtechceu.gtceu.common.pipelike.fluid.FluidPipeNet;
import com.gregtechceu.gtceu.common.pipelike.fluid.FluidPipeType;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.EmptyFluidHandler;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.AEKeyFilter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.MEStorageHost;
import appeng.api.storage.StorageAccess;
import appeng.capabilities.Capabilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.EnumMap;

public final class FluidPipeBlockEntity extends PipeBlockEntity<FluidPipeType, FluidPipeProperties> implements MEStorageHost {

    private WeakReference<FluidPipeNet> currentFluidPipeNet = new WeakReference<>(null);
    private final EnumMap<Direction, FluidNetHandler> handlers = new EnumMap<>(Direction.class);
    private FluidNetHandler defaultHandler;
    @SuppressWarnings("unchecked")
    private final LazyOptional<IFluidHandler>[] capabilityCache = new LazyOptional[6];
    @SuppressWarnings("unchecked")
    private final LazyOptional<MEStorage>[] storageCache = new LazyOptional[6];
    @SuppressWarnings("unchecked")
    private final IKeyHandler<AEFluidKey>[] exposed = new IKeyHandler[6];
    private int storageEpoch;
    private int transferredFluids = 0;
    private long timer = 0;

    public FluidPipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public static FluidPipeBlockEntity create(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        return new FluidPipeBlockEntity(type, pos, blockState);
    }

    public long getLevelTime() {
        return hasLevel() ? getLevel().getGameTime() : 0L;
    }

    @Override
    @NotNull
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            if (side != null && isConnected(side)) {
                return ForgeCapabilities.FLUID_HANDLER.orEmpty(cap, getFluidCapability(side));
            }
            return LazyOptional.empty();
        }
        if (cap == Capabilities.STORAGE) {
            return side != null && isConnected(side) ? getStorageCapability(side).cast() : LazyOptional.empty();
        }
        return super.getCapability(cap, side);
    }

    private LazyOptional<IFluidHandler> getFluidCapability(Direction side) {
        var cached = capabilityCache[side.ordinal()];
        if (cached != null) return cached;
        if (isRemote()) return LazyOptional.of(() -> EmptyFluidHandler.INSTANCE);
        ensureHandlersInitialized();
        checkNetwork();
        if (this.currentFluidPipeNet.get() == null) return LazyOptional.of(() -> EmptyFluidHandler.INSTANCE);
        var handler = exposedHandler(side);
        if (handler == null) return LazyOptional.empty();
        var adapter = ForgeAdapters.fluids(handler);
        cached = LazyOptional.of(() -> adapter);
        capabilityCache[side.ordinal()] = cached;
        return cached;
    }

    private LazyOptional<MEStorage> getStorageCapability(Direction side) {
        var cached = storageCache[side.ordinal()];
        if (cached != null) return cached;
        var handler = exposedHandler(side);
        if (handler == null) return LazyOptional.empty();
        cached = LazyOptional.of(() -> handler);
        storageCache[side.ordinal()] = cached;
        return cached;
    }

    private @Nullable IKeyHandler<AEFluidKey> exposedHandler(Direction side) {
        int i = side.ordinal();
        var handler = exposed[i];
        if (handler == null) exposed[i] = handler = getHandler(side, true);
        return handler;
    }

    @Override
    public @Nullable MEStorage getMEStorage(@Nullable Direction side) {
        return side != null && isConnected(side) ? exposedHandler(side) : null;
    }

    @Override
    public int storageEpoch() {
        return storageEpoch;
    }

    @Override
    public void onJoinedNet() {
        invalidateCapabilityCache();
        if (level != null) level.updateNeighborsAt(getBlockPos(), getBlockState().getBlock());
    }

    @Override
    public void invalidateCapabilityCache() {
        storageEpoch = storageEpoch + 1 & Integer.MAX_VALUE;
        Arrays.fill(exposed, null);
        for (int i = 0; i < 6; i++) {
            var cached = capabilityCache[i];
            if (cached != null) {
                capabilityCache[i] = null;
                cached.invalidate();
            }
            var storage = storageCache[i];
            if (storage != null) {
                storageCache[i] = null;
                storage.invalidate();
            }
        }
        BlockEntityWatch.changed(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidateCapabilityCache();
    }

    private void ensureHandlersInitialized() {
        if (handlers.isEmpty()) initHandlers();
    }

    public void initHandlers() {
        FluidPipeNet net = getFluidPipeNet();
        if (net == null) {
            return;
        }
        invalidateCapabilityCache();
        for (Direction facing : GTUtil.DIRECTIONS) {
            handlers.put(facing, new FluidNetHandler(net, this, facing));
        }
        defaultHandler = new FluidNetHandler(net, this, null);
    }

    public void checkNetwork() {
        if (defaultHandler != null) {
            FluidPipeNet current = getFluidPipeNet();
            if (defaultHandler.getNet() != current) {
                defaultHandler.updateNetwork(current);
                for (FluidNetHandler handler : handlers.values()) {
                    handler.updateNetwork(current);
                }
                invalidateCapabilityCache();
            }
        }
    }

    @Nullable
    public FluidPipeNet getFluidPipeNet() {
        if (level instanceof ServerLevel serverLevel && getBlockState().getBlock() instanceof FluidPipeBlock fluidPipeBlock) {
            FluidPipeNet currentFluidPipeNet = this.currentFluidPipeNet.get();
            if (currentFluidPipeNet != null && currentFluidPipeNet.isValid() && currentFluidPipeNet.containsNode(getPipeLongPos())) return currentFluidPipeNet;
            currentFluidPipeNet = fluidPipeBlock.getWorldPipeNet(serverLevel).getNetFromPos(getBlockPos(), getPipeLongPos());
            if (currentFluidPipeNet != null) {
                this.currentFluidPipeNet = new WeakReference<>(currentFluidPipeNet);
            }
        }
        return this.currentFluidPipeNet.get();
    }

    /**
     * every time the transferred variable is accessed this method should be called
     * if 20 ticks passed since the last access it will reset it
     * this method is equal to
     *
     * @code {
     *       if (++time % 20 == 0) {
     *       this.transferredFluids = 0;
     *       }
     *       }
     *       <p/>
     *       if it was in a ticking TileEntity
     */
    private void updateTransferredState() {
        long currentTime = getLevelTime();
        long dif = currentTime - this.timer;
        if (dif >= 20 || dif < 0) {
            this.transferredFluids = 0;
            this.timer = currentTime;
        }
    }

    public void addTransferredFluids(int amount) {
        updateTransferredState();
        this.transferredFluids += amount;
    }

    public int getTransferredFluids() {
        updateTransferredState();
        return this.transferredFluids;
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        this.handlers.clear();
        invalidateCapabilityCache();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (blockedSide != null && isBlocked(blockedSide)) {
            updateTransferTick(true, this::autoTransfer);
        }
    }

    @Override
    protected void blockedChanged(boolean isBlocked) {
        updateTransferTick(isBlocked && blockedSide != null, this::autoTransfer);
    }

    @Override
    protected void updateNetworkConnection(Direction side, boolean connected) {
        super.updateNetworkConnection(side, connected);
        invalidateCapabilityCache();
        updateTransferTick(blockedSide != null && isBlocked(blockedSide), this::autoTransfer);
    }

    @Override
    public void onNeighborChanged() {
        super.onNeighborChanged();
        updateTransferTick(blockedSide != null && isBlocked(blockedSide), this::autoTransfer);
    }

    private void autoTransfer() {
        ensureHandlersInitialized();
        checkNetwork();
        if (this.currentFluidPipeNet.get() == null) return;
        boolean hasHandler = false;
        int throughput = 20 * getNodeData().getThroughput();
        autoTransfer = true;
        for (Direction facing : GTUtil.DIRECTIONS) {
            if (facing != blockedSide && isConnected(facing)) {
                var be = getNeighborBlockEntity(facing);
                if (be == null || be instanceof PipeBlockEntity<?, ?>) continue;
                @SuppressWarnings("unchecked")
                var handler = (IKeyHandler<AEFluidKey>) blockEntityDirectionCache.getAdjacentKeyHandler(be, facing, AEKeyTypes.FLUIDS, StorageAccess.EXTRACT);
                if (handler != null) {
                    hasHandler = true;
                    throughput -= (int) KeyTransfer.transfer(handler, handlers.getOrDefault(facing, defaultHandler), throughput, getCoverContainer().getCoverAtSide(facing) instanceof FluidFilterCover filterCover ? fluidFilter(filterCover.getFluidFilter()) : null);
                    if (throughput <= 0) break;
                }
            }
        }
        autoTransfer = false;
        if (!hasHandler) {
            transferSubs.unsubscribe();
            transferSubs = null;
        }
    }

    private static AEKeyFilter fluidFilter(FluidFilter filter) {
        return key -> key instanceof AEFluidKey fluidKey && filter.test(Keys.displayFluid(fluidKey));
    }

    @Nullable
    public IKeyHandler<AEFluidKey> getHandler(@Nullable Direction side, boolean useCoverCapability) {
        if (isRemote()) return null;
        ensureHandlersInitialized();
        checkNetwork();
        if (this.currentFluidPipeNet.get() == null) return null;
        FluidNetHandler handler = handlers.getOrDefault(side, defaultHandler);
        if (!useCoverCapability || side == null) return handler;
        CoverBehavior cover = getCoverContainer().getCoverAtSide(side);
        return cover != null ? cover.getFluidHandlerCap(handler) : handler;
    }
}
