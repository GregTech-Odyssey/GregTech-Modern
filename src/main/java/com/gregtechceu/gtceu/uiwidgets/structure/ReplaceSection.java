package com.gregtechceu.gtceu.uiwidgets.structure;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Layout;
import com.gregtechceu.gtceu.api.machine.multiblockpro.StructureBuild;
import com.gregtechceu.gtceu.uipro.Horizontal;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.Level;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.ItemCell;
import com.gregtechceu.gtceu.uipro.elements.Switch;
import com.gregtechceu.gtceu.uipro.elements.TextLine;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uiwidgets.patternbuilder.PatternBuilderModel;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import dev.vfyjxf.taffy.style.AlignContent;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

/**
 * 建造配置页的替换方块区块：列出替换模式下将被换掉的方块（原方块→新方块×数量），每行可单独改为保留原方块。
 */
@OnlyIn(Dist.CLIENT)
final class ReplaceSection extends UIElement {

    static final Component TITLE = Component.translatable("gtceu.structure_build.replace.section");
    static final String COUNT = "gtceu.structure_build.replace.count";
    static final String REPLACE_TO = "gtceu.structure_build.replace.to";
    static final Component KEPT = Component.translatable("gtceu.structure_build.replace.kept");
    static final Component TOGGLE = Component.translatable("gtceu.structure_build.replace.toggle");
    static final Component NONE = Component.translatable("gtceu.structure_build.replace.none");
    private static final long REFRESH_MS = 200;

    private final IMultiController controller;
    private final Layout layout;
    private final PatternBuilderModel model;
    private final boolean flip;
    private final Set<StructureBuild.Swap> kept;
    private final UIElement section = UIElement.section();
    private Object2IntLinkedOpenHashMap<StructureBuild.Swap> swaps = new Object2IntLinkedOpenHashMap<>();
    private int version;
    private long refreshedAt;
    private int replacing = -1;
    private Component countText = Component.empty();

    ReplaceSection(IMultiController controller, Layout layout, PatternBuilderModel model, boolean flip, Set<StructureBuild.Swap> kept) {
        this.controller = controller;
        this.layout = layout;
        this.model = model;
        this.flip = flip;
        this.kept = kept;
        setClientSideWidget();
        layout(l -> l.column());
        addChild(section);
        refresh();
        fill();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (model.getVersion() == version || System.currentTimeMillis() - refreshedAt < REFRESH_MS) return;
        if (refresh()) fill();
    }

    private boolean refresh() {
        version = model.getVersion();
        refreshedAt = System.currentTimeMillis();
        var result = ReplaceScan.scan(controller, layout, StructurePlans.assign(model, layout), flip, Collections.emptySet());
        boolean changed = !result.swaps().equals(swaps);
        swaps = result.swaps();
        recount();
        return changed;
    }

    private void recount() {
        int total = 0;
        for (var entry : swaps.object2IntEntrySet()) {
            if (!kept.contains(entry.getKey())) total += entry.getIntValue();
        }
        if (total == replacing) return;
        replacing = total;
        countText = Component.translatable(COUNT, total);
    }

    private void fill() {
        section.clearAllWidgets();
        var header = UIElement.centeredRow(UISizes.CONTROL_HEIGHT).addChildren(
                TextLine.constant(LayoutStyle.AUTO, TITLE).layout(l -> l.flex(1)),
                TextLine.of(2 * UISizes.VALUE_WIDTH, () -> countText).setTextAlign(Horizontal.RIGHT)
                        .bindLevel(() -> replacing > 0 ? Level.WARNING : Level.NORMAL));
        section.addChild(header);
        if (swaps.isEmpty()) {
            section.addChild(TextLine.constant(LayoutStyle.AUTO, NONE).bindClientColor(() -> UITheme.TEXT_SECONDARY));
        } else {
            var entries = new ArrayList<>(swaps.object2IntEntrySet());
            entries.sort((a, b) -> Integer.compare(b.getIntValue(), a.getIntValue()));
            for (var entry : entries) section.addChild(row(entry));
        }
        UIElement.markLayoutDirty(this);
    }

    private UIElement row(Object2IntMap.Entry<StructureBuild.Swap> entry) {
        var swap = entry.getKey();
        var from = new ItemStack(swap.from());
        var to = new ItemStack(swap.to());
        var toggle = Switch.of(() -> !kept.contains(swap), on -> {
            if (on) kept.remove(swap);
            else kept.add(swap);
            recount();
        });
        toggle.tooltips(TOGGLE);
        var fromName = plainName(from);
        var replaced = Component.translatable(REPLACE_TO, plainName(to));
        var detail = TextLine.of(LayoutStyle.AUTO, () -> kept.contains(swap) ? KEPT : replaced).styled()
                .bindLevel(() -> kept.contains(swap) ? Level.NORMAL : Level.WARNING);
        var names = new UIElement().layout(l -> l.column().flex(1).minWidth(2 * UISizes.BUTTON_WIDTH).justifyContent(AlignContent.CENTER))
                .addChildren(TextLine.constant(LayoutStyle.AUTO, fromName), detail);
        names.tooltips(fromName.copy().append(" ").append(replaced));
        var count = TextLine.constant(UISizes.VALUE_WIDTH, Component.literal("×" + entry.getIntValue())).setTextAlign(Horizontal.RIGHT);
        return UIElement.centeredRow(UISizes.SLOT_SIZE).addChildren(toggle, ItemCell.constant(from), names, count);
    }

    private static MutableComponent plainName(ItemStack stack) {
        return Component.literal(ChatFormatting.stripFormatting(stack.getHoverName().getString()));
    }
}
