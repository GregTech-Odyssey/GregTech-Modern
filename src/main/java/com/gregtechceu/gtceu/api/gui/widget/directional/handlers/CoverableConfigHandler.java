package com.gregtechceu.gtceu.api.gui.widget.directional.handlers;

import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.IUICover;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.widget.CoverConfigurator;
import com.gregtechceu.gtceu.api.gui.widget.directional.CombinedDirectionalConfigurator;
import com.gregtechceu.gtceu.api.gui.widget.directional.IDirectionalConfigHandler;
import com.gregtechceu.gtceu.api.item.IComponentItem;
import com.gregtechceu.gtceu.api.item.component.IItemComponent;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.api.transfer.item.SingleCustomItemStackHandler;
import com.gregtechceu.gtceu.common.item.CoverPlaceBehavior;
import com.gregtechceu.gtceu.uipro.elements.Button;
import com.gregtechceu.gtceu.uipro.elements.ItemSlot;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class CoverableConfigHandler implements IDirectionalConfigHandler {

    private static final IGuiTexture CONFIG_BTN_TEXTURE = new GuiTextureGroup(GuiTextures.IO_CONFIG_COVER_SETTINGS);
    private static final List<Component> CLOSE_TOOLTIPS = List.of(Component.translatable("gtceu.gui.close"));

    private final ICoverable machine;
    private CustomItemStackHandler handler;
    private Direction side;

    private ConfiguratorPanel panel;
    private ConfiguratorPanel.FloatingTab coverConfigurator;

    private ItemSlot slotWidget;
    private CoverBehavior coverBehavior;

    public CoverableConfigHandler(ICoverable machine) {
        this.machine = machine;
        this.handler = createItemStackHandler();
    }

    private CustomItemStackHandler createItemStackHandler() {
        var handler = new SingleCustomItemStackHandler(1);
        handler.setFilter(itemStack -> {
            if (itemStack.isEmpty()) return true;
            if (this.side == null) return false;
            return CoverPlaceBehavior.isCoverBehaviorItem(itemStack, () -> false,
                    coverDef -> ICoverable.canPlaceCover(coverDef, this.machine));
        });

        return handler;
    }

    @Override
    /**
     * 三视图下方的两个控件：覆盖板设置（该面的覆盖板有设置界面时可点，否则禁用并说明原因），覆盖板槽（放入 / 取出覆盖板）。
     */
    public Widget getSideSelectorWidget(SceneWidget scene, FancyMachineUIWidget machineUI) {
        WidgetGroup group = new WidgetGroup(0, 0, UISizes.SLOT * 2 + UISizes.GAP, UISizes.SLOT);
        this.panel = machineUI.getConfiguratorPanel();

        group.addWidget(Button.icon(CONFIG_BTN_TEXTURE, UISizes.SLOT).setOnClick(this::toggleConfigTab)
                .disabled(() -> side == null || coverBehavior == null || !(machine.getCoverAtSide(side) instanceof IUICover),
                        CombinedDirectionalConfigurator.NO_COVER_SETTINGS));

        slotWidget = new ItemSlot(handler, 0, true, true) {

            @Override
            public boolean canPutStack(ItemStack stack) {
                return super.canPutStack(stack) && CoverPlaceBehavior.isCoverBehaviorItem(stack, () -> false,
                        def -> def.createCoverBehavior(machine, side).canAttach());
            }
        };
        slotWidget.setChangeListener(this::coverItemChanged);
        slotWidget.setBackgroundTexture(new GuiTextureGroup(UITheme.ITEM_SLOT, GuiTextures.IO_CONFIG_COVER_SLOT_OVERLAY));
        slotWidget.setSelfPosition(new Position(UISizes.SLOT + UISizes.GAP, 0));
        group.addWidget(slotWidget);

        checkCoverBehaviour();
        // 初次打开还没选面时，覆盖板槽两端都先停用（服务端的 Shift 快速移动、伪造点击也放不进去）
        updateWidgetVisibility();

        return group;
    }

    // FIXME: This gets called twice in a single tick, causing two covers to exist simultaneously
    private void coverItemChanged() {
        if (side == null) return;
        if (!(panel.getGui().entityPlayer instanceof ServerPlayer serverPlayer)) {
            if (hasCoverChanged()) closeConfigTab();
            return;
        }

        closeConfigTab();

        var item = handler.getStackInSlot(0);
        if (machine.getCoverAtSide(side) != null) {
            machine.removeCover(false, side, serverPlayer);
        }

        if (!item.isEmpty() && machine.getCoverAtSide(side) == null) {
            if (item.getItem() instanceof IComponentItem componentItem) {
                for (IItemComponent component : componentItem.getComponents()) {
                    if (component instanceof CoverPlaceBehavior(CoverDefinition coverDefinition)) {
                        machine.placeCoverOnSide(side, item, coverDefinition, serverPlayer);
                        break;
                    }
                }
            }
        }

        checkCoverBehaviour();
    }

    private boolean hasCoverChanged() {
        // Filter settings mutate the attached stack's NBT; only structural cover changes close the tab.
        var currentCover = machine.getCoverAtSide(side);
        var item = handler.getStackInSlot(0);
        if (currentCover != coverBehavior) return true;
        if (currentCover == null) return !item.isEmpty();
        return item.isEmpty() || !ItemStack.isSameItem(item, currentCover.getAttachItem());
    }

    @Override
    public void onSideSelected(BlockPos pos, Direction side) {
        this.side = side;
        checkCoverBehaviour();
        closeConfigTab();
        // 取消选中：槽里还放着上一面的覆盖板，必须两端都停用，否则取出时机器上的覆盖板不会被拆（side 为空时不处理）
        if (side == null && slotWidget != null) updateWidgetVisibility();
    }

    private void updateWidgetVisibility() {
        var sideSelected = this.side != null;
        slotWidget.setVisible(sideSelected);
        slotWidget.setActive(sideSelected);
    }

    public void checkCoverBehaviour() {
        if (side == null)
            return;

        var coverBehaviour = machine.getCoverAtSide(side);
        if (coverBehaviour != this.coverBehavior) {
            this.coverBehavior = coverBehaviour;

            var attachItem = coverBehaviour == null ? ItemStack.EMPTY : coverBehaviour.getAttachItem();
            handler.setStackInSlot(0, attachItem);
            handler.onContentsChanged(0);
        }

        updateWidgetVisibility();
    }

    private void toggleConfigTab(ClickData cd) {
        if (this.coverConfigurator == null)
            openConfigTab();
        else
            closeConfigTab();
    }

    /**
     * 浮动标签页：内容与排布沿用 {@link CoverConfigurator}（面板标题留空，覆盖板界面上移
     * {@link CoverConfigurator#COVER_TITLE_HEIGHT}，自带的标题落进面板标题行、与右侧的关闭图标同一行），这里只把图标和说明换成"关闭"。
     */
    private void openConfigTab() {
        CoverConfigurator configurator = new CoverConfigurator(this.machine, this.side, this.coverBehavior) {

            @Override
            public IGuiTexture getIcon() {
                return GuiTextures.CLOSE_ICON;
            }

            @Override
            public List<Component> getTooltips() {
                return CLOSE_TOOLTIPS;
            }
        };

        this.coverConfigurator = this.panel.createFloatingTab(configurator);
        this.coverConfigurator.setGui(this.panel.getGui());
        this.panel.addWidget(this.coverConfigurator);
        this.panel.expandTab(this.coverConfigurator);

        coverConfigurator.onClose(() -> {
            if (coverConfigurator != null) {
                this.panel.removeWidget(this.coverConfigurator);
            }

            this.coverConfigurator = null;
        });
    }

    private void closeConfigTab() {
        if (this.coverConfigurator != null) {
            this.panel.collapseTab();
        }
    }

    @Override
    public ScreenSide getScreenSide() {
        return ScreenSide.RIGHT;
    }
}
