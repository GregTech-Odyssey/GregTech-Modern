package com.gregtechceu.gtceu.common.machine.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Interaction state belongs to one storage block, rather than following a player between blocks. */
final class StorageInteraction {

    private UUID lastPlayer;
    private InteractionHand lastHand;
    private long lastUseTick;
    private long lastExtractTick = -3;

    static boolean isDisplayHit(BlockPos pos, Direction front, BlockHitResult hit) {
        if (hit.getType() != HitResult.Type.BLOCK || hit.getDirection() != front || !pos.equals(hit.getBlockPos())) {
            return false;
        }
        var location = hit.getLocation();
        double x = location.x - pos.getX();
        double y = location.y - pos.getY();
        double z = location.z - pos.getZ();
        return switch (front.getAxis()) {
            case X -> inside(y) && inside(z);
            case Y -> inside(x) && inside(z);
            case Z -> inside(x) && inside(y);
        };
    }

    private static boolean inside(double coordinate) {
        // Both storage models have a glass display from pixel 2 through pixel 14.
        return coordinate >= 2.0 / 16 && coordinate <= 14.0 / 16;
    }

    boolean recordUse(UUID player, InteractionHand hand, long tick) {
        long elapsed = tick - lastUseTick;
        boolean repeated = player.equals(lastPlayer) && hand == lastHand && elapsed > 0 && elapsed <= 6;
        lastPlayer = player;
        lastHand = hand;
        lastUseTick = tick;
        return repeated;
    }

    boolean claimExtraction(long tick) {
        if (tick - lastExtractTick < 3) return false;
        lastExtractTick = tick;
        return true;
    }

    static ItemStack transferContainers(ItemStack stack, IFluidHandler tank, IItemHandler inventory,
                                        @Nullable Player player, int count, boolean fill) {
        if (stack.isEmpty() || !stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent()) return stack;
        if (player != null && player.isCreative()) count = Math.min(count, 1);
        for (int i = 0; i < count; i++) {
            // The same Forge operations used by Functional Storage preserve container remainders and stow results.
            var result = fill ? FluidUtil.tryFillContainerAndStow(stack, tank, inventory, Integer.MAX_VALUE, player, true) :
                    FluidUtil.tryEmptyContainerAndStow(stack, tank, inventory, Integer.MAX_VALUE, player, true);
            if (!result.isSuccess()) break;
            stack = result.getResult();
        }
        return stack;
    }
}
