package com.gregtechceu.gtceu.api.pattern.predicates;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public class PredicateFluids extends SimplePredicate {

    protected Fluid fluid;

    public PredicateFluids() {
        super("fluids");
    }

    public PredicateFluids(Fluid fluid) {
        this();
        this.fluid = fluid;
        buildPredicate();
    }

    @Override
    public SimplePredicate buildPredicate() {
        if (fluid == null) fluid = Fluids.WATER;
        predicate = state -> state.getBlockState().getFluidState().getType() == fluid;
        var block = new Block[] { fluid.defaultFluidState().createLegacyBlock().getBlock() };
        candidates = () -> block;
        var info = BlockInfo.fromBlock(block[0]);
        blockInfo = () -> info;
        return this;
    }
}
