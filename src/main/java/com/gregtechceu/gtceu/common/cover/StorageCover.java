package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.IUICover;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.gui.widget.CoverConfigurator;
import com.gregtechceu.gtceu.api.machine.MachineCoverContainer;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.api.transfer.item.SingleCustomItemStackHandler;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.TextLine;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uiwidgets.inventory.SlotGridView;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class StorageCover extends CoverBehavior implements IUICover {

    @SaveToDisk
    @SyncToClient
    public final CustomItemStackHandler inventory;
    private final int SIZE = 18;
    /// 标题文字在标题行里离顶边的距离（与 GTM 其他覆盖板界面的标题同高）
    private static final int TITLE_TOP = 5;

    public StorageCover(@NotNull CoverDefinition definition, @NotNull ICoverable coverableView,
                        @NotNull Direction attachedSide) {
        super(definition, coverableView, attachedSide);
        inventory = new SingleCustomItemStackHandler(SIZE);
    }

    @Override
    @NotNull
    public List<ItemStack> getAdditionalDrops() {
        var list = super.getAdditionalDrops();
        for (int slot = 0; slot < SIZE; slot++) {
            list.add(inventory.getStackInSlot(slot));
        }
        return list;
    }

    @Override
    public boolean canAttach() {
        if (!(coverHolder instanceof MachineCoverContainer)) return false;
        for (var dir : Direction.values()) {
            if (coverHolder.hasCover(dir) && coverHolder.getCoverAtSide(dir) instanceof StorageCover)
                return false;
        }
        return super.canAttach();
    }

    /**
     * 覆盖板自己的界面（独立界面 {@link IUICover#createUI}，以及三视图配置里展开的 {@link CoverConfigurator}）：
     * 顶部 {@link CoverConfigurator#COVER_TITLE_HEIGHT} 高的标题行（覆盖板界面的排布约定，展开时它落进面板标题行），
     * 下面是与左侧配置项相同的槽网格（{@link SlotGridView}）。底部留 {@link UISizes#SECTION_GAP}，独立界面里与下方玩家背包隔开。
     */
    @Override
    public Widget createUIWidget() {
        var title = UIElement.row(CoverConfigurator.COVER_TITLE_HEIGHT).layout(l -> l.alignStart().paddingTop(TITLE_TOP))
                .addChild(TextLine.translatable(0, getUITitle()).layout(l -> l.flex(1)));
        return new UIElement().layout(l -> l.column().paddingBottom(UISizes.SECTION_GAP))
                .addChildren(title, SlotGridView.items(inventory));
    }

    private String getUITitle() {
        return "cover.storage.title";
    }

    @Override
    public @Nullable IFancyConfigurator getConfigurator() {
        return new StorageCoverConfigurator();
    }

    private class StorageCoverConfigurator implements IFancyConfigurator {

        @Override
        public Component getTitle() {
            return Component.translatable("cover.storage.title");
        }

        @Override
        public IGuiTexture getIcon() {
            return GuiTextures.STORAGE_ICON;
        }

        /**
         * 展开后的内容：标准物品槽紧排成的小网格（{@link SlotGridView}，与共享物品库同一组件）。
         * 标题、底图和内边距由左侧配置面板统一处理，这里不再留标题空位、不写死偏移。
         * 槽位是容器槽，物品经原版容器同步。
         */
        @Override
        public Widget createConfigurator() {
            return SlotGridView.items(inventory);
        }
    }
}
