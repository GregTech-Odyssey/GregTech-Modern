package com.gregtechceu.gtceu.api.machine.multiblockpro;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.ControllerPredicate;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldData;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;

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
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

public final class StructureBuild {

    public static final String RESULT = "gtceu.structure_build.result";
    public static final String MISSING = "gtceu.structure_build.missing";
    public static final String INVALID = "gtceu.structure_build.invalid";

    private StructureBuild() {}

    public static void build(ServerPlayer player, IMultiController controller, int[] values, Item[] choices) {
        var structure = StructurePattern.of(controller.self().getDefinition());
        var layout = structure == null ? null : structure.layout(values);
        if (layout == null) {
            player.sendSystemMessage(Component.translatable(INVALID));
            return;
        }
        build(player, controller, layout, choices);
    }

    public static void build(ServerPlayer player, IMultiController controller, Layout layout, Item[] choices) {
        if (layout.cells().size() != choices.length) {
            player.sendSystemMessage(Component.translatable(INVALID));
            return;
        }
        if (!player.mayBuild()) return;
        var machine = controller.self();
        var level = player.serverLevel();
        var origin = machine.getPos();
        var front = machine.getFrontFacing();
        var up = machine.getUpwardsFacing();
        boolean flip = machine.isFlipped();
        boolean ultraWarm = level.dimensionType().ultraWarm();
        var allowed = new Reference2ObjectOpenHashMap<TraceabilityPredicate, ReferenceOpenHashSet<Item>>();
        int placed = 0, missing = 0;
        var supply = player.isCreative() ? null : PlayerSupply.of(player, true);
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
                if (supply != null && !supply.take(item)) {
                    missing++;
                    continue;
                }
                var facing = layout.outwardWorld(i, front, up, flip);
                if (item instanceof BlockItem blockItem) {
                    var context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, new ItemStack(item),
                            BlockHitResult.miss(player.getEyePosition(0), Direction.UP, pos));
                    if (blockItem.place(context) == InteractionResult.FAIL) {
                        if (supply != null) player.getInventory().placeItemBackInInventory(new ItemStack(item));
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

    private static ReferenceOpenHashSet<Item> candidates(TraceabilityPredicate predicate) {
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
