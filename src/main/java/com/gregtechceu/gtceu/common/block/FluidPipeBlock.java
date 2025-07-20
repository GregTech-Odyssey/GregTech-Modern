package com.gregtechceu.gtceu.common.block;

import com.gregtechceu.gtceu.api.block.MaterialPipeBlock;
import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.FluidPipeProperties;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.pipenet.IPipeNode;
import com.gregtechceu.gtceu.client.model.PipeModel;
import com.gregtechceu.gtceu.common.blockentity.FluidPipeBlockEntity;
import com.gregtechceu.gtceu.common.data.GTBlockEntities;
import com.gregtechceu.gtceu.common.pipelike.fluid.FluidPipeType;
import com.gregtechceu.gtceu.common.pipelike.fluid.LevelFluidPipeNet;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import org.jetbrains.annotations.Nullable;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class FluidPipeBlock extends MaterialPipeBlock<FluidPipeType, FluidPipeProperties, LevelFluidPipeNet> {

    public FluidPipeBlock(Properties properties, FluidPipeType fluidPipeType, Material material) {
        super(properties, fluidPipeType, material);
    }

    @Override
    protected FluidPipeProperties createProperties(FluidPipeType fluidPipeType, Material material) {
        return fluidPipeType.modifyProperties(material.getProperty(PropertyKey.FLUID_PIPE));
    }

    @Override
    protected FluidPipeProperties createMaterialData() {
        return material.getProperty(PropertyKey.FLUID_PIPE);
    }

    @Override
    protected PipeModel createPipeModel() {
        return pipeType.createPipeModel(material);
    }

    @Override
    public LevelFluidPipeNet getWorldPipeNet(ServerLevel level) {
        return LevelFluidPipeNet.getOrCreate(level);
    }

    @Override
    public BlockEntityType<? extends PipeBlockEntity<FluidPipeType, FluidPipeProperties>> getBlockEntityType() {
        return GTBlockEntities.FLUID_PIPE.get();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        FluidPipeProperties properties = createProperties(defaultBlockState(), stack);
        tooltip.add(Component.translatable("gtceu.universal.tooltip.fluid_transfer_rate", properties.getThroughput()));
    }

    @Override
    public boolean canPipesConnect(IPipeNode<FluidPipeType, FluidPipeProperties> selfTile, Direction side,
                                   IPipeNode<FluidPipeType, FluidPipeProperties> sideTile) {
        return selfTile instanceof FluidPipeBlockEntity && sideTile instanceof FluidPipeBlockEntity;
    }

    @Override
    public boolean canPipeConnectToBlock(IPipeNode<FluidPipeType, FluidPipeProperties> selfTile, Direction side,
                                         @Nullable BlockEntity tile) {
        return tile != null &&
                tile.getCapability(ForgeCapabilities.FLUID_HANDLER, side.getOpposite()).isPresent();
    }
}
