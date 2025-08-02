package com.gregtechceu.gtceu.api.machine.feature.multiblock;

import com.gregtechceu.gtceu.api.capability.IParallelHatch;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineFeature;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;
import com.gregtechceu.gtceu.client.renderer.MultiblockInWorldPreviewRenderer;
import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;

public interface IMultiController extends IMachineFeature, IInteractedMachine {

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

    default List<BlockPattern> getSubPattern() {
        return Collections.emptyList();
    }

    default List<MultiblockState> getSubMultiblockState() {
        return Collections.emptyList();
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

    /**
     * Called in an async thread. It's unsafe, Don't modify anything of world but checking information.
     * It will be called per 10 tick.
     *
     */
    void asyncCheckPattern(MultiblockWorldSavedData data);

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
    List<IMultiPart> getParts();

    /**
     * The instance of {@link IParallelHatch} attached to this Controller.
     * <p>
     * Note that this will return a singular instance, and will not account for multiple attached IParallelHatches
     * 
     * @return an {@link Optional} of the attached IParallelHatch, empty if one is not attached
     */
    Optional<IParallelHatch> getParallelHatch();

    /**
     *
     * @return Whether batching is enabled on this multiblock
     */
    default boolean isBatchEnabled() {
        return false;
    }

    /**
     * Called from part, when part is invalid due to chunk unload or broken.
     */
    void onPartUnload();

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

    /**
     * Show the preview of structure.
     */
    @Override
    default InteractionResult onUse(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand,
                                    BlockHitResult hit) {
        if (!self().isFormed() && player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            if (world.isClientSide()) {
                MultiblockInWorldPreviewRenderer.showPreview(pos, self(),
                        ConfigHolder.INSTANCE.client.inWorldPreviewDuration * 20);
            }
            return InteractionResult.SUCCESS;
        }
        return IInteractedMachine.super.onUse(state, world, pos, player, hand, hit);
    }
}
