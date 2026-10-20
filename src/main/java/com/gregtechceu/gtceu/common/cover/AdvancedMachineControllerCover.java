package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.IMachineShutdownListener;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.tooltip.TipRole;
import com.gregtechceu.gtceu.api.tooltip.TipSpec;
import com.gregtechceu.gtceu.common.cover.data.ControllerMode;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.Level;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Form;
import com.gregtechceu.gtceu.uipro.elements.NumberField;
import com.gregtechceu.gtceu.uipro.elements.StatusPanel;
import com.gregtechceu.gtceu.uipro.elements.Switch;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import com.gto.datasynclib.annotations.SaveToDisk;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 机器控制覆盖板的全部功能，另在机器因每刻消耗不足而自行停机后按设定延迟重启；停机由 RecipeLogic 经 IMachineShutdownListener 通知。
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class AdvancedMachineControllerCover extends MachineControllerCover implements IMachineShutdownListener {

    public static final int MAX_DELAY = 3600;

    public static final TipSpec TIPS = TipSpec.builder()
            .lines(l -> l
                    .line(TipRole.SUMMARY, Component.translatable("item.gtceu.advanced_machine_controller_cover.tip.summary"))
                    .feature(Component.translatable("item.gtceu.advanced_machine_controller_cover.tip.restart"))
                    .feature(Component.translatable("item.gtceu.advanced_machine_controller_cover.tip.delay"))
                    .attention(Component.translatable("item.gtceu.advanced_machine_controller_cover.tip.rule"))
                    .attention(Component.translatable("item.gtceu.advanced_machine_controller_cover.tip.attach"))
                    .warning(Component.translatable("item.gtceu.advanced_machine_controller_cover.tip.warning"))
                    .rightClick(Component.translatable("item.gtceu.machine_controller_cover.tip.open")))
            .details(null, l -> l
                    .detail(Component.translatable("item.gtceu.advanced_machine_controller_cover.tip.redstone"))
                    .detail(Component.translatable("item.gtceu.advanced_machine_controller_cover.tip.manual"))
                    .detail(Component.translatable("item.gtceu.advanced_machine_controller_cover.tip.reload"))
                    .detail(Component.translatable("item.gtceu.machine_controller_cover.tip.normal"))
                    .detail(Component.translatable("item.gtceu.machine_controller_cover.tip.inverted"))
                    .detail(Component.translatable("item.gtceu.machine_controller_cover.tip.multiple")))
            .build();

    @Getter
    @SaveToDisk(defaultValue = "true")
    private boolean autoRestart = true;
    @Getter
    @SaveToDisk
    private int restartDelay;
    @SaveToDisk
    private int remainingTicks;
    @Nullable
    private TickableSubscription subscription;

    public AdvancedMachineControllerCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
        super(definition, coverHolder, attachedSide);
    }

    public void setAutoRestart(boolean autoRestart) {
        this.autoRestart = autoRestart;
        if (!autoRestart) cancel();
        coverHolder.onChanged();
    }

    public void setRestartDelay(int restartDelay) {
        this.restartDelay = Math.max(0, Math.min(MAX_DELAY, restartDelay));
        coverHolder.onChanged();
    }

    public boolean isRestartPending() {
        return remainingTicks > 0;
    }

    @Override
    public void onMachineShutdown() {
        if (!autoRestart) return;
        remainingTicks = Math.max(1, restartDelay * 20);
        subscription = coverHolder.subscribeServerTick(subscription, this::tick);
        coverHolder.onChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (remainingTicks > 0) subscription = coverHolder.subscribeServerTick(subscription, this::tick);
    }

    @Override
    public void onUnload() {
        super.onUnload();
        unsubscribe();
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        unsubscribe();
    }

    private void tick() {
        var controllable = GTCapabilityHelper.getControllable(coverHolder.holder(), null);
        if (controllable == null || controllable.isWorkingEnabled()) {
            cancel();
            return;
        }
        if (--remainingTicks > 0) return;
        cancel();
        restart(controllable);
    }

    private void cancel() {
        remainingTicks = 0;
        unsubscribe();
    }

    private void unsubscribe() {
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
    }

    private void restart(IControllable controllable) {
        for (var cover : coverHolder.getCovers()) {
            if (cover instanceof MachineControllerCover controller && controller.getControllerMode() == ControllerMode.MACHINE && !controller.shouldAllowWorking()) return;
        }
        controllable.setWorkingEnabled(true);
        coverHolder.onChanged();
    }

    @Override
    public Widget createUIWidget() {
        var page = (UIElement) super.createUIWidget();
        var status = new StatusPanel();
        int[] shown = { Integer.MIN_VALUE };
        Component[] text = { Component.empty() };
        status.addLine("cover.advanced_machine_controller.status", () -> {
            int key = !autoRestart ? -1 : (remainingTicks + 19) / 20;
            if (key != shown[0]) {
                shown[0] = key;
                text[0] = key < 0 ? Component.translatable("cover.advanced_machine_controller.status.off") :
                        key == 0 ? Component.translatable("cover.advanced_machine_controller.status.standby") :
                                Component.translatable("cover.advanced_machine_controller.status.waiting", key);
            }
            return text[0];
        }).bindLevel(() -> !autoRestart ? Level.NORMAL : remainingTicks > 0 ? Level.WARNING : Level.GOOD);
        page.addChild(Form.section("cover.advanced_machine_controller.section").addChildren(status,
                Form.controlRow("cover.advanced_machine_controller.auto", Switch.of(this::isAutoRestart, this::setAutoRestart),
                        "cover.advanced_machine_controller.auto.tooltip"),
                Form.numberRow("cover.advanced_machine_controller.delay",
                        NumberField.ofInt(LayoutStyle.AUTO, this::getRestartDelay, this::setRestartDelay, 0, MAX_DELAY),
                        "cover.advanced_machine_controller.delay.tooltip")
                        .disabled(() -> !autoRestart, "cover.advanced_machine_controller.delay.disabled")));
        return page;
    }
}
