package com.gregtechceu.gtceu.api.pattern;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;

import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.Builder;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import lombok.Getter;

import java.util.function.Supplier;

public class MultiblockShapeInfo {

    public final Supplier<BlockPattern> pattern;
    @Getter
    private final BlockInfo[][][] blocks; // [z][y][x]

    public MultiblockShapeInfo(Supplier<BlockPattern> pattern, BlockInfo[][][] blocks) {
        this.blocks = blocks;
        this.pattern = pattern;
    }

    public static ShapeInfoBuilder builder() {
        return new ShapeInfoBuilder();
    }

    public static class ShapeInfoBuilder extends Builder<BlockInfo, ShapeInfoBuilder> {

        public ShapeInfoBuilder where(char symbol, BlockState blockState) {
            return where(symbol, BlockInfo.fromBlockState(blockState));
        }

        public ShapeInfoBuilder where(char symbol, Supplier<? extends Block> block) {
            return where(symbol, block.get());
        }

        public ShapeInfoBuilder where(char symbol, Block block) {
            return where(symbol, block.defaultBlockState());
        }

        public ShapeInfoBuilder where(char symbol, Supplier<? extends MetaMachineBlock> machine, Direction facing) {
            return where(symbol, machine.get(), facing);
        }

        public ShapeInfoBuilder where(char symbol, MetaMachineBlock machine, Direction facing) {
            return where(symbol, machine.getRotationState() == RotationState.NONE ?
                    machine.defaultBlockState() :
                    machine.defaultBlockState().setValue(machine.getRotationState().property, facing));
        }

        private BlockInfo[][][] bake() {
            return this.bakeArray(BlockInfo.class, BlockInfo.EMPTY);
        }

        public MultiblockShapeInfo build(Supplier<BlockPattern> pattern) {
            return new MultiblockShapeInfo(pattern, bake());
        }

        public MultiblockShapeInfo build(MultiblockMachineDefinition definition, int index) {
            return build(definition.getPatternFactory()[index]);
        }

        public MultiblockShapeInfo build(MultiblockMachineDefinition definition) {
            return build(definition.getPatternFactory()[0]);
        }
    }
}
