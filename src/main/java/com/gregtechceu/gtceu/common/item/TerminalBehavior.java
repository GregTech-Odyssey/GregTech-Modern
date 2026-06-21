package com.gregtechceu.gtceu.common.item;

import com.gregtechceu.gtceu.api.item.component.IInteractionItem;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.client.renderer.MultiblockInWorldPreviewRenderer;
import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class TerminalBehavior implements IInteractionItem {

    @Override
    public InteractionResult onItemUseFirst(ItemStack itemStack, UseOnContext context) {
        if (context.getPlayer() != null) {
            Level level = context.getLevel();
            BlockPos blockPos = context.getClickedPos();
            if (MetaMachine.getMachine(level, blockPos) instanceof IMultiController controller) {
                if (context.getPlayer().isShiftKeyDown()) {
                    if (!level.isClientSide) {
                        controller.requestCheck();
                        controller.setWaitingTime(10);
                        controller.getPattern()[0].get().autoBuild(context.getPlayer(), controller.getMultiblockState());
                        controller.getMultiblockState().clearCache();
                        controller.setWaitingTime(0);
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                } else {
                    var self = controller.self();
                    if (level.isClientSide && self.getDefinition().isRenderWorldPreview()) {
                        MultiblockInWorldPreviewRenderer.showPreview(blockPos, self.getFrontFacing(), self.getUpwardsFacing(), self.getDefinition().getMatchingShapes().getFirst(), ConfigHolder.INSTANCE.client.inWorldPreviewDuration * 20);
                    }
                    return InteractionResult.SUCCESS;
                }
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Item item, Level level, Player player, InteractionHand usedHand) {
        ItemStack heldItem = player.getItemInHand(usedHand);
        return InteractionResultHolder.pass(heldItem);
    }
}
