package com.gregtechceu.gtceu.api.machine.multiblock;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiModule;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IWorkableMultiPart;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldData;
import com.gregtechceu.gtceu.common.network.GTNetwork;
import com.gregtechceu.gtceu.common.network.packets.SCPacketStructureFormed;
import com.gregtechceu.gtceu.core.ILevel;
import com.gregtechceu.gtceu.utils.TaskHandler;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.fast.recipesearch.IteratorUtil;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MultiblockControllerMachine extends MetaMachine implements IMultiController, IMachineLife {

    protected MultiblockState multiblockState;
    @Getter
    protected final int subPatternAmount = getSubPattern() == null ? 0 : getSubPattern().length;
    protected MultiblockState[] subMultiblockState = new MultiblockState[subPatternAmount];
    @SyncToClient
    protected final boolean[] formeds = new boolean[subPatternAmount];
    protected int formedAmount;
    protected IMultiPart[] parts = new IMultiPart[0];
    protected final List<IMultiModule<?>> modules = new ArrayList<>();

    @Getter
    @SyncToClient(listener = "onPartsUpdated")
    protected BlockPos[] partPositions = new BlockPos[0];
    @Getter
    @SyncToClient(listener = "onFormedUpdated", notifyUpdate = true)
    protected boolean isFormed;

    @Getter
    @SyncToClient
    protected final boolean[] isFormedsFlipped = new boolean[subPatternAmount];

    @Getter
    @SyncToClient
    protected boolean isFlipped;

    protected volatile boolean simpleLock;

    protected volatile boolean checking;

    protected volatile int waitingTime;

    protected boolean toldNotFormed = false;

    /**
     * Cache for rendering highlight boxes on client side.
     * rendering is done in GTOCore
     */
    public static final Multimap<UUID, Component> MESSAGE_CACHE = HashMultimap.create();
    public static boolean sendMessage;

    public MultiblockControllerMachine(MetaMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public MultiblockMachineDefinition getDefinition() {
        return (MultiblockMachineDefinition) definition;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() instanceof ServerLevel serverLevel) {
            multiblockState = new MultiblockState(this, getLevel(), getPos());
            MultiblockWorldData.getOrCreate(serverLevel).addAsyncLogic(this);
        } else if (isFormed) {
            onStructureFormedClient();
        } else {
            ILevel.getHighlightCache(getLevel()).add(getPos().asLong());
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (getLevel() instanceof ServerLevel serverLevel) {
            MultiblockWorldData.getOrCreate(serverLevel).removeAsyncLogic(this);
        } else {
            ILevel.getHighlightCache(getLevel()).remove(getPos().asLong());
            onStructureInvalidClient();
        }
    }

    @Override
    public void onMachinePlaced(@Nullable LivingEntity player, ItemStack stack) {
        toldNotFormed = true; // newly placed machine won't tell invalid structure
    }

    @Override
    public MultiblockState getMultiblockState() {
        if (multiblockState == null) {
            multiblockState = new MultiblockState(this, getLevel(), getPos());
        }
        return multiblockState;
    }

    @Override
    public MultiblockState[] getSubMultiblockState() {
        return subMultiblockState;
    }

    @SuppressWarnings("unused")
    protected void onPartsUpdated(BlockPos[] newValue, BlockPos[] oldValue) {
        var list = new ArrayList<IMultiPart>();
        for (var pos : newValue) {
            if (getMachine(getLevel(), pos) instanceof IMultiPart part) {
                if (part instanceof IMultiModule<?> module) continue;
                list.add(part);
            }
        }
        parts = list.toArray(new IMultiPart[0]);
    }

    @SuppressWarnings("unused")
    protected void onFormedUpdated(boolean newValue, boolean oldValue) {
        if (newValue) {
            onStructureFormedClient();
        }
    }

    protected void updatePartPositions() {
        this.partPositions = new BlockPos[this.parts.length];
        for (int i = 0; i < this.parts.length; i++) {
            this.partPositions[i] = this.parts[i].self().getPos();
        }
    }

    public Iterable<IWorkableMultiPart> getWorkableParts() {
        return IteratorUtil.wrap(Arrays.stream(parts).filter(IWorkableMultiPart.class::isInstance).map(IWorkableMultiPart.class::cast).iterator());
    }

    @Override
    public IMultiPart[] getParts() {
        // for the client side, when the chunk unloaded
        if (parts.length != this.partPositions.length) {
            onPartsUpdated(this.partPositions, this.partPositions);
        }
        return this.parts;
    }

    //////////////////////////////////////
    // *** Multiblock LifeCycle ***//
    //////////////////////////////////////
    @Getter
    private final Lock patternLock = new ReentrantLock();

    public boolean @NotNull [] getSubFormed() {
        return formeds;
    }

    public int getSubFormedAmount() {
        return formedAmount;
    }

    @Override
    public boolean checkPattern() {
        if (waitingTime < 1) {
            BlockPattern pattern = getPattern();
            var state = getMultiblockState();
            boolean result = false;
            if (pattern != null) {
                checking = true;
                state.clearCache();
                state.removeShared();
                result = pattern.checkPatternAt(state, false);
                if (result) {
                    var subPattern = getSubPattern();
                    if (subPattern != null) {
                        formedAmount = 0;
                        Arrays.fill(formeds, false);
                        Arrays.fill(isFormedsFlipped, false);
                        Arrays.fill(subMultiblockState, null);
                        for (int i = 0; i < subPattern.length; i++) {
                            var subState = MultiblockState.copy(state);
                            if (subPattern[i].get().checkPatternAt(subState, false)) {
                                state.merge(subState);
                                formeds[i] = true;
                                formedAmount++;
                                isFormedsFlipped[i] = subState.isNeededFlip();
                            }
                            subMultiblockState[i] = subState;
                        }
                    }
                    if (getLevel() instanceof ServerLevel serverLevel) {
                        var c = state.blockEntityCache.longStream().mapToObj(BlockPos::of).toList();
                        TaskHandler.enqueueTask(serverLevel, () -> c.forEach(pos -> serverLevel.getChunkAt(pos).removeBlockEntityTicker(pos)));
                    }
                }
                state.clearCache();
                for (var subState : subMultiblockState) {
                    if (subState == null) continue;
                    subState.clearCache();
                }
                checking = false;
            }
            if (result) {
                state.addShared();
                waitingTime = 0;
                return true;
            } else if (state.error != MultiblockState.UNLOAD_ERROR && hasCheckButton()) {
                waitingTime = 10;
            } else {
                waitingTime = 1;
            }
        } else {
            waitingTime--;
        }
        return false;
    }

    @Override
    public void asyncCheckPattern(MultiblockWorldData data) {
        if (simpleLock) return;
        if (getMultiblockState().error == null && isFormed) {
            data.addMapping(getMultiblockState());
            data.removeAsyncLogic(this);
            return;
        }
        if (getLevel() instanceof ServerLevel serverLevel) {
            simpleLock = true;
            if (checkPatternWithTryLock()) {
                TaskHandler.enqueueTask(serverLevel, () -> {
                    if (requiresServerCheck() && !checkPatternWithLock()) {
                        simpleLock = false;
                        return;
                    }
                    isFlipped = getMultiblockState().isNeededFlip();
                    onStructureFormed();
                    requestSync();
                    var mwsd = MultiblockWorldData.getOrCreate(serverLevel);
                    mwsd.addMapping(getMultiblockState());
                    mwsd.removeAsyncLogic(this);
                    simpleLock = false;
                });
            } else {
                if (sendMessage && getMultiblockState().error != MultiblockState.UNINIT_ERROR && !toldNotFormed && getOwner() != null) {
                    TaskHandler.enqueueTask(serverLevel, () -> getOwner().getMembers().forEach(uuid -> {
                        Player p = serverLevel.getPlayerByUUID(uuid);
                        Component m = Component.translatable("gtocore.multiblock.invalid.message",
                                getDefinition().get().getName().withStyle(ChatFormatting.YELLOW),
                                Component.literal(getPos().toShortString()).withStyle(ChatFormatting.AQUA));
                        if (p != null) p.sendSystemMessage(m); // this player is online
                        else MESSAGE_CACHE.put(uuid, m); // cache message for offline player
                    }));
                    toldNotFormed = true;
                }
                simpleLock = false;
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    public void onStructureFormedClient() {}

    @MustBeInvokedByOverriders
    protected void onStructureFormedAfter() {
        GTNetwork.NETWORK.sendToAll(new SCPacketStructureFormed(getPos().asLong(), true));
    }

    @Override
    @MustBeInvokedByOverriders
    public void onStructureFormed() {
        if (getLevel() instanceof ServerLevel serverLevel) {
            TaskHandler.enqueueTask(serverLevel, this::onStructureFormedAfter, 0);
        }
        isFormed = true;
        modules.clear();
        var list = new ArrayList<IMultiPart>();
        for (IMultiPart part : getMultiblockState().getMatchContext().getParts()) {
            if (shouldAddPartToController(part)) {
                if (part instanceof IMultiModule<?> module) {
                    modules.add(module);
                } else {
                    list.add(part);
                }
                part.addedToController(this);
            }
        }
        parts = list.toArray(new IMultiPart[0]);
        var sorter = getPartSorter();
        if (sorter != null) {
            Arrays.sort(parts, sorter);
        }
        updatePartPositions();
    }

    @OnlyIn(Dist.CLIENT)
    public void onStructureInvalidClient() {}

    protected void onStructureInvalidAfter() {
        GTNetwork.NETWORK.sendToAll(new SCPacketStructureFormed(getPos().asLong(), false));
    }

    @Override
    @MustBeInvokedByOverriders
    public void onStructureInvalid() {
        isFormed = false;
        Arrays.fill(formeds, false);
        modules.forEach(m -> m.removedFromController(this));
        for (IMultiPart part : parts) {
            part.removedFromController(this);
        }
        modules.clear();
        this.parts = new IMultiPart[0];
        updatePartPositions();
        if (getLevel() instanceof ServerLevel serverLevel) {
            TaskHandler.enqueueTask(serverLevel, this::onStructureInvalidAfter, 0);
        }
    }

    @Override
    public void onRotated(Direction oldFacing, Direction newFacing) {
        super.onRotated(oldFacing, newFacing);
        if (oldFacing != newFacing) {
            requestCheck();
        }
    }

    public boolean allowFlip() {
        return getDefinition().isAllowFlip();
    }

    @Override
    public void setUpwardsFacing(Direction upwardsFacing) {
        if (!getDefinition().isAllowExtendedFacing()) {
            return;
        }
        if (upwardsFacing.getAxis() == Direction.Axis.Y) {
            GTCEu.LOGGER.error("Tried to set upwards facing to invalid facing {}! Skipping", upwardsFacing);
            return;
        }
        var blockState = getBlockState();
        if (blockState.getBlock() instanceof MetaMachineBlock && blockState.getValue(MetaMachineBlock.UPWARDS_FACING_PROPERTY) != upwardsFacing) {
            getLevel().setBlockAndUpdate(getPos(), blockState.setValue(MetaMachineBlock.UPWARDS_FACING_PROPERTY, upwardsFacing));
            if (getLevel() != null && !getLevel().isClientSide) {
                notifyBlockUpdate();
                onChanged();
                requestCheck();
            }
        }
    }

    @Override
    protected InteractionResult onWrenchClick(Player playerIn, InteractionHand hand, Direction gridSide, BlockHitResult hitResult) {
        if (gridSide == getFrontFacing() && allowExtendedFacing()) {
            setUpwardsFacing(playerIn.isShiftKeyDown() ? getUpwardsFacing().getCounterClockWise() : getUpwardsFacing().getClockWise());
            return InteractionResult.sidedSuccess(playerIn.level().isClientSide);
        }
        if (playerIn.isShiftKeyDown()) {
            if (gridSide == getFrontFacing() || !isFacingValid(gridSide)) {
                return InteractionResult.FAIL;
            }
            if (!isRemote()) {
                setFrontFacing(gridSide);
            }
            return InteractionResult.sidedSuccess(playerIn.level().isClientSide);
        }
        return super.onWrenchClick(playerIn, hand, gridSide, hitResult);
    }

    @Override
    public void setFrontFacing(Direction facing) {
        super.setFrontFacing(facing);
        if (getLevel() != null && !getLevel().isClientSide) {
            requestCheck();
        }
    }

    @Override
    public void requestCheck() {
        if (!simpleLock && isFormed && getLevel() instanceof ServerLevel serverLevel) {
            patternLock.lock();
            try {
                if (isFormed) {
                    if (requiresServerCheck() && checkPatternWithLock()) {
                        isFlipped = getMultiblockState().isNeededFlip();
                        onStructureFormed();
                        requestSync();
                        return;
                    }
                    isFlipped = false;
                    onStructureInvalid();
                    requestSync();
                    var mwsd = MultiblockWorldData.getOrCreate(serverLevel);
                    mwsd.removeMapping(getMultiblockState());
                    mwsd.addAsyncLogic(this);
                }
            } finally {
                patternLock.unlock();
            }
        }
    }

    @Override
    public void setWaitingTime(int time) {
        waitingTime = time;
    }

    @Override
    public int getWaitingTime() {
        return waitingTime;
    }

    @Override
    public boolean checking() {
        return checking;
    }

    @Override
    public MultiblockControllerMachine self() {
        return this;
    }
}
