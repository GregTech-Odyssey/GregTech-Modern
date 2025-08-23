package com.gregtechceu.gtceu.api.blockentity;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.block.MaterialPipeBlock;
import com.gregtechceu.gtceu.api.block.PipeBlock;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.capability.IToolable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.item.tool.IToolGridHighlight;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.pipenet.*;
import com.gregtechceu.gtceu.common.data.GTMaterialBlocks;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gregtechceu.gtceu.utils.cache.BlockEntityDirectionCache;

import com.lowdragmc.lowdraglib.Platform;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.networking.LDLNetworking;
import com.lowdragmc.lowdraglib.networking.s2c.SPacketManagedPayload;
import com.lowdragmc.lowdraglib.syncdata.IEnhancedManaged;
import com.lowdragmc.lowdraglib.syncdata.IManagedStorage;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.RequireRerender;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAsyncAutoSyncBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAutoPersistBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.field.FieldManagedStorage;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.lowdraglib.syncdata.managed.IRef;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class PipeBlockEntity<PipeType extends Enum<PipeType> & IPipeType<NodeDataType>, NodeDataType> extends BlockEntity implements ITickSubscription, IPaintable, IEnhancedManaged, IAsyncAutoSyncBlockEntity, IAutoPersistBlockEntity, IToolGridHighlight, IToolable {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(PipeBlockEntity.class);
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);
    private final long offset = GTValues.RNG.nextInt(20);
    @DescSynced
    @Persisted(key = "cover")
    protected final PipeCoverContainer coverContainer;
    @DescSynced
    @Persisted
    @RequireRerender
    protected int connections = Node.ALL_CLOSED;
    @DescSynced
    @Persisted
    @RequireRerender
    private int blockedConnections = Node.ALL_CLOSED;
    @Persisted
    public Direction blockedSide;
    private NodeDataType cachedNodeData;
    @Persisted
    @DescSynced
    @RequireRerender
    private int paintingColor = -1;
    @RequireRerender
    @DescSynced
    @Persisted
    @NotNull
    private Material frameMaterial = GTMaterials.NULL;
    private final List<TickableSubscription> serverTicks = new ObjectArrayList<>();
    private final List<TickableSubscription> waitingToAdd = new ObjectArrayList<>();

    private boolean asyncSyncing;
    private boolean sync = true;

    private final long posLong;

    private boolean wait;

    public final BlockEntityDirectionCache blockEntityDirectionCache = BlockEntityDirectionCache.create();

    @Nullable
    protected TickableSubscription transferSubs;

    public boolean autoTransfer;

    public PipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        this.coverContainer = new PipeCoverContainer(this);
        posLong = worldPosition.asLong();
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    @Override
    public IManagedStorage getRootStorage() {
        return syncStorage;
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public void onChanged() {
        var level = getLevel();
        if (level != null && !level.isClientSide && level.getServer() != null) {
            level.getServer().execute(this::setChanged);
        }
    }

    public long getOffsetTimer() {
        return level == null ? offset : (level.getServer().getTickCount() + offset);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        coverContainer.onUnload();
        blockEntityDirectionCache.clearCache();
        if (transferSubs != null) {
            transferSubs.unsubscribe();
            transferSubs = null;
        }
    }

    @Override
    public void clearRemoved() {
        blockEntityDirectionCache.clearCache();
        super.clearRemoved();
        coverContainer.onLoad();
    }

    public int getNumConnections() {
        int count = 0;
        int connections = this.connections;
        while (connections > 0) {
            count++;
            connections = connections & (connections - 1);
        }
        return count;
    }

    @NotNull
    public Material getFrameMaterial() {
        // backwards compat
        // noinspection ConstantValue
        if (frameMaterial == null) {
            frameMaterial = GTMaterials.NULL;
        }
        return frameMaterial;
    }

    public int getBlockedConnections() {
        return canHaveBlockedFaces() ? blockedConnections : 0;
    }

    @Override
    public PipeBlockEntity<PipeType, NodeDataType> getSelf() {
        return this;
    }

    public void onNeighborChanged() {
        blockEntityDirectionCache.clearCache();
        sync = true;
    }

    public @Nullable BlockEntity getNeighbor(Direction facing) {
        return blockEntityDirectionCache.getAdjacentBlockEntity(getLevel(), getBlockPos(), facing);
    }

    public BlockPos getPipePos() {
        return worldPosition;
    }

    public long getPipePosLong() {
        return posLong;
    }

    public NodeDataType getNodeData() {
        if (cachedNodeData == null) {
            this.cachedNodeData = getPipeBlock().createProperties(this);
        }
        return cachedNodeData;
    }

    @Nullable
    public TickableSubscription subscribeServerTick(Runnable runnable) {
        if (!isRemote()) {
            wait = false;
            var subscription = new TickableSubscription(runnable);
            waitingToAdd.add(subscription);
            return subscription;
        }
        return null;
    }

    public void unsubscribe(@Nullable TickableSubscription current) {
        if (current != null) {
            current.unsubscribe();
        }
    }

    public final void serverTick() {
        if (wait) return;
        if (!waitingToAdd.isEmpty()) {
            serverTicks.addAll(waitingToAdd);
            waitingToAdd.clear();
        }
        if (serverTicks.isEmpty()) {
            if (!isRemoved()) {
                wait = true;
            }
        } else {
            for (var iter = serverTicks.listIterator(0); iter.hasNext();) {
                var tickable = iter.next();
                if (tickable.isStillSubscribed()) {
                    tickable.run();
                } else {
                    iter.remove();
                }
            }
        }
    }

    protected void updateTransferTick(boolean tick, Runnable runnable) {
        if (tick) {
            transferSubs = subscribeServerTick(transferSubs, runnable);
        } else if (transferSubs != null) {
            transferSubs.unsubscribe();
            transferSubs = null;
        }
    }

    protected void blockedChanged(boolean isBlocked) {}

    //////////////////////////////////////
    // ******* Pipe Status *******//
    //////////////////////////////////////

    public void setBlocked(Direction side, boolean isBlocked) {
        if (level instanceof ServerLevel serverLevel && canHaveBlockedFaces()) {
            if (blockedSide != null && blockedSide != side) {
                setBlocked(blockedSide, false);
            }
            blockedConnections = withSideConnection(blockedConnections, side, isBlocked);
            setChanged();
            if (isBlocked) {
                blockedSide = side;
            } else {
                blockedSide = null;
            }
            blockedChanged(isBlocked);
            LevelPipeNet<?, ?> worldPipeNet = getPipeBlock().getWorldPipeNet(serverLevel);
            PipeNet<?> net = worldPipeNet.getNetFromPos(getBlockPos(), posLong);
            if (net != null) {
                net.onPipeConnectionsUpdate();
            }
            sync = true;
        }
    }

    public int getVisualConnections() {
        var visualConnections = connections;
        for (var side : GTUtil.DIRECTIONS) {
            var cover = coverContainer.getCoverAtSide(side);
            if (cover != null && cover.canPipePassThrough()) {
                visualConnections = visualConnections | (1 << side.ordinal());
            }
        }
        return visualConnections;
    }

    public void setConnection(Direction side, boolean connected, boolean fromNeighbor) {
        // fix desync between two connections. Can happen if a pipe side is blocked, and a new pipe is placed next to
        // it.
        if (!getLevel().isClientSide) {
            if (isConnected(side) == connected) {
                return;
            }
            BlockEntity tile = getNeighbor(side);
            // block connections if Pipe Types do not match
            if (connected && tile instanceof PipeBlockEntity<?, ?> pipeTile && pipeTile.getPipeType().getClass() != this.getPipeType().getClass()) {
                return;
            }
            if (!connected) {
                var cover = coverContainer.getCoverAtSide(side);
                if (cover != null && cover.canPipePassThrough()) return;
            }
            connections = withSideConnection(connections, side, connected);
            updateNetworkConnection(side, connected);
            // notify neighbor of change so Auto Output updates its ticking status
            getLevel().neighborChanged(getBlockPos().relative(side), getPipeBlock(), getBlockPos());
            setChanged();
            if (!fromNeighbor && tile instanceof PipeBlockEntity<?, ?> pipeTile) {
                syncPipeConnections(side, pipeTile);
            }
        }
    }

    private void syncPipeConnections(Direction side, PipeBlockEntity<?, ?> pipe) {
        Direction oppositeSide = side.getOpposite();
        boolean neighbourOpen = pipe.isConnected(oppositeSide);
        if (isConnected(side) == neighbourOpen) {
            return;
        }
        if (!neighbourOpen || pipe.coverContainer.getCoverAtSide(oppositeSide) == null) {
            pipe.setConnection(oppositeSide, !neighbourOpen, true);
        }
    }

    private void updateNetworkConnection(Direction side, boolean connected) {
        LevelPipeNet<?, ?> worldPipeNet = getPipeBlock().getWorldPipeNet((ServerLevel) getLevel());
        worldPipeNet.updateBlockedConnections(worldPosition, posLong, side, !connected);
    }

    protected int withSideConnection(int blockedConnections, Direction side, boolean connected) {
        int index = 1 << side.ordinal();
        if (connected) {
            return blockedConnections | index;
        } else {
            return blockedConnections & ~index;
        }
    }

    public void notifyBlockUpdate() {
        getLevel().updateNeighborsAt(getBlockPos(), getPipeBlock());
        sync = true;
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
    public void setChanged() {
        if (getLevel() != null) {
            getLevel().blockEntityChanged(getBlockPos());
        }
    }

    //////////////////////////////////////
    // ******* Interaction *******//
    //////////////////////////////////////
    @Override
    public boolean shouldRenderGrid(Player player, BlockPos pos, BlockState state, ItemStack held, Set<GTToolType> toolTypes) {
        if (toolTypes.contains(getPipeTuneTool())) return true;
        for (CoverBehavior cover : coverContainer.getCovers()) {
            if (cover.shouldRenderGrid(player, pos, state, held, toolTypes)) return true;
        }
        return false;
    }

    public ResourceTexture getPipeTexture(boolean isBlock) {
        return isBlock ? GuiTextures.TOOL_PIPE_CONNECT : GuiTextures.TOOL_PIPE_BLOCK;
    }

    @Override
    public ResourceTexture sideTips(Player player, BlockPos pos, BlockState state, Set<GTToolType> toolTypes, Direction side) {
        if (toolTypes.contains(getPipeTuneTool())) {
            if (player.isShiftKeyDown() && this.canHaveBlockedFaces()) {
                return getPipeTexture(isBlocked(side));
            } else {
                return getPipeTexture(isConnected(side));
            }
        }
        var cover = coverContainer.getCoverAtSide(side);
        if (cover != null) {
            return cover.sideTips(player, pos, state, toolTypes, side);
        }
        return null;
    }

    @Override
    public Pair<GTToolType, InteractionResult> onToolClick(Set<GTToolType> toolTypes, ItemStack itemStack, UseOnContext context) {
        // the side hit from the machine grid
        var playerIn = context.getPlayer();
        if (playerIn == null) return Pair.of(null, InteractionResult.PASS);
        var hand = context.getHand();
        var hitResult = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), false);
        Direction gridSide = ICoverable.determineGridSideHit(hitResult);
        CoverBehavior coverBehavior = gridSide == null ? null : coverContainer.getCoverAtSide(gridSide);
        if (gridSide == null) gridSide = hitResult.getDirection();
        // Prioritize covers where they apply (Screwdriver, Soft Mallet)
        if (toolTypes.isEmpty() && playerIn.isShiftKeyDown()) {
            if (coverBehavior != null) {
                return Pair.of(null, coverBehavior.onScrewdriverClick(playerIn, hand, hitResult));
            }
        }
        if (toolTypes.contains(GTToolType.SCREWDRIVER)) {
            if (coverBehavior != null) {
                return Pair.of(GTToolType.SCREWDRIVER, coverBehavior.onScrewdriverClick(playerIn, hand, hitResult));
            }
        } else if (toolTypes.contains(GTToolType.SOFT_MALLET)) {
            if (coverBehavior != null) {
                return Pair.of(GTToolType.SOFT_MALLET, coverBehavior.onSoftMalletClick(playerIn, hand, hitResult));
            }
        } else if (toolTypes.contains(getPipeTuneTool())) {
            boolean isOpen = this.isConnected(gridSide);
            if (playerIn.isShiftKeyDown() && this.canHaveBlockedFaces()) {
                if (isOpen) {
                    boolean isBlocked = this.isBlocked(gridSide);
                    this.setBlocked(gridSide, !isBlocked);
                }
            } else {
                this.setConnection(gridSide, !isOpen, false);
                sync = true;
            }
            return Pair.of(getPipeTuneTool(), InteractionResult.sidedSuccess(playerIn.level().isClientSide));
        } else if (toolTypes.contains(GTToolType.CROWBAR)) {
            if (coverBehavior != null) {
                if (!isRemote()) {
                    coverContainer.removeCover(gridSide, playerIn);
                    sync = true;
                    return Pair.of(GTToolType.CROWBAR, InteractionResult.sidedSuccess(playerIn.level().isClientSide));
                }
            } else {
                if (!frameMaterial.isNull()) {
                    Block.popResource(getLevel(), worldPosition, GTMaterialBlocks.MATERIAL_BLOCKS.get(TagPrefix.frameGt, frameMaterial).asStack());
                    frameMaterial = GTMaterials.NULL;
                    sync = true;
                    return Pair.of(GTToolType.CROWBAR, InteractionResult.sidedSuccess(playerIn.level().isClientSide));
                }
            }
        }
        return Pair.of(null, InteractionResult.PASS);
    }

    public GTToolType getPipeTuneTool() {
        return GTToolType.WRENCH;
    }

    @Override
    public int getDefaultPaintingColor() {
        return this.getPipeBlock() instanceof MaterialPipeBlock<?, ?, ?> materialPipeBlock ? materialPipeBlock.material.getMaterialRGB() : 0xFFFFFF;
    }

    public void doExplosion(float explosionPower) {
        getLevel().removeBlock(worldPosition, false);
        if (!getLevel().isClientSide) {
            ((ServerLevel) getLevel()).sendParticles(ParticleTypes.LARGE_SMOKE, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, 10, 0.2, 0.2, 0.2, 0.0);
        }
        getLevel().explode(null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, explosionPower, Level.ExplosionInteraction.NONE);
    }

    public static boolean isFaceBlocked(int blockedConnections, Direction side) {
        return (blockedConnections & (1 << side.ordinal())) > 0;
    }

    public static boolean isConnected(int connections, Direction side) {
        return (connections & (1 << side.ordinal())) > 0;
    }

    public FieldManagedStorage getSyncStorage() {
        return this.syncStorage;
    }

    public PipeCoverContainer getCoverContainer() {
        return this.coverContainer;
    }

    public int getConnections() {
        return this.connections;
    }

    public void setConnections(final int connections) {
        this.connections = connections;
    }

    public int getPaintingColor() {
        return this.paintingColor;
    }

    public void setPaintingColor(final int paintingColor) {
        this.paintingColor = paintingColor;
    }

    public void setFrameMaterial(@NotNull final Material frameMaterial) {
        if (frameMaterial == null) {
            throw new NullPointerException("frameMaterial is marked non-null but is null");
        }
        this.frameMaterial = frameMaterial;
        sync = true;
    }

    private boolean needSync() {
        if (sync) {
            sync = false;
            return true;
        }
        return false;
    }

    @Override
    public void asyncTick(long periodID) {
        if (Platform.isServerNotSafe()) return;
        if (needSync() || periodID + offset % 20 == 0) {
            if (useAsyncThread() && !isRemoved()) {
                for (IRef field : getNonLazyFields()) {
                    field.update();
                }
                if (syncStorage.hasDirtySyncFields() && !asyncSyncing) {
                    asyncSyncing = true;
                    Platform.getMinecraftServer().execute(() -> {
                        if (Platform.isServerNotSafe()) return;
                        var packet = SPacketManagedPayload.of(this, false);
                        LDLNetworking.NETWORK.sendToTrackingChunk(packet, level.getChunkAt(this.getCurrentPos()));
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

    /**
     * If tube is set to block connection from the specific side
     *
     * @param side face
     */
    public boolean isBlocked(Direction side) {
        return PipeBlockEntity.isFaceBlocked(getBlockedConnections(), side);
    }

    /**
     * If node is connected to the specific side
     *
     * @param side face
     */
    public boolean isConnected(Direction side) {
        return PipeBlockEntity.isConnected(connections, side);
    }

    // if a face is blocked it will still render as connected, but it won't be able to receive stuff from that direction
    public boolean canHaveBlockedFaces() {
        return true;
    }

    public void markAsDirty() {
        setChanged();
    }

    public boolean isInValid() {
        return isRemoved();
    }

    public boolean isRemote() {
        var level = this.level;
        if (level == null) {
            return GTCEu.isClientThread();
        }
        return level.isClientSide;
    }

    @SuppressWarnings("unchecked")
    public PipeBlock<PipeType, NodeDataType, ?> getPipeBlock() {
        return (PipeBlock<PipeType, NodeDataType, ?>) getBlockState().getBlock();
    }

    @Nullable
    public PipeNet<NodeDataType> getPipeNet() {
        if (level instanceof ServerLevel serverLevel) {
            return getPipeBlock().getWorldPipeNet(serverLevel).getNetFromPos(worldPosition, posLong);
        }
        return null;
    }

    public PipeType getPipeType() {
        return getPipeBlock().pipeType;
    }

    public void scheduleRenderUpdate() {
        var pos = worldPosition;
        var level = this.level;
        if (level != null) {
            var state = level.getBlockState(pos);
            if (level.isClientSide) {
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_IMMEDIATE);
            } else {
                level.blockEvent(pos, state.getBlock(), 1, 0);
            }
        }
    }

    public void scheduleNeighborShapeUpdate() {
        Level level = this.level;
        BlockPos pos = worldPosition;

        if (level == null || pos == null)
            return;

        level.getBlockState(pos).updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
    }
}
