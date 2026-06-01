package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.IWorkable;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.handler.ActionResult;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.sound.AutoReleasedSound;
import com.gregtechceu.gtceu.utils.TaskHandler;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

public class RecipeLogic extends MachineTrait implements IWorkable, IFancyTooltip, BiPredicate<RecipeHandlerUnit, GTRecipeDefinition> {

    // status
    public static final int IDLE = 0;
    public static final int WORKING = 1;
    public static final int WAITING = 2;
    public static final int SUSPEND = 3;

    public final static int SEARCH_MAX_INTERVAL = 80;

    public final IRecipeLogicMachine machine;

    @Getter
    @SaveToDisk
    @SyncToClient(listener = "onStatusSynced")
    protected int status = IDLE;
    @SaveToDisk
    @SyncToClient(listener = "onActiveSynced")
    protected boolean isActive;

    @Nullable
    @SyncToClient
    protected Component idleReason = null;

    @Setter
    protected Supplier<Component> idleReasonSupplier = null;

    @Nullable
    @SaveToDisk
    protected GTRecipe lastRecipe;

    @Getter
    @Nullable
    protected GTRecipeDefinition lastOriginRecipe;
    @Getter
    @Nullable
    protected RecipeHandlerUnit lastOriginUnit;
    @Getter
    @Setter
    @SaveToDisk
    public int progress;
    @Getter
    @SaveToDisk
    protected int duration;

    @Getter
    @SaveToDisk
    protected long totalContinuousRunningTime;
    @Setter
    @SaveToDisk
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
            if (machine.matchTickRecipe(modified) && machine.matchRecipe(unit, modified)) {
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
        if (lastRecipe != null && machine.handleTickRecipe(lastRecipe)) {
            setStatus(WORKING);
            progress++;
            totalContinuousRunningTime++;
            machine.onWorking();
        } else {
            interruptRecipe();
        }
        if (isWaiting()) {
            machine.regressRecipe(this);
        }
    }

    public void findAndHandleRecipe() {
        lastRecipe = null;
        markLastRecipeDirty();
        machine.findRecipe(machine.getRecipeType(), this);
        if (idleReasonSupplier != null) {
            idleReason = idleReasonSupplier.get();
            idleReasonSupplier = null;
        }
    }

    @Override
    public boolean test(RecipeHandlerUnit unit, GTRecipeDefinition definition) {
        return machine.checkTier(definition) && machine.checkConditions(unit, definition) && checkMatchedRecipeAvailable(unit, definition);
    }

    public void setupRecipe(RecipeHandlerUnit unit, @NotNull GTRecipe recipe) {
        progress = 0;
        if (machine.handleRecipeInput(unit, recipe)) {
            machine.beforeWorking(unit, recipe);
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
        }
    }

    public void setWaiting(@Nullable Component reason) {
        if (this.status != WAITING) {
            setStatus(WAITING);
            if (reason != null) idleReason = reason;
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
        this.idleReasonSupplier = null;
        this.idleReason = null;
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
                if (lastRecipe != null && machine.matchTickRecipe(lastRecipe) && machine.matchRecipe(originUnit, lastRecipe)) {
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

    public void interruptRecipe(@Nullable Component reason) {
        setWaiting(reason);
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
        if (showFancyTooltip()) {
            return GuiTextures.INSUFFICIENT_INPUT;
        }
        return IGuiTexture.EMPTY;
    }

    @Override
    public List<Component> getFancyTooltip() {
        if (showFancyTooltip()) {
            return List.of(getIdleReason());
        }
        return Collections.emptyList();
    }

    @Override
    public boolean showFancyTooltip() {
        return status != WORKING && (status == IDLE || idleReason != null);
    }

    public Component getIdleReason() {
        if (idleReason == null) return ActionResult.FAIL_NO_RECIPE_FOUND.reason();
        return idleReason;
    }

    @Nullable
    public GTRecipe getLastRecipe() {
        return this.lastRecipe;
    }
}
