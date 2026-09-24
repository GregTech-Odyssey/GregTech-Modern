package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.IconToggle;
import com.gregtechceu.gtceu.uipro.elements.ScrollerView;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;
import com.gregtechceu.gtceu.uiwidgets.inventory.HatchViews;
import com.gregtechceu.gtceu.uiwidgets.inventory.SlotGridView;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.utils.Position;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.NotNull;

public class SteamItemBusPartMachine extends ItemBusPartMachine {

    public SteamItemBusPartMachine(MetaMachineBlockEntity holder, IO io, Object... args) {
        super(holder, 1, io, args);
    }

    @NotNull
    @Override
    public ModularUI createUI(@NotNull Player entityPlayer) {
        // 保留蒸汽皮肤（铜/钢底板、蒸汽槽）；内容与其他总线一样：操作区居中，槽位网格旁边一列开关
        boolean steel = ConfigHolder.INSTANCE.machines.steelSteamMultiblocks;
        var slotTexture = GuiTextures.SLOT_STEAM.get(steel);
        var toggles = new UIElement().layout(l -> l.column().gapAll(UISizes.GAP));
        var auto = io == IO.IN ?
                IconToggle.of(WidgetIcons.IMPORT, this::isWorkingEnabled, this::setWorkingEnabled)
                        .tooltips("gtceu.gui.item_auto_input.tooltip.enabled", "gtceu.gui.item_auto_input.tooltip.disabled") :
                IconToggle.of(WidgetIcons.EXPORT, this::isWorkingEnabled, this::setWorkingEnabled)
                        .tooltips("gtceu.gui.item_auto_output.tooltip.enabled", "gtceu.gui.item_auto_output.tooltip.disabled");
        toggles.addChild(auto);
        if (io == IO.IN) {
            toggles.addChild(IconToggle.of(WidgetIcons.INPUT_LIMIT_ON, this::isInputLimit, this::setInputLimit)
                    .tooltips("gtceu.multiblock.steam.input_limit.enabled", "gtceu.multiblock.steam.input_limit.disabled"));
        }
        var operation = HatchViews.operations(HatchViews.items(getInventory().storage, io, slotTexture), toggles);
        int slots = getInventorySize();
        int rows = Math.min(SlotGridView.rows(slots), HatchViews.MAX_GRID_ROWS);
        int gridWidth = SlotGridView.columns(slots) * UISizes.SLOT + (SlotGridView.rows(slots) > rows ? ScrollerView.SCROLL_BAR_SPACE : 0);
        // 槽位多到放不下时窗口随之加宽、加高（皮肤是可拉伸的边框）
        int contentWidth = Math.max(UISizes.CONTENT_WIDTH, gridWidth + HatchViews.GROUP_GAP + UISizes.SLOT);
        int contentHeight = Math.max(SteamHatchPartMachine.STEAM_CONTENT_HEIGHT, rows * UISizes.SLOT);
        int width = contentWidth + 2 * UISizes.WINDOW_PADDING_X;
        var content = HatchViews.fixedPage(contentWidth, contentHeight, operation);
        content.setSelfPosition(new Position(UISizes.WINDOW_PADDING_X, SteamHatchPartMachine.STEAM_CONTENT_Y));
        int inventoryX = (width - UISizes.WINDOW_WIDTH) / 2 + UISizes.WINDOW_PADDING_X;
        int inventoryY = SteamHatchPartMachine.STEAM_CONTENT_Y + contentHeight + UISizes.SECTION_GAP;
        return new ModularUI(width, inventoryY + UISizes.PLAYER_INVENTORY_HEIGHT + UISizes.WINDOW_PADDING_BOTTOM, this, entityPlayer)
                .background(GuiTextures.BACKGROUND_STEAM.get(steel))
                .widget(new LabelWidget(UISizes.WINDOW_PADDING_X, 6, getBlockState().getBlock().getDescriptionId()))
                .widget(content)
                .widget(UITemplate.bindPlayerInventory(entityPlayer.getInventory(), slotTexture, inventoryX, inventoryY, true));
    }

    @Override
    public boolean swapIO() {
        BlockPos blockPos = getHolder().pos();
        MachineDefinition newDefinition = null;
        if (io == IO.IN) {
            newDefinition = GTMachines.STEAM_EXPORT_BUS;
        } else if (io == IO.OUT) {
            newDefinition = GTMachines.STEAM_IMPORT_BUS;
        }

        if (newDefinition == null) return false;
        BlockState newBlockState = newDefinition.get().defaultBlockState();

        getLevel().setBlockAndUpdate(blockPos, newBlockState);

        if (getLevel().getBlockEntity(blockPos) instanceof MetaMachineBlockEntity newHolder) {
            if (newHolder.getMetaMachine() instanceof SteamItemBusPartMachine newMachine) {
                // We don't set the circuit or distinct busses, since
                // that doesn't make sense on an output bus.
                // Furthermore, existing inventory items
                // and conveyors will drop to the floor on block override.
                newMachine.setFrontFacing(this.getFrontFacing());
                newMachine.setUpwardsFacing(this.getUpwardsFacing());
            }
        }
        return true;
    }
}
