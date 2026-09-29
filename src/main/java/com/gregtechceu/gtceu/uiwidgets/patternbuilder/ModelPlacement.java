package com.gregtechceu.gtceu.uiwidgets.patternbuilder;

import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;

import net.minecraft.world.item.Item;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class ModelPlacement {

    private ModelPlacement() {}

    static Item[] assign(PatternBuilderModel model, List<TraceabilityPredicate> cells, IntComparator order, @Nullable int[] layers,
                         @Nullable int[] sections) {
        var result = new Item[cells.size()];
        var byGroup = new Reference2ObjectLinkedOpenHashMap<PatternBuilderModel.Group, IntArrayList>();
        for (int i = 0; i < result.length; i++) {
            var predicate = cells.get(i);
            var group = predicate == null ? null : model.groupOf.get(new PatternBuilderModel.GroupKey(predicate, sections == null ? 0 : sections[i]));
            if (group != null) {
                byGroup.computeIfAbsent(group, g -> new IntArrayList()).add(i);
                continue;
            }
            var item = model.fixedOf.get(predicate);
            if (item == null) continue;
            var entry = model.fixedByItem.get(item);
            if (entry == null || entry.included) result[i] = item;
        }
        for (var it = byGroup.reference2ObjectEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            fillGroup(model, entry.getKey(), entry.getValue(), order, layers, result);
        }
        return result;
    }

    private static void fillGroup(PatternBuilderModel model, PatternBuilderModel.Group group, IntArrayList positions, IntComparator order,
                                  @Nullable int[] layers, Item[] result) {
        positions.sort(order);
        var used = new boolean[positions.size()];
        if (layers != null) {
            for (var candidate : model.allocationOrder) {
                if (candidate.layerMax < 0) continue;
                int take = candidate.taken.getInt(group);
                if (take > 0) placeByLayer(candidate, take, positions, layers, used, result);
            }
        }
        int next = 0;
        for (var candidate : model.allocationOrder) {
            if (layers != null && candidate.layerMax >= 0) continue;
            int take = candidate.taken.getInt(group);
            for (int t = 0; t < take; t++) {
                while (next < positions.size() && used[next]) next++;
                if (next >= positions.size()) break;
                used[next] = true;
                result[positions.getInt(next++)] = candidate.stack.getItem();
            }
        }
        if (!group.included || !group.hasFill()) return;
        var fill = group.getFillStack().getItem();
        for (int i = 0; i < positions.size(); i++) {
            if (!used[i]) result[positions.getInt(i)] = fill;
        }
    }

    private static void placeByLayer(PatternBuilderModel.Candidate candidate, int take, IntArrayList positions, int[] layers, boolean[] used,
                                     Item[] result) {
        var byLayer = new Int2ObjectAVLTreeMap<IntArrayList>();
        for (int i = 0; i < positions.size(); i++) {
            if (!used[i]) byLayer.computeIfAbsent(layers[positions.getInt(i)], l -> new IntArrayList()).add(i);
        }
        var placed = new Int2IntOpenHashMap();
        boolean progress = true;
        while (take > 0 && progress) {
            progress = false;
            for (var it = byLayer.int2ObjectEntrySet().iterator(); it.hasNext();) {
                if (take == 0) break;
                var entry = it.next();
                int layer = entry.getIntKey();
                if (layer >= 0 && placed.get(layer) >= candidate.layerMax) continue;
                var list = entry.getValue();
                for (int k = 0; k < list.size(); k++) {
                    int index = list.getInt(k);
                    if (used[index]) continue;
                    used[index] = true;
                    result[positions.getInt(index)] = candidate.stack.getItem();
                    placed.addTo(layer, 1);
                    take--;
                    progress = true;
                    break;
                }
            }
        }
    }
}
