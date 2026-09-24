package com.gregtechceu.gtceu.api.gui.widget.directional;

import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
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
    /// 三视图与内凹框边缘的距离（内凹框的边宽）
    private static final int SCENE_INSET = 1;

    protected final IDirectionalConfigHandler[] configHandlers;
    protected final int width, height;
    private final FancyMachineUIWidget machineUI;
    private final MetaMachine machine;

    protected SceneWidget sceneWidget;
    protected ImageWidget imageWidget;

    /** 悬浮栏（底部：选中面的设置；右侧：整机设置），没有控件的不加。 */
    protected final List<Dock> docks = new ArrayList<>(2);
    protected @Nullable BlockPos selectedPos;
    protected @Nullable Direction selectedSide;

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

        SceneWidget sceneWidget = new SceneWidget(SCENE_INSET, SCENE_INSET, width - 2 * SCENE_INSET, sceneHeight() - 2 * SCENE_INSET, this.machine.getLevel())
                .setRenderedCore(List.of(pos), null)
                .setRenderSelect(false)
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
     * <li>只作用于选中面的（输出面、覆盖板）：底部居中的横向悬浮栏，每个配置项一组，屏幕左侧的配置项在前；</li>
     * <li>作用于整台机器的（允许从输出面输入）：右侧竖向居中的竖向悬浮栏。</li>
     * </ul>
     */
    private void addConfigWidgets(SceneWidget sceneWidget) {
        var sideDock = new Dock();
        var machineDock = Dock.vertical();
        for (var side : IDirectionalConfigHandler.ScreenSide.values()) {
            for (IDirectionalConfigHandler configHandler : configHandlers) {
                if (configHandler.getScreenSide() != side) continue;
                Widget sideWidget = configHandler.getSideSelectorWidget(sceneWidget, machineUI);
                if (sideWidget != null) sideDock.addGroup(sideWidget);
                Widget machineWidget = configHandler.getMachineWidget(sceneWidget, machineUI);
                if (machineWidget != null) machineDock.addGroup(machineWidget);
            }
        }
        if (!sideDock.isEmpty()) {
            sideDock.setSelfPosition(new Position((width - sideDock.getSizeWidth()) / 2, sceneHeight() - UISizes.DOCK_MARGIN - sideDock.getSizeHeight()));
            this.addWidget(sideDock);
            docks.add(sideDock);
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
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        var lastSide = this.selectedSide;

        var result = super.mouseClicked(mouseX, mouseY, button);
        // 点在悬浮栏上：交给栏里的控件，不算点三视图（栏下面可能正悬停着已选中的面）
        for (var dock : docks) if (dock.isMouseOverElement(mouseX, mouseY)) return result;

        if (isMouseOverElement(mouseX, mouseY) && this.selectedSide == lastSide && this.selectedSide != null) {
            var hover = sceneWidget.getHoverPosFace();

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
    public void handleClientAction(int id, FriendlyByteBuf buf) {
        if (id != MOUSE_CLICK_CLIENT_ACTION_ID) {
            super.handleClientAction(id, buf);
            return;
        }

        var clickData = ClickData.readFromBuf(buf);
        var side = GTUtil.DIRECTIONS[buf.readByte()];

        for (IDirectionalConfigHandler configHandler : configHandlers) {
            configHandler.handleClick(clickData, side);
        }
    }
}
