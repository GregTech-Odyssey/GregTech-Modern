package com.gregtechceu.gtceu.uiwidgets.patternbuilder;

import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ModelAssembly {

    private static final int KIND_EXTRA = 1;
    private static final int KIND_FILL = 2;
    private static final int FIT_SCALE = 1000;

    record Padding(PatternBuilderModel.Group group, PatternBuilderModel.Role role, int count) {}

    record ExtraMinimum(List<PatternBuilderModel.Candidate> candidates, int min) {

        int selected() {
            int total = 0;
            for (var candidate : candidates) total += candidate.selected;
            return total;
        }
    }

    private record ItemSection(Item item, int section) {}

    private final PatternBuilderModel model;
    private final PatternBuilderModel.Builder builder;
    final Map<PatternBuilderModel.GroupKey, PatternBuilderModel.Group> groupOf;
    final Reference2ObjectOpenHashMap<TraceabilityPredicate, Item> fixedOf;
    final Reference2ObjectOpenHashMap<Item, PatternBuilderModel.Fixed> fixedByItem = new Reference2ObjectOpenHashMap<>();
    final List<PatternBuilderModel.Group> allGroups;
    final List<PatternBuilderModel.Group> groups;
    final List<PatternBuilderModel.Fixed> fixed;
    final List<PatternBuilderModel.Role> roles;
    final List<Padding> paddings;
    final List<ExtraMinimum> extraMinimums;
    final List<PatternBuilderModel.Candidate> candidates;
    final List<PatternBuilderModel.Candidate> allocationOrder;
    private final LinkedHashMap<List<Item>, PatternBuilderModel.Group> leaders = new LinkedHashMap<>();
    private final LinkedHashMap<ItemSection, PatternBuilderModel.Candidate> sharedCandidates = new LinkedHashMap<>();
    private final List<PatternBuilderModel.Candidate> ownCandidates = new ArrayList<>();
    private final Reference2ObjectLinkedOpenHashMap<Item, PatternBuilderModel.Candidate> extraCandidates = new Reference2ObjectLinkedOpenHashMap<>();

    ModelAssembly(PatternBuilderModel model, PatternBuilderModel.Builder builder) {
        this.model = model;
        this.builder = builder;
        this.groupOf = new HashMap<>(builder.groups);
        this.fixedOf = new Reference2ObjectOpenHashMap<>(builder.fixedOf);
        var groupList = new ArrayList<>(builder.groups.values());
        groupList.sort(Comparator.comparingInt(g -> -g.positions));
        this.allGroups = List.copyOf(groupList);
        var fixedMap = new Reference2ObjectLinkedOpenHashMap<>(builder.fixed);
        mergeGroups(fixedMap);
        this.groups = List.copyOf(leaders.values());
        this.fixed = buildFixed(fixedMap);
        var roleList = buildRoles();
        this.paddings = buildPaddings(roleList);
        roleList.sort(Comparator.comparing(role -> !role.isRequired()));
        buildExtras();
        this.roles = List.copyOf(roleList);
        for (var role : roles) role.index();
        this.extraMinimums = buildExtraMinimums();
        var fillCandidates = buildFillCandidates();
        var all = new ArrayList<PatternBuilderModel.Candidate>(sharedCandidates.values());
        all.addAll(extraCandidates.values());
        all.addAll(ownCandidates);
        all.addAll(fillCandidates);
        this.candidates = List.copyOf(all);
        var order = new ArrayList<>(candidates);
        order.sort(Comparator.comparingInt((PatternBuilderModel.Candidate c) -> c.kind).thenComparingInt(c -> c.groups.size()));
        this.allocationOrder = List.copyOf(order);
    }

    private static int bestFill(PatternBuilderModel.Group group) {
        int best = -1;
        for (int i = 0; i < group.fills.size(); i++) {
            if (best < 0 || group.shown.getInt(group.fills.get(i).getItem()) > group.shown.getInt(group.fills.get(best).getItem())) best = i;
        }
        int preferred = group.preferredFill();
        if (preferred >= 0) best = preferred;
        return Math.max(best, 0);
    }

    private void mergeGroups(Reference2ObjectLinkedOpenHashMap<Item, PatternBuilderModel.Fixed> fixedMap) {
        var standalone = new ArrayList<PatternBuilderModel.Group>();
        for (var group : allGroups) {
            group.owner = model;
            group.fill = bestFill(group);
            if (group.fills.size() == 1 && group.extraLimits.isEmpty()) {
                var item = group.fills.get(0).getItem();
                fixedMap.computeIfAbsent(item, i -> new PatternBuilderModel.Fixed(new ItemStack(item))).merged.add(group);
            } else if (!group.fills.isEmpty()) {
                standalone.add(group);
            }
        }
        for (var group : standalone) {
            var key = new ArrayList<Item>(group.fills.size());
            for (var stack : group.fills) key.add(stack.getItem());
            var leader = leaders.putIfAbsent(key, group);
            if (leader == null) continue;
            leader.linked.add(group);
            for (var it = group.shown.reference2IntEntrySet().fastIterator(); it.hasNext();) {
                var entry = it.next();
                leader.shown.addTo(entry.getKey(), entry.getIntValue());
            }
        }
        for (var leader : leaders.values()) {
            int best = bestFill(leader);
            int favorite = PatternFavorites.preferred(leader.fills, ItemStack::getItem);
            if (favorite >= 0) best = favorite;
            leader.fill = best;
            for (var member : leader.linked) member.fill = best;
        }
    }

    private List<PatternBuilderModel.Fixed> buildFixed(Reference2ObjectLinkedOpenHashMap<Item, PatternBuilderModel.Fixed> fixedMap) {
        var fixedList = new ArrayList<>(fixedMap.values());
        fixedList.sort(Comparator.comparing((PatternBuilderModel.Fixed f) -> !ItemStack.isSameItem(f.stack, builder.controller))
                .thenComparingInt(f -> -f.getCount()));
        for (var entry : fixedList) {
            entry.owner = model;
            fixedByItem.put(entry.stack.getItem(), entry);
        }
        return List.copyOf(fixedList);
    }

    private ArrayList<PatternBuilderModel.Role> buildRoles() {
        var roleList = new ArrayList<PatternBuilderModel.Role>();
        for (var roleEntry : builder.roles.entrySet()) roleList.add(buildRole(roleEntry.getKey(), roleEntry.getValue()));
        return roleList;
    }

    private PatternBuilderModel.Role buildRole(Object key, PatternBuilderModel.RoleDraft draft) {
        int taken = 0;
        if (draft.own && key instanceof SimplePredicate simple) {
            for (var placed : builder.preplaced) {
                if (draft.items.contains(placed.item()) && (placed.predicate().common.contains(simple) || placed.predicate().limited.contains(simple))) taken++;
            }
        }
        int max = draft.max < 0 ? draft.max : Math.max(0, draft.max - taken);
        int preview = 0;
        if (draft.own && draft.layerMax >= 0) {
            int capacity = 0;
            for (var group : draft.groups) {
                for (var it = group.layerPositions.int2IntEntrySet().fastIterator(); it.hasNext();) {
                    var entry = it.next();
                    int count = entry.getIntValue();
                    capacity += entry.getIntKey() < 0 ? count : Math.min(draft.layerMax, count);
                    if (entry.getIntKey() >= 0 && draft.layerPreview > 0) preview += Math.min(Math.min(draft.layerPreview, draft.layerMax), count);
                }
            }
            max = max < 0 ? capacity : Math.min(max, capacity);
        }
        var role = new PatternBuilderModel.Role(draft.name, Math.max(0, draft.min - taken), max);
        role.preview = preview;
        var sections = new IntAVLTreeSet();
        for (var item : draft.items) {
            for (var group : builder.itemGroups.get(item)) {
                if (!draft.own || draft.groups.contains(group)) sections.add(group.section);
            }
        }
        for (var it = sections.iterator(); it.hasNext();) addSectionCandidates(role, draft, max, it.nextInt());
        role.original.addAll(role.candidates);
        return role;
    }

    private void addSectionCandidates(PatternBuilderModel.Role role, PatternBuilderModel.RoleDraft draft, int max, int section) {
        for (var item : draft.items) {
            var owned = new ArrayList<PatternBuilderModel.Group>();
            for (var group : builder.itemGroups.get(item)) {
                if (group.section == section && (!draft.own || draft.groups.contains(group)) && !owned.contains(group)) owned.add(group);
            }
            if (owned.isEmpty()) continue;
            PatternBuilderModel.Candidate candidate;
            if (draft.own) {
                candidate = new PatternBuilderModel.Candidate(model, new ItemStack(item), section);
                if (draft.layerMax >= 0) {
                    candidate.layerMax = draft.layerMax;
                    candidate.limit = max;
                }
                candidate.groups.addAll(owned);
                ownCandidates.add(candidate);
            } else {
                candidate = sharedCandidates.computeIfAbsent(new ItemSection(item, section),
                        key -> new PatternBuilderModel.Candidate(model, new ItemStack(item), section));
                for (var group : owned) {
                    if (!candidate.groups.contains(group)) candidate.groups.add(group);
                }
            }
            if (!role.candidates.contains(candidate)) role.candidates.add(candidate);
        }
    }

    private List<Padding> buildPaddings(List<PatternBuilderModel.Role> roleList) {
        var paddingList = new ArrayList<Padding>();
        var allowance = new Reference2IntOpenHashMap<PatternBuilderModel.Role>();
        for (var role : roleList) allowance.put(role, Math.max(role.min, 0));
        for (var group : allGroups) {
            if (!group.fills.isEmpty()) continue;
            var covering = new ArrayList<PatternBuilderModel.Role>();
            for (var role : roleList) {
                for (var candidate : role.original) {
                    if (candidate.groups.contains(group)) {
                        covering.add(role);
                        break;
                    }
                }
            }
            if (covering.isEmpty()) continue;
            int demanded = 0;
            for (var role : covering) {
                int take = Math.min(allowance.getInt(role), group.positions - demanded);
                demanded += take;
                allowance.addTo(role, -take);
            }
            int missing = group.positions - demanded;
            if (missing <= 0) continue;
            covering.sort(Comparator.comparingInt((PatternBuilderModel.Role role) -> -fitting(role, group)));
            var target = covering.get(0);
            for (var role : covering) {
                if (role.max < 0 || role.max - Math.max(role.min, 0) >= missing) {
                    target = role;
                    break;
                }
            }
            target.min = Math.max(target.min, 0) + missing;
            paddingList.add(new Padding(group, target, missing));
        }
        return List.copyOf(paddingList);
    }

    private static int fitting(PatternBuilderModel.Role role, PatternBuilderModel.Group group) {
        int count = 0;
        for (var candidate : role.original) {
            if (candidate.groups.contains(group)) count++;
        }
        return role.original.isEmpty() ? 0 : count * FIT_SCALE / role.original.size();
    }

    private void buildExtras() {
        for (var group : allGroups) {
            for (var it = group.extraLimits.reference2IntEntrySet().fastIterator(); it.hasNext();) {
                var entry = it.next();
                var item = entry.getKey();
                var candidate = extraCandidates.computeIfAbsent(item, i -> new PatternBuilderModel.Candidate(model, new ItemStack(item), -1));
                candidate.kind = Math.max(candidate.kind, KIND_EXTRA);
                for (var owner : builder.itemGroups.get(item)) {
                    if (!candidate.groups.contains(owner)) candidate.groups.add(owner);
                }
                int limit = entry.getIntValue() < 0 ? entry.getIntValue() : Math.max(0, entry.getIntValue() - builder.preplacedCount(item));
                candidate.limit = candidate.limit < 0 ? limit : Math.min(candidate.limit, limit);
            }
        }
        for (var leader : leaders.values()) {
            for (var member : leader.linkedWithSelf()) {
                for (var it = member.extraLimits.keySet().iterator(); it.hasNext();) {
                    var candidate = extraCandidates.get(it.next());
                    if (candidate != null && !leader.extras.contains(candidate)) leader.extras.add(candidate);
                }
            }
        }
    }

    private List<ExtraMinimum> buildExtraMinimums() {
        var minimums = new ArrayList<ExtraMinimum>();
        for (var entry : builder.extraMinimums.entrySet()) {
            var owned = new ArrayList<PatternBuilderModel.Candidate>();
            for (var item : entry.getKey()) {
                var candidate = extraCandidates.get(item);
                if (candidate != null && candidate.kind == KIND_EXTRA) owned.add(candidate);
            }
            int taken = 0;
            for (var item : entry.getKey()) taken += builder.preplacedCount(item);
            if (!owned.isEmpty()) minimums.add(new ExtraMinimum(List.copyOf(owned), Math.max(0, entry.getValue() - taken)));
        }
        return List.copyOf(minimums);
    }

    private List<PatternBuilderModel.Candidate> buildFillCandidates() {
        var fillCandidates = new Reference2ObjectLinkedOpenHashMap<Item, PatternBuilderModel.Candidate>();
        for (var group : allGroups) {
            if (group.fills.size() < 2) continue;
            for (var stack : group.fills) {
                var candidate = fillCandidates.computeIfAbsent(stack.getItem(), i -> {
                    var created = new PatternBuilderModel.Candidate(model, new ItemStack(stack.getItem()), -1);
                    created.kind = KIND_FILL;
                    return created;
                });
                if (!candidate.groups.contains(group)) candidate.groups.add(group);
                group.fillCandidates.put(stack.getItem(), candidate);
            }
        }
        return new ArrayList<>(fillCandidates.values());
    }
}
