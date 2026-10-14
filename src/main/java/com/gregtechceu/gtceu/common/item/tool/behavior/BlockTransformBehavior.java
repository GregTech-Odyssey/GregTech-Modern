package com.gregtechceu.gtceu.common.item.tool.behavior;

import com.gregtechceu.gtceu.api.item.tool.ToolHelper;
import com.gregtechceu.gtceu.api.item.tool.aoe.AoESymmetrical;
import com.gregtechceu.gtceu.api.item.tool.behavior.IToolBehavior;
import com.gregtechceu.gtceu.api.tooltip.Tips;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 右键按工具动作改造方块（铺路、耕地、去皮、去氧化、去蜡），支持范围；中心块可改造才会触发。
 */
public final class BlockTransformBehavior implements IToolBehavior {

    public static final BlockTransformBehavior FLATTEN = new BlockTransformBehavior(ToolActions.SHOVEL_FLATTEN, SoundEvents.SHOVEL_FLATTEN, -1, true, true, "item.gtceu.tool.behavior.grass_path", "item.gtceu.tool.behavior.grass_path.detail");
    public static final BlockTransformBehavior TILL = new BlockTransformBehavior(ToolActions.HOE_TILL, SoundEvents.HOE_TILL, -1, true, false, "item.gtceu.tool.behavior.ground_tilling", "item.gtceu.tool.behavior.ground_tilling.detail");
    public static final BlockTransformBehavior STRIP = new BlockTransformBehavior(ToolActions.AXE_STRIP, SoundEvents.AXE_STRIP, -1, false, false, "item.gtceu.tool.behavior.strip_log", "item.gtceu.tool.behavior.strip_log.detail");
    public static final BlockTransformBehavior SCRAPE = new BlockTransformBehavior(ToolActions.AXE_SCRAPE, SoundEvents.AXE_SCRAPE, LevelEvent.PARTICLES_SCRAPE, false, false, "item.gtceu.tool.behavior.scrape", "item.gtceu.tool.behavior.scrape.detail");
    public static final BlockTransformBehavior WAX_OFF = new BlockTransformBehavior(ToolActions.AXE_WAX_OFF, SoundEvents.AXE_WAX_OFF, LevelEvent.PARTICLES_WAX_OFF, false, false, "item.gtceu.tool.behavior.remove_wax", "item.gtceu.tool.behavior.remove_wax.detail");

    private final ToolAction action;
    private final SoundEvent sound;
    private final int particleEvent;
    private final boolean fromAboveOnly;
    private final boolean flatten;
    private final String tooltipKey;
    private final String detailKey;

    private BlockTransformBehavior(ToolAction action, SoundEvent sound, int particleEvent, boolean fromAboveOnly,
                                   boolean flatten, String tooltipKey, String detailKey) {
        this.action = action;
        this.sound = sound;
        this.particleEvent = particleEvent;
        this.fromAboveOnly = fromAboveOnly;
        this.flatten = flatten;
        this.tooltipKey = tooltipKey;
        this.detailKey = detailKey;
    }

    @NotNull
    @Override
    public InteractionResult onItemUse(UseOnContext context) {
        if (fromAboveOnly && context.getClickedFace() == Direction.DOWN) return InteractionResult.PASS;
        Level level = context.getLevel();
        BlockPos center = context.getClickedPos();
        if (!canTransform(context, center)) {
            return flatten ? dowseCampfire(context) : InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ItemStack stack = context.getItemInHand();
        AoESymmetrical aoe = ToolHelper.getAoEDefinition(stack);
        List<BlockPos> positions = aoe.isZero() ? List.of(center) : ToolHelper.iterateAoE(aoe, context, this::canTransform);
        Player player = context.getPlayer();
        boolean changed = false;
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            BlockState newState = state.getToolModifiedState(at(context, pos), action, false);
            if (newState == null || newState == state) continue;
            if (!level.setBlock(pos, newState, Block.UPDATE_ALL_IMMEDIATE)) continue;
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, newState));
            if (particleEvent >= 0) level.levelEvent(player, particleEvent, pos, 0);
            changed = true;
            ToolHelper.damageItem(stack, player);
            if (stack.isEmpty()) break;
        }
        if (!changed) return InteractionResult.PASS;
        level.playSound(null, center, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        return InteractionResult.CONSUME;
    }

    private boolean canTransform(UseOnContext context, BlockPos pos) {
        Level level = context.getLevel();
        if (flatten && !level.isEmptyBlock(pos.above())) return false;
        BlockState state = level.getBlockState(pos);
        BlockState newState = state.getToolModifiedState(at(context, pos), action, true);
        return newState != null && newState != state;
    }

    private static UseOnContext at(UseOnContext context, BlockPos pos) {
        if (pos.equals(context.getClickedPos())) return context;
        return new UseOnContext(context.getLevel(), context.getPlayer(), context.getHand(), context.getItemInHand(),
                context.getHitResult().withPosition(pos.immutable()));
    }

    private static InteractionResult dowseCampfire(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CampfireBlock) || !state.getValue(CampfireBlock.LIT)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        level.levelEvent(null, LevelEvent.SOUND_EXTINGUISH_FIRE, pos, 0);
        CampfireBlock.dowse(player, level, pos, state);
        BlockState newState = state.setValue(CampfireBlock.LIT, false);
        level.setBlock(pos, newState, Block.UPDATE_ALL_IMMEDIATE);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, newState));
        ToolHelper.damageItem(context.getItemInHand(), player);
        return InteractionResult.CONSUME;
    }

    @Override
    public void addInformation(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip,
                               @NotNull TooltipFlag flag) {
        tooltip.add(Tips.feature(Component.translatable(tooltipKey)));
        if (GTUtil.isShiftDown()) tooltip.add(Tips.detail(Component.translatable(detailKey)));
    }
}
