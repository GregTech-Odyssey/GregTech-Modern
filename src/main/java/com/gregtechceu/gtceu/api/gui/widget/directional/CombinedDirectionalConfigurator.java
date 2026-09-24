package com.gregtechceu.gtceu.api.gui.widget.directional;

import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Dock;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.client.scene.ISceneBlockRenderHook;
import com.lowdragmc.lowdraglib.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.BlockPosFace;
import com.lowdragmc.lowdraglib.utils.Position;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class CombinedDirectionalConfigurator extends WidgetGroup {

    /** 禁用原因：还没在三视图上选中一面。 */
    public static final String SELECT_SIDE = "gtceu.gui.directional_setting.select_side";
    /** 禁用原因：选中的面没有可设置的覆盖板。 */
    public static final String NO_COVER_SETTINGS = "gtceu.gui.directional_setting.no_cover_settings";

    protected final static int MOUSE_CLICK_CLIENT_ACTION_ID = 0x0001_0001;
    protected final static int UPDATE_UI_ID = 0x0001_0002;
    /// 取消选中（客户端点三视图空白处后通知服务端）
    protected final static int DESELECT_ID = 0x0001_0003;
    /// 三视图与内凹框边缘的距离（内凹框的边宽）
    private static final int SCENE_INSET = 1;
    /// 按下到松开移动不超过这么多像素才算"点击空白"，超过的是拖动视角，不取消选中
    private static final int CLICK_SLOP = 2;

    protected final IDirectionalConfigHandler[] configHandlers;
    protected final int width, height;
    private final FancyMachineUIWidget machineUI;
    private final MetaMachine machine;

    protected SceneWidget sceneWidget;
    protected ImageWidget imageWidget;

    /** 悬浮栏（底部：选中面的设置；右侧：整机设置），没有控件的不加。 */
    protected final List<Dock> docks = new ArrayList<>(2);
    /** 底部悬浮栏（只作用于选中的面）：外框用选中色，只在选中某一面时显示。 */
    protected @Nullable Dock sideDock;
    protected @Nullable BlockPos selectedPos;
    protected @Nullable Direction selectedSide;

    /// 左键按在三视图空白处（没有悬停的面）时记下按下位置，松开时没拖动就取消选中
    private boolean pressOnBlank;
    private double pressX, pressY;

    public CombinedDirectionalConfigurator(FancyMachineUIWidget machineUI, IDirectionalConfigHandler[] configHandlers,
                                           MetaMachine machine, int width, int height) {
        super(0, 0, width, height);
        this.width = width;
        this.height = height;

        this.machineUI = machineUI;
        this.configHandlers = configHandlers;
        this.machine = machine;
    }

    @Override
    public void initWidget() {
        super.initWidget();

        // 三视图铺满整页（标准尺寸），放在深色内凹框里；各配置项的控件收在底部居中的悬浮栏里
        addWidget(imageWidget = new ImageWidget(0, 0, width, sceneHeight(), UITheme.INSET));
        addWidget(sceneWidget = createSceneWidget());

        for (IDirectionalConfigHandler configHandler : configHandlers) {
            configHandler.addAdditionalUIElements(this);
        }

        addConfigWidgets(sceneWidget);
    }

    private SceneWidget createSceneWidget() {
        var pos = this.machine.getPos();

        // 面描边由 SideScene 自己画（选中色取框架的），关掉 LDLib 自带的纯绿描边
        SceneWidget sceneWidget = new SideScene(SCENE_INSET, SCENE_INSET, width - 2 * SCENE_INSET, sceneHeight() - 2 * SCENE_INSET, this.machine.getLevel())
                .setRenderedCore(List.of(pos), null)
                .setRenderSelect(false)
                .setRenderFacing(false)
                .setOnSelected(this::onSideSelected);

        if (isRemote()) {
            sceneWidget.getRenderer().addRenderedBlocks(
                    List.of(pos.above(), pos.below(), pos.north(), pos.south(), pos.east(), pos.west()),
                    new ISceneBlockRenderHook() {

                        @Override
                        @OnlyIn(Dist.CLIENT)
                        public void apply(boolean isTESR, RenderType layer) {
                            RenderSystem.enableBlend();
                            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
                        }
                    });

            sceneWidget.getRenderer().setAfterWorldRender(this::renderOverlays);

            var playerRotation = gui.entityPlayer.getRotationVector();
            sceneWidget.setCameraYawAndPitch(playerRotation.x, playerRotation.y - 90);
        }
        return sceneWidget;
    }

    private void renderOverlays(WorldSceneRenderer renderer) {
        sceneWidget.renderBlockOverLay(renderer);
        ((SideScene) sceneWidget).drawFaceBorders();

        for (Direction face : GTUtil.DIRECTIONS) {
            for (IDirectionalConfigHandler configHandler : configHandlers) {
                configHandler.renderOverlay(sceneWidget, new BlockPosFace(machine.getPos(), face));
            }
        }
    }

    /** 三视图的高：铺满整页（控件在悬浮栏里，不另占一行）。 */
    protected int sceneHeight() {
        return height;
    }

    /**
     * 各配置项的控件分两处悬浮在三视图上，作用范围不同的设置不混在一起：
     * <ul>
     * <li>只作用于选中面的（输出方式、覆盖板）：底部居中的横向悬浮栏，同一侧的配置项竖着叠成一组，屏幕左侧的组在前；</li>
     * <li>作用于整台机器的（允许从输出面输入）：右侧竖向居中的竖向悬浮栏。</li>
     * </ul>
     */
    private void addConfigWidgets(SceneWidget sceneWidget) {
        var sideDock = new Dock();
        var machineDock = Dock.vertical();
        for (var side : IDirectionalConfigHandler.ScreenSide.values()) {
            // 同一侧的选中面控件竖着叠成一组（如物品、流体两行输出方式），不同侧的组横排、中间分隔线：页面只有 162 宽
            var column = UIElement.column(LayoutStyle.AUTO).layout(l -> l.gapAll(UISizes.GAP));
            for (IDirectionalConfigHandler configHandler : configHandlers) {
                if (configHandler.getScreenSide() != side) continue;
                Widget sideWidget = configHandler.getSideSelectorWidget(sceneWidget, machineUI);
                if (sideWidget != null) column.addChild(sideWidget);
                Widget machineWidget = configHandler.getMachineWidget(sceneWidget, machineUI);
                if (machineWidget != null) machineDock.addGroup(machineWidget);
            }
            if (!column.widgets.isEmpty()) sideDock.addGroup(column);
        }
        if (!sideDock.isEmpty()) {
            sideDock.setSelfPosition(new Position((width - sideDock.getSizeWidth()) / 2, sceneHeight() - UISizes.DOCK_MARGIN - sideDock.getSizeHeight()));
            this.addWidget(sideDock);
            docks.add(sideDock);
            this.sideDock = sideDock;
            updateSideDockVisibility();
        }
        if (!machineDock.isEmpty()) {
            machineDock.setSelfPosition(new Position(width - UISizes.DOCK_MARGIN - machineDock.getSizeWidth(), (sceneHeight() - machineDock.getSizeHeight()) / 2));
            this.addWidget(machineDock);
            docks.add(machineDock);
        }
    }

    protected void onSideSelected(BlockPos pos, Direction side) {
        if (!pos.equals(machine.getPos()))
            return;

        if (this.selectedSide == side)
            return; // No need to do anything if the same side is already selected

        this.selectedSide = side;

        for (IDirectionalConfigHandler configWidget : this.configHandlers) {
            configWidget.onSideSelected(pos, side);
        }
        updateSideDockVisibility();
    }

    /**
     * 取消选中（客户端：点三视图空白处或相邻方块）：清掉三视图的选中面，各配置项收到 {@code side = null}，并通知服务端做同样的事。
     * 服务端据此禁用只作用于选中面的控件（覆盖板槽等），与客户端一致。
     */
    @OnlyIn(Dist.CLIENT)
    protected void clearSelection() {
        if (this.selectedSide == null) return;
        applyDeselect();
        writeClientAction(DESELECT_ID, buf -> {});
    }

    private void applyDeselect() {
        if (this.selectedSide == null) return;
        this.selectedSide = null;
        ((SideScene) sceneWidget).clearSelection();
        for (IDirectionalConfigHandler configHandler : this.configHandlers) {
            configHandler.onSideSelected(machine.getPos(), null);
        }
        updateSideDockVisibility();
    }

    /**
     * 底部悬浮栏只在选中某一面时显示。显隐只是客户端的观感（setVisible 不增删控件，两端控件树不变；隐藏的栏不绘制、
     * 不响应点击和悬停），服务端的栏保持原样——没选面时栏里的控件本来就由各配置项按 {@code side == null} 禁用。
     */
    private void updateSideDockVisibility() {
        if (sideDock != null && isRemote()) sideDock.setVisible(this.selectedSide != null);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        var lastSide = this.selectedSide;
        pressOnBlank = false;

        var result = super.mouseClicked(mouseX, mouseY, button);
        // 点在悬浮栏上：交给栏里的控件，不算点三视图（栏下面可能正悬停着已选中的面）；隐藏的栏不算
        for (var dock : docks) if (dock.isVisible() && dock.isMouseOverElement(mouseX, mouseY)) return result;

        if (!sceneWidget.isMouseOverElement(mouseX, mouseY)) return result;
        var hover = sceneWidget.getHoverPosFace();

        // 左键按在空白处（没有悬停机器的面）：松开时若没拖动视角就取消选中
        if (button == 0 && hover == null && this.selectedSide != null) {
            pressOnBlank = true;
            pressX = mouseX;
            pressY = mouseY;
        }

        if (this.selectedSide == lastSide && this.selectedSide != null) {
            if (hover != null && hover.pos.equals(machine.getPos()) && hover.facing == this.selectedSide) {
                var cd = new ClickData();
                writeClientAction(MOUSE_CLICK_CLIENT_ACTION_ID, buf -> {
                    cd.writeToBuf(buf);
                    buf.writeByte(this.selectedSide.ordinal());
                });
            }
        }

        return result;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        var result = super.mouseReleased(mouseX, mouseY, button);
        if (pressOnBlank && button == 0) {
            pressOnBlank = false;
            if (Math.abs(mouseX - pressX) <= CLICK_SLOP && Math.abs(mouseY - pressY) <= CLICK_SLOP &&
                    sceneWidget.getHoverPosFace() == null) {
                clearSelection();
            }
        }
        return result;
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buf) {
        if (id == DESELECT_ID) {
            applyDeselect();
            return;
        }
        if (id != MOUSE_CLICK_CLIENT_ACTION_ID) {
            super.handleClientAction(id, buf);
            return;
        }

        var clickData = ClickData.readFromBuf(buf);
        // 客户端可以伪造：方向下标越界、没有选中面、点的不是选中的面都不处理
        int index = buf.readByte();
        if (index < 0 || index >= GTUtil.DIRECTIONS.length || selectedSide == null) return;
        var side = GTUtil.DIRECTIONS[index];
        if (side != selectedSide) return;

        for (IDirectionalConfigHandler configHandler : configHandlers) {
            configHandler.handleClick(clickData, side);
        }
    }

    /**
     * 方向配置页的三视图：面描边改用框架颜色（LDLib 自带的是纯绿，已用 {@code setRenderFacing(false)} 关掉），并能取消选中。
     * 选中的面用 {@link UITheme#SELECTION_COLOR}（与槽位选中框同色），鼠标悬停的面用 {@link UITheme#SCENE_HOVER_FACE}。
     */
    private static final class SideScene extends SceneWidget {

        private SideScene(int x, int y, int width, int height, Level level) {
            super(x, y, width, height, level);
        }

        private void clearSelection() {
            selectedPosFace = null;
        }

        /** 在 {@link #renderBlockOverLay} 算完悬停面之后调用；拖动视角时悬停描边停在按下的那一面（与 LDLib 原逻辑一致）。 */
        @OnlyIn(Dist.CLIENT)
        private void drawFaceBorders() {
            var poseStack = new PoseStack();
            if (selectedPosFace != null) drawFacingBorder(poseStack, selectedPosFace, UITheme.SELECTION_COLOR);
            BlockPosFace hover = dragging ? clickPosFace : hoverPosFace;
            if (hover != null && !hover.equals(selectedPosFace)) drawFacingBorder(poseStack, hover, UITheme.SCENE_HOVER_FACE);
        }
    }
}
