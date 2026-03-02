package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.IWorkable;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.IdleReason;
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

import it.unimi.dsi.fastutil.objects.Object2IntMap;
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
    @Getter
    @Setter
    @Nullable
    @DescSynced
    protected Component idleReason = null;

    @Getter
    @Nullable
    @Persisted
    protected GTRecipe lastRecipe;

    @Getter
    @Nullable
    protected GTRecipeDefinition lastOriginRecipe;
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
        idleReason = null;
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
            } else {
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

    public boolean checkMatchedRecipeAvailable(GTRecipeDefinition match) {
        var modified = machine.fullModifyRecipe(match.toRuntime());
        if (modified != null) {
            if (machine.matchRecipeTick(modified) && machine.matchRecipe(modified)) {
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
        if (lastRecipe.handleTickRecipe(machine, false) && machine.onWorking()) {
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
        recipeDirty = false;
        idleReason = null;
        lastRecipe = null;
        lastOriginRecipe = null;
        machine.getRecipeType().findRecipe(machine, match -> machine.checkTier(match.tier) && machine.checkConditions(match) && checkMatchedRecipeAvailable(match));
    }

    public void setupRecipe(@NotNull GTRecipe recipe) {
        progress = 0;
        if (machine.beforeWorking(recipe) && machine.handleRecipeInput(recipe)) {
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
        if (lastRecipe != null && !machine.handleRecipeOutput(lastRecipe)) {
            suspendAfterFinish = true;
        }
        if (suspendAfterFinish) {
            setStatus(SUSPEND);
            suspendAfterFinish = false;
        } else {
            if (!recipeDirty && !machine.alwaysSearchRecipe()) {
                lastRecipe = null;
                if (lastOriginRecipe != null && machine.checkConditions(lastOriginRecipe)) {
                    lastRecipe = machine.fullModifyRecipe(lastOriginRecipe.toRuntime());
                }
                if (lastRecipe != null && machine.matchRecipe(lastRecipe)) {
                    setupRecipe(lastRecipe);
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
        if (showFancyTooltip()) {
            return GuiTextures.INDICATOR_NO_STEAM.get(true);
        }
        return IGuiTexture.EMPTY;
    }

    @Override
    public List<Component> getFancyTooltip() {
        if (showFancyTooltip()) {
            return Collections.singletonList(idleReason == null ? IdleReason.NO_RECIPE_FOUND.get() : idleReason);
        }
        return Collections.emptyList();
    }

    @Override
    public boolean showFancyTooltip() {
        return isIdle() || isWaiting();
    }

    protected Map<RecipeCapability<?>, Object2IntMap<?>> makeChanceCaches() {
        return new RecipeCapabilityMap<>(ItemRecipeCapability.CAP.makeChanceCache(), FluidRecipeCapability.CAP.makeChanceCache());
    }
}
