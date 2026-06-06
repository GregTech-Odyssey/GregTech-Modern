package com.gregtechceu.gtceu.integration.jade.provider;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.GTBlockEntity;
import com.gregtechceu.gtceu.api.capability.IWailaDisplayProvider;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum BlockEntityProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    INSTANCE;

    private final ResourceLocation UID = GTCEu.id("block_entity_provider");

    @Override
    public void appendTooltip(ITooltip iTooltip, BlockAccessor blockAccessor, IPluginConfig iPluginConfig) {
        if (blockAccessor.getBlockEntity() instanceof IWailaDisplayProvider provider) {
            var data = blockAccessor.getServerData().get(UID.toString());
            if (data instanceof CompoundTag tag) {
                provider.appendWailaTooltip(tag, iTooltip, blockAccessor, iPluginConfig);
            }
        }
    }

    @Override
    public void appendServerData(CompoundTag compoundTag, BlockAccessor blockAccessor) {
        var be = blockAccessor.getBlockEntity();
        if (be instanceof GTBlockEntity blockEntity) {
            blockEntity.observe();
        }
        if (be instanceof IWailaDisplayProvider display) {
            var data = new CompoundTag();
            display.appendWailaData(data, blockAccessor);
            if (data.isEmpty()) return;
            compoundTag.put(UID.toString(), data);
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
