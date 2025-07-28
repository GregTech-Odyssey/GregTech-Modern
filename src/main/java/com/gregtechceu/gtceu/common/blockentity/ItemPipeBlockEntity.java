package com.gregtechceu.gtceu.common.blockentity;

import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.ItemPipeProperties;
import com.gregtechceu.gtceu.common.block.ItemPipeBlock;
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

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.EnumMap;

public class ItemPipeBlockEntity extends PipeBlockEntity<ItemPipeType, ItemPipeProperties> {

    protected WeakReference<ItemPipeNet> currentItemPipeNet = new WeakReference<>(null);
    private final EnumMap<Direction, ItemNetHandler> handlers = new EnumMap<>(Direction.class);
    private final Object2IntMap<FacingPos> transferred = new Object2IntOpenHashMap<>();
    private ItemNetHandler defaultHandler;
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
                return ForgeCapabilities.ITEM_HANDLER.orEmpty(cap, LazyOptional.of(() -> getHandler(side, true)));
            }
            return LazyOptional.empty();
        } else if (cap == GTCapability.CAPABILITY_COVERABLE) {
            return GTCapability.CAPABILITY_COVERABLE.orEmpty(cap, LazyOptional.of(this::getCoverContainer));
        }
        return super.getCapability(cap, side);
    }

    private void ensureHandlersInitialized() {
        if (getHandlers().isEmpty()) initHandlers();
    }

    public void initHandlers() {
        ItemPipeNet net = getItemPipeNet();
        if (net == null) {
            return;
        }
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
            }
        }
    }

    @Nullable
    public ItemPipeNet getItemPipeNet() {
        if (level instanceof ServerLevel serverLevel && getBlockState().getBlock() instanceof ItemPipeBlock itemPipeBlock) {
            ItemPipeNet currentItemPipeNet = this.currentItemPipeNet.get();
            if (currentItemPipeNet != null && currentItemPipeNet.isValid() && currentItemPipeNet.containsNode(getPipePosLong())) return currentItemPipeNet;
            currentItemPipeNet = itemPipeBlock.getWorldPipeNet(serverLevel).getNetFromPos(getBlockPos(), getPipePosLong());
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
    }

    public IItemHandler getHandler(@Nullable Direction side, boolean useCoverCapability) {
        if (isRemote()) return EmptyHandler.INSTANCE;
        ensureHandlersInitialized();
        checkNetwork();
        if (this.currentItemPipeNet.get() == null) return EmptyHandler.INSTANCE;
        ItemNetHandler handler = getHandlers().getOrDefault(side, getDefaultHandler());
        if (!useCoverCapability || side == null) return handler;
        CoverBehavior cover = getCoverContainer().getCoverAtSide(side);
        return cover != null ? cover.getItemHandlerCap(handler) : handler;
    }

    public EnumMap<Direction, ItemNetHandler> getHandlers() {
        return this.handlers;
    }

    public Object2IntMap<FacingPos> getTransferred() {
        return this.transferred;
    }

    public ItemNetHandler getDefaultHandler() {
        return this.defaultHandler;
    }
}
