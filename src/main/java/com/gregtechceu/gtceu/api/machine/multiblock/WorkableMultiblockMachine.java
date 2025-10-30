package com.gregtechceu.gtceu.api.machine.multiblock;

import com.gregtechceu.gtceu.api.block.ActiveBlock;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.ICleanroomProvider;
import com.gregtechceu.gtceu.api.machine.feature.IMufflableMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IWorkableMultiController;
import com.gregtechceu.gtceu.api.machine.trait.IRecipeHandlerTrait;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerList;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

import com.lowdragmc.lowdraglib.syncdata.ISubscription;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public abstract class WorkableMultiblockMachine extends MultiblockControllerMachine implements IWorkableMultiController, IMufflableMachine {

    @Nullable
    protected ICleanroomProvider cleanroom;
    @Persisted
    @DescSynced
    public final RecipeLogic recipeLogic;
    protected final GTRecipeType[] recipeTypes;
    @Persisted
    protected int activeRecipeType;
    protected final Map<IO, List<RecipeHandlerList>> capabilitiesProxy;
    protected final Map<IO, Map<RecipeCapability<?>, List<IRecipeHandler<?>>>> capabilitiesFlat;
    protected final List<ISubscription> traitSubscriptions;
    @Persisted
    @DescSynced
    protected boolean isMuffled;
    protected boolean previouslyMuffled = true;
    @DescSynced
    protected final Set<Long> activeBlocks = new LongOpenHashSet();
    protected RecipeHandlerList currentHandlerList;

    protected IMultiPart[] onWorkings = new IMultiPart[0];

    @DescSynced
    protected boolean activated;
    @DescSynced
    protected ActiveBlock.State activeState = ActiveBlock.State.UNKNOWN;

    public WorkableMultiblockMachine(MetaMachineBlockEntity holder, Object... args) {
        super(holder);
        this.recipeTypes = getDefinition().getRecipeTypes();
        this.activeRecipeType = 0;
        this.recipeLogic = createRecipeLogic(args);
        this.capabilitiesProxy = new EnumMap<>(IO.class);
        this.capabilitiesFlat = new EnumMap<>(IO.class);
        this.traitSubscriptions = new ObjectArrayList<>();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (isRemote()) activeState = ActiveBlock.State.UNKNOWN;
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (isRemote()) activeState = ActiveBlock.State.UNKNOWN;
        traitSubscriptions.forEach(ISubscription::unsubscribe);
        traitSubscriptions.clear();
    }

    //////////////////////////////////////
    // *** Multiblock LifeCycle ***//
    //////////////////////////////////////
    @Override
    protected void onStructureFormedAfter() {
        recipeLogic.updateTickSubscription();
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        onWorkings = Arrays.stream(getParts()).filter(IMultiPart::hasOnWorkingMethod).toList().toArray(new IMultiPart[0]);
        activeState = ActiveBlock.State.UNKNOWN;
        // attach parts' traits
        activeBlocks.clear();
        activeBlocks.addAll(getMultiblockState().getMatchContext().vaBlocks);
        capabilitiesProxy.clear();
        capabilitiesFlat.clear();
        traitSubscriptions.forEach(ISubscription::unsubscribe);
        traitSubscriptions.clear();
        for (IMultiPart part : getParts()) {
            for (var handlerList : part.getRecipeHandlers()) {
                this.addHandlerList(handlerList);
                traitSubscriptions.add(handlerList.subscribe(recipeLogic::updateTickSubscription));
            }
        }
        // attach self traits
        Map<IO, List<IRecipeHandler<?>>> ioTraits = new EnumMap<>(IO.class);
        for (MachineTrait trait : getTraits()) {
            if (trait instanceof IRecipeHandlerTrait<?> handlerTrait && handlerTrait.isAvailable() && handlerTrait.getHandlerIO() != IO.NONE) {
                ioTraits.computeIfAbsent(handlerTrait.getHandlerIO(), i -> new ObjectArrayList<>()).add(handlerTrait);
            }
        }
        for (var entry : ioTraits.entrySet()) {
            var handlerList = RecipeHandlerList.of(entry.getKey(), entry.getValue());
            this.addHandlerList(handlerList);
            traitSubscriptions.add(handlerList.subscribe(recipeLogic::updateTickSubscription));
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        onWorkings = new IMultiPart[0];
        activeState = ActiveBlock.State.UNKNOWN;
        activeBlocks.clear();
        capabilitiesProxy.clear();
        capabilitiesFlat.clear();
        traitSubscriptions.forEach(ISubscription::unsubscribe);
        traitSubscriptions.clear();
        // reset recipe Logic
        recipeLogic.resetRecipeLogic();
    }

    @Override
    public void onPartUnload() {
        super.onPartUnload();
        activeBlocks.clear();
        capabilitiesProxy.clear();
        capabilitiesFlat.clear();
        traitSubscriptions.forEach(ISubscription::unsubscribe);
        traitSubscriptions.clear();
        // fine some parts invalid now.
        // but we shouldn't reset recipe logic rn.
        // if it's due to chunk unload, we should just wait for it to be valid again.
        recipeLogic.updateTickSubscription();
    }

    //////////////////////////////////////
    // ****** RECIPE LOGIC *******//
    //////////////////////////////////////
    @Override
    public void clientTick() {
        super.clientTick();
        if (previouslyMuffled != isMuffled) {
            previouslyMuffled = isMuffled;
            if (recipeLogic != null) recipeLogic.updateSound();
        }
        var shouldActive = activated || (isFormed && getRecipeLogic().isWorking());
        var state = shouldActive ? ActiveBlock.State.ACTIVE : ActiveBlock.State.NON_ACTIVE;
        if (activeState.ordinal() == 0 || state != activeState) {
            activeState = state;
            updateActiveBlocks(shouldActive);
        }
    }

    @Nullable
    @Override
    public final GTRecipe doModifyRecipe(GTRecipe recipe) {
        for (IMultiPart part : getParts()) {
            recipe = part.modifyRecipe(recipe);
            if (recipe == null) return null;
        }
        return getRealRecipe(recipe);
    }

    @Nullable
    protected GTRecipe getRealRecipe(GTRecipe recipe) {
        return self().getDefinition().getRecipeModifier().applyModifier(self(), recipe);
    }

    @OnlyIn(Dist.CLIENT)
    protected void updateActiveBlocks(boolean active) {
        for (long pos : activeBlocks) {
            var blockPos = BlockPos.of(pos);
            var blockState = getLevel().getBlockState(blockPos);
            if (blockState.hasProperty(ActiveBlock.ACTIVE)) {
                var newState = blockState.setValue(ActiveBlock.ACTIVE, active);
                if (newState != blockState) {
                    getLevel().setBlock(blockPos, newState, Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
    }

    @Override
    public boolean isRecipeLogicAvailable() {
        return isFormed;
    }

    @Override
    public void afterWorking() {
        for (var part : getParts()) {
            part.afterWorking(this);
        }
        IWorkableMultiController.super.afterWorking();
    }

    @Override
    public boolean beforeWorking(@Nullable GTRecipe recipe) {
        for (IMultiPart part : getParts()) {
            if (!part.beforeWorking(this)) {
                return false;
            }
        }
        return IWorkableMultiController.super.beforeWorking(recipe);
    }

    @Override
    public boolean onWorking() {
        for (IMultiPart part : onWorkings) {
            if (!part.onWorking(this)) {
                return false;
            }
        }
        return IWorkableMultiController.super.onWorking();
    }

    @Override
    public void onWaiting() {
        for (var part : getParts()) {
            part.onWaiting(this);
        }
        IWorkableMultiController.super.onWaiting();
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        if (!isWorkingAllowed) {
            for (var part : getParts()) {
                part.onPaused(this);
            }
        }
        IWorkableMultiController.super.setWorkingEnabled(isWorkingAllowed);
    }

    @NotNull
    public GTRecipeType getRecipeType() {
        return recipeTypes[Math.min(recipeTypes.length - 1, activeRecipeType)];
    }

    @Override
    public void setActiveRecipeType(final int activeRecipeType) {
        if (this.activeRecipeType != activeRecipeType) {
            getRecipeLogic().markLastRecipeDirty();
            getRecipeLogic().updateTickSubscription();
        }
        this.activeRecipeType = activeRecipeType;
    }

    @Nullable
    public ICleanroomProvider getCleanroom() {
        return this.cleanroom;
    }

    public void setCleanroom(@Nullable final ICleanroomProvider cleanroom) {
        this.cleanroom = cleanroom;
    }

    public @NotNull RecipeLogic getRecipeLogic() {
        return this.recipeLogic;
    }

    public GTRecipeType[] getRecipeTypes() {
        return this.recipeTypes;
    }

    public int getActiveRecipeType() {
        return this.activeRecipeType;
    }

    @Override
    public @Nullable RecipeHandlerList getCurrentHandlerList() {
        return currentHandlerList;
    }

    @Override
    public void setCurrentHandlerList(RecipeHandlerList list, GTRecipe recipe) {
        this.currentHandlerList = list;
    }

    public @NotNull Map<IO, List<RecipeHandlerList>> getCapabilitiesProxy() {
        return this.capabilitiesProxy;
    }

    public @NotNull Map<IO, Map<RecipeCapability<?>, List<IRecipeHandler<?>>>> getCapabilitiesFlat() {
        return this.capabilitiesFlat;
    }

    public boolean isMuffled() {
        return this.isMuffled;
    }

    public void setMuffled(final boolean isMuffled) {
        this.isMuffled = isMuffled;
    }

    @Override
    public boolean regressWhenWaiting() {
        return !getDefinition().isGenerator();
    }
}
