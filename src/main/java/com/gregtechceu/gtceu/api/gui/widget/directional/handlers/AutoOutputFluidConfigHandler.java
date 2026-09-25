package com.gregtechceu.gtceu.api.gui.widget.directional.handlers;

import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.widget.directional.IDirectionalConfigHandler;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputFluid;
import com.gregtechceu.gtceu.uipro.elements.IconToggle;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;

import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.utils.BlockPosFace;
import com.lowdragmc.lowdraglib.utils.Position;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.vertex.PoseStack;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class AutoOutputFluidConfigHandler implements IDirectionalConfigHandler {

    /// 输出方式按钮组（默认 / 输出 / 自动）各选项的完整说明
    private static final String[] MODE_TOOLTIPS = {
            "gtceu.gui.directional_setting.fluid_output.set_default",
            "gtceu.gui.directional_setting.fluid_output.set_output",
            "gtceu.gui.directional_setting.fluid_output.set_auto" };

    private final IAutoOutputFluid machine;
    private Direction side;

    public AutoOutputFluidConfigHandler(IAutoOutputFluid machine) {
        this.machine = machine;
    }

    /** 选中面的控件：输出方式按钮组 {@code 流体 [默认][输出][自动]}（见 {@link OutputModeGroup}），没选面时禁用。 */
    @Override
    public Widget getSideSelectorWidget(SceneWidget scene, FancyMachineUIWidget machineUI) {
        return OutputModeGroup.create(MODE_TOOLTIPS, () -> side,
                machine::getOutputFacingFluids, machine::isAutoOutputFluids,
                machine::setOutputFacingFluids, machine::setAutoOutputFluids, () -> machine.self().requestSync());
    }

    /** 整机设置：允许从输出面输入（图标开关，开时为绿色），与选中哪一面无关。 */
    @Override
    public Widget getMachineWidget(SceneWidget scene, FancyMachineUIWidget machineUI) {
        return IconToggle.of(WidgetIcons.ALLOW_INPUT_FLUID,
                machine::isAllowInputFromOutputSideFluids, machine::setAllowInputFromOutputSideFluids)
                .tooltips("gtceu.gui.fluid_auto_output.allow_input.enabled", "gtceu.gui.fluid_auto_output.allow_input.disabled");
    }

    @Override
    public void onSideSelected(BlockPos pos, Direction side) {
        this.side = side;
    }

    @Override
    public ScreenSide getScreenSide() {
        return ScreenSide.LEFT;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderOverlay(SceneWidget sceneWidget, BlockPosFace blockPosFace) {
        if (machine.getOutputFacingFluids() != blockPosFace.facing)
            return;

        sceneWidget.drawFacingBorder(new PoseStack(), blockPosFace,
                machine.isAutoOutputFluids() ? 0xff00b4ff : 0x8f00b4ff, 2);
    }

    @Override
    public void addAdditionalUIElements(WidgetGroup parent) {
        LabelWidget text = new LabelWidget(4, 4, "gtceu.gui.auto_output.name") {

            @Override
            public boolean isVisible() {
                return machine.isAutoOutputFluids() && machine.getOutputFacingFluids() != null;
            }
        };

        text.setSelfPosition(new Position(parent.getSize().width - 4 - text.getSize().width, 4));

        text.setTextColor(0xff00b4ff).setDropShadow(false);
        parent.addWidget(text);
    }
}
