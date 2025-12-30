package com.gregtechceu.gtceu.api.machine.feature.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import it.unimi.dsi.fastutil.objects.ReferenceSets;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public interface IMultiModule<T extends IMultiController> extends IMultiPart {

    T getController();

    void setController(T controller);

    @Override
    default boolean isFormed() {
        return getController() != null;
    }

    @Override
    default Set<IMultiController> getControllers() {
        return ReferenceSets.singleton(getController());
    }

    @Override
    default void removedFromController(@NotNull IMultiController controller) {
        setController(null);
        self().clearDirectionCache();
        self().requestSync();
    }

    @Override
    default void addedToController(@NotNull IMultiController controller) {
        setController((T) controller);
        self().clearDirectionCache();
        self().requestSync();
    }

    @Override
    default boolean canShared() {
        return false;
    }

    @Override
    default boolean hasController(BlockPos controllerPos) {
        return false;
    }

    @Override
    default boolean replacePartModelWhenFormed() {
        return false;
    }

    @Override
    default BlockState getFormedAppearance(BlockState sourceState, BlockPos sourcePos, Direction side) {
        return null;
    }
}
