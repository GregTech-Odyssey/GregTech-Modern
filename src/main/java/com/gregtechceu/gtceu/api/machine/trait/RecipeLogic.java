package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.IWorkable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.sound.AutoReleasedSound;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.UpdateListener;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.StreamSupport;

import static com.gregtechceu.gtceu.common.data.GTRecipeTypes.COMBINED_RECIPES;

public class RecipeLogic extends MachineTrait implements IWorkable, IFancyTooltip {

    public enum Status {
        IDLE,
        WORKING,
        WAITING,
        SUSPEND
    }

    public static int SEARCH_MAX_INTERVAL = 20;

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(RecipeLogic.class);
    public final IRecipeLogicMachine machine;
    @Persisted
    @DescSynced
    @UpdateListener(methodName = "onStatusSynced")
    protected Status status = Status.IDLE;
    @Persisted
    @DescSynced
    @UpdateListener(methodName = "onActiveSynced")
    protected boolean isActive;
    @Nullable
    @Persisted
    @DescSynced
    protected Component waitingReason = null;
    /**
     * unsafe, it may not be found from {@link RecipeManager}. Do not index it.
     */
    @Nullable
    @Persisted
    protected GTRecipe lastRecipe;
    /**
     * safe, it is the origin recipe before {@link IRecipeLogicMachine#fullModifyRecipe(GTRecipe)}'
     * which can be found
     * from {@link RecipeManager}.
     */
    @Nullable
    @Persisted
    protected GTRecipe lastOriginRecipe;
    @Persisted
    public int progress;
    @Persisted
    protected int duration;
    protected boolean recipeDirty = true;
    @Persisted
    protected long totalContinuousRunningTime;
    @Persisted
    protected boolean suspendAfterFinish = false;
    protected final Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches = makeChanceCaches();
    protected TickableSubscription subscription;
    protected Object workingSound;
    protected int interval = 5;

    public RecipeLogic(IRecipeLogicMachine machine) {
        super(machine.self());
        this.machine = machine;
    }

    @OnlyIn(Dist.CLIENT)
    @SuppressWarnings("unused")
    protected void onStatusSynced(Status newValue, Status oldValue) {
        getMachine().scheduleRenderUpdate();
        machine.notifyStatusChanged(this.status, status);
        updateSound();
    }

    @OnlyIn(Dist.CLIENT)
    @SuppressWarnings("unused")
    protected void onActiveSynced(boolean newActive, boolean oldActive) {
        getMachine().scheduleRenderUpdate();
    }

    @Override
    public void scheduleRenderUpdate() {
        getMachine().scheduleRenderUpdate();
    }

    /**
     * Call it to abort current recipe and reset the first state.
     */
    public void resetRecipeLogic() {
        recipeDirty = false;
        lastRecipe = null;
        lastOriginRecipe = null;
        progress = 0;
        duration = 0;
        isActive = false;
        if (status != Status.SUSPEND) {
            setStatus(Status.IDLE);
        }
        updateTickSubscription();
    }

    @Override
    public void onMachineLoad() {
        super.onMachineLoad();
        if (getMachine().getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().tell(new TickTask(1, this::updateTickSubscription));
        }
    }

    @Override
    public void onMachineUnLoad() {
        super.onMachineUnLoad();
        unsubscribe();
    }

    public void unsubscribe() {
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
    }

    public void updateTickSubscription() {
        if (isSuspend() || !machine.isRecipeLogicAvailable()) {
            unsubscribe();
        } else {
            subscription = getMachine().subscribeServerTick(subscription, this::serverTick);
        }
    }

    public double getProgressPercent() {
        return duration == 0 ? 0.0 : progress / (duration * 1.0);
    }

    public void serverTick() {
        if (isSuspend()) {
            unsubscribe();
        } else if (!isIdle() && lastRecipe != null) {
            if (progress < duration) {
                handleRecipeWorking();
            } else {
                machine.onRecipeFinish();
                onRecipeFinish();
            }
        } else if (getMachine().getOffsetTimer() % interval == 0) {
            recipeDirty = false;
            findAndHandleRecipe();
            if (lastRecipe == null) {
                if (interval < SEARCH_MAX_INTERVAL) {
                    interval <<= 1;
                }
                if (!machine.keepSubscribing()) unsubscribe();
            }
        }
    }

    protected boolean matchRecipe(GTRecipe recipe) {
        return RecipeHelper.matchContents(machine, recipe);
    }

    protected boolean checkConditions(GTRecipe recipe) {
        return RecipeHelper.checkConditions(recipe, this).isSuccess();
    }

    public boolean checkMatchedRecipeAvailable(GTRecipe match) {
        var modified = machine.fullModifyRecipe(match.copy());
        if (modified != null) {
            if (matchRecipe(modified)) {
                setupRecipe(modified);
            }
            if (lastRecipe != null && status == RecipeLogic.Status.WORKING) {
                lastOriginRecipe = match;
                return true;
            }
        }
        return false;
    }

    public void handleRecipeWorking() {
        assert lastRecipe != null;
        if (handleTickRecipe(lastRecipe)) {
            if (!machine.onWorking()) {
                interruptRecipe();
                return;
            }
            setStatus(RecipeLogic.Status.WORKING);
            progress++;
            totalContinuousRunningTime++;
        } else {
            setWaiting(null);
        }
        if (isWaiting()) {
            machine.regressRecipe(this);
        }
    }

    @NotNull
    public Iterator<GTRecipe> searchRecipe() {
        if (machine.getRecipeType() != COMBINED_RECIPES)
            return machine.getRecipeType().searchRecipe(machine, this::matchRecipe);
        else {
            return Arrays.stream(machine.getRecipeTypes())
                    .filter(gtRecipeType -> gtRecipeType != COMBINED_RECIPES)
                    .flatMap(recipeType -> {
                        Iterator<GTRecipe> iterator = recipeType.searchRecipe(machine, this::matchRecipe);
                        return StreamSupport.stream(
                                Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED),
                                false
                        );
                    })
                    .iterator();
        }
    }

    public void findAndHandleRecipe() {
        lastRecipe = null;
        lastOriginRecipe = null;
        handleSearchingRecipes(searchRecipe());
    }

    protected void handleSearchingRecipes(@NotNull Iterator<GTRecipe> matches) {
        while (matches.hasNext()) {
            GTRecipe match = matches.next();
            if (match == null) continue;
            if (checkMatchedRecipeAvailable(match)) return;
        }
    }

    public boolean handleTickRecipe(GTRecipe recipe) {
        if (!recipe.hasTick()) return true;
        var result = RecipeHelper.matchTickRecipe(machine, recipe);
        if (!result) return false;
        result = handleTickRecipeIO(recipe, IO.IN);
        if (!result) return false;
        result = handleTickRecipeIO(recipe, IO.OUT);
        return result;
    }

    public void setupRecipe(GTRecipe recipe) {
        progress = 0;
        if (machine.beforeWorking(recipe) && handleRecipeIO(recipe, IO.IN)) {
            if (lastRecipe != null && !recipe.equals(lastRecipe)) {
                chanceCaches.clear();
            }
            interval = 5;
            lastRecipe = recipe;
            setStatus(RecipeLogic.Status.WORKING);
            duration = recipe.duration;
            isActive = true;
        } else {
            setStatus(RecipeLogic.Status.IDLE);
            duration = 0;
            isActive = false;
            if (!machine.keepSubscribing()) unsubscribe();
        }
    }

    public void setStatus(Status status) {
        if (this.status != status) {
            if (this.status == Status.WORKING) {
                this.totalContinuousRunningTime = 0;
            }
            machine.notifyStatusChanged(this.status, status);
            this.status = status;
            updateTickSubscription();
            if (this.status != Status.WAITING) {
                waitingReason = null;
            }
        }
    }

    public void setWaiting(@Nullable Component reason) {
        setStatus(Status.WAITING);
        waitingReason = reason;
        machine.onWaiting();
    }

    /**
     * mark current handling recipe (if exist) as dirty.
     * do not try it immediately in the next round
     */
    public void markLastRecipeDirty() {
        this.recipeDirty = true;
    }

    public boolean isWorking() {
        return status == Status.WORKING;
    }

    public boolean isIdle() {
        return status == Status.IDLE;
    }

    public boolean isWaiting() {
        return status == Status.WAITING;
    }

    public boolean isSuspend() {
        return status == Status.SUSPEND;
    }

    public boolean isWorkingEnabled() {
        return !isSuspend();
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        if (!isWorkingAllowed) {
            setStatus(Status.SUSPEND);
        } else {
            if (lastRecipe != null && duration > 0) {
                setStatus(Status.WORKING);
            } else {
                setStatus(Status.IDLE);
            }
        }
    }

    @Override
    public int getMaxProgress() {
        return duration;
    }

    public boolean isActive() {
        return isWorking() || isWaiting() || (isSuspend() && isActive);
    }

    public void onRecipeFinish() {
        machine.afterWorking();
        if (lastRecipe != null) {
            handleRecipeIO(lastRecipe, IO.OUT);
        }
        if (suspendAfterFinish) {
            setStatus(RecipeLogic.Status.SUSPEND);
            suspendAfterFinish = false;
        } else {
            if (!recipeDirty && !machine.alwaysSearchRecipe()) {
                lastRecipe = null;
                if (lastOriginRecipe != null && checkConditions(lastOriginRecipe)) {
                    lastRecipe = machine.fullModifyRecipe(lastOriginRecipe.copy());
                }
                if (lastRecipe != null && matchRecipe(lastRecipe)) {
                    setupRecipe(lastRecipe);
                    return;
                }
            }
            recipeDirty = false;
            findAndHandleRecipe();
            if (lastRecipe != null) return;
            setStatus(RecipeLogic.Status.IDLE);
        }
        progress = 0;
        duration = 0;
        isActive = false;
        if (!machine.keepSubscribing()) unsubscribe();
    }

    protected boolean handleRecipeIO(GTRecipe recipe, IO io) {
        return RecipeHelper.handleRecipeIO(machine, recipe, io, this.chanceCaches);
    }

    protected boolean handleTickRecipeIO(GTRecipe recipe, IO io) {
        return RecipeHelper.handleTickRecipeIO(machine, recipe, io, this.chanceCaches);
    }

    /**
     * Interrupt current recipe without io.
     */
    public void interruptRecipe() {
        machine.afterWorking();
        if (lastRecipe != null) {
            setStatus(Status.IDLE);
            progress = 0;
            duration = 0;
        }
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    //////////////////////////////////////
    // ******** MISC *********//
    //////////////////////////////////////
    @OnlyIn(Dist.CLIENT)
    public void updateSound() {
        if (isWorking() && machine.shouldWorkingPlaySound()) {
            var sound = machine.getSound();
            if (sound == null) sound = machine.getRecipeType().getSound();
            if (workingSound instanceof AutoReleasedSound soundEntry) {
                if (soundEntry.soundEntry == sound && !soundEntry.isStopped()) {
                    return;
                }
                soundEntry.release();
                workingSound = null;
            }
            if (sound != null) {
                workingSound = sound.playAutoReleasedSound(() -> machine.shouldWorkingPlaySound() && isWorking() && !getMachine().isInValid() && getMachine().getLevel().isLoaded(getMachine().getPos()), getMachine().getPos(), true, 0, 1, 1);
            }
        } else if (workingSound instanceof AutoReleasedSound soundEntry) {
            soundEntry.release();
            workingSound = null;
        }
    }

    @Override
    public IGuiTexture getFancyTooltipIcon() {
        if (waitingReason != null) {
            return GuiTextures.INSUFFICIENT_INPUT;
        }
        return IGuiTexture.EMPTY;
    }

    @Override
    public List<Component> getFancyTooltip() {
        if (waitingReason != null) {
            return List.of(waitingReason);
        }
        return Collections.emptyList();
    }

    @Override
    public boolean showFancyTooltip() {
        return waitingReason != null;
    }

    protected Map<RecipeCapability<?>, Object2IntMap<?>> makeChanceCaches() {
        Map<RecipeCapability<?>, Object2IntMap<?>> map = new Reference2ObjectOpenHashMap<>();
        for (RecipeCapability<?> cap : GTRegistries.RECIPE_CAPABILITIES.values()) {
            map.put(cap, cap.makeChanceCache());
        }
        return map;
    }

    public Status getStatus() {
        return this.status;
    }

    /**
     * unsafe, it may not be found from {@link RecipeManager}. Do not index it.
     */
    @Nullable
    public GTRecipe getLastRecipe() {
        return this.lastRecipe;
    }

    /**
     * safe, it is the origin recipe before {@link IRecipeLogicMachine#fullModifyRecipe(GTRecipe)}'
     * which can be found
     * from {@link RecipeManager}.
     */
    @Nullable
    public GTRecipe getLastOriginRecipe() {
        return this.lastOriginRecipe;
    }

    public int getProgress() {
        return this.progress;
    }

    public void setProgress(final int progress) {
        this.progress = progress;
    }

    public int getDuration() {
        return this.duration;
    }

    public boolean isRecipeDirty() {
        return this.recipeDirty;
    }

    public long getTotalContinuousRunningTime() {
        return this.totalContinuousRunningTime;
    }

    public void setSuspendAfterFinish(final boolean suspendAfterFinish) {
        this.suspendAfterFinish = suspendAfterFinish;
    }

    public Map<RecipeCapability<?>, Object2IntMap<?>> getChanceCaches() {
        return this.chanceCaches;
    }
}
