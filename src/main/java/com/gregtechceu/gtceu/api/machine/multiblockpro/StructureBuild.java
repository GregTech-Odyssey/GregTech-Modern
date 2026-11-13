package com.gregtechceu.gtceu.api.machine.multiblockpro;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.ControllerPredicate;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldData;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class StructureBuild {

    public static final String RESULT = "gtceu.structure_build.result";
    public static final String MISSING = "gtceu.structure_build.missing";
    public static final String REPLACED = "gtceu.structure_build.replaced";
    public static final Component INVALID = Component.translatable("gtceu.structure_build.invalid");
    public static final String DEMOLISHED = "gtceu.structure_build.demolished";
    public static final Component NO_NETWORK = Component.translatable("gtceu.structure_build.no_network");
    public static final int FLAG_MIRROR = 1;
    public static final int FLAG_NETWORK = 2;
    public static final int FLAG_DEMOLISH = 4;
    public static final int FLAG_REPLACE = 8;
    public static final int ALL_FLAGS = FLAG_MIRROR | FLAG_NETWORK | FLAG_DEMOLISH | FLAG_REPLACE;
    public static final int MAX_KEPT = 256;

    public record Swap(Item from, Item to) {

        public static void write(FriendlyByteBuf buf, Collection<Swap> swaps) {
            buf.writeVarInt(Math.min(swaps.size(), MAX_KEPT));
            int written = 0;
            for (var swap : swaps) {
                if (written++ >= MAX_KEPT) break;
                buf.writeId(BuiltInRegistries.ITEM, swap.from);
                buf.writeId(BuiltInRegistries.ITEM, swap.to);
            }
        }

        public static Set<Swap> read(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            if (size < 0 || size > MAX_KEPT) throw new IllegalArgumentException("Too many kept swaps: " + size);
            var swaps = new ObjectOpenHashSet<Swap>(size);
            for (int i = 0; i < size; i++) {
                var from = buf.readById(BuiltInRegistries.ITEM);
                var to = buf.readById(BuiltInRegistries.ITEM);
                if (from != null && to != null) swaps.add(new Swap(from, to));
            }
            return swaps;
        }
    }

    private StructureBuild() {}

    public static void build(ServerPlayer player, IMultiController controller, int[] values, Item[] choices) {
        var structure = controller.self().getDefinition().displayStructure();
        var layout = structure == null ? null : structure.layout(values);
        if (layout == null) {
            player.sendSystemMessage(INVALID);
            return;
        }
        build(player, controller, layout, choices);
    }

    public static void build(ServerPlayer player, IMultiController controller, Layout layout, Item[] choices) {
        build(player, controller, layout, choices, controller.self().isFlipped() ? FLAG_MIRROR : 0);
    }

    public static void build(ServerPlayer player, IMultiController controller, Layout layout, Item[] choices, int flags) {
        build(player, controller, layout, choices, flags, null);
    }

    public static void build(ServerPlayer player, IMultiController controller, Layout layout, Item[] choices, int flags, @Nullable boolean[] demolishNodes) {
        build(player, controller, layout, choices, flags, demolishNodes, Collections.emptySet());
    }

    public static void build(ServerPlayer player, IMultiController controller, Layout layout, Item[] choices, int flags, @Nullable boolean[] demolishNodes,
                             Set<Swap> kept) {
        if (layout.cells().size() != choices.length) {
            player.sendSystemMessage(INVALID);
            return;
        }
        if (!player.mayBuild()) return;
        var machine = controller.self();
        boolean flip = (flags & FLAG_MIRROR) != 0 && machine.allowFlip();
        boolean useNetwork = (flags & FLAG_NETWORK) != 0;
        if (useNetwork && !PlayerSupply.hasNetwork(player)) {
            player.sendSystemMessage(NO_NETWORK);
            useNetwork = false;
        }
        if ((flags & FLAG_DEMOLISH) != 0) {
            demolish(player, controller, layout, demolishNodes, flip, useNetwork);
            return;
        }
        boolean replace = (flags & FLAG_REPLACE) != 0;
        var level = player.serverLevel();
        var origin = machine.getPos();
        var front = machine.getFrontFacing();
        var up = machine.getUpwardsFacing();
        boolean ultraWarm = level.dimensionType().ultraWarm();
        var allowed = new Reference2ObjectOpenHashMap<TraceabilityPredicate, ReferenceOpenHashSet<Item>>();
        int placed = 0, replaced = 0, missing = 0;
        var supply = player.isCreative() ? null : PlayerSupply.of(player, true, useNetwork);
        var cells = layout.cells();
        var retry = new IntArrayList();
        for (int pass = 0; pass < 2; pass++) {
            var indices = pass == 0 ? null : retry.toIntArray();
            int count = pass == 0 ? choices.length : indices.length;
            for (int n = 0; n < count; n++) {
                int i = pass == 0 ? n : indices[n];
                var item = choices[i];
                var predicate = cells.get(i).predicate();
                if (item == null || predicate instanceof ControllerPredicate) continue;
                boolean isBlock = item instanceof BlockItem;
                if (!isBlock && !(item instanceof BucketItem bucket && bucket.getFluid() != Fluids.EMPTY)) continue;
                if (!isBlock && ultraWarm && ((BucketItem) item).getFluid().is(FluidTags.WATER)) continue;
                if (!allowed.computeIfAbsent(predicate, StructureBuild::candidates).contains(item)) continue;
                var pos = layout.worldPos(origin, i, front, up, flip);
                if (!level.isLoaded(pos) || !level.mayInteract(player, pos)) continue;
                boolean swapping = !level.isEmptyBlock(pos);
                if (swapping) {
                    if (!replace) continue;
                    var from = swapFrom(level, pos, item);
                    if (from == null || kept.contains(new Swap(from, item))) continue;
                    var target = ((BlockItem) item).getBlock().defaultBlockState();
                    if (!target.canSurvive(level, pos) || !level.isUnobstructed(target, pos, CollisionContext.of(player))) continue;
                }
                if (!isBlock) {
                    var took = supply == null ? PlayerSupply.FluidTake.STORED : supply.takeFluid(((BucketItem) item).getFluid());
                    if (took == PlayerSupply.FluidTake.NONE) {
                        missing++;
                        continue;
                    }
                    level.setBlock(pos, StructureBlocks.stateOf(item), 3);
                    if (took == PlayerSupply.FluidTake.BUCKET) player.getInventory().placeItemBackInInventory(new ItemStack(Items.BUCKET));
                    placed++;
                    continue;
                }
                var taken = supply == null ? new ItemStack(item) : supply.take(item);
                if (taken.isEmpty()) {
                    missing++;
                    continue;
                }
                List<ItemStack> drops = Collections.emptyList();
                if (swapping) {
                    drops = Block.getDrops(level.getBlockState(pos), level, pos, null, player, ItemStack.EMPTY);
                    level.removeBlock(pos, false);
                }
                var facing = layout.outwardWorld(i, front, up, flip);
                var context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, taken.copy(),
                        BlockHitResult.miss(player.getEyePosition(0), Direction.UP, pos));
                if (((BlockItem) item).place(context) == InteractionResult.FAIL) {
                    if (supply != null) {
                        supply.giveBack(player, taken);
                        for (var drop : drops) supply.giveBack(player, drop);
                    }
                    if (pass == 0) retry.add(i);
                    continue;
                }
                if (level.getBlockEntity(pos) instanceof MetaMachineBlockEntity holder) {
                    if (facing != null && holder.getMetaMachine().isFacingValid(facing)) holder.getMetaMachine().setFrontFacing(facing);
                } else {
                    var state = level.getBlockState(pos);
                    var faced = StructureBlocks.face(state, facing);
                    if (faced != state && faced.canSurvive(level, pos)) level.setBlock(pos, faced, 3);
                }
                if (swapping) {
                    if (supply != null) {
                        for (var drop : drops) supply.giveBack(player, drop);
                    }
                    replaced++;
                } else {
                    placed++;
                }
            }
        }
        if (placed > 0 || replaced == 0) player.sendSystemMessage(Component.translatable(RESULT, placed));
        if (replaced > 0) player.sendSystemMessage(Component.translatable(REPLACED, replaced));
        if (missing > 0) player.sendSystemMessage(Component.translatable(MISSING, missing));
        controller.setWaitingTime(0);
        if (controller.isFormed()) controller.requestCheck();
        else controller.asyncCheckPattern(MultiblockWorldData.getOrCreate(level));
    }

    @Nullable
    public static Item swapFrom(BlockGetter level, BlockPos pos, Item chosen) {
        if (!(chosen instanceof BlockItem blockItem) || blockItem.getBlock().defaultBlockState().hasBlockEntity()) return null;
        var state = level.getBlockState(pos);
        if (state.isAir() || state.hasBlockEntity() || state.getBlock() instanceof LiquidBlock || !state.getFluidState().isEmpty()) return null;
        if (state.getDestroySpeed(level, pos) < 0) return null;
        var from = SimplePredicate.toItem(state.getBlock());
        return from == Items.AIR || from == chosen ? null : from;
    }

    public static boolean demolishes(@Nullable boolean[] nodes, int node) {
        return nodes == null || node < 0 || node >= nodes.length || nodes[node];
    }

    public static IntArrayList demolishCells(Layout layout, @Nullable boolean[] nodes) {
        var cells = layout.cells();
        var result = new IntArrayList();
        for (int i = 0; i < cells.size(); i++) {
            var cell = cells.get(i);
            if (cell.predicate() instanceof ControllerPredicate || !demolishes(nodes, cell.node())) continue;
            boolean kept = false;
            for (int other : layout.sharedNodes(i)) {
                if (!demolishes(nodes, other)) {
                    kept = true;
                    break;
                }
            }
            if (!kept) result.add(i);
        }
        return result;
    }

    private static void demolish(ServerPlayer player, IMultiController controller, Layout layout, @Nullable boolean[] nodes, boolean flip, boolean useNetwork) {
        var machine = controller.self();
        var level = player.serverLevel();
        var origin = machine.getPos();
        var front = machine.getFrontFacing();
        var up = machine.getUpwardsFacing();
        var allowed = new Reference2ObjectOpenHashMap<TraceabilityPredicate, ReferenceOpenHashSet<Item>>();
        var supply = PlayerSupply.of(player, false, useNetwork);
        var cells = layout.cells();
        var pos = new BlockPos.MutableBlockPos();
        int removed = 0;
        var targets = demolishCells(layout, nodes);
        for (int n = 0; n < targets.size(); n++) {
            int i = targets.getInt(n);
            var predicate = cells.get(i).predicate();
            layout.worldPos(origin, i, front, up, flip, pos);
            if (!level.isLoaded(pos) || !level.mayInteract(player, pos)) continue;
            var state = level.getBlockState(pos);
            if (state.isAir()) continue;
            if (state.getBlock() instanceof LiquidBlock) {
                if (state.getFluidState().isSource() && allowed.computeIfAbsent(predicate, StructureBuild::candidates).contains(state.getFluidState().getType().getBucket())) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    removed++;
                }
                continue;
            }
            if (state.getDestroySpeed(level, pos) < 0) continue;
            if (!allowed.computeIfAbsent(predicate, StructureBuild::candidates).contains(state.getBlock().asItem())) continue;
            var drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, ItemStack.EMPTY);
            level.removeBlock(pos, false);
            if (!player.isCreative()) {
                for (var drop : drops) supply.giveBack(player, drop);
            }
            removed++;
        }
        player.sendSystemMessage(Component.translatable(DEMOLISHED, removed));
    }

    public static ReferenceOpenHashSet<Item> candidates(TraceabilityPredicate predicate) {
        var items = new ReferenceOpenHashSet<Item>();
        predicate.forEachSimple(simple -> {
            if (simple == null || simple.candidates == null) return;
            for (var block : simple.candidates.get()) {
                var item = SimplePredicate.toItem(block);
                if (item != Items.AIR) items.add(item);
            }
        });
        return items;
    }
}
