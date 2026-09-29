package com.gregtechceu.gtceu.api.machine.multiblockpro;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.ControllerPredicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

public final class StructureBlocks {

    private StructureBlocks() {}

    @Nullable
    public static BlockState stateOf(@Nullable Item item) {
        if (item instanceof BlockItem blockItem) return blockItem.getBlock().defaultBlockState();
        if (item instanceof BucketItem bucket && bucket.getFluid() != Fluids.EMPTY) return bucket.getFluid().defaultFluidState().createLegacyBlock();
        return null;
    }

    public static BlockState face(BlockState state, @Nullable Direction facing) {
        if (facing == null) return state;
        if (state.getBlock() instanceof MetaMachineBlock machine && !MetaMachine.isFacingValid(machine, state, facing)) return state;
        if (state.hasProperty(BlockStateProperties.FACING)) return state.setValue(BlockStateProperties.FACING, facing);
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && facing.getAxis().isHorizontal()) {
            return state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        }
        return state;
    }

    public static BlockState controllerState(MultiblockMachineDefinition definition) {
        var state = definition.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.FACING)) return state.setValue(BlockStateProperties.FACING, Direction.NORTH);
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        return state;
    }

    public static Long2ObjectOpenHashMap<BlockState> worldBlocks(Layout layout, Item[] items, BlockPos origin, Direction front, Direction up,
                                                                 boolean flip) {
        var cells = layout.cells();
        var blocks = new Long2ObjectOpenHashMap<BlockState>(cells.size());
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i).predicate() instanceof ControllerPredicate) continue;
            var base = stateOf(items[i]);
            if (base == null) continue;
            var state = face(base, layout.outwardWorld(i, front, up, flip));
            if (!state.isAir()) blocks.put(layout.worldPos(origin, i, front, up, flip).asLong(), state);
        }
        return blocks;
    }
}
