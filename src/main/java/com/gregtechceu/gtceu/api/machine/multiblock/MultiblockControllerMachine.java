package com.gregtechceu.gtceu.api.machine.multiblock;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.IParallelHatch;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.RequireRerender;
import com.lowdragmc.lowdraglib.syncdata.annotation.UpdateListener;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MultiblockControllerMachine extends MetaMachine implements IMultiController {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(MultiblockControllerMachine.class, MetaMachine.MANAGED_FIELD_HOLDER);
    protected MultiblockState multiblockState;
    protected MultiblockState[] subMultiblockState = null;
    protected int formeds = 0;
    private final List<IMultiPart> parts = new ArrayList<>();
    @Nullable
    private IParallelHatch parallelHatch = null;
    @DescSynced
    @UpdateListener(methodName = "onPartsUpdated")
    private BlockPos[] partPositions = new BlockPos[0];
    @DescSynced
    @RequireRerender
    protected boolean isFormed;
    @Persisted
    @DescSynced
    protected boolean isFlipped;

    private boolean simpleLock;

    private boolean checking;

    private int waitingTime;

    public MultiblockControllerMachine(MetaMachineBlockEntity holder) {
        super(holder);
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////
    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public MultiblockMachineDefinition getDefinition() {
        return (MultiblockMachineDefinition) definition;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() instanceof ServerLevel serverLevel) {
            MultiblockWorldSavedData.getOrCreate(serverLevel).addAsyncLogic(this);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (getLevel() instanceof ServerLevel serverLevel) {
            MultiblockWorldSavedData.getOrCreate(serverLevel).removeAsyncLogic(this);
        }
    }

    @Override
    @NotNull
    public MultiblockState getMultiblockState() {
        if (multiblockState == null) {
            multiblockState = new MultiblockState(this, getLevel(), getPos());
        }
        return multiblockState;
    }

    @SuppressWarnings("unused")
    protected void onPartsUpdated(BlockPos[] newValue, BlockPos[] oldValue) {
        parts.clear();
        for (var pos : newValue) {
            if (getMachine(getLevel(), pos) instanceof IMultiPart part) {
                parts.add(part);
            }
        }
    }

    protected void updatePartPositions() {
        this.partPositions = this.parts.isEmpty() ? new BlockPos[0] : this.parts.stream().map(part -> part.self().getPos()).toArray(BlockPos[]::new);
    }

    @Override
    public List<IMultiPart> getParts() {
        // for the client side, when the chunk unloaded
        if (parts.size() != this.partPositions.length) {
            parts.clear();
            for (var pos : this.partPositions) {
                if (getMachine(getLevel(), pos) instanceof IMultiPart part) {
                    parts.add(part);
                }
            }
        }
        return this.parts;
    }

    @Override
    public Optional<IParallelHatch> getParallelHatch() {
        return Optional.ofNullable(parallelHatch);
    }

    //////////////////////////////////////
    // *** Multiblock LifeCycle ***//
    //////////////////////////////////////
    private final Lock patternLock = new ReentrantLock();

    public int getSubFormed() {
        return formeds;
    }

    @Override
    public boolean checkPattern() {
        if (waitingTime < 1) {
            BlockPattern pattern = getPattern();
            boolean result = false;
            if (pattern != null) {
                checking = true;
                var state = getMultiblockState();
                formeds = 0;
                state.cleanCache();
                result = pattern.checkPatternAt(state, false);
                if (result) {
                    var subPattern = getSubPattern();
                    if (subPattern != null) {
                        for (int i = 0; i < subPattern.length; i++) {
                            var subState = MultiblockState.copy(state);
                            if (subPattern[i].get().checkPatternAt(subState, false)) {
                                state.merge(subState);
                                formeds++;
                            }
                            if (subMultiblockState != null) {
                                subMultiblockState[i] = subState;
                            }
                        }
                        if (subMultiblockState != null) {
                            for (var subState : subMultiblockState) subState.cleanCache();
                        }
                    }
                }
                state.cleanCache();
                checking = false;
            }
            if (result) {
                waitingTime = 0;
                return true;
            } else if (hasCheckButton()) {
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
    public void asyncCheckPattern(MultiblockWorldSavedData data) {
        if (simpleLock) return;
        if (getMultiblockState().error == null && isFormed) {
            data.addMapping(getMultiblockState());
            data.removeAsyncLogic(this);
            return;
        }
        if ((getHolder().getOffset() + data.periodID) % 4 == 0 && getLevel() instanceof ServerLevel serverLevel) {
            simpleLock = true;
            if (checkPatternWithTryLock()) {
                serverLevel.getServer().execute(() -> {
                    setFlipped(getMultiblockState().isNeededFlip());
                    onStructureFormed();
                    requestSync();
                    var mwsd = MultiblockWorldSavedData.getOrCreate(serverLevel);
                    mwsd.addMapping(getMultiblockState());
                    mwsd.removeAsyncLogic(this);
                    simpleLock = false;
                });
            } else {
                simpleLock = false;
            }
        }
    }

    protected void onStructureFormedAfter() {}

    @Override
    @MustBeInvokedByOverriders
    public void onStructureFormed() {
        if (getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().tell(new TickTask(1, this::onStructureFormedAfter));
        }
        isFormed = true;
        this.parts.clear();
        for (IMultiPart part : getMultiblockState().getMatchContext().parts) {
            if (shouldAddPartToController(part)) {
                this.parts.add(part);
            }
        }
        var sorter = getPartSorter();
        if (sorter != null) {
            this.parts.sort(sorter);
        }
        for (var part : parts) {
            if (part instanceof IParallelHatch pHatch) {
                parallelHatch = pHatch;
            }
            part.addedToController(this);
        }
        updatePartPositions();
    }

    @Override
    @MustBeInvokedByOverriders
    public void onStructureInvalid() {
        isFormed = false;
        for (IMultiPart part : parts) {
            part.removedFromController(this);
        }
        parallelHatch = null;
        parts.clear();
        updatePartPositions();
    }

    /**
     * mark multiblockState as unload error first.
     * if it's actually cuz by block breaking.
     * {@link #onStructureInvalid()} will be called from
     * {@link MultiblockState#onBlockStateChanged(BlockPos, BlockState)}
     */
    @Override
    @MustBeInvokedByOverriders
    public void onPartUnload() {
        parts.removeIf(part -> part.self().isInValid());
        getMultiblockState().setError(MultiblockState.UNLOAD_ERROR);
        if (getLevel() instanceof ServerLevel serverLevel) {
            MultiblockWorldSavedData.getOrCreate(serverLevel).addAsyncLogic(this);
        }
        updatePartPositions();
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
    public void setUpwardsFacing(@NotNull Direction upwardsFacing) {
        if (!getDefinition().isAllowExtendedFacing()) {
            return;
        }
        if (upwardsFacing.getAxis() == Direction.Axis.Y) {
            GTCEu.LOGGER.error("Tried to set upwards facing to invalid facing {}! Skipping", upwardsFacing);
            return;
        }
        var blockState = getBlockState();
        if (blockState.getBlock() instanceof MetaMachineBlock && blockState.getValue(IMachineBlock.UPWARDS_FACING_PROPERTY) != upwardsFacing) {
            getLevel().setBlockAndUpdate(getPos(), blockState.setValue(IMachineBlock.UPWARDS_FACING_PROPERTY, upwardsFacing));
            if (getLevel() != null && !getLevel().isClientSide) {
                notifyBlockUpdate();
                markDirty();
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

    public BlockPos[] getPartPositions() {
        return this.partPositions;
    }

    @Override
    public void requestCheck() {
        if (!simpleLock && isFormed && getLevel() instanceof ServerLevel serverLevel) {
            patternLock.lock();
            try {
                if (isFormed) {
                    if (requiresServerCheck()) {
                        if (checkPatternWithLock()) {
                            setFlipped(getMultiblockState().isNeededFlip());
                            onStructureFormed();
                            requestSync();
                            return;
                        }
                    }
                    setFlipped(false);
                    onStructureInvalid();
                    requestSync();
                    var mwsd = MultiblockWorldSavedData.getOrCreate(serverLevel);
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

    public boolean isFormed() {
        return this.isFormed;
    }

    public boolean isFlipped() {
        return this.isFlipped;
    }

    public void setFlipped(final boolean isFlipped) {
        this.isFlipped = isFlipped;
    }

    public Lock getPatternLock() {
        return this.patternLock;
    }

    @Override
    public MultiblockControllerMachine self() {
        return this;
    }
}
