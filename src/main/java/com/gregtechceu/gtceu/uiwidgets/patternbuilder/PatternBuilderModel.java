package com.gregtechceu.gtceu.uiwidgets.patternbuilder;

import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Reference2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;

public final class PatternBuilderModel {

    public static final String ROLE_MORE = "gtceu.pattern_builder.role_more";
    public static final String ROLE_EXCEPT = "gtceu.pattern_builder.role_except";

    public record Stock(long stored, boolean craftable, long carried) {

        public long total() {
            return Math.max(stored, 0) + Math.max(carried, 0);
        }
    }

    public record Input(ItemStack stack, long count) {}

    public enum Sort {
        STOCK,
        CRAFTABLE,
        NONE
    }

    public static final class Fixed {

        final ItemStack stack;
        final List<Group> merged = new ArrayList<>();
        int count;
        boolean included = true;
        @Nullable
        PatternBuilderModel owner;

        Fixed(ItemStack stack) {
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
            if (owner != null) owner.invalidateInputs();
        }
    }

    public static final class Group {

        final List<ItemStack> fills = new ArrayList<>();
        final List<ItemStack> sortedFills = new ArrayList<>();
        final List<Group> linked = new ArrayList<>();
        final Reference2IntLinkedOpenHashMap<Item> extraLimits = new Reference2IntLinkedOpenHashMap<>();
        final List<Candidate> extras = new ArrayList<>();
        final Reference2ObjectOpenHashMap<Item, Candidate> fillCandidates = new Reference2ObjectOpenHashMap<>();
        final Reference2IntOpenHashMap<Item> shown = new Reference2IntOpenHashMap<>();
        final Int2IntLinkedOpenHashMap layerPositions = new Int2IntLinkedOpenHashMap();
        @Nullable
        Item preferred;
        int positions;
        int fill;
        int allocated;
        int layerAllocated;
        int section;
        boolean included = true;
        @Nullable
        PatternBuilderModel owner;

        public int getSection() {
            return section;
        }

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

        int layerCapacity(int layerMax) {
            int capacity = 0;
            for (var it = layerPositions.int2IntEntrySet().fastIterator(); it.hasNext();) {
                var entry = it.next();
                capacity += entry.getIntKey() < 0 ? entry.getIntValue() : Math.min(layerMax, entry.getIntValue());
            }
            return capacity;
        }

        int preferredFill() {
            if (preferred == null) return -1;
            for (int i = 0; i < fills.size(); i++) {
                if (fills.get(i).getItem() == preferred) return i;
            }
            return -1;
        }

        public int getFill() {
            return fill;
        }

        public void setFill(int fill) {
            if (fill >= 0 && fill < fills.size()) {
                this.fill = fill;
                for (var group : linked) group.fill = fill;
                var primary = fillCandidates.get(fills.get(fill).getItem());
                if (primary != null) primary.selected = 0;
            }
            if (owner != null) owner.reallocate();
        }

        @Nullable
        public Candidate getFillCandidate(ItemStack stack) {
            return fillCandidates.get(stack.getItem());
        }

        public List<Candidate> getExtras() {
            return extras;
        }

        List<Group> linkedWithSelf() {
            var all = new ArrayList<Group>(linked.size() + 1);
            all.add(this);
            all.addAll(linked);
            return all;
        }

        public ItemStack getFillStack() {
            return fills.isEmpty() ? ItemStack.EMPTY : fills.get(fill);
        }

        public int getPositions() {
            int total = positions;
            for (var group : linked) total += group.positions;
            return total;
        }

        public int getFillCount() {
            int total = Math.max(0, positions - allocated);
            for (var group : linked) total += Math.max(0, group.positions - group.allocated);
            return total;
        }

        public boolean isIncluded() {
            return included;
        }

        public void setIncluded(boolean included) {
            this.included = included;
            for (var group : linked) group.included = included;
            if (owner != null) owner.invalidateInputs();
        }
    }

    public static final class Candidate {

        private final PatternBuilderModel owner;
        final ItemStack stack;
        final List<Group> groups = new ArrayList<>();
        final Reference2IntOpenHashMap<Group> taken = new Reference2IntOpenHashMap<>();
        int selected;
        int limit = -1;
        int layerMax = -1;
        int kind;
        final int section;

        Candidate(PatternBuilderModel owner, ItemStack stack, int section) {
            this.owner = owner;
            this.stack = stack;
            this.section = section;
        }

        public int getSection() {
            return section;
        }

        int capacity() {
            int capacity = 0;
            for (var group : groups) capacity += layerMax >= 0 ? group.layerCapacity(layerMax) : group.positions;
            return limit >= 0 ? Math.min(limit, capacity) : capacity;
        }

        public ItemStack getStack() {
            return stack;
        }

        public int getSelected() {
            return selected;
        }

        public void setSelected(int selected) {
            this.selected = Math.max(0, Math.min(selected, getMax()));
            owner.reallocate();
        }

        public int getMax() {
            int free = 0;
            for (var group : groups) free += group.positions - group.allocated;
            return limit >= 0 ? Math.min(limit, selected + free) : selected + free;
        }
    }

    public static final class Role {

        private final Component name;
        int min;
        final int max;
        int preview;
        final List<Candidate> candidates = new ArrayList<>();
        final List<Candidate> original = new ArrayList<>();
        private final Int2ObjectLinkedOpenHashMap<List<Candidate>> bySection = new Int2ObjectLinkedOpenHashMap<>();

        Role(Component name, int min, int max) {
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

        public int getTally(int section) {
            int tally = 0;
            for (var candidate : candidates) {
                if (candidate.section == section) tally += candidate.selected;
            }
            return tally;
        }

        public IntList getSections() {
            var sections = new IntArrayList();
            for (var candidate : original) {
                if (candidate.section >= 0 && !sections.contains(candidate.section)) sections.add(candidate.section);
            }
            return sections;
        }

        public List<Candidate> getCandidates(int section) {
            var list = bySection.get(section);
            return list == null ? List.of() : list;
        }

        void index() {
            bySection.clear();
            for (var candidate : candidates) bySection.computeIfAbsent(candidate.section, s -> new ArrayList<>()).add(candidate);
        }

        public boolean isSatisfied() {
            int tally = getTally();
            return tally >= Math.max(min, 0) && (max < 0 || tally <= max);
        }
    }

    final List<Fixed> fixed;
    final List<Group> groups;
    final List<Group> allGroups;
    final List<Candidate> candidates;
    final List<Candidate> allocationOrder;
    final List<Role> roles;
    final List<ModelAssembly.ExtraMinimum> extraMinimums;
    final List<ModelAssembly.Padding> paddings;
    final Map<GroupKey, Group> groupOf;
    final Reference2ObjectOpenHashMap<TraceabilityPredicate, Item> fixedOf;
    final Reference2ObjectOpenHashMap<Item, Fixed> fixedByItem;
    private final Function<ItemStack, Stock> stock;
    private final IntFunction<Component> sectionTitles;
    private int overflow;
    private int version;
    @Nullable
    private List<Input> inputs;

    private PatternBuilderModel(Builder builder, Function<ItemStack, Stock> stock) {
        this.stock = stock;
        this.sectionTitles = builder.sectionTitles;
        var assembly = new ModelAssembly(this, builder);
        this.groupOf = assembly.groupOf;
        this.fixedOf = assembly.fixedOf;
        this.fixedByItem = assembly.fixedByItem;
        this.allGroups = assembly.allGroups;
        this.groups = assembly.groups;
        this.fixed = assembly.fixed;
        this.roles = assembly.roles;
        this.paddings = assembly.paddings;
        this.extraMinimums = assembly.extraMinimums;
        this.candidates = assembly.candidates;
        this.allocationOrder = assembly.allocationOrder;
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

    public IntList getSections() {
        var sections = new IntAVLTreeSet();
        for (var role : roles) sections.addAll(role.getSections());
        return new IntArrayList(sections);
    }

    public Component getSectionTitle(int section) {
        return sectionTitles.apply(section);
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
        for (var it = merged.reference2LongEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            inputs.add(new Input(stacks.get(entry.getKey()), entry.getLongValue()));
        }
        return inputs;
    }

    private static void add(Reference2LongLinkedOpenHashMap<Item> merged, Reference2ObjectOpenHashMap<Item, ItemStack> stacks, ItemStack stack, long count) {
        merged.addTo(stack.getItem(), count);
        stacks.putIfAbsent(stack.getItem(), stack);
    }

    public int getVersion() {
        return version;
    }

    private void invalidateInputs() {
        inputs = null;
        version++;
    }

    void reallocate() {
        invalidateInputs();
        for (var group : allGroups) {
            group.allocated = 0;
            group.layerAllocated = 0;
        }
        overflow = 0;
        for (var candidate : allocationOrder) {
            candidate.taken.clear();
            int remaining = candidate.selected;
            for (int pass = 0; pass < 2; pass++) {
                for (var group : candidate.groups) {
                    if (group.fills.isEmpty() != (pass == 0)) continue;
                    int room = group.positions - group.allocated;
                    if (candidate.layerMax >= 0) room = Math.min(room, group.layerCapacity(candidate.layerMax) - group.layerAllocated);
                    int take = Math.max(0, Math.min(remaining, room));
                    group.allocated += take;
                    if (candidate.layerMax >= 0) group.layerAllocated += take;
                    candidate.taken.addTo(group, take);
                    remaining -= take;
                }
            }
            overflow += remaining;
        }
    }

    public boolean hasUnfilled() {
        for (var group : allGroups) {
            if (group.fills.isEmpty() && group.allocated < group.positions) return true;
        }
        return false;
    }

    public boolean isComplete() {
        if (hasUnfilled() || overflow > 0) return false;
        for (var role : roles) {
            if (!role.isSatisfied()) return false;
        }
        for (var minimum : extraMinimums) {
            if (minimum.selected() < minimum.min()) return false;
        }
        return true;
    }

    public void selectMinimum() {
        for (var padding : paddings) {
            var fits = new ArrayList<Candidate>();
            for (var candidate : padding.role().original) {
                if (candidate.groups.contains(padding.group())) fits.add(candidate);
            }
            if (fits.isEmpty()) continue;
            int have = 0;
            for (var candidate : fits) have += candidate.selected;
            if (have >= padding.count()) continue;
            distribute(fits, padding.count() - have);
        }
        for (var role : roles) {
            if (role.min <= 0 || role.original.isEmpty() || role.getTally() >= role.min) continue;
            distribute(role.original, role.min - role.getTally());
        }
        for (var minimum : extraMinimums) {
            int have = minimum.selected();
            if (have >= minimum.min()) continue;
            int favorite = PatternFavorites.preferred(minimum.candidates(), candidate -> candidate.stack.getItem());
            minimum.candidates().get(Math.max(favorite, 0)).selected += minimum.min() - have;
        }
        for (var role : roles) {
            if (role.preview <= 0 || role.original.isEmpty() || role.getTally() >= role.preview) continue;
            distribute(role.original, role.preview - role.getTally());
        }
        reallocate();
    }

    private static void distribute(List<Candidate> pool, int amount) {
        int favorite = Math.max(PatternFavorites.preferred(pool, candidate -> candidate.stack.getItem()), 0);
        var item = pool.get(favorite).stack.getItem();
        for (var candidate : pool) {
            if (amount <= 0) return;
            if (candidate.stack.getItem() != item) continue;
            int add = Math.min(amount, Math.max(0, candidate.capacity() - candidate.selected));
            candidate.selected += add;
            amount -= add;
        }
        if (amount > 0) pool.get(favorite).selected += amount;
    }

    public Item[] assign(List<TraceabilityPredicate> cells, IntComparator order, @Nullable int[] layers, @Nullable int[] sections) {
        return ModelPlacement.assign(this, cells, order, layers, sections);
    }

    public void sort(Sort sort) {
        var order = sort == Sort.NONE ? null : StockOrder.of(sort, stock);
        for (var group : groups) {
            group.sortedFills.clear();
            group.sortedFills.addAll(group.fills);
            if (order != null) group.sortedFills.sort(order);
        }
        for (var role : roles) {
            role.candidates.clear();
            role.candidates.addAll(role.original);
            if (order != null) role.candidates.sort(Comparator.comparing(candidate -> candidate.stack, order));
            role.index();
        }
    }

    public static final class Builder {

        final ItemStack controller;
        final Reference2ObjectLinkedOpenHashMap<Item, Fixed> fixed = new Reference2ObjectLinkedOpenHashMap<>();
        final Reference2ObjectOpenHashMap<TraceabilityPredicate, Item> fixedOf = new Reference2ObjectOpenHashMap<>();
        final LinkedHashMap<GroupKey, Group> groups = new LinkedHashMap<>();
        IntFunction<Component> sectionTitles = section -> Component.empty();
        final Map<Object, RoleDraft> roles = new LinkedHashMap<>();
        final Reference2ObjectOpenHashMap<Item, List<Group>> itemGroups = new Reference2ObjectOpenHashMap<>();
        final Map<List<Item>, Integer> extraMinimums = new LinkedHashMap<>();
        final List<Preplaced> preplaced = new ArrayList<>();
        private final RoleNames names = new RoleNames();

        private Builder(ItemStack controller) {
            this.controller = controller;
        }

        public Builder abilityNames(Function<PartAbility, Component> abilityNames) {
            names.setAbilityNames(abilityNames);
            return this;
        }

        public Builder preplace(TraceabilityPredicate predicate, Item item) {
            preplaced.add(new Preplaced(predicate, item));
            return this;
        }

        int preplacedCount(Item item) {
            int count = 0;
            for (var placed : preplaced) {
                if (placed.item() == item) count++;
            }
            return count;
        }

        public Builder addCell(@Nullable BlockInfo shown, @Nullable TraceabilityPredicate predicate) {
            return addCell(shown, predicate, -1);
        }

        public Builder sectionTitles(IntFunction<Component> sectionTitles) {
            this.sectionTitles = sectionTitles;
            return this;
        }

        public Builder addCell(@Nullable BlockInfo shown, @Nullable TraceabilityPredicate predicate, int layer) {
            return addCell(shown, predicate, layer, 0);
        }

        public Builder addCell(@Nullable BlockInfo shown, @Nullable TraceabilityPredicate predicate, int layer, int section) {
            if (predicate == null || shown == null) return this;
            var groupKey = new GroupKey(predicate, section);
            var shownItem = SimplePredicate.toItem(shown.getBlockState().getBlock());
            if (shownItem == Items.AIR || shownItem == Items.BARRIER) return this;
            var group = groups.get(groupKey);
            if (group != null) {
                group.positions++;
                group.layerPositions.addTo(layer, 1);
                group.shown.addTo(shownItem, 1);
                return this;
            }
            var simples = simplePredicates(predicate);
            var union = new ReferenceLinkedOpenHashSet<Item>();
            for (var simple : simples) union.addAll(candidateItems(simple));
            if (union.size() <= 1) {
                var item = union.isEmpty() ? shownItem : union.first();
                fixedOf.putIfAbsent(predicate, item);
                var entry = fixed.computeIfAbsent(item, i -> new Fixed(new ItemStack(item)));
                entry.count = ItemStack.isSameItem(entry.stack, controller) ? 1 : entry.count + 1;
                return this;
            }
            group = new Group();
            group.section = section;
            group.positions = 1;
            group.layerPositions.addTo(layer, 1);
            group.shown.addTo(shownItem, 1);
            groups.put(groupKey, group);
            var fills = new ReferenceLinkedOpenHashSet<Item>();
            for (var simple : simples) {
                if (simple != null) addSimple(group, simple, fills);
            }
            for (var item : fills) {
                group.fills.add(new ItemStack(item));
                group.extraLimits.removeInt(item);
            }
            return this;
        }

        private void addSimple(Group group, SimplePredicate simple, ReferenceLinkedOpenHashSet<Item> fills) {
            var parts = new ArrayList<Item>();
            var limited = new ArrayList<Item>();
            boolean countLimited = simple.maxCount >= 0 || simple.maxLayerCount >= 0;
            for (var item : candidateItems(simple)) {
                if (names.isPart(item)) {
                    parts.add(item);
                } else if (!countLimited) {
                    fills.add(item);
                    if (simple.minCount > 0 && group.preferred == null) group.preferred = item;
                } else {
                    group.extraLimits.put(item, simple.maxCount);
                    limited.add(item);
                    var list = itemGroups.computeIfAbsent(item, i -> new ArrayList<>());
                    if (!list.contains(group)) list.add(group);
                }
            }
            if (simple.minCount > 0 && !limited.isEmpty()) extraMinimums.merge(List.copyOf(limited), simple.minCount, Math::max);
            if (parts.isEmpty()) return;
            var key = countLimited || simple.minCount > 0 ? simple : List.copyOf(parts);
            var role = roles.computeIfAbsent(key, k -> new RoleDraft(names.roleName(simple, parts), simple.minCount, simple.maxCount,
                    k instanceof SimplePredicate, simple.maxLayerCount, simple.previewCount));
            if (role.own && !role.groups.contains(group)) role.groups.add(group);
            for (var item : parts) {
                var list = itemGroups.computeIfAbsent(item, i -> new ArrayList<>());
                if (!list.contains(group)) list.add(group);
                if (!role.items.contains(item)) role.items.add(item);
            }
        }

        public PatternBuilderModel build(Function<ItemStack, Stock> stock) {
            var model = new PatternBuilderModel(this, stock);
            model.reallocate();
            return model;
        }

        private static List<SimplePredicate> simplePredicates(TraceabilityPredicate predicate) {
            var list = new ArrayList<SimplePredicate>(predicate.common.size() + predicate.limited.size());
            predicate.forEachSimple(list::add);
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
    }

    record Preplaced(TraceabilityPredicate predicate, Item item) {}

    record GroupKey(TraceabilityPredicate predicate, int section) {}

    static final class RoleDraft {

        final Component name;
        final int min;
        final int max;
        final List<Item> items = new ArrayList<>();
        final List<Group> groups = new ArrayList<>();
        final boolean own;
        final int layerMax;
        final int layerPreview;

        RoleDraft(Component name, int min, int max, boolean own, int layerMax, int layerPreview) {
            this.name = name;
            this.min = min;
            this.max = max;
            this.own = own;
            this.layerMax = layerMax;
            this.layerPreview = layerPreview;
        }
    }
}
