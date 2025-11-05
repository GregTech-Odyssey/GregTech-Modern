package com.gregtechceu.gtceu.api.machine.multiblock;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.IParallelHatch;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;
import com.gregtechceu.gtceu.core.ILevel;
import com.gregtechceu.gtceu.utils.collection.OpenCacheHashSet;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.RequireRerender;
import com.lowdragmc.lowdraglib.syncdata.annotation.UpdateListener;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;
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
    @DescSynced
    protected final boolean[] formeds = new boolean[subPatternAmount];
    protected int formedAmount;
    protected IMultiPart[] parts = new IMultiPart[0];
    @Nullable
    protected IParallelHatch parallelHatch = null;
    @Getter
    @DescSynced
    @UpdateListener(methodName = "onPartsUpdated")
    protected BlockPos[] partPositions = new BlockPos[0];
    @Getter
    @DescSynced
    @RequireRerender
    protected boolean isFormed;

    @Getter
    @DescSynced
    protected final boolean[] isFormedsFlipped = new boolean[subPatternAmount];
    @Getter
    @Setter
    @Persisted
    @DescSynced
    protected boolean isFlipped;

    protected boolean simpleLock;

    protected boolean checking;

    protected int waitingTime;

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
            MultiblockWorldSavedData.getOrCreate(serverLevel).addAsyncLogic(this);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (getLevel() instanceof ServerLevel serverLevel) {
            MultiblockWorldSavedData.getOrCreate(serverLevel).removeAsyncLogic(this);
        } else {
            ILevel.getHighlightCache(getLevel()).remove(getPos().asLong());
        }
    }

    @Override
    public void onMachinePlaced(@Nullable LivingEntity player, ItemStack stack) {
        toldNotFormed = true; // newly placed machine won't tell invalid structure
    }

    @Override
    @NotNull
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
        var list = new ObjectArrayList<IMultiPart>();
        for (var pos : newValue) {
            if (getMachine(getLevel(), pos) instanceof IMultiPart part) {
                list.add(part);
            }
        }
        parts = list.toArray(new IMultiPart[0]);
    }

    protected void updatePartPositions() {
        this.partPositions = this.parts.length == 0 ? new BlockPos[0] : Arrays.stream(this.parts).map(part -> part.self().getPos()).toArray(BlockPos[]::new);
    }

    @Override
    public IMultiPart[] getParts() {
        // for the client side, when the chunk unloaded
        if (parts.length != this.partPositions.length) {
            var list = new ObjectArrayList<IMultiPart>();
            for (var pos : this.partPositions) {
                if (getMachine(getLevel(), pos) instanceof IMultiPart part) {
                    list.add(part);
                }
            }
            parts = list.toArray(new IMultiPart[0]);
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
            boolean result = false;
            if (pattern != null) {
                checking = true;
                var state = getMultiblockState();
                state.cleanCache();
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
                        var c = new OpenCacheHashSet<>(state.blockEntityCache);
                        serverLevel.getServer().execute(() -> c.forEach(serverLevel::removeBlockEntity));
                    }
                }
                state.cleanCache();
                for (var subState : subMultiblockState) {
                    if (subState == null) continue;
                    subState.cleanCache();
                }
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
        if ((holder.offset + data.periodID) % 4 == 0 && getLevel() instanceof ServerLevel serverLevel) {
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
                if (sendMessage && getMultiblockState().error != MultiblockState.UNINIT_ERROR && !toldNotFormed && getOwner() != null) {
                    serverLevel.getServer().execute(() -> getOwner().getMembers().forEach(uuid -> {
                        Player p = serverLevel.getPlayerByUUID(uuid);
                        Component m = Component.translatable("gtocore.multiblock.invalid.message",
                                getDefinition().getBlock().getName().withStyle(ChatFormatting.YELLOW),
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

    protected void onStructureFormedAfter() {}

    @Override
    @MustBeInvokedByOverriders
    public void onStructureFormed() {
        if (getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().tell(new TickTask(1, this::onStructureFormedAfter));
        }
        isFormed = true;
        var list = new ObjectArrayList<IMultiPart>();
        for (IMultiPart part : getMultiblockState().getMatchContext().parts) {
            if (shouldAddPartToController(part)) {
                list.add(part);
            }
        }
        parts = list.toArray(new IMultiPart[0]);
        var sorter = getPartSorter();
        if (sorter != null) {
            Arrays.sort(parts, sorter);
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
        Arrays.fill(formeds, false);
        for (IMultiPart part : parts) {
            part.removedFromController(this);
        }
        parallelHatch = null;
        this.parts = new IMultiPart[0];
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
        var list = new ObjectArrayList<>(parts);
        list.removeIf(part -> part.self().isInValid());
        parts = list.toArray(new IMultiPart[0]);
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

    @Override
    public MultiblockControllerMachine self() {
        return this;
    }

    @Override
    public void clientTick() {
        super.clientTick();
        if (getLevel() != null && getOffsetTimer() % 20 == 0) {
            if (!isFormed) {
                ILevel.getHighlightCache(getLevel()).add(getPos().asLong());
            } else {
                ILevel.getHighlightCache(getLevel()).remove(getPos().asLong());
            }
        }
    }
}
