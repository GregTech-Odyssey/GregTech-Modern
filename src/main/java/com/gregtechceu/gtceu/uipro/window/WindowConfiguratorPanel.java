package com.gregtechceu.gtceu.uipro.window;

import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.uipro.animation.Animation;
import com.gregtechceu.gtceu.uipro.animation.AnimationEngine;
import com.gregtechceu.gtceu.uipro.animation.Eases;
import com.gregtechceu.gtceu.uipro.elements.TextLine;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 机器窗口左侧的机器小组件（GTM 配置面板）。沿用 GTM {@link ConfiguratorPanel} 的数据流与排版：页面照常
 * {@code attachConfigurators}，盖板配置照常 {@code createFloatingTab}/{@code expandTab}/{@code collapseTab}；
 * 按可用高度分列，一列放不下向左加列。
 * <p>
 * 所有移动（展开、收起、内容尺寸变化）改用本框架的 {@link AnimationEngine}，不用 LDLib 的 Transform 动画：
 * 位置和尺寸在两点之间按 {@link #MOVE} 缓动，展开的内容在动画结束后才显示。
 * 位置没变的小组件不播动画——GTM 展开一个标签时会让所有标签重播一遍"收回原位"，看起来一抖一抖。
 * 动画只在客户端播，服务端直接到位。
 */
final class WindowConfiguratorPanel extends ConfiguratorPanel {

    /// 绘制高度层：整块抬到主窗口之上（主窗口里的物品模型约在 150~170），展开后伸到主窗口上方时盖得住
    private static final int LAYER_Z = 200;
    /// 小组件移动（展开、收起）的动画
    private static final Animation MOVE = Animation.of(0.2f, Eases.CUBIC_OUT);

    private final AnimationEngine animations = new AnimationEngine();
    private final Map<Widget, AnimationEngine.Playback> moving = new IdentityHashMap<>();

    WindowConfiguratorPanel() {
        super(0, 0);
        // 展开后的内边距与窗口一致
        border = UISizes.WINDOW_PADDING_X;
    }

    /**
     * 展开后的标题：GTM 用白字带阴影的 {@code TextTexture}（为深色面板设计），在窗口的浅色底上看不清；
     * 换成框架的标准文字行（正文色、超长截断、悬停显示全文），与标题栏右侧的图标（收起按钮）垂直居中。
     */
    private void restyleTitle(@Nullable WidgetGroup view, IFancyConfigurator configurator) {
        if (view == null) return;
        for (var child : java.util.List.copyOf(view.widgets)) {
            if (child instanceof ImageWidget) view.removeWidget(child);
        }
        int width = Math.max(0, view.getSizeWidth() - 2 * border - getTabSize());
        var title = TextLine.constant(width, configurator.getTitle()).setColor(UITheme.TEXT);
        title.setSelfPosition(new Position(border, (getTabSize() - UISizes.TEXT_HEIGHT) / 2 + 1));
        view.addWidget(title);
    }

    @Override
    public void attachConfigurators(IFancyConfigurator... configurators) {
        for (var configurator : configurators) {
            var tab = new AnimatedTab(configurator);
            tab.setBackground(texture);
            tabs.add(tab);
            addWidget(tab);
        }
        updateLayout();
    }

    @Override
    public FloatingTab createFloatingTab(IFancyConfigurator configurator) {
        return new AnimatedFloatingTab(configurator);
    }

    @Override
    public void clear() {
        for (var playback : moving.values()) playback.cancel();
        moving.clear();
        super.clear();
    }

    @Override
    public void expandTab(Tab tab) {
        for (int i = 0; i < tabs.size(); i++) {
            var other = tabs.get(i);
            if (other != tab) collapse(other, getTabPosition(i));
        }
        if (expanded != null && expanded != tab && !tabs.contains(expanded)) collapse(expanded, Position.ORIGIN);
        expanded = tab;
        open(tab);
    }

    @Override
    public void collapseTab() {
        if (expanded != null) {
            for (int i = 0; i < tabs.size(); i++) {
                collapse(tabs.get(i), getTabPosition(i));
            }
            if (!tabs.contains(expanded)) collapse(expanded, Position.ORIGIN);
        }
        expanded = null;
    }

    /// 标签页的 collapseTo 是 protected（只有 GTM 同包和 Tab 子类能调），经本类的标签实现调用
    private void collapse(Tab tab, Position position) {
        if (tab instanceof Draggable draggable) draggable.collapseTo(position);
        else close(tab, position.x, position.y, null);
    }

    /** 展开：移到面板左侧、放大到内容尺寸（超出屏幕时往回挪，与 GTM 相同），到位后显示内容。 */
    private void open(Tab tab) {
        var view = viewOf(tab);
        if (view == null) return;
        var size = view.getSize();
        int offsetX = 0, offsetY = 0;
        int anchor = tabs.size() > 1 ? -2 : getTabSize();
        if (isRemote() && gui != null) {
            if (tab.getParentPosition().x - size.width + anchor < 0) offsetX -= view.getParentPosition().x - size.width + anchor;
            if (tab.getParentPosition().y + size.height > gui.getScreenHeight()) offsetY -= view.getParentPosition().y + size.height - gui.getScreenHeight();
        }
        ((Draggable) tab).setDragOffset(offsetX, offsetY);
        moveTo(tab, new Position(offsetX - size.width + anchor, offsetY), size, () -> {
            view.setVisible(true);
            view.setActive(true);
        });
    }

    /** 收起：先藏起内容，再缩回标签大小移到 ({@code x}, {@code y})。 */
    private void close(Tab tab, int x, int y, @Nullable Runnable onFinished) {
        var view = viewOf(tab);
        if (view != null) {
            view.setVisible(false);
            view.setActive(false);
        }
        moveTo(tab, new Position(x, y), new Size(getTabSize(), getTabSize()), onFinished);
    }

    /** 从当前位置、尺寸缓动到目标；已经在目标上就不动（不重播动画）。服务端直接到位。 */
    private void moveTo(Widget tab, Position position, Size size, @Nullable Runnable onFinished) {
        var running = moving.remove(tab);
        if (running != null) running.cancel();
        var fromPosition = tab.getSelfPosition();
        var fromSize = tab.getSize();
        if (fromPosition.equals(position) && fromSize.equals(size) || !isRemote()) {
            tab.setSelfPosition(position);
            tab.setSize(size);
            if (onFinished != null) onFinished.run();
            return;
        }
        var playback = animations.play(MOVE, 0, 1, k -> {
            tab.setSelfPosition(new Position(lerp(fromPosition.x, position.x, k), lerp(fromPosition.y, position.y, k)));
            tab.setSize(new Size(lerp(fromSize.width, size.width, k), lerp(fromSize.height, size.height, k)));
        });
        playback.onFinished(() -> {
            moving.remove(tab);
            if (onFinished != null) onFinished.run();
        });
        moving.put(tab, playback);
    }

    private static int lerp(int from, int to, float k) {
        return Math.round(from + (to - from) * k);
    }

    @Nullable
    private static Widget viewOf(Tab tab) {
        return tab instanceof Draggable draggable ? draggable.view() : null;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        animations.updateFrame();
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, LAYER_Z);
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        pose.popPose();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, LAYER_Z);
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        pose.popPose();
    }

    /**
     * 展开的配置项是否盖住这一点（屏幕坐标）。展开后面板会伸到主窗口上方（屏幕窄时尤其明显），
     * 盖住的部分里主窗口不应再响应悬停、点击（见 {@link MachineWindow}）。
     */
    boolean isCovering(double mouseX, double mouseY) {
        return expanded != null && expanded.isVisible() &&
                isMouseOver(expanded.getPositionX(), expanded.getPositionY(), expanded.getSizeWidth(), expanded.getSizeHeight(), mouseX, mouseY);
    }

    /** 两种标签页共用：读内容控件、设拖拽偏移（GTM 的字段是 protected，只在子类里能碰）。 */
    private interface Draggable {

        @Nullable
        Widget view();

        void setDragOffset(int x, int y);

        void collapseTo(Position position);
    }

    private final class AnimatedTab extends Tab implements Draggable {

        private AnimatedTab(IFancyConfigurator configurator) {
            super(configurator);
            restyleTitle(view, configurator);
        }

        @Override
        public @Nullable Widget view() {
            return view;
        }

        @Override
        public void setDragOffset(int x, int y) {
            dragOffsetX = x;
            dragOffsetY = y;
        }

        @Override
        protected void collapseTo(int x, int y) {
            close(this, x, y, null);
        }

        @Override
        public void collapseTo(Position position) {
            collapseTo(position.x, position.y);
        }

        /// 展开时内容尺寸变了（如配置项里的列表增减）：跟着缩放到新尺寸
        @Override
        protected void onChildSizeUpdate(Widget child) {
            if (view != null && view == child && expanded == this) {
                int anchor = tabs.size() > 1 ? -2 : getTabSize();
                moveTo(this, new Position(dragOffsetX - view.getSizeWidth() + anchor, dragOffsetY), view.getSize(), () -> {
                    view.setVisible(true);
                    view.setActive(true);
                });
            }
        }
    }

    /** 盖板配置等临时打开的标签：收起后从面板移除并通知打开方。 */
    private final class AnimatedFloatingTab extends FloatingTab implements Draggable {

        private AnimatedFloatingTab(IFancyConfigurator configurator) {
            super(configurator);
            restyleTitle(view, configurator);
        }

        @Override
        public @Nullable Widget view() {
            return view;
        }

        @Override
        public void setDragOffset(int x, int y) {
            dragOffsetX = x;
            dragOffsetY = y;
        }

        @Override
        public void collapseTo(int x, int y) {
            close(this, x, y, () -> {
                WindowConfiguratorPanel.this.removeWidget(this);
                closeCallback.run();
            });
        }

        @Override
        public void collapseTo(Position position) {
            collapseTo(position.x, position.y);
        }

        @Override
        protected void onChildSizeUpdate(Widget child) {
            if (view != null && view == child && expanded == this) {
                int anchor = tabs.size() > 1 ? -2 : getTabSize();
                moveTo(this, new Position(dragOffsetX - view.getSizeWidth() + anchor, dragOffsetY), view.getSize(), () -> {
                    view.setVisible(true);
                    view.setActive(true);
                });
            }
        }
    }
}
