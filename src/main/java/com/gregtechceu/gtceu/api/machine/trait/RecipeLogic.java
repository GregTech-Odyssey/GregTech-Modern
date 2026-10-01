package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.IWorkable;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.issue.DiagnosisResult;
import com.gregtechceu.gtceu.api.machine.issue.GTIssues;
import com.gregtechceu.gtceu.api.machine.issue.IIssueProvider;
import com.gregtechceu.gtceu.api.machine.issue.IssueDraft;
import com.gregtechceu.gtceu.api.machine.issue.IssueLines;
import com.gregtechceu.gtceu.api.machine.issue.IssueSink;
import com.gregtechceu.gtceu.api.machine.issue.IssueSnapshot;
import com.gregtechceu.gtceu.api.machine.issue.IssueStage;
import com.gregtechceu.gtceu.api.machine.issue.IssueText;
import com.gregtechceu.gtceu.api.machine.issue.IssueType;
import com.gregtechceu.gtceu.api.machine.issue.MachineIssue;
import com.gregtechceu.gtceu.api.misc.TickTimeMonitor;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.info.FluidRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.RecipeInfo;
import com.gregtechceu.gtceu.api.sound.AutoReleasedSound;
import com.gregtechceu.gtceu.common.data.GTTickTimeMonitors;
import com.gregtechceu.gtceu.uiwidgets.icon.IssueIcons;
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

public class RecipeLogic extends MachineTrait implements IWorkable, IFancyTooltip, IIssueProvider, BiPredicate<RecipeHandlerUnit, GTRecipeDefinition> {

    // status
    public static final int IDLE = 0;
    public static final int WORKING = 1;
    public static final int WAITING = 2;
    public static final int SUSPEND = 3;

    public static int SEARCH_MAX_INTERVAL = 80;

    private static final byte SEARCH_STAGE = (byte) IssueStage.SEARCH.ordinal();
    private static final int LAZY_WORKING_ROUND = 1 << 30;

    public final IRecipeLogicMachine machine;

    @Getter
    @SaveToDisk(defaultValue = "0")
    @SyncToClient(listener = "onStatusSynced", scheduleUpdate = true)
    protected int status = IDLE;
    @SaveToDisk(defaultValue = "false")
    @SyncToClient(scheduleUpdate = true)
    protected boolean isActive;

    private final IssueDraft issueDraft = new IssueDraft();
    private boolean issueDraftUsed;
    private boolean issueSettled;
    private volatile IssueSnapshot issueSnapshot = IssueSnapshot.EMPTY;
    private int publishedStatus = IssueSnapshot.EMPTY.status();
    @Nullable
    private MachineIssue publishedPrimary = IssueSnapshot.EMPTY.primary();
    private int issueVersion;
    private int issueRoundDepth;
    private boolean issueRoundWorking;
    private byte issueStage = SEARCH_STAGE;
    @Nullable
    private GTRecipeDefinition issueRecipe;
    private boolean issueRecipeCleared = true;
    private boolean outsideIssuePublished;
    private boolean outsideClearPending;
    @Nullable
    private DiagnosisResult diagnosisCache;
    private boolean outputLost;

    @Getter
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
    @Nullable
    private GTRecipeDefinition lastRunRecipe;
    @Getter
    @Setter
    @SaveToDisk(defaultValue = "0")
    public int progress;
    @Getter
    @SaveToDisk(defaultValue = "0")
    protected int duration;

    @Getter
    @SaveToDisk(defaultValue = "0")
    protected long totalContinuousRunningTime;
    @Setter
    @SaveToDisk(defaultValue = "false")
    protected boolean suspendAfterFinish = false;
    @Getter
    @SaveToDisk(defaultValue = "false")
    protected boolean recipeLocked;

    @Getter
    @SaveToDisk(skipWhen = "skipSaveLockRecipe")
    protected GTRecipeDefinition lockedRecipe;

    public TickableSubscription subscription;
    public int interval = 5;

    protected Object workingSound;

    public final TickTimeMonitor monitor;

    public RecipeLogic(IRecipeLogicMachine machine) {
        super(machine.self());
        this.machine = machine;
        // 注册到机器的监控表里，Jade 会按 key 一起显示
        this.monitor = super.machine.holder.monitorTick(GTTickTimeMonitors.RECIPE_LOGIC, this::serverTick);
    }

    private boolean skipSaveLockRecipe(GTRecipeDefinition recipe) {
        return !recipeLocked || !recipe.registered;
    }

    public void setRecipeLocked(boolean value) {
        if (machine.supportLockRecipe() && !machine.alwaysSearchRecipe()) {
            this.recipeLocked = value;
            updateTickSubscription();
        }
        this.lockedRecipe = null;
    }

    public void setLockedRecipe(GTRecipeDefinition recipe) {
        if (this.recipeLocked) this.lockedRecipe = recipe;
    }

    @SuppressWarnings("unused")
    protected void onStatusSynced(int newValue, int oldValue) {
        updateSound();
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
        if (issueRoundDepth == 0) publishStatusIssue();
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
                subscription = TaskHandler.enqueueTick(serverLevel, super.machine.holder.isRemove, this.monitor, interval, 5);
                if (isActive) subscription.cycle = 0;
            }
        } else {
            unsubscribe();
            if (status != SUSPEND && issueRoundDepth == 0) publishIssue(status == WORKING ? WAITING : status, machine.getUnavailableIssue());
        }
    }

    public double getProgressPercent() {
        return duration == 0 ? 0.0 : progress / (duration * 1.0);
    }

    public void serverTick() {
        if (status == SUSPEND) {
            unsubscribe();
        } else {
            if (status != IDLE && lastRecipe != null) {
                if (progress < duration) {
                    handleRecipeWorking();
                    if (machine.nextTickSearch()) return;
                }
                if (progress < duration) return;
                if (onRecipeFinish()) return;
                progress = 0;
                duration = 0;
                isActive = false;
            } else if (findAndHandleRecipe()) {
                return;
            }
            if (interval < SEARCH_MAX_INTERVAL) {
                interval <<= 1;
                if (subscription != null) subscription.cycle = interval;
            }
            if (!machine.keepSubscribing()) unsubscribe();
        }
    }

    public boolean findAndHandleRecipe() {
        lastRecipe = null;
        beginSearchRound();
        try {
            return searchRecipe();
        } finally {
            endIssueRound();
        }
    }

    private boolean searchRecipe() {
        markLastRecipeDirty();
        return machine.findRecipe(machine.getRecipeType(), this, lockedRecipe);
    }

    @Override
    public boolean test(RecipeHandlerUnit unit, GTRecipeDefinition definition) {
        enterIssueStep();
        setIssueRecipe(definition);
        moveIssueStage(IssueStage.TIER);
        if (!machine.checkTier(definition)) return false;
        moveIssueStage(IssueStage.CONDITION);
        if (machine.checkConditions(unit, definition) && checkMatchedRecipeAvailable(unit, definition)) {
            setLockedRecipe(definition);
            return true;
        }
        return false;
    }

    public boolean checkMatchedRecipeAvailable(RecipeHandlerUnit unit, GTRecipeDefinition match) {
        enterIssueStep();
        setIssueRecipe(match);
        moveIssueStage(IssueStage.MODIFIER);
        var modified = machine.fullModifyRecipe(unit, match);
        if (modified == null) return false;
        moveIssueStage(IssueStage.ENERGY);
        if (!machine.matchTickRecipe(modified)) return false;
        moveIssueStage(IssueStage.INPUT);
        if (!machine.matchRecipe(unit, modified)) return false;
        moveIssueStage(IssueStage.SETUP);
        if (setupRecipe(unit, modified)) {
            lastOriginRecipe = match;
            lastOriginUnit = unit;
            return true;
        }
        return false;
    }

    public boolean setupRecipe(RecipeHandlerUnit unit, @NotNull GTRecipe recipe) {
        progress = 0;
        if (machine.handleRecipeInput(unit, recipe)) {
            machine.beforeWorking(unit, recipe);
            interval = 5;
            lastRecipe = recipe;
            setStatus(WORKING);
            duration = recipe.duration;
            if (subscription != null) subscription.cycle = 0;
            isActive = true;
            return true;
        } else {
            setStatus(IDLE);
            duration = 0;
            isActive = false;
            return false;
        }
    }

    public void handleRecipeWorking() {
        if (issueRoundDepth != 0) {
            handleRecipeWorkingInRound();
            return;
        }
        issueRoundDepth = LAZY_WORKING_ROUND;
        try {
            tickRecipe();
        } finally {
            endWorkingRound();
        }
    }

    private void handleRecipeWorkingInRound() {
        beginIssueRound(IssueStage.WORKING);
        try {
            tickRecipe();
        } finally {
            endIssueRound();
        }
    }

    private void tickRecipe() {
        if (lastRecipe != null && machine.handleTickRecipe(lastRecipe)) {
            setStatus(WORKING);
            progress++;
            totalContinuousRunningTime++;
            machine.onWorking();
        } else {
            machine.regressRecipe(this);
            interruptRecipe();
        }
    }

    private void endWorkingRound() {
        if (issueSettled) {
            issueRoundDepth = 0;
            return;
        }
        if (issueRoundDepth != LAZY_WORKING_ROUND) {
            endIssueRound();
            return;
        }
        issueRoundDepth = 0;
        settleWorkingRound();
    }

    private void settleWorkingRound() {
        resetIssueDraft();
        if (outsideIssuePublished) {
            outsideIssuePublished = false;
            outsideClearPending = false;
        }
        issueStage = SEARCH_STAGE;
        issueRecipeCleared = true;
        int s = status;
        if (s == WORKING) publishWorkingIssue();
        else publishIssue(s, s == SUSPEND ? restingIssue(s) : retainedIssue());
    }

    private void materializeWorkingRound() {
        issueSettled = false;
        issueRoundDepth = 0;
        beginIssueRound(IssueStage.WORKING);
    }

    private void enterIssueStep() {
        int depth = issueRoundDepth;
        if (depth == LAZY_WORKING_ROUND) materializeWorkingRound();
        else if (depth == 0) issueSettled = false;
    }

    public boolean onRecipeFinish() {
        machine.afterWorking();
        produceOutputs();
        if (suspendAfterFinish) {
            setStatus(SUSPEND);
            suspendAfterFinish = false;
        } else {
            beginIssueRound(IssueStage.SEARCH);
            try {
                if (!machine.alwaysSearchRecipe()) {
                    lastRecipe = null;
                    var originRecipe = lastOriginRecipe;
                    var originUnit = lastOriginUnit;
                    if (originRecipe != null && originUnit != null) {
                        setIssueRecipe(originRecipe);
                        moveIssueStage(IssueStage.CONDITION);
                        if (machine.checkConditions(originUnit, originRecipe) && checkMatchedRecipeAvailable(originUnit, originRecipe)) return true;
                    }
                }
                if (findAndHandleRecipe()) return true;
                setStatus(IDLE);
            } finally {
                endIssueRound();
            }
        }
        return false;
    }

    public void setStatus(int status) {
        if (this.status != status) {
            if (this.status == WORKING) {
                this.totalContinuousRunningTime = 0;
            }
            machine.self().requestSync();
            this.status = status;
            issueSettled = false;
            updateTickSubscription();
            if (issueRoundDepth == 0) publishStatusIssue();
        }
    }

    @Deprecated
    public void setWaiting(@Nullable Component reason) {
        if (reason == null) enterWaiting(null, IssueStage.SEARCH, IO.NONE, null, -1, 0, 0, null);
        else enterWaiting(GTIssues.CUSTOM, getIssueStage(), IO.NONE, null, -1, 0, 0, () -> reason);
    }

    public void setWaiting(IssueType type, long a, long b) {
        setWaiting(type, IO.NONE, null, -1, a, b);
    }

    public void setWaiting(IssueType type, IO io, @Nullable RecipeInfo capability, int index, long a, long b) {
        enterWaiting(type, type.stage, io, capability, index, a, b, null);
    }

    private void enterWaiting(@Nullable IssueType type, IssueStage stage, IO io, @Nullable RecipeInfo capability, int index, long a, long b, @Nullable Supplier<Component> custom) {
        boolean entering = status != WAITING;
        if (entering) setStatus(WAITING);
        if (type != null) forceIssue(type, stage, io, capability, index, a, b, custom);
        if (entering) machine.onWaiting();
    }

    /**
     * Interrupt current recipe without io.
     */
    public void interruptRecipe() {
        enterWaiting(null, IssueStage.SEARCH, IO.NONE, null, -1, 0, 0, null);
        unsubscribe();
    }

    @Deprecated
    public void interruptRecipe(@Nullable Component reason) {
        setWaiting(reason);
        unsubscribe();
    }

    public void interruptRecipe(IssueType type, long a, long b) {
        setWaiting(type, a, b);
        unsubscribe();
    }

    protected void produceOutputs() {
        var recipe = lastRecipe;
        if (recipe == null) return;
        if (lastRunRecipe != recipe.definition) lastRunRecipe = recipe.definition;
        boolean produced = machine.handleRecipeOutput(recipe);
        outputLost = !produced && !(machine.canVoidRecipeOutputs(ItemRecipeInfo.INSTANCE) || machine.canVoidRecipeOutputs(FluidRecipeInfo.INSTANCE));
    }

    @Override
    public void collectIssues(IssueSink sink) {
        if (outputLost) sink.accept(GTIssues.OUTPUT_LOST);
    }

    /**
     * mark current handling recipe (if exist) as dirty.
     * do not try it immediately in the next round
     */
    public void markLastRecipeDirty() {
        this.lastOriginRecipe = null;
        this.lastOriginUnit = null;
        if (issueRoundDepth > 0) return;
        resetIssueDraft();
        if (outsideIssuePublished) {
            if (outsideClearPending) {
                outsideClearPending = false;
                outsideIssuePublished = false;
                publishIssue(status, restingIssue(status));
            } else {
                outsideClearPending = true;
            }
        }
    }

    public IssueSnapshot getIssueSnapshot() {
        return issueSnapshot;
    }

    @Nullable
    public DiagnosisResult getDiagnosisCache() {
        return diagnosisCache;
    }

    public void setDiagnosisCache(@Nullable DiagnosisResult result) {
        this.diagnosisCache = result;
    }

    public IssueStage getIssueStage() {
        if (issueRoundDepth == LAZY_WORKING_ROUND) return IssueStage.WORKING;
        return IssueStage.of(issueStage);
    }

    public void setIssueStage(IssueStage stage) {
        enterIssueStep();
        moveIssueStage(stage);
    }

    private void moveIssueStage(IssueStage stage) {
        this.issueStage = (byte) stage.ordinal();
    }

    private void setIssueRecipe(@Nullable GTRecipeDefinition recipe) {
        if (recipe == null) {
            issueRecipeCleared = true;
        } else {
            if (issueRecipe != recipe) issueRecipe = recipe;
            issueRecipeCleared = false;
        }
    }

    @Nullable
    private GTRecipeDefinition currentIssueRecipe() {
        return issueRecipeCleared ? null : issueRecipe;
    }

    public void report(IssueType type) {
        report(type, null, IO.NONE, null, -1, 0, 0, null);
    }

    public void report(IssueType type, long a, long b) {
        report(type, null, IO.NONE, null, -1, a, b, null);
    }

    public void report(IssueType type, @Nullable IssueStage stage, IO io, @Nullable RecipeInfo capability, int index, long a, long b, @Nullable GTRecipeDefinition recipe) {
        offerIssue(type, stage == null ? type.stage : stage, io, capability, index, a, b, recipe, null);
    }

    @Deprecated
    public void reportCustom(Supplier<Component> text) {
        offerIssue(GTIssues.CUSTOM, getIssueStage(), IO.NONE, null, -1, 0, 0, null, text);
    }

    public void beginIssueRound(IssueStage stage) {
        if (issueRoundDepth == LAZY_WORKING_ROUND) materializeWorkingRound();
        if (issueRoundDepth++ == 0) {
            issueRoundWorking = stage == IssueStage.WORKING;
            resetIssueDraft();
        }
        issueStage = (byte) stage.ordinal();
        var recipe = lastRecipe;
        if (stage == IssueStage.WORKING && recipe != null) setIssueRecipe(recipe.definition);
        else issueRecipeCleared = true;
    }

    private void beginSearchRound() {
        if (issueRoundDepth != 0) {
            beginIssueRound(IssueStage.SEARCH);
            return;
        }
        issueRoundDepth = 1;
        issueRoundWorking = false;
        if (issueDraftUsed) resetIssueDraft();
        issueStage = SEARCH_STAGE;
        issueRecipeCleared = true;
    }

    private void resetIssueDraft() {
        if (issueDraftUsed) {
            issueDraftUsed = false;
            issueDraft.reset();
        }
    }

    public void endIssueRound() {
        int depth = issueRoundDepth;
        if (depth == 0) return;
        if (depth == LAZY_WORKING_ROUND) {
            materializeWorkingRound();
            depth = 1;
        }
        issueRoundDepth = --depth;
        if (depth == 0) finishIssueRound();
    }

    private void finishIssueRound() {
        if (outsideIssuePublished) {
            outsideIssuePublished = false;
            outsideClearPending = false;
        }
        issueStage = SEARCH_STAGE;
        issueRecipeCleared = true;
        int s = status;
        if (s == WORKING) {
            resetIssueDraft();
            publishWorkingIssue();
        } else {
            publishRoundIssue(s);
        }
    }

    private void publishWorkingIssue() {
        publishIssue(WORKING, null);
        issueSettled = publishedStatus == WORKING && publishedPrimary == null;
    }

    private void publishRoundIssue(int s) {
        if (s == SUSPEND) publishIssue(s, restingIssue(s));
        else if (issueDraftUsed && !issueDraft.isEmpty()) publishDraft(s);
        else publishIssue(s, issueRoundWorking ? retainedIssue() : GTIssues.NO_RECIPE.bare());
    }

    private void offerIssue(IssueType type, IssueStage stage, IO io, @Nullable RecipeInfo capability, int index, long a, long b,
                            @Nullable GTRecipeDefinition recipe, @Nullable Supplier<Component> custom) {
        if (issueRoundDepth == LAZY_WORKING_ROUND) materializeWorkingRound();
        if (recipe == null) recipe = currentIssueRecipe();
        boolean focused = recipe != null && recipe == lockedRecipe;
        issueDraftUsed = true;
        if (issueRoundDepth > 0) {
            issueDraft.offer(type, stage, io, capability, index, a, b, recipe, custom, focused);
            return;
        }
        issueSettled = false;
        if (issueDraft.type() == type || !issueDraft.matches(issueSnapshot.primary())) issueDraft.reset();
        if (!issueDraft.offer(type, stage, io, capability, index, a, b, recipe, custom, focused) || status == SUSPEND) return;
        outsideIssuePublished = true;
        outsideClearPending = false;
        publishDraft(status);
    }

    private void forceIssue(IssueType type, IssueStage stage, IO io, @Nullable RecipeInfo capability, int index, long a, long b, @Nullable Supplier<Component> custom) {
        if (issueRoundDepth == LAZY_WORKING_ROUND) materializeWorkingRound();
        issueDraftUsed = true;
        issueDraft.set(type, stage, io, capability, index, a, b, currentIssueRecipe(), custom, true);
        if (issueRoundDepth == 0) {
            issueSettled = false;
            publishDraft(status);
        }
    }

    private void publishStatusIssue() {
        int s = status;
        publishIssue(s, s == WORKING || s == SUSPEND ? restingIssue(s) : retainedIssue());
    }

    @Nullable
    private static MachineIssue restingIssue(int status) {
        if (status == IDLE) return GTIssues.NO_RECIPE.bare();
        if (status == SUSPEND) return GTIssues.PAUSED.bare();
        return null;
    }

    @Nullable
    private MachineIssue retainedIssue() {
        var primary = issueSnapshot.primary();
        return primary != null && primary.type() == GTIssues.PAUSED ? null : primary;
    }

    private void publishDraft(int status) {
        if (publishedStatus == status && issueDraft.matches(publishedPrimary)) return;
        if (super.machine.isRemote()) return;
        publishSnapshot(status, issueDraft.toIssue());
    }

    private void publishIssue(int status, @Nullable MachineIssue primary) {
        if (publishedStatus != status || publishedPrimary != primary) publishChangedIssue(status, primary);
    }

    private void publishChangedIssue(int status, @Nullable MachineIssue primary) {
        if (publishedStatus == status && primary != null && primary.equals(publishedPrimary)) return;
        if (super.machine.isRemote()) return;
        publishSnapshot(status, primary);
    }

    private void publishSnapshot(int status, @Nullable MachineIssue primary) {
        publishedStatus = status;
        publishedPrimary = primary;
        issueSettled = false;
        issueSnapshot = new IssueSnapshot(status, primary, ++issueVersion);
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
                workingSound = sound.playAutoReleasedSound(() -> machine.shouldWorkingPlaySound() && isWorking() && !super.machine.isRemoved() && super.machine.getLevel().isLoaded(super.machine.getPos()), super.machine.getPos(), true, 0, 1, 1);
            }
        } else if (workingSound instanceof AutoReleasedSound soundEntry) {
            soundEntry.release();
            workingSound = null;
        }
    }

    @Override
    public IGuiTexture getFancyTooltipIcon() {
        return showFancyTooltip() ? IssueIcons.iconFor(fancyTooltipSnapshot()) : IGuiTexture.EMPTY;
    }

    @Override
    public List<Component> getFancyTooltip() {
        return showFancyTooltip() ? IssueLines.tooltip(fancyTooltipSnapshot()) : Collections.emptyList();
    }

    @Override
    public boolean showFancyTooltip() {
        return IssueLines.visible(fancyTooltipSnapshot());
    }

    private IssueSnapshot fancyTooltipSnapshot() {
        return super.machine.isRemote() ? IssueLines.statusOnly(status) : issueSnapshot;
    }

    @Deprecated
    public Component getIdleReason() {
        return IssueText.summary(issueSnapshot);
    }
}
