package com.gregtechceu.gtceu.common.blockentity;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.AssemblyLineMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class AssemblyLineBlockEntity extends MetaMachineBlockEntity {

    public AssemblyLineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public AABB getRenderBoundingBox() {
        var machine = (AssemblyLineMachine) getMetaMachine();
        var front = machine.getFrontFacing();
        var upwards = machine.getUpwardsFacing();
        boolean flipped = machine.isFlipped();
        var down = RelativeDirection.DOWN.getRelative(front, upwards, flipped);
        var back = RelativeDirection.BACK.getRelative(front, upwards, flipped);
        var right = RelativeDirection.RIGHT.getRelative(front, upwards, flipped);
        return new AABB(getBlockPos())
                .expandTowards(down.getStepX() * 2, down.getStepY() * 2, down.getStepZ() * 2)
                .expandTowards(-down.getStepX(), -down.getStepY(), -down.getStepZ())
                .expandTowards(back.getStepX() * 2, back.getStepY() * 2, back.getStepZ() * 2)
                .expandTowards(right.getStepX() * 17, right.getStepY() * 17, right.getStepZ() * 17);
    }
}
