package com.gregtechceu.gtceu.uiwidgets.patternbuilder;

import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class PatternBuilderModel {

    public record Stock(long stored, boolean craftable) {}

    public record Input(ItemStack stack, long count) {}

    public enum Sort {
        STOCK,
        CRAFTABLE,
        NONE
    }

    public static final class Fixed {

        private final ItemStack stack;
        private final List<Group> merged = new ArrayList<>();
        private int count;
        private boolean included = true;
        @Nullable
        private PatternBuilderModel owner;

        private Fixed(ItemStack stack) {
            this.stack = stack;
        }

        public ItemStack getStack() {
            return stack;
        }

        public int getCount() {
            int total = count;
            for (var group : merged) total += group.getFillCount();
            return total;
        }

        public boolean isIncluded() {
            return included;
        }

        public void setIncluded(boolean included) {
            this.included = included;
            for (var group : merged) group.included = included;
            if (owner != null) owner.inputs = null;
        }
    }

    public static final class Group {

        private final List<ItemStack> fills = new ArrayList<>();
        private final List<ItemStack> sortedFills = new ArrayList<>();
        private final Reference2IntOpenHashMap<Item> shown = new Reference2IntOpenHashMap<>();
        private int positions;
        private int fill;
        private int allocated;
        private boolean included = true;
        @Nullable
        private PatternBuilderModel owner;

        public List<ItemStack> getFills() {
            return fills;
        }

        public List<ItemStack> getSortedFills() {
            return sortedFills;
        }

        public void setFill(ItemStack stack) {
            setFill(fills.indexOf(stack));
        }

        public boolean hasFill() {
            return !fills.isEmpty();
        }

        public int getFill() {
            return fill;
        }

        public void setFill(int fill) {
            if (fill >= 0 && fill < fills.size()) this.fill = fill;
            if (owner != null) owner.inputs = null;
        }

        public ItemStack getFillStack() {
            return fills.isEmpty() ? ItemStack.EMPTY : fills.get(fill);
        }

        public int getPositions() {
            return positions;
        }

        public int getFillCount() {
            return Math.max(0, positions - allocated);
        }

        public boolean isIncluded() {
            return included;
        }

        public void setIncluded(boolean included) {
            this.included = included;
            if (owner != null) owner.inputs = null;
        }
    }

    public final class Candidate {

        private final ItemStack stack;
        private final List<Group> groups = new ArrayList<>();
        private int selected;

        private Candidate(ItemStack stack) {
            this.stack = stack;
        }

        public ItemStack getStack() {
            return stack;
        }

        public int getSelected() {
            return selected;
        }

        public void setSelected(int selected) {
            this.selected = Math.max(0, Math.min(selected, getMax()));
            reallocate();
        }

        public int getMax() {
            int free = 0;
            for (var group : groups) free += group.positions - group.allocated;
            return selected + free;
        }
    }

    public final class Role {

        private final Component name;
        private final int min;
        private final int max;
        private final List<Candidate> candidates = new ArrayList<>();
        private final List<Candidate> original = new ArrayList<>();

        private Role(Component name, int min, int max) {
            this.name = name;
            this.min = min;
            this.max = max;
        }

        public Component getName() {
            return name;
        }

        public int getMin() {
            return min;
        }

        public int getMaxCount() {
            return max;
        }

        public boolean isRequired() {
            return min > 0;
        }

        public List<Candidate> getCandidates() {
            return candidates;
        }

        public int getTally() {
            int tally = 0;
            for (var candidate : candidates) tally += candidate.selected;
            return tally;
        }

        public boolean isSatisfied() {
            int tally = getTally();
            return tally >= Math.max(min, 0) && (max < 0 || tally <= max);
        }
    }

    private final List<Fixed> fixed;
    private final List<Group> groups;
    private final List<Group> allGroups;
    private final List<Candidate> candidates;
    private final List<Candidate> allocationOrder;
    private final List<Role> roles;
    private final Function<ItemStack, Stock> stock;
    private int overflow;
    @Nullable
    private List<Input> inputs;

    private PatternBuilderModel(Builder builder, Function<ItemStack, Stock> stock) {
        this.stock = stock;
        var groupList = new ArrayList<>(builder.groups.values());
        groupList.sort(Comparator.comparingInt(g -> -g.positions));
        this.allGroups = List.copyOf(groupList);
        var fixedMap = new Reference2ObjectLinkedOpenHashMap<>(builder.fixed);
        var standalone = new ArrayList<Group>();
        for (var group : allGroups) {
            group.owner = this;
            int best = -1;
            for (int i = 0; i < group.fills.size(); i++) {
                if (best < 0 || group.shown.getInt(group.fills.get(i).getItem()) > group.shown.getInt(group.fills.get(best).getItem())) best = i;
            }
            group.fill = Math.max(best, 0);
            if (group.fills.size() == 1) {
                var item = group.fills.get(0).getItem();
                fixedMap.computeIfAbsent(item, i -> new Fixed(new ItemStack(item))).merged.add(group);
            } else if (group.fills.size() > 1) {
                standalone.add(group);
            }
        }
        this.groups = List.copyOf(standalone);
        var fixedList = new ArrayList<>(fixedMap.values());
        fixedList.sort(Comparator.comparing((Fixed f) -> !ItemStack.isSameItem(f.stack, builder.controller)).thenComparingInt(f -> -f.getCount()));
        for (var entry : fixedList) entry.owner = this;
        this.fixed = List.copyOf(fixedList);
        var candidateMap = new Reference2ObjectLinkedOpenHashMap<Item, Candidate>();
        var roleList = new ArrayList<Role>();
        for (var draft : builder.roles.values()) {
            var role = new Role(draft.name, draft.min, draft.max);
            for (var item : draft.items) {
                var candidate = candidateMap.computeIfAbsent(item, i -> new Candidate(new ItemStack(item)));
                for (var group : builder.itemGroups.get(item)) {
                    if (!candidate.groups.contains(group)) candidate.groups.add(group);
                }
                role.candidates.add(candidate);
            }
            role.original.addAll(role.candidates);
            roleList.add(role);
        }
        roleList.sort(Comparator.comparing(role -> !role.isRequired()));
        this.roles = List.copyOf(roleList);
        this.candidates = List.copyOf(candidateMap.values());
        var order = new ArrayList<>(candidates);
        order.sort(Comparator.comparingInt(c -> c.groups.size()));
        this.allocationOrder = List.copyOf(order);
    }

    public static Builder builder(ItemStack controller) {
        return new Builder(controller);
    }

    public List<Fixed> getFixed() {
        return fixed;
    }

    public List<Group> getGroups() {
        return groups;
    }

    public List<Role> getRoles() {
        return roles;
    }

    public boolean isEmpty() {
        return fixed.isEmpty() && groups.isEmpty();
    }

    @Nullable
    public Stock getStock(ItemStack stack) {
        return stock.apply(stack);
    }

    public boolean hasOverflow() {
        return overflow > 0;
    }

    public List<Input> getInputs() {
        if (inputs == null) inputs = collectInputs();
        return inputs;
    }

    private List<Input> collectInputs() {
        var merged = new Reference2LongLinkedOpenHashMap<Item>();
        var stacks = new Reference2ObjectOpenHashMap<Item, ItemStack>();
        for (var entry : fixed) {
            int count = entry.getCount();
            if (entry.included && count > 0) add(merged, stacks, entry.stack, count);
        }
        for (var group : groups) {
            if (group.included && group.getFillCount() > 0) add(merged, stacks, group.getFillStack(), group.getFillCount());
        }
        for (var candidate : candidates) {
            if (candidate.selected > 0) add(merged, stacks, candidate.stack, candidate.selected);
        }
        var inputs = new ArrayList<Input>(merged.size());
        for (var entry : merged.reference2LongEntrySet()) inputs.add(new Input(stacks.get(entry.getKey()), entry.getLongValue()));
        return inputs;
    }

    private static void add(Reference2LongLinkedOpenHashMap<Item> merged, Reference2ObjectOpenHashMap<Item, ItemStack> stacks, ItemStack stack, long count) {
        merged.addTo(stack.getItem(), count);
        stacks.putIfAbsent(stack.getItem(), stack);
    }

    private void reallocate() {
        inputs = null;
        for (var group : allGroups) group.allocated = 0;
        overflow = 0;
        for (var candidate : allocationOrder) {
            int remaining = candidate.selected;
            for (var group : candidate.groups) {
                int take = Math.min(remaining, group.positions - group.allocated);
                group.allocated += take;
                remaining -= take;
            }
            overflow += remaining;
        }
    }

    public void sort(Sort sort) {
        for (var group : groups) {
            group.sortedFills.clear();
            group.sortedFills.addAll(group.fills);
            if (sort != Sort.NONE) group.sortedFills.sort(stackOrder(sort));
        }
        for (var role : roles) {
            role.candidates.clear();
            role.candidates.addAll(role.original);
            if (sort != Sort.NONE) role.candidates.sort(Comparator.comparing(candidate -> candidate.stack, stackOrder(sort)));
        }
    }

    private Comparator<ItemStack> stackOrder(Sort sort) {
        Comparator<ItemStack> rank = sort == Sort.CRAFTABLE ? Comparator.comparingInt(this::craftRank) : Comparator.comparingInt(this::stockRank);
        return rank.thenComparingLong(stack -> -storedOf(stack));
    }

    private int stockRank(ItemStack stack) {
        var entry = stock.apply(stack);
        if (entry == null) return 2;
        if (entry.stored > 0) return 0;
        return entry.craftable ? 1 : 2;
    }

    private int craftRank(ItemStack stack) {
        var entry = stock.apply(stack);
        if (entry == null) return 2;
        if (entry.craftable) return 0;
        return entry.stored > 0 ? 1 : 2;
    }

    private long storedOf(ItemStack stack) {
        var entry = stock.apply(stack);
        return entry == null ? 0 : entry.stored;
    }

    public static final class Builder {

        private final ItemStack controller;
        private final Reference2ObjectLinkedOpenHashMap<Item, Fixed> fixed = new Reference2ObjectLinkedOpenHashMap<>();
        private final Reference2ObjectLinkedOpenHashMap<TraceabilityPredicate, Group> groups = new Reference2ObjectLinkedOpenHashMap<>();
        private final Map<List<Item>, RoleDraft> roles = new LinkedHashMap<>();
        private final Reference2ObjectOpenHashMap<Item, List<Group>> itemGroups = new Reference2ObjectOpenHashMap<>();
        private Function<PartAbility, Component> abilityNames = ability -> null;
        @Nullable
        private ReferenceOpenHashSet<Block> partBlocks;
        @Nullable
        private Reference2ObjectLinkedOpenHashMap<PartAbility, ReferenceOpenHashSet<Block>> abilityBlocks;

        private Builder(ItemStack controller) {
            this.controller = controller;
        }

        public Builder abilityNames(Function<PartAbility, Component> abilityNames) {
            this.abilityNames = abilityNames;
            return this;
        }

        public Builder addCell(@Nullable BlockInfo shown, @Nullable TraceabilityPredicate predicate) {
            if (predicate == null || shown == null) return this;
            var shownItem = SimplePredicate.toItem(shown.getBlockState().getBlock());
            if (shownItem == Items.AIR || shownItem == Items.BARRIER) return this;
            var group = groups.get(predicate);
            if (group != null) {
                group.positions++;
                group.shown.addTo(shownItem, 1);
                return this;
            }
            var union = new ReferenceLinkedOpenHashSet<Item>();
            for (var simple : simplePredicates(predicate)) union.addAll(candidateItems(simple));
            if (union.size() <= 1) {
                var item = union.isEmpty() ? shownItem : union.first();
                var entry = fixed.computeIfAbsent(item, i -> new Fixed(new ItemStack(item)));
                entry.count = ItemStack.isSameItem(entry.stack, controller) ? 1 : entry.count + 1;
                return this;
            }
            group = new Group();
            group.positions = 1;
            group.shown.addTo(shownItem, 1);
            groups.put(predicate, group);
            var fills = new ReferenceLinkedOpenHashSet<Item>();
            for (var simple : simplePredicates(predicate)) {
                var parts = new ArrayList<Item>();
                for (var item : candidateItems(simple)) {
                    if (isPart(item)) parts.add(item);
                    else fills.add(item);
                }
                if (parts.isEmpty()) continue;
                var role = roles.computeIfAbsent(List.copyOf(parts), key -> new RoleDraft(roleName(key), simple.minCount, simple.maxCount));
                for (var item : parts) {
                    var list = itemGroups.computeIfAbsent(item, i -> new ArrayList<>());
                    if (!list.contains(group)) list.add(group);
                    if (!role.items.contains(item)) role.items.add(item);
                }
            }
            for (var item : fills) group.fills.add(new ItemStack(item));
            return this;
        }

        public PatternBuilderModel build(Function<ItemStack, Stock> stock) {
            var model = new PatternBuilderModel(this, stock);
            model.reallocate();
            return model;
        }

        private static List<SimplePredicate> simplePredicates(TraceabilityPredicate predicate) {
            var list = new ArrayList<SimplePredicate>(predicate.common.size() + predicate.limited.size());
            list.addAll(predicate.common);
            list.addAll(predicate.limited);
            return list;
        }

        private static List<Item> candidateItems(SimplePredicate simple) {
            if (simple == null || simple.candidates == null) return List.of();
            var items = new ArrayList<Item>();
            for (var block : simple.candidates.get()) {
                var item = SimplePredicate.toItem(block);
                if (item != Items.AIR && item != Items.BARRIER && !items.contains(item)) items.add(item);
            }
            return items;
        }

        private boolean isPart(Item item) {
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
            return items.get(0).getDescription();
        }
    }

    private static final class RoleDraft {

        private final Component name;
        private final int min;
        private final int max;
        private final List<Item> items = new ArrayList<>();

        private RoleDraft(Component name, int min, int max) {
            this.name = name;
            this.min = min;
            this.max = max;
        }
    }
}
