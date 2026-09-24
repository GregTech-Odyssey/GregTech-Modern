package com.gregtechceu.gtceu.api.gui.widget.directional.handlers;

import com.gregtechceu.gtceu.api.gui.widget.directional.CombinedDirectionalConfigurator;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.ButtonGroup;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 三视图选中面的输出方式：{@code [默认][输出][自动]}，物品与流体共用。按钮组单选，当前是哪种就选中哪个；
 * 按钮上的字写短（页面只有 162 宽），完整含义（含是物品还是流体）在各选项的悬停说明里。输出面 / 自动输出面整台机器只有一个，与原来一样：
 * 选"输出""自动"会把输出面移到这一面；在当前输出面上选"默认"则取消输出面。点击只在服务端执行，选中态由服务端判定下发。
 * <p>
 * 改完要请求机器同步（{@code onChanged}）：输出面、自动输出是机器的 {@code @SyncToClient} 字段，只在服务端调用同步时才发给客户端，
 * 机器空闲时要等周期同步（约 10 秒），三视图里的输出面描边、左上"自动"字样就会迟迟不变。
 */
final class OutputModeGroup {

    private static final String DEFAULT = "gtceu.gui.directional_setting.output_mode.default";
    private static final String OUTPUT = "gtceu.gui.directional_setting.output_mode.output";
    private static final String AUTO = "gtceu.gui.directional_setting.output_mode.auto";

    private OutputModeGroup() {}

    /**
     * @param tooltipKeys  三个选项的完整说明（默认 / 设为输出面 / 设为自动输出面）
     * @param side         当前选中的面（没选为 null）
     * @param outputFacing 机器当前的输出面
     * @param auto         是否自动输出
     * @param setFacing    设置输出面（null 为取消）
     * @param setAuto      设置自动输出
     * @param onChanged    服务端改完后调用（请求机器同步）
     */
    static UIElement create(String[] tooltipKeys, Supplier<Direction> side,
                            Supplier<Direction> outputFacing, BooleanSupplier auto,
                            Consumer<Direction> setFacing, BooleanConsumer setAuto, Runnable onChanged) {
        String[] labels = { DEFAULT, OUTPUT, AUTO };
        var group = ButtonGroup.single(3, i -> Component.translatable(labels[i]),
                () -> mode(side.get(), outputFacing.get(), auto.getAsBoolean()),
                i -> {
                    select(i, side.get(), outputFacing.get(), setFacing, setAuto);
                    onChanged.run();
                })
                .compact()
                .tooltips(i -> Component.translatable(tooltipKeys[i]));
        group.disabled(() -> side.get() == null, CombinedDirectionalConfigurator.SELECT_SIDE);
        return group;
    }

    /// 0 默认、1 输出面、2 自动输出面
    private static int mode(@Nullable Direction side, @Nullable Direction outputFacing, boolean auto) {
        if (side == null || outputFacing != side) return 0;
        return auto ? 2 : 1;
    }

    private static void select(int mode, @Nullable Direction side, @Nullable Direction outputFacing,
                               Consumer<Direction> setFacing, BooleanConsumer setAuto) {
        if (side == null) return;
        switch (mode) {
            case 0 -> {
                if (outputFacing == side) {
                    setAuto.accept(false);
                    setFacing.accept(null);
                }
            }
            case 1 -> {
                setAuto.accept(false);
                setFacing.accept(side);
            }
            case 2 -> {
                setFacing.accept(side);
                setAuto.accept(true);
            }
            default -> {}
        }
    }
}
