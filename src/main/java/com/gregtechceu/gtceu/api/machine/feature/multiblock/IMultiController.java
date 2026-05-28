package com.gregtechceu.gtceu.api.machine.feature.multiblock;

import com.gregtechceu.gtceu.api.machine.feature.IMachineFeature;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldData;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.common.network.GTNetwork;
import com.gregtechceu.gtceu.common.network.packets.SCPacketUpdateActiveBlock;
import com.gregtechceu.gtceu.utils.TaskHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

public interface IMultiController extends IMachineFeature {

    @Override
    MultiblockControllerMachine self();

    void requestCheck();

    void setWaitingTime(int time);

    int getWaitingTime();

    boolean checking();

    default boolean requiresServerCheck() {
        return false;
    }

    default boolean hasCheckButton() {
        return false;
    }

    default int checkPriority() {
        return self().getDefinition().checkPriority();
    }

    boolean @NotNull [] getSubFormed();

    int getSubPatternAmount();

    int getSubFormedAmount();

    /**
     * Check MultiBlock Pattern. Just checking pattern without any other logic.
     * You can override it but it's unsafe for calling. because it will also be called in an async thread.
     * <br>
     * you should always use {@link IMultiController#checkPatternWithLock()} and
     * {@link IMultiController#checkPatternWithTryLock()} instead.
     *
     * @return whether it can be formed.
     */
    boolean checkPattern();

    /**
     * Check pattern with a lock.
     */
    default boolean checkPatternWithLock() {
        var lock = getPatternLock();
        lock.lock();
        try {
            return checkPattern();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Check pattern with a try lock
     *
     * @return false - checking failed or cant get the lock.
     */
    default boolean checkPatternWithTryLock() {
        var lock = getPatternLock();
        if (lock.tryLock()) {
            try {
                return checkPattern();
            } finally {
                lock.unlock();
            }
        } else {
            return false;
        }
    }

    /**
     * Get structure pattern.
     * You can override it to create dynamic patterns.
     */
    default BlockPattern getPattern() {
        return self().getDefinition().getPatternFactory().get();
    }

    default Supplier<BlockPattern>[] getSubPattern() {
        return self().getDefinition().getSubPatternFactory();
    }

    /**
     * Whether Multiblock Formed.
     * <br>
     * NOTE: even machine is formed, it doesn't mean to workable!
     * Its parts maybe invalid due to chunk unload.
     */
    boolean isFormed();

    /**
     * Get MultiblockState. It records all structure-related information.
     */
    @NotNull
    MultiblockState getMultiblockState();

    @NotNull
    MultiblockState[] getSubMultiblockState();

    /**
     * Called in an async thread. It's unsafe, Don't modify anything of world but checking information.
     * It will be called per 10 tick.
     *
     */
    void asyncCheckPattern(MultiblockWorldData data);

    /**
     * Called when structure is formed, have to be called after {@link #checkPattern()}. (server-side / fake scene only)
     * <br>
     * Trigger points:
     * <br>
     * 1 - Blocks in structure changed but still formed.
     * <br>
     * 2 - Literally, structure formed.
     */
    void onStructureFormed();

    /**
     * Called when structure is invalid. (server-side / fake scene only)
     * <br>
     * Trigger points:
     * <br>
     * 1 - Blocks in structure changed.
     * <br>
     * 2 - Before controller machine removed.
     */
    void onStructureInvalid();

    /**
     * Whether it has front face.
     * false means structure of all sides are available.
     */
    boolean hasFrontFacing();

    /**
     * Get all parts
     */
    IMultiPart[] getParts();

    /**
     * Get lock for pattern checking.
     */
    Lock getPatternLock();

    /**
     * should add part to the part list.
     */
    default boolean shouldAddPartToController(IMultiPart part) {
        return true;
    }

    /**
     * get parts' Appearance. same as IForgeBlock.getAppearance() / IFabricBlock.getAppearance()
     */
    @Nullable
    default BlockState getPartAppearance(IMultiPart part, Direction side, BlockState sourceState, BlockPos sourcePos) {
        if (isFormed()) {
            return self().getDefinition().getPartAppearance().apply(this, part, side);
        }
        return null;
    }

    default @Nullable Comparator<IMultiPart> getPartSorter() {
        return null;
    }

    default void updateActiveBlock(boolean active) {
        if (self().getLevel() instanceof ServerLevel serverLevel) {
            var vaBlocks = getMultiblockState().matchContext.get(Predicates.DataKey.ACTIVE_BLOCKS);
            if (vaBlocks == null || vaBlocks.isEmpty()) return;
            TaskHandler.enqueueTask(serverLevel, () -> GTNetwork.NETWORK.sendToAll(new SCPacketUpdateActiveBlock(vaBlocks, active)), 0);
        }
    }
}
