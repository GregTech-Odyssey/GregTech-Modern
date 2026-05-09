package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.IWorkable;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.sound.AutoReleasedSound;
import com.gregtechceu.gtceu.utils.TaskHandler;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.UpdateListener;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;

public class RecipeLogic extends MachineTrait implements IWorkable, IFancyTooltip, BiPredicate<RecipeHandlerUnit, GTRecipeDefinition> {

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

    @Nullable
    @Persisted
    protected GTRecipe lastRecipe;

    @Getter
    @Nullable
    protected GTRecipeDefinition lastOriginRecipe;
    @Getter
    @Nullable
    protected RecipeHandlerUnit lastOriginUnit;
    @Getter
    @Setter
    @Persisted
    public int progress;
    @Getter
    @Persisted
    protected int duration;

    @Getter
    @Persisted
    protected long totalContinuousRunningTime;
    @Setter
    @Persisted
    protected boolean suspendAfterFinish = false;

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
        lastRecipe = null;
        lastOriginRecipe = null;
        lastOriginUnit = null;
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
        markLastRecipeDirty();
        updateTickSubscription();
    }

    @Override
    public void onMachineUnLoad() {
        super.onMachineUnLoad();
        markLastRecipeDirty();
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
                subscription = TaskHandler.enqueueTick(serverLevel, super.machine.holder.isRemove, this::serverTick, interval, 5);
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
            }
            if (progress >= duration) {
                machine.onRecipeFinish();
                onRecipeFinish();
            }
        } else {
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

    public boolean checkMatchedRecipeAvailable(RecipeHandlerUnit unit, GTRecipeDefinition match) {
        var modified = machine.fullModifyRecipe(unit, match.toRuntime());
        if (modified != null) {
            if (machine.matchRecipe(unit, modified)) {
                setupRecipe(unit, modified);
            }
            if (lastRecipe != null && status == WORKING) {
                lastOriginRecipe = match;
                lastOriginUnit = unit;
                return true;
            }
        }
        return false;
    }

    public void handleRecipeWorking() {
        if (lastRecipe != null && machine.handleTickRecipe(lastRecipe) && machine.onWorking()) {
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
        machine.getRecipeType().findRecipe(machine, this);
    }

    public void setupRecipe(RecipeHandlerUnit unit, @NotNull GTRecipe recipe) {
        progress = 0;
        if (machine.handleRecipeInput(unit, recipe)) {
            machine.beforeWorking(recipe);
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
            machine.self().requestSync();
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
        this.lastOriginRecipe = null;
        this.lastOriginUnit = null;
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
            machine.handleRecipeOutput(lastRecipe);
        }
        if (suspendAfterFinish) {
            setStatus(SUSPEND);
            suspendAfterFinish = false;
        } else {
            if (!machine.alwaysSearchRecipe()) {
                lastRecipe = null;
                var originRecipe = lastOriginRecipe;
                var originUnit = lastOriginUnit;
                if (originRecipe != null && originUnit != null && machine.checkConditions(originUnit, originRecipe)) {
                    lastRecipe = machine.fullModifyRecipe(originUnit, originRecipe.toRuntime());
                }
                if (lastRecipe != null && machine.matchRecipe(originUnit, lastRecipe)) {
                    setupRecipe(originUnit, lastRecipe);
                    return;
                }
            }
            findAndHandleRecipe();
            if (lastRecipe != null) return;
            setStatus(IDLE);
        }
        progress = 0;
        duration = 0;
        isActive = false;
        if (!machine.keepSubscribing()) unsubscribe();
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

    @Nullable
    public GTRecipe getLastRecipe() {
        return this.lastRecipe;
    }

    @Override
    public boolean test(RecipeHandlerUnit unit, GTRecipeDefinition definition) {
        return machine.checkConditions(unit, definition) && checkMatchedRecipeAvailable(unit, definition);
    }
}
