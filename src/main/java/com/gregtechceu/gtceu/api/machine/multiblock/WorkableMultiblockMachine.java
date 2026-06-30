package com.gregtechceu.gtceu.api.machine.multiblock;

import com.gregtechceu.gtceu.api.block.ActiveBlock;
import com.gregtechceu.gtceu.api.blockentity.ITickSubscription;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.IParallelHatch;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.ICleanroomProvider;
import com.gregtechceu.gtceu.api.machine.feature.IMufflableMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IWorkableMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IWorkableMultiPart;
import com.gregtechceu.gtceu.api.machine.trait.IRecipeHandlerTrait;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandler;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.core.ILevel;

import com.lowdragmc.lowdraglib.syncdata.ISubscription;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

import com.gto.datasynclib.annotations.Access;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
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
    @SaveToDisk
    @SyncToClient
    public final RecipeLogic recipeLogic;
    @Setter
    @Getter
    @Nullable
    @SyncToClient
    protected GTRecipeType[] availableRecipeTypesCache;
    @Getter
    @SaveToDisk(defaultValue = "0")
    protected int activeRecipeType;

    protected boolean recipeLogicAvailable;

    @Getter
    protected final Map<IO, List<RecipeHandlerUnit>> capabilitiesProxy;
    @Getter
    protected final Map<IO, List<IRecipeHandler>> capabilitiesFlat;

    @Getter
    protected final Int2ReferenceOpenHashMap<RecipeHandlerUnit> outputColorMap;

    protected final List<ISubscription> traitSubscriptions;
    @Getter
    @Setter
    @SaveToDisk(defaultValue = "false")
    @SyncToClient
    protected boolean isMuffled;

    @Getter
    @Setter
    protected List<RecipeHandlerUnit> inputUnits;
    @Getter
    @Setter
    protected List<RecipeHandlerUnit> outputUnits;

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

    @SyncToClient(autoUpdate = false, listener = "onActiveBlocksUpdate")
    @Access
    protected LongSet activeBlocks = new LongOpenHashSet();

    @SyncToClient
    protected boolean activated;
    protected ActiveBlock.State activeState = ActiveBlock.State.UNKNOWN;

    public WorkableMultiblockMachine(MetaMachineBlockEntity holder, Object... args) {
        super(holder);
        this.recipeLogic = createRecipeLogic(args);
        this.capabilitiesProxy = new EnumMap<>(IO.class);
        this.capabilitiesFlat = new EnumMap<>(IO.class);
        this.traitSubscriptions = new ArrayList<>();
        this.outputColorMap = new Int2ReferenceOpenHashMap<>();
        this.inputUnits = Collections.emptyList();
        this.outputUnits = Collections.emptyList();
    }

    @Override
    public void onUnload() {
        super.onUnload();
        activeState = ActiveBlock.State.UNKNOWN;
        activeBlocksSubs = ITickSubscription.unsubscribe(activeBlocksSubs);
        traitSubscriptions.forEach(ISubscription::unsubscribe);
        traitSubscriptions.clear();
    }

    @SuppressWarnings("unused")
    protected void onActiveBlocksUpdate(LongSet newValue, LongSet oldValue) {
        activeState = ActiveBlock.State.UNKNOWN;
        if (newValue.isEmpty()) {
            activeBlocksSubs = ITickSubscription.unsubscribe(activeBlocksSubs);
        } else {
            activeBlocksSubs = subscribeAsyncTick(activeBlocksSubs, () -> {
                if (getLevel() == null) return;
                var shouldActive = isFormed && (activated || getRecipeLogic().isWorking());
                var state = shouldActive ? ActiveBlock.State.ACTIVE : ActiveBlock.State.NON_ACTIVE;
                if (activeState.ordinal() == 0 || state != activeState) {
                    activeState = state;
                    List<Runnable> runnables = new ArrayList<>();
                    for (long pos : activeBlocks) {
                        var blockPos = BlockPos.of(pos);
                        var blockState = ILevel.asyncGetBlockState(getLevel(), blockPos);
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
    public void onStructureFormedClient() {
        activeState = ActiveBlock.State.UNKNOWN;
    }

    //////////////////////////////////////
    // *** Multiblock LifeCycle ***//
    //////////////////////////////////////
    @Override
    protected void onStructureFormedAfter() {
        super.onStructureFormedAfter();
        arrangeHandlerList();
        markFieldsForSync("activeBlocks");
        recipeLogicAvailable = true;
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        availableRecipeTypesCache = null;
        var parts = getParts();
        List<IWorkableMultiPart> workableMultiPart = new ArrayList<>();
        List<IWorkableMultiPart> onWorkingList = new ArrayList<>();
        List<IWorkableMultiPart> beforeWorkingList = new ArrayList<>();
        List<IWorkableMultiPart> afterWorkingList = new ArrayList<>();
        List<IWorkableMultiPart> modifyRecipeList = new ArrayList<>();
        for (IMultiPart part : parts) {
            if (part instanceof IWorkableMultiPart workablePart) {
                workableMultiPart.add(workablePart);
                if (workablePart.hasOnWorkingMethod()) onWorkingList.add(workablePart);
                if (workablePart.hasBeforeWorkingMethod()) beforeWorkingList.add(workablePart);
                if (workablePart.hasAfterWorkingMethod()) afterWorkingList.add(workablePart);
                if (workablePart.hasModifyRecipeMethod()) modifyRecipeList.add(workablePart);
            }
            if (part instanceof IParallelHatch pHatch) {
                parallelHatch = pHatch;
            }
        }

        onWorkingPart = onWorkingList.toArray(new IWorkableMultiPart[0]);
        beforeWorkingPart = beforeWorkingList.toArray(new IWorkableMultiPart[0]);
        afterWorkingPart = afterWorkingList.toArray(new IWorkableMultiPart[0]);
        modifyRecipePart = modifyRecipeList.toArray(new IWorkableMultiPart[0]);

        activeBlocks = getMultiblockState().getMatchContext().getOrDefault(Predicates.DataKey.ACTIVE_BLOCKS, LongSets.EMPTY_SET);

        // attach parts' traits
        capabilitiesProxy.clear();
        capabilitiesFlat.clear();
        traitSubscriptions.forEach(ISubscription::unsubscribe);
        traitSubscriptions.clear();
        for (var part : workableMultiPart) {
            for (var handlerList : part.getRecipeHandlers()) {
                this.addHandlerList(handlerList);
                traitSubscriptions.add(handlerList.subscribe(recipeLogic::updateTickSubscription));
            }
        }
        // attach self traits
        Map<IO, List<IRecipeHandler>> ioTraits = new EnumMap<>(IO.class);
        for (MachineTrait trait : getTraits()) {
            if (trait instanceof IRecipeHandlerTrait handlerTrait && handlerTrait.isAvailable() && handlerTrait.getHandlerIO() != IO.NONE) {
                ioTraits.computeIfAbsent(handlerTrait.getHandlerIO(), i -> new ArrayList<>()).add(handlerTrait);
            }
        }
        for (var entry : ioTraits.entrySet()) {
            var handlerList = RecipeHandlerUnit.of(entry.getKey(), entry.getValue());
            this.addHandlerList(handlerList);
            traitSubscriptions.add(handlerList.subscribe(recipeLogic::updateTickSubscription));
        }
    }

    @Override
    public void onStructureInvalid() {
        availableRecipeTypesCache = null;
        recipeLogicAvailable = false;
        markFieldsForSync("activeBlocks");
        updateActiveBlock(false);
        super.onStructureInvalid();
        parallelHatch = null;
        onWorkingPart = new IWorkableMultiPart[0];
        beforeWorkingPart = new IWorkableMultiPart[0];
        afterWorkingPart = new IWorkableMultiPart[0];
        modifyRecipePart = new IWorkableMultiPart[0];
        capabilitiesProxy.clear();
        capabilitiesFlat.clear();
        outputColorMap.clear();
        traitSubscriptions.forEach(ISubscription::unsubscribe);
        traitSubscriptions.clear();
        inputUnits = Collections.emptyList();
        outputUnits = Collections.emptyList();
        // reset recipe Logic
        recipeLogic.resetRecipeLogic();
    }

    //////////////////////////////////////
    // ****** RECIPE LOGIC *******//
    //////////////////////////////////////

    @Nullable
    @Override
    public final GTRecipe doModifyRecipe(RecipeHandlerUnit unit, GTRecipe recipe) {
        for (var part : modifyRecipePart) {
            recipe = part.modifyRecipe(this, unit, recipe);
            if (recipe == null) return null;
        }
        return getRealRecipe(unit, recipe);
    }

    @Nullable
    protected GTRecipe getRealRecipe(RecipeHandlerUnit unit, GTRecipe recipe) {
        return definition.getRecipeModifier().applyModifier(this, unit, recipe);
    }

    @Override
    public boolean isRecipeLogicAvailable() {
        return recipeLogicAvailable;
    }

    @Override
    public void afterWorking() {
        for (var part : afterWorkingPart) {
            part.afterWorking(this);
        }
        IWorkableMultiController.super.afterWorking();
    }

    @Override
    public void beforeWorking(RecipeHandlerUnit unit, GTRecipe recipe) {
        for (var part : beforeWorkingPart) {
            part.beforeWorking(this, unit, recipe);
        }
        IWorkableMultiController.super.beforeWorking(unit, recipe);
    }

    @Override
    public void onWorking() {
        for (var part : onWorkingPart) {
            part.onWorking(this);
        }
        IWorkableMultiController.super.onWorking();
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
        getRecipeLogic().markLastRecipeDirty();
        getRecipeLogic().updateTickSubscription();
    }

    @Override
    public boolean regressWhenWaiting() {
        return !getDefinition().isGenerator();
    }
}
