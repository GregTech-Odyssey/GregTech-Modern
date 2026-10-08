package com.gregtechceu.gtceu.api.block;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import it.unimi.dsi.fastutil.longs.LongSet;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class ActiveBlock extends AppearanceBlock {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public ActiveBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ACTIVE, false));
    }

    public static void updateActiveBlocks(LongSet activeBlocks, Level level, boolean active) {
        activeBlocks.forEach(pos -> {
            var blockPos = BlockPos.of(pos);
            var blockState = level.getBlockState(blockPos);
            if (blockState.hasProperty(ActiveBlock.ACTIVE)) {
                var newState = blockState.setValue(ActiveBlock.ACTIVE, active);
                if (newState != blockState) {
                    level.setBlock(blockPos, newState, Block.UPDATE_KNOWN_SHAPE);
                }
            }
        });
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }

    @Override
    public BlockState getBlockAppearance(BlockState state, BlockAndTintGetter level, BlockPos pos, Direction side,
                                         BlockState sourceState, BlockPos sourcePos) {
        return defaultBlockState();
    }

    public enum State {
        UNKNOWN,
        ACTIVE,
        NON_ACTIVE
    }
}
