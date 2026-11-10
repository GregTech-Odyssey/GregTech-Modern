package com.gregtechceu.gtceu.api.machine.multiblockpro;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
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

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2IntLinkedOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

/**
 * 终端一键搭建方案：在建造配置页把结构组合与逐格方块存进终端，潜行右击控制器时服务端按方案直接搭建。
 */
public final class TerminalBuildPlans {

    public static final Component SAVE = Component.translatable("gtceu.structure_build.save_plan");
    public static final String SAVED = "gtceu.structure_build.plan_saved";
    public static final String SAVED_REPLACE = "gtceu.structure_build.plan_saved.replace";
    public static final Component INVALID = Component.translatable("gtceu.structure_build.plan_invalid");
    public static final Component TOO_LARGE = Component.translatable("gtceu.structure_build.plan_too_large");
    public static final String FULL = "gtceu.structure_build.plan_full";
    public static final String OUTDATED = "gtceu.structure_build.plan_outdated";
    public static final int MAX_PLANS = 64;
    public static final int MAX_RUNS = 1 << 14;
    private static final int SAVE_COOLDOWN = 10;
    private static final int BUILD_COOLDOWN = 40;
    private static final int KEPT_FLAGS = StructureBuild.FLAG_MIRROR | StructureBuild.FLAG_NETWORK | StructureBuild.FLAG_REPLACE;
    private static final String TAG = "buildPlans";
    private static final String VALUES = "values";
    private static final String PALETTE = "palette";
    private static final String RUNS = "runs";
    private static final String FLAGS = "flags";
    private static final String KEPT = "kept";

    private TerminalBuildPlans() {}

    public static boolean has(ItemStack stack, MultiblockMachineDefinition definition) {
        var tag = stack.getTag();
        return tag != null && tag.getCompound(TAG).contains(definition.getId().toString(), Tag.TAG_COMPOUND);
    }

    public static void save(ServerPlayer player, ResourceLocation id, int[] values, int upload, int flags, Set<StructureBuild.Swap> kept) {
        var stack = heldTerminal(player);
        if (stack == null || player.getCooldowns().isOnCooldown(stack.getItem())) return;
        player.getCooldowns().addCooldown(stack.getItem(), SAVE_COOLDOWN);
        if (!(GTRegistries.MACHINES.get(id) instanceof MultiblockMachineDefinition definition)) return;
        var structure = definition.displayStructure();
        var layout = structure == null || !structure.accepts(values) ? null : structure.layout(values);
        var choices = layout == null ? null : BuildUpload.take(player, upload, definition, values, layout.cells().size());
        if (choices == null) {
            player.sendSystemMessage(INVALID);
            return;
        }
        var palette = new Reference2IntLinkedOpenHashMap<Item>();
        var runs = new IntArrayList();
        for (int i = 0; i < choices.length;) {
            var item = choices[i];
            int end = i + 1;
            while (end < choices.length && choices[end] == item) end++;
            int index = 0;
            if (item != null) {
                index = palette.getInt(item);
                if (index == 0) {
                    index = palette.size() + 1;
                    palette.put(item, index);
                }
            }
            runs.add(index);
            runs.add(end - i);
            if (runs.size() > 2 * MAX_RUNS) {
                player.sendSystemMessage(TOO_LARGE);
                return;
            }
            i = end;
        }
        var plans = stack.getOrCreateTag().getCompound(TAG);
        var key = id.toString();
        if (!plans.contains(key) && plans.size() >= MAX_PLANS) {
            player.sendSystemMessage(Component.translatable(FULL, MAX_PLANS));
            return;
        }
        var items = new ListTag();
        for (var item : palette.keySet()) items.add(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(item).toString()));
        var plan = new CompoundTag();
        plan.putIntArray(VALUES, values);
        plan.put(PALETTE, items);
        plan.putIntArray(RUNS, runs.toIntArray());
        plan.putInt(FLAGS, flags & KEPT_FLAGS);
        if ((flags & StructureBuild.FLAG_REPLACE) != 0 && !kept.isEmpty()) {
            var swaps = new ListTag();
            for (var swap : kept) {
                if (swaps.size() >= 2 * StructureBuild.MAX_KEPT) break;
                swaps.add(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(swap.from()).toString()));
                swaps.add(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(swap.to()).toString()));
            }
            plan.put(KEPT, swaps);
        }
        plans.put(key, plan);
        stack.getOrCreateTag().put(TAG, plans);
        boolean replace = (flags & StructureBuild.FLAG_REPLACE) != 0;
        player.displayClientMessage(Component.translatable(replace ? SAVED_REPLACE : SAVED, definition.asStack().getHoverName()), true);
    }

    public static void build(ServerPlayer player, IMultiController controller, ItemStack stack) {
        var terminal = stack.getItem();
        if (player.getCooldowns().isOnCooldown(terminal)) return;
        var definition = controller.self().getDefinition();
        var tag = stack.getTag();
        if (tag == null) return;
        var plan = tag.getCompound(TAG).getCompound(definition.getId().toString());
        var values = plan.getIntArray(VALUES);
        var structure = definition.displayStructure();
        var layout = structure == null || !structure.accepts(values) ? null : structure.layout(values);
        var choices = layout == null ? null : decode(plan, layout.cells().size());
        if (choices == null) {
            player.sendSystemMessage(Component.translatable(OUTDATED, definition.asStack().getHoverName()));
            return;
        }
        player.getCooldowns().addCooldown(terminal, BUILD_COOLDOWN);
        StructureBuild.build(player, controller, layout, choices, plan.getInt(FLAGS) & KEPT_FLAGS, null, kept(plan));
    }

    private static Set<StructureBuild.Swap> kept(CompoundTag plan) {
        var list = plan.getList(KEPT, Tag.TAG_STRING);
        if (list.isEmpty()) return Collections.emptySet();
        var kept = new ObjectOpenHashSet<StructureBuild.Swap>();
        for (int i = 0; i + 1 < list.size() && kept.size() < StructureBuild.MAX_KEPT; i += 2) {
            var from = item(list.getString(i));
            var to = item(list.getString(i + 1));
            if (from != null && to != null) kept.add(new StructureBuild.Swap(from, to));
        }
        return kept;
    }

    @Nullable
    private static Item item(String id) {
        var location = ResourceLocation.tryParse(id);
        return location == null ? null : BuiltInRegistries.ITEM.getOptional(location).orElse(null);
    }

    @Nullable
    private static Item[] decode(CompoundTag plan, int cells) {
        var items = plan.getList(PALETTE, Tag.TAG_STRING);
        var palette = new Item[items.size() + 1];
        for (int i = 0; i < items.size(); i++) palette[i + 1] = item(items.getString(i));
        var runs = plan.getIntArray(RUNS);
        if ((runs.length & 1) != 0) return null;
        var choices = new Item[cells];
        int cursor = 0;
        for (int r = 0; r < runs.length; r += 2) {
            int index = runs[r], length = runs[r + 1];
            if (index < 0 || index >= palette.length || length <= 0 || length > cells - cursor) return null;
            Arrays.fill(choices, cursor, cursor + length, palette[index]);
            cursor += length;
        }
        return cursor == cells ? choices : null;
    }

    @Nullable
    private static ItemStack heldTerminal(ServerPlayer player) {
        var terminal = GTItems.TERMINAL.get();
        if (player.getMainHandItem().is(terminal)) return player.getMainHandItem();
        if (player.getOffhandItem().is(terminal)) return player.getOffhandItem();
        return null;
    }
}
