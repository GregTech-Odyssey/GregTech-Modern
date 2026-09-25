package com.gregtechceu.gtceu.api.gui.widget.directional;

import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;

import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.BlockPosFace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

public interface IDirectionalConfigHandler {

    /**
     * Returns the buttons to display inside the side selector（只作用于选中的面，放在三视图底部的悬浮栏）
     */
    Widget getSideSelectorWidget(SceneWidget scene, FancyMachineUIWidget machineUI);

    /**
     * 整台机器的设置控件（与选中哪一面无关，例如"允许从输出面输入"），放在三视图右侧的竖向悬浮栏里，
     * 与 {@link #getSideSelectorWidget}（只作用于选中的面，放在底部悬浮栏）分开。没有整机设置时返回 null。
     */
    @Nullable
    default Widget getMachineWidget(SceneWidget scene, FancyMachineUIWidget machineUI) {
        return null;
    }

    /**
     * Called whenever a side is selected in the side selector GUI.
     * 取消选中（点三视图空白处）时 {@code side} 为 null；两端都会调用。
     */
    void onSideSelected(BlockPos pos, @Nullable Direction side);

    /**
     * Determines which side of the screen the UI element should be placed on.
     */
    ScreenSide getScreenSide();

    enum ScreenSide {
        LEFT,
        RIGHT,
    }

    @OnlyIn(Dist.CLIENT)
    default void renderOverlay(SceneWidget sceneWidget, BlockPosFace blockPosFace) {
        // Do nothing by default
    }

    default void addAdditionalUIElements(WidgetGroup parent) {
        // Do nothing by default
    }
}
