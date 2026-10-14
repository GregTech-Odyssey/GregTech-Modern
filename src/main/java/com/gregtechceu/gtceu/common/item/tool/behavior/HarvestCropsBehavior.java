package com.gregtechceu.gtceu.common.item.tool.behavior;

import com.gregtechceu.gtceu.api.item.tool.ToolHelper;
import com.gregtechceu.gtceu.api.item.tool.aoe.AoESymmetrical;
import com.gregtechceu.gtceu.api.item.tool.behavior.IToolBehavior;
import com.gregtechceu.gtceu.api.tooltip.Tips;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class HarvestCropsBehavior implements IToolBehavior {

    public static final HarvestCropsBehavior INSTANCE = new HarvestCropsBehavior();

    protected HarvestCropsBehavior() {/**/}

    @NotNull
    @Override
    public InteractionResult onItemUse(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            BlockState state = level.getBlockState(context.getClickedPos());
            return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        ItemStack stack = context.getItemInHand();
        AoESymmetrical aoe = ToolHelper.getAoEDefinition(stack);
        List<BlockPos> blocks = aoe.isZero() ? List.of(context.getClickedPos()) :
                ToolHelper.iterateAoE(aoe, context, HarvestCropsBehavior::isCrop);
        boolean harvested = false;
        for (BlockPos blockPos : blocks) {
            harvested |= harvestBlockRoutine(blockPos, context);
            if (stack.isEmpty()) break;
        }
        return harvested ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    private static boolean isCrop(UseOnContext context, BlockPos pos) {
        Level level = context.getLevel();
        return level.getBlockState(pos.above()).isAir() && level.getBlockState(pos).getBlock() instanceof CropBlock;
    }

    private static boolean harvestBlockRoutine(BlockPos pos, UseOnContext context) {
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();

        BlockState blockState = level.getBlockState(pos);
        if (!(blockState.getBlock() instanceof CropBlock cropBlock)) return false;

        ItemStack seed = blockState.getCloneItemStack(context.getHitResult().withPosition(pos), level, pos, player);
        if (cropBlock.isMaxAge(blockState)) {
            var drops = Block.getDrops(blockState, (ServerLevel) level, pos, null);
            boolean removedSeed = false;
            for (ItemStack drop : drops) {
                if (!removedSeed && ItemStack.isSameItemSameTags(drop, seed)) {
                    drop.shrink(1);
                    removedSeed = true;
                    if (drop.isEmpty()) continue;
                }
                Block.popResource(level, pos, drop);
            }
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(blockState));
            level.setBlock(pos, cropBlock.getStateForAge(0), Block.UPDATE_ALL_IMMEDIATE);
            ToolHelper.damageItem(stack, player);
            return true;
        }

        return false;
    }

    @Override
    public void addInformation(@NotNull ItemStack stack, @Nullable Level world, @NotNull List<Component> tooltip,
                               @NotNull TooltipFlag flag) {
        tooltip.add(Tips.feature(Component.translatable("item.gtceu.tool.behavior.crop_harvesting")));
        if (GTUtil.isShiftDown()) tooltip.add(Tips.detail(Component.translatable("item.gtceu.tool.behavior.crop_harvesting.detail")));
    }
}
