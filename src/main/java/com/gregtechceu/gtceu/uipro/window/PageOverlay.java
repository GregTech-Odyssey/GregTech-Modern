package com.gregtechceu.gtceu.uipro.window;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

/**
 * 页内浮层：挂在页面深处（如 AE 配置格网格里），却要画在整个窗口最上层、先拿到鼠标的小面板（配置格的数量面板等）。
 * <p>
 * LDLib1 按子控件先后绘制、不做深度遮挡，也按倒序分发点击：浮层后面加入的兄弟控件（下面几行格子）会画在它上面、先拿到点击，
 * 抬高 z 也没用。所以浮层留在原位（两端控件树不变），在所在的 {@link MachineWindow} 登记：
 * 窗口在所有子控件画完之后再画它（背景与前景），被它盖住的地方点击、滚轮、悬停、EMI 查询都先交给它，下面的控件当作鼠标不在。
 * 不在 {@link MachineWindow} 里时退回普通绘制。
 * <p>
 * 点在浮层外面（窗口里别处，或窗口外任意处）时调用 {@link #onOutsideClick}，在这次点击分发之前、输入框提交草稿之后
 * （见 {@code UIClientEvents}）；弹出式的浮层在这里关闭自己，这样点哪里都算确认。
 */
public abstract class PageOverlay extends UIElement {

    @Nullable
    private MachineWindow window;
    /// 窗口正在画浮层这一轮（普通绘制时跳过，避免画两遍、被后面的兄弟盖住）
    private boolean overlayPass;

    @Override
    public void initWidget() {
        super.initWidget();
        window = MachineWindow.of(this);
        if (window != null) window.registerOverlay(this);
    }

    /** 浮层此刻是否显示（自己和所有上级都可见）。 */
    boolean isShown() {
        for (Widget widget = this; widget != null; widget = widget.getParent()) {
            if (!widget.isVisible()) return false;
        }
        return window != null && MachineWindow.of(this) == window;
    }

    /** 显示中且鼠标在浮层上。 */
    boolean isCovering(double mouseX, double mouseY) {
        return isShown() && isMouseOverElement(mouseX, mouseY);
    }

    /** 由窗口在最后调用：画一整层（背景 + 前景）。 */
    @OnlyIn(Dist.CLIENT)
    void drawAsOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        overlayPass = true;
        var pose = graphics.pose();
        pose.pushPose();
        // 抬到页内浮层高度：盖住下面格子里的物品模型与数量文字
        pose.translate(0, 0, UITheme.PAGE_OVERLAY_Z);
        try {
            drawInBackground(graphics, mouseX, mouseY, partialTicks);
            drawInForeground(graphics, mouseX, mouseY, partialTicks);
        } finally {
            pose.popPose();
            overlayPass = false;
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public final void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (window != null && !overlayPass) return;
        drawOverlayBackground(graphics, mouseX, mouseY, partialTicks);
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public final void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (window != null && !overlayPass) return;
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
    }

    /** 客户端：显示中的浮层，这次点击不落在它上面时调用（点击分发之前）；默认什么都不做。 */
    protected void onOutsideClick() {}

    /** 浮层自己的底（外框等），在子控件之前画。 */
    @OnlyIn(Dist.CLIENT)
    protected abstract void drawOverlayBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks);

    /// 点在浮层上都由浮层吃掉，不漏到下面
    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button) || isMouseOverElement(mouseX, mouseY);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        return super.mouseWheelMove(mouseX, mouseY, wheelDelta) || isMouseOverElement(mouseX, mouseY);
    }
}
