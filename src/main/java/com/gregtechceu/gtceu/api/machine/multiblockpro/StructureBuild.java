package com.gregtechceu.gtceu.api.machine.multiblockpro;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.ControllerPredicate;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldData;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.Nullable;

public final class StructureBuild {

    public static final String RESULT = "gtceu.structure_build.result";
    public static final String MISSING = "gtceu.structure_build.missing";
    public static final Component INVALID = Component.translatable("gtceu.structure_build.invalid");
    public static final String DEMOLISHED = "gtceu.structure_build.demolished";
    public static final Component NO_NETWORK = Component.translatable("gtceu.structure_build.no_network");
    public static final int FLAG_MIRROR = 1;
    public static final int FLAG_NETWORK = 2;
    public static final int FLAG_DEMOLISH = 4;

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
        var level = player.serverLevel();
        var origin = machine.getPos();
        var front = machine.getFrontFacing();
        var up = machine.getUpwardsFacing();
        boolean ultraWarm = level.dimensionType().ultraWarm();
        var allowed = new Reference2ObjectOpenHashMap<TraceabilityPredicate, ReferenceOpenHashSet<Item>>();
        int placed = 0, missing = 0;
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
                if (!level.isLoaded(pos) || !level.isEmptyBlock(pos) || !level.mayInteract(player, pos)) continue;
                var taken = supply == null ? new ItemStack(item) : supply.take(item);
                if (taken.isEmpty()) {
                    missing++;
                    continue;
                }
                var facing = layout.outwardWorld(i, front, up, flip);
                if (item instanceof BlockItem blockItem) {
                    var context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, taken.copy(),
                            BlockHitResult.miss(player.getEyePosition(0), Direction.UP, pos));
                    if (blockItem.place(context) == InteractionResult.FAIL) {
                        if (supply != null) supply.giveBack(player, taken);
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
                } else {
                    level.setBlock(pos, StructureBlocks.stateOf(item), 3);
                    if (supply != null) player.getInventory().placeItemBackInInventory(new ItemStack(Items.BUCKET));
                }
                placed++;
            }
        }
        player.sendSystemMessage(Component.translatable(RESULT, placed));
        if (missing > 0) player.sendSystemMessage(Component.translatable(MISSING, missing));
        controller.setWaitingTime(0);
        if (controller.isFormed()) controller.requestCheck();
        else controller.asyncCheckPattern(MultiblockWorldData.getOrCreate(level));
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
