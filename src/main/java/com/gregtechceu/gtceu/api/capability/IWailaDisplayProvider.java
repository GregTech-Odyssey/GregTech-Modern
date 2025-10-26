package com.gregtechceu.gtceu.api.capability;

import net.minecraft.nbt.CompoundTag;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public interface IWailaDisplayProvider {

    void appendWailaTooltip(CompoundTag data, ITooltip iTooltip, BlockAccessor blockAccessor, IPluginConfig iPluginConfig);

    void appendWailaData(CompoundTag data, BlockAccessor blockAccessor);
}
