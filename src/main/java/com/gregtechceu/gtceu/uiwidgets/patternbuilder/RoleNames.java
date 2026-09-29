package com.gregtechceu.gtceu.uiwidgets.patternbuilder;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.predicates.PredicateAbilities;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

final class RoleNames {

    private static final Pattern TIER_TOKEN = Pattern.compile(
            "(?<![A-Za-z])(" + String.join("|", GTValues.VN) + ")(?![A-Za-z])");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private Function<PartAbility, Component> abilityNames = ability -> null;
    @Nullable
    private ReferenceOpenHashSet<Block> partBlocks;
    @Nullable
    private Reference2ObjectLinkedOpenHashMap<PartAbility, ReferenceOpenHashSet<Block>> abilityBlocks;

    void setAbilityNames(Function<PartAbility, Component> abilityNames) {
        this.abilityNames = abilityNames;
    }

    boolean isPart(Item item) {
        if (!(item instanceof BlockItem blockItem)) return false;
        if (partBlocks == null) {
            partBlocks = new ReferenceOpenHashSet<>();
            for (var blocks : abilityBlocks().values()) partBlocks.addAll(blocks);
        }
        return partBlocks.contains(blockItem.getBlock());
    }

    private Reference2ObjectLinkedOpenHashMap<PartAbility, ReferenceOpenHashSet<Block>> abilityBlocks() {
        if (abilityBlocks == null) {
            abilityBlocks = new Reference2ObjectLinkedOpenHashMap<>();
            for (var ability : PartAbility.getAll()) {
                var blocks = ability.getAllBlocks();
                if (!blocks.isEmpty()) abilityBlocks.put(ability, new ReferenceOpenHashSet<>(blocks));
            }
        }
        return abilityBlocks;
    }

    Component roleName(SimplePredicate simple, List<Item> items) {
        if (!(simple instanceof PredicateAbilities abilities)) return roleName(items);
        var name = abilityName(abilities.getAbilities());
        if (abilities.getExcluded().length == 0) return name;
        return Component.translatable(PatternBuilderModel.ROLE_EXCEPT, name, abilityName(abilities.getExcluded()));
    }

    private Component abilityName(PartAbility[] abilities) {
        var result = Component.empty();
        for (int i = 0; i < abilities.length; i++) {
            if (i > 0) result.append(" / ");
            var custom = abilityNames.apply(abilities[i]);
            result.append(custom != null ? custom : abilities[i].getDisplayName());
        }
        return result;
    }

    private Component roleName(List<Item> items) {
        var blocks = new ReferenceOpenHashSet<Block>();
        for (var item : items) blocks.add(((BlockItem) item).getBlock());
        PartAbility superset = null;
        int supersetSize = Integer.MAX_VALUE;
        var subsets = new ArrayList<Map.Entry<PartAbility, ReferenceOpenHashSet<Block>>>();
        for (var entry : abilityBlocks().entrySet()) {
            var set = entry.getValue();
            if (set.containsAll(blocks)) {
                if (set.size() < supersetSize && abilityNames.apply(entry.getKey()) != null) {
                    superset = entry.getKey();
                    supersetSize = set.size();
                }
            } else if (blocks.containsAll(set) && abilityNames.apply(entry.getKey()) != null) {
                subsets.add(entry);
            }
        }
        if (superset != null) return abilityNames.apply(superset);
        subsets.sort(Comparator.comparingInt(entry -> -entry.getValue().size()));
        var covered = new ReferenceOpenHashSet<Block>();
        var names = new ArrayList<Component>();
        for (var entry : subsets) {
            if (covered.containsAll(entry.getValue())) continue;
            covered.addAll(entry.getValue());
            names.add(abilityNames.apply(entry.getKey()));
            if (covered.size() == blocks.size()) break;
        }
        if (covered.size() == blocks.size() && !names.isEmpty()) {
            var name = Component.empty().append(names.get(0));
            for (int i = 1; i < names.size(); i++) name.append(" / ").append(names.get(i));
            return name;
        }
        return commonName(items);
    }

    private static Component commonName(List<Item> items) {
        var counts = new Object2IntLinkedOpenHashMap<String>();
        for (var item : items) {
            var name = ChatFormatting.stripFormatting(item.getDescription().getString());
            if (name == null) continue;
            name = SPACES.matcher(TIER_TOKEN.matcher(name).replaceAll("")).replaceAll(" ").strip();
            if (!name.isEmpty()) counts.addTo(name, 1);
        }
        String best = null;
        int bestCount = 0;
        for (var it = counts.object2IntEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            if (entry.getIntValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getIntValue();
            }
        }
        if (best == null) return items.get(0).getDescription();
        if (bestCount == items.size()) return Component.literal(best);
        return Component.translatable(PatternBuilderModel.ROLE_MORE, best, items.size());
    }
}
