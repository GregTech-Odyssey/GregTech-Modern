package com.gregtechceu.gtceu.api.gui.widget.directional.handlers;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.widget.directional.CombinedDirectionalConfigurator;
import com.gregtechceu.gtceu.api.gui.widget.directional.IDirectionalConfigHandler;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputFluid;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputItem;
import com.gregtechceu.gtceu.uipro.elements.Button;
import com.gregtechceu.gtceu.uipro.elements.IconToggle;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.utils.BlockPosFace;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.vertex.PoseStack;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class AutoOutputItemConfigHandler implements IDirectionalConfigHandler {

    /// 输出模式图标（按钮底由框架的按钮画）：关 / 输出 / 自动输出
    private static final IGuiTexture ICON_OFF = GuiTextures.IO_CONFIG_ITEM_MODES_BUTTON.getSubTexture(0, 0, 1, 1 / 3f);
    private static final IGuiTexture ICON_OUTPUT = GuiTextures.IO_CONFIG_ITEM_MODES_BUTTON.getSubTexture(0, 1 / 3f, 1, 1 / 3f);
    private static final IGuiTexture ICON_AUTO = GuiTextures.IO_CONFIG_ITEM_MODES_BUTTON.getSubTexture(0, 2 / 3f, 1, 1 / 3f);
    /// 输出模式按钮的说明：当前状态 + 点击会怎样
    private static final String MODE_OFF = "gtceu.gui.directional_setting.item_output.off";
    private static final String MODE_OUTPUT = "gtceu.gui.directional_setting.item_output.output";
    private static final String MODE_AUTO = "gtceu.gui.directional_setting.item_output.auto";

    private final IAutoOutputItem machine;
    private Direction side;
    private Button ioModeButton;

    public AutoOutputItemConfigHandler(IAutoOutputItem machine) {
        this.machine = machine;
    }

    @Override
    /**
     * 选中面的控件：输出模式（点一下在 不从这面输出 → 输出 → 自动输出 之间切换，图标随状态变化；没选面时禁用）。
     * 悬停说明当前状态和点击会怎样，由服务端按选中的面判定后下发。
     */
    public Widget getSideSelectorWidget(SceneWidget scene, FancyMachineUIWidget machineUI) {
        IGuiTexture modeIcon = (graphics, mouseX, mouseY, x, y, width, height) -> {
            IGuiTexture icon = machine.getOutputFacingItems() != side ? ICON_OFF : machine.isAutoOutputItems() ? ICON_AUTO : ICON_OUTPUT;
            icon.draw(graphics, mouseX, mouseY, x, y, width, height);
        };
        ioModeButton = Button.icon(modeIcon, UISizes.SLOT).setOnClick(this::onIOModePressed)
                .disabled(() -> side == null, CombinedDirectionalConfigurator.SELECT_SIDE)
                .bindTooltip(() -> Component.translatable(machine.getOutputFacingItems() != side ? MODE_OFF :
                        machine.isAutoOutputItems() ? MODE_AUTO : MODE_OUTPUT));
        return ioModeButton;
    }

    /** 整机设置：允许从输出面输入（图标开关，开时为绿色），与选中哪一面无关。 */
    @Override
    public Widget getMachineWidget(SceneWidget scene, FancyMachineUIWidget machineUI) {
        return IconToggle.of(GuiTextures.BUTTON_ITEM_OUTPUT,
                machine::isAllowInputFromOutputSideItems, machine::setAllowInputFromOutputSideItems)
                .tooltips("gtceu.gui.item_auto_output.allow_input.enabled", "gtceu.gui.item_auto_output.allow_input.disabled");
    }

    private void onIOModePressed(ClickData cd) {
        if (this.side == null)
            return;

        if (machine.getOutputFacingItems() == this.side) {
            machine.setAutoOutputItems(!machine.isAutoOutputItems());
        } else {
            machine.setAutoOutputItems(false);
            machine.setOutputFacingItems(this.side);
        }
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
    public void handleClick(ClickData cd, Direction direction) {
        if (!canHandleClick(cd) || !machine.hasAutoOutputItem())
            return;

        if (machine.getOutputFacingItems() != side) {
            machine.setOutputFacingItems(side);
            machine.setAutoOutputItems(false);
        } else {
            machine.setAutoOutputItems(!machine.isAutoOutputItems());
        }
    }

    @SuppressWarnings("RedundantIfStatement") // Cleaner code this way
    private boolean canHandleClick(ClickData cd) {
        if (cd.button == 0)
            return true;

        if (!(machine instanceof IAutoOutputFluid) && cd.button == 1)
            return true;

        return false;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderOverlay(SceneWidget sceneWidget, BlockPosFace blockPosFace) {
        if (machine.getOutputFacingItems() != blockPosFace.facing)
            return;

        sceneWidget.drawFacingBorder(new PoseStack(), blockPosFace,
                machine.isAutoOutputItems() ? 0xffff6e0f : 0x8fff6e0f, 1);
    }

    @Override
    public void addAdditionalUIElements(WidgetGroup parent) {
        LabelWidget text = new LabelWidget(4, 4, "gtceu.gui.auto_output.name") {

            @Override
            public boolean isVisible() {
                return machine.isAutoOutputItems() && machine.getOutputFacingItems() != null;
            }
        };

        text.setTextColor(0xffff6e0f).setDropShadow(false);
        parent.addWidget(text);
    }
}
