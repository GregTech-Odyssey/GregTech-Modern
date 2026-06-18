package com.gregtechceu.gtceu.api.blockentity;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.block.MaterialPipeBlock;
import com.gregtechceu.gtceu.api.block.PipeBlock;
import com.gregtechceu.gtceu.api.capability.ICoverable;
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

import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;

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

import com.gto.datasynclib.FieldDataManager;
import com.gto.datasynclib.LazyFieldDataManager;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import com.mojang.datafixers.util.Pair;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class PipeBlockEntity<PipeType extends Enum<PipeType> & IPipeType<NodeDataType>, NodeDataType> extends GTBlockEntity implements IPaintable, IToolGridHighlight {

    private final LazyFieldDataManager fieldDataManager = new LazyFieldDataManager(this);

    @Getter
    @SyncToClient
    @SaveToDisk(key = "cover")
    protected final PipeCoverContainer coverContainer;
    @Getter
    @Setter
    @SaveToDisk
    @SyncToClient(notifyUpdate = true)
    protected int connections = Node.ALL_CLOSED;

    @SaveToDisk
    @SyncToClient(notifyUpdate = true)
    private int blockedConnections = Node.ALL_CLOSED;
    @SaveToDisk
    @SyncToClient(notifyUpdate = true)
    public Direction blockedSide;
    private NodeDataType cachedNodeData;
    @Getter
    @Setter
    @SaveToDisk
    @SyncToClient(notifyUpdate = true)
    private int paintingColor = -1;

    @SyncToClient(notifyUpdate = true)
    @SaveToDisk
    @NotNull
    private Material frameMaterial = GTMaterials.NULL;

    protected TickableSubscription transferSubs;

    public boolean autoTransfer;

    public PipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        this.coverContainer = new PipeCoverContainer(this);
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    @Override
    public void setRemoved() {
        super.setRemoved();
        coverContainer.onUnload();
        if (transferSubs != null) {
            transferSubs.unsubscribe();
            transferSubs = null;
        }
    }

    @Override
    public void clearRemoved() {
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

    public void onNeighborChanged() {
        super.onNeighborChanged();
        sync = true;
    }

    public BlockPos getPipePos() {
        return worldPosition;
    }

    public long getPipeLongPos() {
        return longPos;
    }

    public NodeDataType getNodeData() {
        if (cachedNodeData == null) {
            this.cachedNodeData = getPipeBlock().createProperties(this);
        }
        return cachedNodeData;
    }

    protected void updateTransferTick(boolean tick, Runnable runnable) {
        if (tick) {
            transferSubs = subscribeServerTick(transferSubs, runnable, 20);
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
            PipeNet<?> net = worldPipeNet.getNetFromPos(getBlockPos(), longPos);
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
            BlockEntity tile = getNeighborBlockEntity(side);
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

    protected void updateNetworkConnection(Direction side, boolean connected) {
        LevelPipeNet<?, ?> worldPipeNet = getPipeBlock().getWorldPipeNet((ServerLevel) getLevel());
        worldPipeNet.updateBlockedConnections(worldPosition, longPos, side, !connected);
    }

    protected int withSideConnection(int blockedConnections, Direction side, boolean connected) {
        int index = 1 << side.ordinal();
        if (connected) {
            return blockedConnections | index;
        } else {
            return blockedConnections & ~index;
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

    public void setFrameMaterial(final Material frameMaterial) {
        if (frameMaterial == null) {
            throw new NullPointerException("frameMaterial is marked non-null but is null");
        }
        this.frameMaterial = frameMaterial;
        sync = true;
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
            return getPipeBlock().getWorldPipeNet(serverLevel).getNetFromPos(worldPosition, longPos);
        }
        return null;
    }

    public PipeType getPipeType() {
        return getPipeBlock().pipeType;
    }

    public void scheduleNeighborShapeUpdate() {
        Level level = this.level;
        if (level == null) return;
        getBlockState().updateNeighbourShapes(level, worldPosition, Block.UPDATE_ALL);
    }

    @Override
    public FieldDataManager getFieldDataManager() {
        return fieldDataManager.get();
    }
}
