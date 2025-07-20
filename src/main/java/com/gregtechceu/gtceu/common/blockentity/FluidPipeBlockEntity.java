package com.gregtechceu.gtceu.common.blockentity;

import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.FluidPipeProperties;
import com.gregtechceu.gtceu.api.transfer.fluid.IFluidHandlerModifiable;
import com.gregtechceu.gtceu.common.block.FluidPipeBlock;
import com.gregtechceu.gtceu.common.pipelike.fluid.FluidNetHandler;
import com.gregtechceu.gtceu.common.pipelike.fluid.FluidPipeNet;
import com.gregtechceu.gtceu.common.pipelike.fluid.FluidPipeType;
import com.gregtechceu.gtceu.utils.FacingPos;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gregtechceu.gtceu.utils.cache.FluidHandlerDirectionCache;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.EnumMap;

public class FluidPipeBlockEntity extends PipeBlockEntity<FluidPipeType, FluidPipeProperties> {

    protected WeakReference<FluidPipeNet> currentFluidPipeNet = new WeakReference<>(null);
    private final EnumMap<Direction, FluidNetHandler> handlers = new EnumMap<>(Direction.class);
    private final Object2IntMap<FacingPos> transferred = new Object2IntOpenHashMap<>();
    private FluidNetHandler defaultHandler;
    private int transferredFluids = 0;
    private long timer = 0;

    public final FluidHandlerDirectionCache fluidHandlerDirectionCache = new FluidHandlerDirectionCache();

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
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        fluidHandlerDirectionCache.clearCache();
    }

    @Override
    @NotNull
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            Level world = getLevel();
            if (world.isClientSide()) return LazyOptional.empty();
            if (side != null && isConnected(side)) {
                ensureHandlersInitialized();
                checkNetwork();
                if (this.currentFluidPipeNet.get() == null) return LazyOptional.empty();
                return ForgeCapabilities.FLUID_HANDLER.orEmpty(cap, LazyOptional.of(() -> getHandler(side, true)));
            }
        } else if (cap == GTCapability.CAPABILITY_COVERABLE) {
            return GTCapability.CAPABILITY_COVERABLE.orEmpty(cap, LazyOptional.of(this::getCoverContainer));
        } else if (cap == GTCapability.CAPABILITY_TOOLABLE) {
            return GTCapability.CAPABILITY_TOOLABLE.orEmpty(cap, LazyOptional.of(() -> this));
        }
        return super.getCapability(cap, side);
    }

    private void ensureHandlersInitialized() {
        if (getHandlers().isEmpty()) initHandlers();
    }

    public void initHandlers() {
        FluidPipeNet net = getFluidPipeNet();
        if (net == null) {
            return;
        }
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
            }
        }
    }

    @Nullable
    public FluidPipeNet getFluidPipeNet() {
        if (level instanceof ServerLevel serverLevel && getBlockState().getBlock() instanceof FluidPipeBlock fluidPipeBlock) {
            FluidPipeNet currentFluidPipeNet = this.currentFluidPipeNet.get();
            if (currentFluidPipeNet != null && currentFluidPipeNet.isValid() && currentFluidPipeNet.containsNode(getPipePosLong())) return currentFluidPipeNet;
            currentFluidPipeNet = fluidPipeBlock.getWorldPipeNet(serverLevel).getNetFromPos(getBlockPos(), getPipePosLong());
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
    }

    public IFluidHandlerModifiable getHandler(@Nullable Direction side, boolean useCoverCapability) {
        ensureHandlersInitialized();
        checkNetwork();
        if (this.currentFluidPipeNet.get() == null) return null;
        FluidNetHandler handler = getHandlers().getOrDefault(side, getDefaultHandler());
        if (!useCoverCapability || side == null) return handler;
        CoverBehavior cover = getCoverContainer().getCoverAtSide(side);
        return cover != null ? cover.getFluidHandlerCap(handler) : handler;
    }

    public EnumMap<Direction, FluidNetHandler> getHandlers() {
        return this.handlers;
    }

    public Object2IntMap<FacingPos> getTransferred() {
        return this.transferred;
    }

    public FluidNetHandler getDefaultHandler() {
        return this.defaultHandler;
    }
}
