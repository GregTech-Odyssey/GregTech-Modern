package com.gregtechceu.gtceu.api.machine.multiblock;

import com.gregtechceu.gtceu.api.block.ActiveBlock;
import com.gregtechceu.gtceu.api.blockentity.ITickSubscription;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.IParallelHatch;
import com.gregtechceu.gtceu.api.capability.recipe.IFilteredHandler;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.ICleanroomProvider;
import com.gregtechceu.gtceu.api.machine.feature.IMufflableMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IWorkableMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IWorkableMultiPart;
import com.gregtechceu.gtceu.api.machine.trait.INotifiableTrait;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerList;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

import com.lowdragmc.lowdraglib.syncdata.ISubscription;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public abstract class WorkableMultiblockMachine extends MultiblockControllerMachine implements IWorkableMultiController, IMufflableMachine {

    @Nullable
    protected ICleanroomProvider cleanroom;
    @Getter
    @Persisted
    @DescSynced
    public final RecipeLogic recipeLogic;
    @Getter
    protected final GTRecipeType[] recipeTypes;
    @Getter
    @Persisted
    protected int activeRecipeType;

    @Getter
    protected final Map<IO, List<RecipeHandlerList>> capabilitiesProxy;
    @Getter
    protected final Map<IO, Map<RecipeCapability<?>, List<IFilteredHandler>>> capabilitiesFlat;

    @Getter
    protected final Int2ReferenceOpenHashMap<RecipeHandlerList> outputColorMap;

    protected final List<ISubscription> traitSubscriptions;
    @Getter
    @Setter
    @Persisted
    @DescSynced
    protected boolean isMuffled;

    protected RecipeHandlerList currentHandlerList;

    @Getter
    @Setter
    protected List<RecipeHandlerList> inputList;
    @Getter
    @Setter
    protected List<RecipeHandlerList> outputList;

    @Nullable
    protected IParallelHatch parallelHatch = null;

    @Getter
    protected IWorkableMultiPart[] onWorkingPart = new IWorkableMultiPart[0];
    @Getter
    protected IWorkableMultiPart[] beforeWorkingPart = new IWorkableMultiPart[0];
    @Getter
    protected IWorkableMultiPart[] afterWorkingPart = new IWorkableMultiPart[0];
    @Getter
    protected IWorkableMultiPart[] modifyRecipePart = new IWorkableMultiPart[0];

    @Nullable
    protected TickableSubscription activeBlocksSubs;
    @DescSynced
    protected Set<Long> activeBlocks = new LongOpenHashSet();
    @DescSynced
    protected boolean activated;
    protected ActiveBlock.State activeState = ActiveBlock.State.UNKNOWN;

    public WorkableMultiblockMachine(MetaMachineBlockEntity holder, Object... args) {
        super(holder);
        this.recipeTypes = getDefinition().getRecipeTypes();
        this.activeRecipeType = 0;
        this.recipeLogic = createRecipeLogic(args);
        this.capabilitiesProxy = new EnumMap<>(IO.class);
        this.capabilitiesFlat = new EnumMap<>(IO.class);
        this.traitSubscriptions = new ArrayList<>();
        this.outputColorMap = new Int2ReferenceOpenHashMap<>();
        this.inputList = Collections.emptyList();
        this.outputList = Collections.emptyList();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (isRemote()) {
            activeState = ActiveBlock.State.UNKNOWN;
            activeBlocksSubs = subscribeAsyncTick(activeBlocksSubs, () -> {
                if (getLevel() == null) return;
                var shouldActive = isFormed && (activated || getRecipeLogic().isWorking());
                var state = shouldActive ? ActiveBlock.State.ACTIVE : ActiveBlock.State.NON_ACTIVE;
                if (activeState.ordinal() == 0 || state != activeState) {
                    activeState = state;
                    List<Runnable> runnables = new ArrayList<>();
                    for (long pos : activeBlocks) {
                        var blockPos = BlockPos.of(pos);
                        var blockState = getLevel().getBlockState(blockPos);
                        if (blockState.hasProperty(ActiveBlock.ACTIVE)) {
                            var newState = blockState.setValue(ActiveBlock.ACTIVE, shouldActive);
                            if (newState != blockState) {
                                runnables.add(() -> getLevel().setBlock(blockPos, newState, Block.UPDATE_KNOWN_SHAPE));
                            }
                        }
                    }
                    if (!runnables.isEmpty()) Minecraft.getInstance().tell(() -> runnables.forEach(Runnable::run));
                }
            }, 10);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        activeState = ActiveBlock.State.UNKNOWN;
        activeBlocksSubs = ITickSubscription.unsubscribe(activeBlocksSubs);
        traitSubscriptions.forEach(ISubscription::unsubscribe);
        traitSubscriptions.clear();
    }

    //////////////////////////////////////
    // *** Multiblock LifeCycle ***//
    //////////////////////////////////////
    @Override
    protected void onStructureFormedAfter() {
        super.onStructureFormedAfter();
        arrangeHandlerList();
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        clearPartCache();
        var parts = getParts();
        Runnable listener = recipeLogic::updateTickSubscription;
        List<IWorkableMultiPart> onWorkingList = new ArrayList<>();
        List<IWorkableMultiPart> beforeWorkingList = new ArrayList<>();
        List<IWorkableMultiPart> afterWorkingList = new ArrayList<>();
        List<IWorkableMultiPart> modifyRecipeList = new ArrayList<>();
        for (IMultiPart part : parts) {
            if (part instanceof IWorkableMultiPart workablePart) {
                if (workablePart.hasOnWorkingMethod()) onWorkingList.add(workablePart);
                if (workablePart.hasBeforeWorkingMethod()) beforeWorkingList.add(workablePart);
                if (workablePart.hasAfterWorkingMethod()) afterWorkingList.add(workablePart);
                if (workablePart.hasModifyRecipeMethod()) modifyRecipeList.add(workablePart);
                for (var handlerList : workablePart.getRecipeHandlers()) {
                    this.addHandlerList(handlerList);
                }
            }
            if (part instanceof IParallelHatch pHatch) {
                parallelHatch = pHatch;
            }
            for (var trait : part.self().getTraits()) {
                addHandlerAndListener(trait, listener);
            }
        }

        onWorkingPart = onWorkingList.toArray(new IWorkableMultiPart[0]);
        beforeWorkingPart = beforeWorkingList.toArray(new IWorkableMultiPart[0]);
        afterWorkingPart = afterWorkingList.toArray(new IWorkableMultiPart[0]);
        modifyRecipePart = modifyRecipeList.toArray(new IWorkableMultiPart[0]);

        activeBlocks = getMultiblockState().getMatchContext().vaBlocks;

        // attach self traits
        Map<IO, List<IRecipeHandler<?>>> ioTraits = new EnumMap<>(IO.class);
        for (MachineTrait trait : getTraits()) {
            if (trait instanceof IRecipeHandler<?> handlerTrait && handlerTrait.isAvailable()) {
                ioTraits.computeIfAbsent(handlerTrait.getHandlerIO(), i -> new ArrayList<>()).add(handlerTrait);
            }
            addHandlerAndListener(trait, listener);
        }
        for (var entry : ioTraits.entrySet()) {
            var handlerList = RecipeHandlerList.of(entry.getKey(), entry.getValue());
            this.addHandlerList(handlerList);
        }
    }

    @Override
    public void onStructureInvalid() {
        updateActiveBlock(false);
        super.onStructureInvalid();
        clearPartCache();
        onWorkingPart = new IWorkableMultiPart[0];
        beforeWorkingPart = new IWorkableMultiPart[0];
        afterWorkingPart = new IWorkableMultiPart[0];
        modifyRecipePart = new IWorkableMultiPart[0];
        // reset recipe Logic
        recipeLogic.resetRecipeLogic();
    }

    protected void clearPartCache() {
        parallelHatch = null;
        currentHandlerList = null;
        inputList = Collections.emptyList();
        outputList = Collections.emptyList();
        capabilitiesProxy.clear();
        capabilitiesFlat.clear();
        outputColorMap.clear();
        traitSubscriptions.forEach(ISubscription::unsubscribe);
        traitSubscriptions.clear();
    }

    protected void addHandlerAndListener(MachineTrait trait, Runnable listener) {
        if (trait instanceof IFilteredHandler handler) {
            addHandler(handler);
        }
        INotifiableTrait.addListener(trait, listener, traitSubscriptions::add);
    }

    //////////////////////////////////////
    // ****** RECIPE LOGIC *******//
    //////////////////////////////////////

    @Nullable
    @Override
    public final GTRecipe doModifyRecipe(GTRecipe recipe) {
        for (var part : modifyRecipePart) {
            recipe = part.modifyRecipe(this, recipe);
            if (recipe == null) return null;
        }
        return getRealRecipe(recipe);
    }

    @Nullable
    protected GTRecipe getRealRecipe(GTRecipe recipe) {
        return definition.getRecipeModifier().applyModifier(this, recipe);
    }

    @Override
    public boolean isRecipeLogicAvailable() {
        return isFormed;
    }

    @Override
    public void afterWorking() {
        for (var part : afterWorkingPart) {
            part.afterWorking(this);
        }
        IWorkableMultiController.super.afterWorking();
    }

    @Override
    public boolean beforeWorking(GTRecipe recipe) {
        for (var part : beforeWorkingPart) {
            if (!part.beforeWorking(this, recipe)) {
                return false;
            }
        }
        return IWorkableMultiController.super.beforeWorking(recipe);
    }

    @Override
    public boolean onWorking() {
        for (var part : onWorkingPart) {
            if (!part.onWorking(this)) {
                return false;
            }
        }
        return IWorkableMultiController.super.onWorking();
    }

    @Override
    public void onWaiting() {
        for (var part : getParts()) {
            if (part instanceof IWorkableMultiPart workableMultiPart) workableMultiPart.onWaiting(this);
        }
        IWorkableMultiController.super.onWaiting();
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        if (!isWorkingAllowed) {
            for (var part : getParts()) {
                if (part instanceof IWorkableMultiPart workableMultiPart) workableMultiPart.onPaused(this);
            }
        }
        IWorkableMultiController.super.setWorkingEnabled(isWorkingAllowed);
    }

    @Override
    @Nullable
    public IParallelHatch getParallelHatch() {
        return parallelHatch;
    }

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

    @Override
    public RecipeHandlerList getCurrentHandlerList() {
        return currentHandlerList;
    }

    @Override
    public void setCurrentHandlerList(RecipeHandlerList list) {
        this.currentHandlerList = list;
    }

    @Override
    public boolean regressWhenWaiting() {
        return !getDefinition().isGenerator();
    }
}
