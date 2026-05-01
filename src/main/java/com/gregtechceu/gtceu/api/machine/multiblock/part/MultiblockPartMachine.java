package com.gregtechceu.gtceu.api.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import com.gto.datasynclib.annotations.SyncToClient;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.MustBeInvokedByOverriders;

import java.util.Collections;
import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MultiblockPartMachine extends MetaMachine implements IMultiPart {

    @SyncToClient(listener = "onControllersUpdated", notifyUpdate = true)
    protected final LongSet controllerPositions = new LongOpenHashSet(1);
    protected final Set<IMultiController> controllers = Collections.synchronizedSet(new ReferenceOpenHashSet<>());

    public MultiblockPartMachine(MetaMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public boolean hasController(BlockPos controllerPos) {
        return controllerPositions.contains(controllerPos.asLong());
    }

    @Override
    public boolean isFormed() {
        return !controllerPositions.isEmpty();
    }

    @Override
    @MustBeInvokedByOverriders
    public void onRotated(Direction oldFacing, Direction newFacing) {
        super.onRotated(oldFacing, newFacing);
        for (var controller : controllers) {
            controller.requestCheck();
        }
    }

    // Not sure if necessary, but added to match the Controller class
    @SuppressWarnings("unused")
    public synchronized void onControllersUpdated(LongSet newPositions, LongSet old) {
        controllers.clear();
        for (var pos : newPositions) {
            if (MetaMachine.getMachine(getLevel(), BlockPos.of(pos)) instanceof IMultiController controller) {
                controllers.add(controller);
            }
        }
    }

    @Override
    public Set<IMultiController> getControllers() {
        // Necessary to rebuild the set of controllers on client-side
        if (controllers.size() != controllerPositions.size()) {
            onControllersUpdated(controllerPositions, LongSets.emptySet());
        }
        return controllers;
    }

    @Override
    @MustBeInvokedByOverriders
    public void onUnload() {
        super.onUnload();
        if (getLevel() instanceof ServerLevel serverLevel) {
            for (IMultiController controller : controllers) {
                if (serverLevel.isLoaded(controller.self().getPos())) {
                    removedFromController(controller);
                    controller.requestCheck();
                }
            }
        }
        controllerPositions.clear();
        controllers.clear();
    }

    //////////////////////////////////////
    // *** Multiblock LifeCycle ***//
    //////////////////////////////////////

    @Override
    @MustBeInvokedByOverriders
    public void removedFromController(IMultiController controller) {
        controllerPositions.remove(controller.self().getPos().asLong());
        controllers.remove(controller);
        clearDirectionCache();
        requestSync();
    }

    @Override
    @MustBeInvokedByOverriders
    public void addedToController(IMultiController controller) {
        controllerPositions.add(controller.self().getPos().asLong());
        controllers.add(controller);
        clearDirectionCache();
        requestSync();
    }
}
