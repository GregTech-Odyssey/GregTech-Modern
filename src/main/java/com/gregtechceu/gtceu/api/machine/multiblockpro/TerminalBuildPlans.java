package com.gregtechceu.gtceu.api.machine.multiblockpro;

import com.gregtechceu.gtceu.api.item.IDataComponentItem;
import com.gregtechceu.gtceu.api.item.ItemDataComponentKey;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.common.data.GTItems;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2IntLinkedOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 终端按多方块记下上次建造的方案（结构组合、逐格方块、建造标志），建造界面以它为默认选择，潜行右击控制器按它直接搭建。
 */
public final class TerminalBuildPlans {

    public static final ItemDataComponentKey<Plans, Tag> KEY = ItemDataComponentKey.create("buildPlans", Plans::write, Plans::read);
    public static final String MISSING = "gtceu.structure_build.plan_missing";
    public static final String OUTDATED = "gtceu.structure_build.plan_outdated";
    public static final Component TOO_LARGE = Component.translatable("gtceu.structure_build.plan_too_large");
    private static final int MAX_PLANS = 64;
    private static final int MAX_PLAN_SIZE = 1 << 14;
    private static final int MAX_TOTAL_SIZE = 1 << 16;
    private static final int KEPT_FLAGS = StructureBuild.FLAG_MIRROR | StructureBuild.FLAG_NETWORK | StructureBuild.FLAG_REPLACE;
    private static final String VALUES = "values";
    private static final String PALETTE = "palette";
    private static final String RUNS = "runs";
    private static final String FLAGS = "flags";
    private static final String KEPT = "kept";
    private static final String SEQ = "seq";

    private TerminalBuildPlans() {}

    public record Plan(int[] values, Item[] palette, int[] runs, int flags, Set<StructureBuild.Swap> kept, int seq) {

        static Plan encode(int[] values, Item[] choices, int flags, Set<StructureBuild.Swap> kept, int seq) {
            var indices = new Reference2IntLinkedOpenHashMap<Item>();
            var runs = new IntArrayList();
            for (int i = 0; i < choices.length;) {
                var item = choices[i];
                int end = i + 1;
                while (end < choices.length && choices[end] == item) end++;
                int index = 0;
                if (item != null) {
                    index = indices.getInt(item);
                    if (index == 0) {
                        index = indices.size() + 1;
                        indices.put(item, index);
                    }
                }
                runs.add(index);
                runs.add(end - i);
                i = end;
            }
            var palette = new Item[indices.size() + 1];
            for (var it = indices.reference2IntEntrySet().fastIterator(); it.hasNext();) {
                var entry = it.next();
                palette[entry.getIntValue()] = entry.getKey();
            }
            Set<StructureBuild.Swap> swaps = Collections.emptySet();
            if ((flags & StructureBuild.FLAG_REPLACE) != 0 && !kept.isEmpty()) {
                var limited = new ObjectOpenHashSet<StructureBuild.Swap>();
                for (var swap : kept) {
                    if (limited.size() >= StructureBuild.MAX_KEPT) break;
                    limited.add(swap);
                }
                swaps = limited;
            }
            var plan = new Plan(values.clone(), palette, runs.toIntArray(), flags & KEPT_FLAGS, swaps, seq);
            return plan.size() > MAX_PLAN_SIZE ? new Plan(plan.values, new Item[1], new int[0], plan.flags, swaps, seq) : plan;
        }

        @Nullable
        public Item[] cells(int count) {
            if ((runs.length & 1) != 0) return null;
            var choices = new Item[count];
            int cursor = 0;
            for (int r = 0; r < runs.length; r += 2) {
                int index = runs[r], length = runs[r + 1];
                if (index < 0 || index >= palette.length || length <= 0 || length > count - cursor) return null;
                Arrays.fill(choices, cursor, cursor + length, palette[index]);
                cursor += length;
            }
            return cursor == count ? choices : null;
        }

        public boolean hasCells() {
            return runs.length > 0;
        }

        int size() {
            return values.length + runs.length + 8 * palette.length + 16 * kept.size();
        }

        CompoundTag write() {
            var tag = new CompoundTag();
            tag.putIntArray(VALUES, values);
            var items = new ListTag();
            for (int i = 1; i < palette.length; i++) items.add(StringTag.valueOf(id(palette[i])));
            tag.put(PALETTE, items);
            tag.putIntArray(RUNS, runs);
            tag.putInt(FLAGS, flags);
            if (!kept.isEmpty()) {
                var swaps = new ListTag();
                for (var swap : kept) {
                    swaps.add(StringTag.valueOf(id(swap.from())));
                    swaps.add(StringTag.valueOf(id(swap.to())));
                }
                tag.put(KEPT, swaps);
            }
            tag.putInt(SEQ, seq);
            return tag;
        }

        static Plan read(CompoundTag tag) {
            var items = tag.getList(PALETTE, Tag.TAG_STRING);
            var palette = new Item[items.size() + 1];
            for (int i = 0; i < items.size(); i++) palette[i + 1] = item(items.getString(i));
            var list = tag.getList(KEPT, Tag.TAG_STRING);
            Set<StructureBuild.Swap> kept = Collections.emptySet();
            if (!list.isEmpty()) {
                var swaps = new ObjectOpenHashSet<StructureBuild.Swap>();
                for (int i = 0; i + 1 < list.size() && swaps.size() < StructureBuild.MAX_KEPT; i += 2) {
                    var from = item(list.getString(i));
                    var to = item(list.getString(i + 1));
                    if (from != null && to != null) swaps.add(new StructureBuild.Swap(from, to));
                }
                kept = swaps;
            }
            return new Plan(tag.getIntArray(VALUES), palette, tag.getIntArray(RUNS), tag.getInt(FLAGS) & KEPT_FLAGS, kept, tag.getInt(SEQ));
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Plan plan && flags == plan.flags && Arrays.equals(values, plan.values) && Arrays.equals(runs, plan.runs) &&
                    Arrays.equals(palette, plan.palette) && kept.equals(plan.kept);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(values), Arrays.hashCode(runs), flags);
        }
    }

    public record Plans(Map<ResourceLocation, Plan> plans) {

        @Nullable
        public Plan get(ResourceLocation id) {
            return plans.get(id);
        }

        Plans with(ResourceLocation id, Plan plan) {
            var updated = new Object2ObjectLinkedOpenHashMap<ResourceLocation, Plan>(plans.size() + 1);
            int total = plan.size();
            for (var entry : plans.entrySet()) {
                if (entry.getKey().equals(id)) continue;
                updated.put(entry.getKey(), entry.getValue());
                total += entry.getValue().size();
            }
            updated.put(id, plan);
            while (updated.size() > 1 && (updated.size() > MAX_PLANS || total > MAX_TOTAL_SIZE)) {
                total -= updated.remove(updated.firstKey()).size();
            }
            return new Plans(Collections.unmodifiableMap(updated));
        }

        int nextSeq() {
            int seq = 0;
            for (var plan : plans.values()) seq = Math.max(seq, plan.seq());
            return seq + 1;
        }

        static Tag write(Plans plans) {
            var tag = new CompoundTag();
            for (var entry : plans.plans.entrySet()) tag.put(entry.getKey().toString(), entry.getValue().write());
            return tag;
        }

        static Plans read(Tag tag) {
            if (!(tag instanceof CompoundTag compound)) return new Plans(Collections.emptyMap());
            var entries = new ObjectArrayList<Map.Entry<ResourceLocation, Plan>>(compound.size());
            for (var key : compound.getAllKeys()) {
                var id = ResourceLocation.tryParse(key);
                if (id != null && compound.get(key) instanceof CompoundTag plan) entries.add(Map.entry(id, Plan.read(plan)));
            }
            entries.sort(Comparator.comparingInt(entry -> entry.getValue().seq()));
            var plans = new Object2ObjectLinkedOpenHashMap<ResourceLocation, Plan>(entries.size());
            for (var entry : entries) plans.put(entry.getKey(), entry.getValue());
            return new Plans(Collections.unmodifiableMap(plans));
        }
    }

    @Nullable
    public static Plan get(ItemStack stack, MultiblockMachineDefinition definition) {
        var plans = IDataComponentItem.get(stack, KEY);
        return plans == null ? null : plans.get(definition.getId());
    }

    public static void record(ServerPlayer player, MultiblockMachineDefinition definition, int[] values, Item[] choices, int flags,
                              Set<StructureBuild.Swap> kept) {
        var stack = heldTerminal(player);
        if (stack == null) return;
        var plans = IDataComponentItem.get(stack, KEY);
        if (plans == null) plans = new Plans(Collections.emptyMap());
        IDataComponentItem.set(stack, KEY, plans.with(definition.getId(), Plan.encode(values, choices, flags, kept, plans.nextSeq())));
    }

    public static void build(ServerPlayer player, IMultiController controller, ItemStack stack) {
        var definition = controller.self().getDefinition();
        var plan = get(stack, definition);
        if (plan == null) {
            player.displayClientMessage(Component.translatable(MISSING, definition.asStack().getHoverName()), true);
            return;
        }
        if (!plan.hasCells()) {
            player.displayClientMessage(TOO_LARGE, true);
            return;
        }
        var structure = definition.displayStructure();
        var layout = structure == null || !structure.accepts(plan.values()) ? null : structure.layout(plan.values());
        var choices = layout == null ? null : plan.cells(layout.cells().size());
        if (choices == null) {
            player.displayClientMessage(Component.translatable(OUTDATED, definition.asStack().getHoverName()), true);
            return;
        }
        StructureBuild.build(player, controller, layout, choices, plan.flags(), null, plan.kept());
    }

    private static String id(@Nullable Item item) {
        return BuiltInRegistries.ITEM.getKey(item == null ? Items.AIR : item).toString();
    }

    @Nullable
    private static Item item(String id) {
        var location = ResourceLocation.tryParse(id);
        var item = location == null ? null : BuiltInRegistries.ITEM.getOptional(location).orElse(null);
        return item == Items.AIR ? null : item;
    }

    @Nullable
    private static ItemStack heldTerminal(ServerPlayer player) {
        var terminal = GTItems.TERMINAL.get();
        if (player.getMainHandItem().is(terminal)) return player.getMainHandItem();
        if (player.getOffhandItem().is(terminal)) return player.getOffhandItem();
        return null;
    }
}
