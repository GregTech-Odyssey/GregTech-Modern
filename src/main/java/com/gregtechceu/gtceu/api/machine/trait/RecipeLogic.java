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
import com.gregtechceu.gtceu.utils.TaskHandler;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.UpdateListener;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RecipeLogic extends MachineTrait implements IWorkable, IFancyTooltip {

    // status
    public static final int IDLE = 0;
    public static final int WORKING = 1;
    public static final int WAITING = 2;
    public static final int SUSPEND = 3;

    public final static int SEARCH_MAX_INTERVAL = 80;

    public final IRecipeLogicMachine machine;
    @Getter
    @Persisted
    @DescSynced
    @UpdateListener(methodName = "onStatusSynced")
    protected int status = IDLE;
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
    @Getter
    @Setter
    @Persisted
    public int progress;
    @Getter
    @Persisted
    protected int duration;
    @Getter
    protected boolean recipeDirty = true;
    @Getter
    @Persisted
    protected long totalContinuousRunningTime;
    @Setter
    @Persisted
    protected boolean suspendAfterFinish = false;
    @Getter
    protected final Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches = makeChanceCaches();

    public TickableSubscription subscription;
    public int interval = 5;

    protected Object workingSound;

    public RecipeLogic(IRecipeLogicMachine machine) {
        super(machine.self());
        this.machine = machine;
    }

    @OnlyIn(Dist.CLIENT)
    @SuppressWarnings("unused")
    protected void onStatusSynced(int newValue, int oldValue) {
        super.machine.scheduleRenderUpdate();
        updateSound();
    }

    @OnlyIn(Dist.CLIENT)
    @SuppressWarnings("unused")
    protected void onActiveSynced(boolean newActive, boolean oldActive) {
        super.machine.scheduleRenderUpdate();
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
        if (status != SUSPEND) {
            setStatus(IDLE);
        }
        updateTickSubscription();
    }

    @Override
    public void onMachineLoad() {
        super.onMachineLoad();
        updateTickSubscription();
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
        if (status != SUSPEND && machine.isRecipeLogicAvailable()) {
            if ((subscription == null || !subscription.stillSubscribed) && super.machine.getLevel() instanceof ServerLevel serverLevel) {
                subscription = TaskHandler.enqueueServerTick(serverLevel, super.machine.holder.isRemove, this::serverTick, interval, 5);
                if (isActive) subscription.cycle = 0;
            }
        } else {
            unsubscribe();
        }
    }

    public double getProgressPercent() {
        return duration == 0 ? 0.0 : progress / (duration * 1.0);
    }

    public void serverTick() {
        if (status == SUSPEND) {
            unsubscribe();
        } else if (status != IDLE && lastRecipe != null) {
            if (progress < duration) {
                handleRecipeWorking();
            } else {
                machine.onRecipeFinish();
                onRecipeFinish();
            }
        } else {
            recipeDirty = false;
            findAndHandleRecipe();
            if (lastRecipe == null) {
                if (interval < SEARCH_MAX_INTERVAL) {
                    interval <<= 1;
                    if (subscription != null) subscription.cycle = interval;
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
            if (lastRecipe != null && status == WORKING) {
                lastOriginRecipe = match;
                return true;
            }
        }
        return false;
    }

    public void handleRecipeWorking() {
        if (handleTickRecipe(lastRecipe) && machine.onWorking()) {
            setStatus(WORKING);
            progress++;
            totalContinuousRunningTime++;
        } else {
            setWaiting(null);
        }
        if (isWaiting()) {
            machine.regressRecipe(this);
        }
    }

    public void findAndHandleRecipe() {
        lastRecipe = null;
        lastOriginRecipe = null;
        machine.getRecipeType().findRecipe(machine, match -> matchRecipe(match) && checkMatchedRecipeAvailable(match));
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

    public void setupRecipe(@NotNull GTRecipe recipe) {
        progress = 0;
        if (machine.beforeWorking(recipe) && handleRecipeIO(recipe, IO.IN)) {
            if (lastRecipe != null && !recipe.equals(lastRecipe)) {
                chanceCaches.clear();
            }
            interval = 5;
            lastRecipe = recipe;
            setStatus(WORKING);
            duration = recipe.duration;
            if (subscription != null) subscription.cycle = 0;
            isActive = true;
        } else {
            setStatus(IDLE);
            duration = 0;
            isActive = false;
            if (!machine.keepSubscribing()) unsubscribe();
        }
    }

    public void setStatus(int status) {
        if (this.status != status) {
            if (this.status == WORKING) {
                this.totalContinuousRunningTime = 0;
            }
            machine.notifyStatusChanged(this.status, status);
            this.status = status;
            updateTickSubscription();
            if (this.status != WAITING) {
                waitingReason = null;
            }
        }
    }

    public void setWaiting(@Nullable Component reason) {
        if (this.status != WAITING) {
            setStatus(WAITING);
            waitingReason = reason;
            machine.onWaiting();
        }
    }

    /**
     * mark current handling recipe (if exist) as dirty.
     * do not try it immediately in the next round
     */
    public void markLastRecipeDirty() {
        this.recipeDirty = true;
    }

    public boolean isWorking() {
        return status == WORKING;
    }

    public boolean isIdle() {
        return status == IDLE;
    }

    public boolean isWaiting() {
        return status == WAITING;
    }

    public boolean isSuspend() {
        return status == SUSPEND;
    }

    public boolean isWorkingEnabled() {
        return status != SUSPEND;
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        if (!isWorkingAllowed) {
            setStatus(SUSPEND);
        } else {
            if (lastRecipe != null && duration > 0) {
                setStatus(WORKING);
            } else {
                setStatus(IDLE);
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
            setStatus(SUSPEND);
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
            setStatus(IDLE);
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
        setWaiting(null);
        unsubscribe();
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
                workingSound = sound.playAutoReleasedSound(() -> machine.shouldWorkingPlaySound() && isWorking() && !super.machine.isInValid() && super.machine.getLevel().isLoaded(super.machine.getPos()), super.machine.getPos(), true, 0, 1, 1);
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
}
