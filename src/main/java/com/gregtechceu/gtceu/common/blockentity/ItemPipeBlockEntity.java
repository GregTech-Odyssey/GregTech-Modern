package com.gregtechceu.gtceu.common.blockentity;

import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.filter.ItemFilter;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.ItemPipeProperties;
import com.gregtechceu.gtceu.api.transfer.forge.ForgeItemAdapter;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.KeyTransfer;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.common.block.ItemPipeBlock;
import com.gregtechceu.gtceu.common.cover.ItemFilterCover;
import com.gregtechceu.gtceu.common.pipelike.item.ItemNetHandler;
import com.gregtechceu.gtceu.common.pipelike.item.ItemPipeNet;
import com.gregtechceu.gtceu.common.pipelike.item.ItemPipeType;
import com.gregtechceu.gtceu.utils.FacingPos;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.EmptyHandler;

import appeng.api.stacks.AEItemKey;
import appeng.api.storage.AEKeyFilter;
import com.gto.fastcollection.fastutil.O2IOpenCacheHashMap;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.EnumMap;

public final class ItemPipeBlockEntity extends PipeBlockEntity<ItemPipeType, ItemPipeProperties> {

    private WeakReference<ItemPipeNet> currentItemPipeNet = new WeakReference<>(null);
    private final EnumMap<Direction, ItemNetHandler> handlers = new EnumMap<>(Direction.class);
    @Getter
    private final O2IOpenCacheHashMap<FacingPos> transferred = new O2IOpenCacheHashMap<>();
    private ItemNetHandler defaultHandler;
    @SuppressWarnings("unchecked")
    private final LazyOptional<IItemHandler>[] capabilityCache = new LazyOptional[6];
    private int transferredItems = 0;
    private long timer = 0;

    public ItemPipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public static ItemPipeBlockEntity create(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        return new ItemPipeBlockEntity(type, pos, blockState);
    }

    public long getLevelTime() {
        return hasLevel() ? getLevel().getGameTime() : 0L;
    }

    @Override
    @NotNull
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            if (side != null && isConnected(side)) {
                return ForgeCapabilities.ITEM_HANDLER.orEmpty(cap, getItemCapability(side));
            }
            return LazyOptional.empty();
        }
        return super.getCapability(cap, side);
    }

    private LazyOptional<IItemHandler> getItemCapability(Direction side) {
        var cached = capabilityCache[side.ordinal()];
        if (cached != null) return cached;
        if (isRemote()) return LazyOptional.of(() -> EmptyHandler.INSTANCE);
        ensureHandlersInitialized();
        checkNetwork();
        if (this.currentItemPipeNet.get() == null) return LazyOptional.of(() -> EmptyHandler.INSTANCE);
        var handler = getHandler(side, true);
        if (handler == null) return LazyOptional.empty();
        var adapter = new ForgeItemAdapter(handler);
        cached = LazyOptional.of(() -> adapter);
        capabilityCache[side.ordinal()] = cached;
        return cached;
    }

    public void invalidateCapabilityCache() {
        for (int i = 0; i < capabilityCache.length; i++) {
            var cached = capabilityCache[i];
            if (cached != null) {
                capabilityCache[i] = null;
                cached.invalidate();
            }
        }
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
        ItemPipeNet net = getItemPipeNet();
        if (net == null) {
            return;
        }
        invalidateCapabilityCache();
        for (Direction facing : GTUtil.DIRECTIONS) {
            handlers.put(facing, new ItemNetHandler(net, this, facing));
        }
        defaultHandler = new ItemNetHandler(net, this, null);
    }

    public void checkNetwork() {
        if (defaultHandler != null) {
            ItemPipeNet current = getItemPipeNet();
            if (defaultHandler.getNet() != current) {
                defaultHandler.updateNetwork(current);
                for (ItemNetHandler handler : handlers.values()) {
                    handler.updateNetwork(current);
                }
                invalidateCapabilityCache();
            }
        }
    }

    @Nullable
    public ItemPipeNet getItemPipeNet() {
        if (level instanceof ServerLevel serverLevel && getBlockState().getBlock() instanceof ItemPipeBlock itemPipeBlock) {
            ItemPipeNet currentItemPipeNet = this.currentItemPipeNet.get();
            if (currentItemPipeNet != null && currentItemPipeNet.isValid() && currentItemPipeNet.containsNode(getPipeLongPos())) return currentItemPipeNet;
            currentItemPipeNet = itemPipeBlock.getWorldPipeNet(serverLevel).getNetFromPos(getBlockPos(), getPipeLongPos());
            if (currentItemPipeNet != null) {
                this.currentItemPipeNet = new WeakReference<>(currentItemPipeNet);
            }
        }
        return this.currentItemPipeNet.get();
    }

    /**
     * every time the transferred variable is accessed this method should be called
     * if 20 ticks passed since the last access it will reset it
     * this method is equal to
     * 
     * @code {
     *       if (++time % 20 == 0) {
     *       this.transferredItems = 0;
     *       }
     *       }
     *       <p/>
     *       if it was in a ticking TileEntity
     */
    private void updateTransferredState() {
        long currentTime = getLevelTime();
        long dif = currentTime - this.timer;
        if (dif >= 20 || dif < 0) {
            this.transferredItems = 0;
            this.timer = currentTime;
        }
    }

    public void addTransferredItems(int amount) {
        updateTransferredState();
        this.transferredItems += amount;
    }

    public int getTransferredItems() {
        updateTransferredState();
        return this.transferredItems;
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
        if (this.currentItemPipeNet.get() == null) return;
        boolean hasHandler = false;
        int throughput = (int) ((getNodeData().getTransferRate() * 64) + 0.5);
        autoTransfer = true;
        for (Direction facing : GTUtil.DIRECTIONS) {
            if (facing != blockedSide && isConnected(facing)) {
                var be = getNeighborBlockEntity(facing);
                if (be == null || be instanceof PipeBlockEntity<?, ?>) continue;
                var handler = GTCapabilityHelper.getItemKeyHandler(be, facing.getOpposite());
                if (handler != null) {
                    hasHandler = true;
                    throughput -= (int) KeyTransfer.transfer(handler, handlers.getOrDefault(facing, defaultHandler), throughput, getCoverContainer().getCoverAtSide(facing) instanceof ItemFilterCover filterCover ? itemFilter(filterCover.getItemFilter()) : null);
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

    private static AEKeyFilter itemFilter(ItemFilter filter) {
        return key -> key instanceof AEItemKey itemKey && filter.test(Keys.displayStack(itemKey));
    }

    @Nullable
    public IKeyHandler<AEItemKey> getHandler(@Nullable Direction side, boolean useCoverCapability) {
        if (isRemote()) return null;
        ensureHandlersInitialized();
        checkNetwork();
        if (this.currentItemPipeNet.get() == null) return null;
        ItemNetHandler handler = handlers.getOrDefault(side, defaultHandler);
        if (!useCoverCapability || side == null) return handler;
        CoverBehavior cover = getCoverContainer().getCoverAtSide(side);
        return cover != null ? cover.getItemHandlerCap(handler) : handler;
    }
}
