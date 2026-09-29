package com.gregtechceu.gtceu.uiwidgets.structure;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Layout;
import com.gregtechceu.gtceu.api.machine.multiblockpro.StructureBlocks;
import com.gregtechceu.gtceu.api.pattern.ControllerPredicate;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.uiwidgets.patternbuilder.PatternBuilderModel;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@OnlyIn(Dist.CLIENT)
public final class StructurePlans {

    private StructurePlans() {}

    public static PatternBuilderModel.Builder modelBuilder(ItemStack controller, Layout layout, boolean includeController) {
        var builder = PatternBuilderModel.builder(controller);
        var source = layout.source();
        if (source != null) {
            var tree = source.tree();
            builder.sectionTitles(section -> StructureConfigView.nodeTitle(tree, section));
        }
        var cells = layout.cells();
        var whole = layout.exclude(new boolean[0]);
        if (whole != layout) {
            var fullModel = modelBuilder(controller, whole, true).build(stack -> null);
            fullModel.selectMinimum();
            var full = assign(fullModel, whole);
            for (int i = 0; i < cells.size(); i++) {
                if (layout.isExcluded(i) && full[i] != null) builder.preplace(cells.get(i).predicate(), full[i]);
            }
        }
        var shown = new Reference2ObjectOpenHashMap<TraceabilityPredicate, BlockInfo>();
        for (int i = 0; i < cells.size(); i++) {
            var cell = cells.get(i);
            var predicate = cell.predicate();
            if (layout.isExcluded(i) || !includeController && predicate instanceof ControllerPredicate) continue;
            BlockInfo info;
            if (shown.containsKey(predicate)) {
                info = shown.get(predicate);
            } else {
                info = shown(predicate);
                shown.put(predicate, info);
            }
            builder.addCell(info, predicate, cell.layer(), Math.max(cell.node(), 0));
        }
        return builder;
    }

    private static int[] sections(Layout layout) {
        var nodes = layout.nodes();
        for (int i = 0; i < nodes.length; i++) nodes[i] = Math.max(nodes[i], 0);
        return nodes;
    }

    public static Item[] assign(PatternBuilderModel model, Layout layout) {
        return model.assign(layout.predicates(), layout.placementOrder(), layout.layers(), sections(layout));
    }

    public record Preview(Layout layout, Item[] items, BlockState controller, List<ItemStack> parts) {

        public Long2ObjectOpenHashMap<BlockState> blocks() {
            return StructurePlans.blocks(layout, items, controller);
        }

        public Long2ObjectOpenHashMap<BlockState> blocks(Predicate<Item> shown) {
            return StructurePlans.blocks(layout, items, controller, shown);
        }
    }

    public static Preview preview(MultiblockMachineDefinition definition, Layout layout) {
        var controller = definition.asStack();
        var model = modelBuilder(controller, layout, true).build(stack -> null);
        model.selectMinimum();
        var items = assign(model, layout);
        return new Preview(layout, items, StructureBlocks.controllerState(definition), parts(layout, items, controller.getItem()));
    }

    public static List<ItemStack> parts(Layout layout, Item[] items, Item controller) {
        var counts = new Reference2IntLinkedOpenHashMap<Item>();
        var cells = layout.cells();
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i).predicate() instanceof ControllerPredicate && !layout.isExcluded(i)) counts.put(controller, 1);
        }
        for (int i = 0; i < items.length; i++) {
            var item = items[i];
            if (item != null && item != controller && !layout.isExcluded(i)) counts.addTo(item, 1);
        }
        var parts = new ArrayList<ItemStack>(counts.size());
        for (var it = counts.reference2IntEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            parts.add(new ItemStack(entry.getKey(), entry.getIntValue()));
        }
        return parts;
    }

    public static Long2ObjectOpenHashMap<BlockState> previewBlocks(MultiblockMachineDefinition definition, Layout layout, Item[] items) {
        return blocks(layout, items, StructureBlocks.controllerState(definition));
    }

    public static Item[] merge(Item[] chosen, Item[] fallback) {
        var merged = fallback.clone();
        for (int i = 0; i < Math.min(chosen.length, merged.length); i++) {
            if (chosen[i] != null) merged[i] = chosen[i];
        }
        return merged;
    }

    public static Long2ObjectOpenHashMap<BlockState> blocks(Layout layout, Item[] items, BlockState controller) {
        return blocks(layout, items, controller, item -> true);
    }

    public static Long2ObjectOpenHashMap<BlockState> blocks(Layout layout, Item[] items, BlockState controller, Predicate<Item> shown) {
        var cells = layout.cells();
        var blocks = new Long2ObjectOpenHashMap<BlockState>(cells.size());
        for (int i = 0; i < cells.size(); i++) {
            var cell = cells.get(i);
            long pos = BlockPos.asLong(cell.x(), cell.y(), cell.z());
            if (cell.predicate() instanceof ControllerPredicate) {
                if (!layout.isExcluded(i) && shown.test(controller.getBlock().asItem())) blocks.put(pos, controller);
                continue;
            }
            if (items[i] == null || !shown.test(items[i])) continue;
            var base = StructureBlocks.stateOf(items[i]);
            if (base == null) continue;
            var state = StructureBlocks.face(base, layout.outwardPreview(i));
            if (!state.isAir()) blocks.put(pos, state);
        }
        return blocks;
    }

    @Nullable
    private static BlockInfo shown(TraceabilityPredicate predicate) {
        var found = new BlockInfo[1];
        predicate.forEachSimple(simple -> {
            if (found[0] != null || simple == null || simple.blockInfo == null) return;
            var info = simple.blockInfo.get();
            if (info != null && info.getBlockState().getBlock() != Blocks.AIR) found[0] = info;
        });
        return found[0];
    }
}
