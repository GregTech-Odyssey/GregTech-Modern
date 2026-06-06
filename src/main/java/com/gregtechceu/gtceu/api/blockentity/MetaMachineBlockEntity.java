package com.gregtechceu.gtceu.api.blockentity;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.capability.*;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.item.tool.IToolGridHighlight;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.client.renderer.GTRendererProvider;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;

import com.gto.datasynclib.FieldDataManager;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.Set;

public class MetaMachineBlockEntity extends GTBlockEntity implements IToolGridHighlight, IPaintable, IWailaDisplayProvider {

    @Getter
    public final MetaMachine metaMachine;
    public final MachineDefinition definition;

    protected MetaMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        this.definition = blockState.getBlock() instanceof MetaMachineBlock machineBlock ? machineBlock.definition : null;
        assert definition != null : "MetaMachineBlockEntity is created for an un available block: +" + blockState.getBlock();
        this.metaMachine = definition.createMetaMachine(this);
    }

    @Override
    public ICoverable getCoverContainer() {
        return metaMachine.coverContainer;
    }

    public static MetaMachineBlockEntity createBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        return new MetaMachineBlockEntity(type, pos, blockState);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        metaMachine.onUnload();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        metaMachine.onLoad();
    }

    @Override
    public boolean shouldRenderGrid(Player player, BlockPos pos, BlockState state, ItemStack held, Set<GTToolType> toolTypes) {
        return metaMachine.shouldRenderGrid(player, pos, state, held, toolTypes);
    }

    @Override
    public ResourceTexture sideTips(Player player, BlockPos pos, BlockState state, Set<GTToolType> toolTypes, Direction side) {
        return metaMachine.sideTips(player, pos, state, toolTypes, side);
    }

    @Nullable
    public final <T> T getGTCapability(@NotNull Class<T> cap, @Nullable Direction side) {
        return metaMachine.getGTCapability(cap, side);
    }

    @Override
    @NotNull
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        var result = getCapability(metaMachine, cap, side);
        return result == null ? super.getCapability(cap, side) : result;
    }

    @Nullable
    public static <T> LazyOptional<T> getCapability(MetaMachine machine, @NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return machine.itemCapDirectionCache.getOrSet(side, () -> {
                var handler = machine.getItemHandlerCap(side, true);
                if (handler != null) {
                    return LazyOptional.of(() -> handler);
                }
                return LazyOptional.empty();
            }).cast();
        } else if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return machine.fluidCapDirectionCache.getOrSet(side, () -> {
                var handler = machine.getFluidHandlerCap(side, true);
                if (handler != null) {
                    return LazyOptional.of(() -> handler);
                }
                return LazyOptional.empty();
            }).cast();
        }
        return machine.getCapability(cap, side);
    }

    /**
     * Why, Forge, Why?
     * Why must you make me add a method for no good reason?
     */
    @OnlyIn(Dist.CLIENT)
    @Override
    public AABB getRenderBoundingBox() {
        GTRendererProvider instance = GTRendererProvider.getInstance();
        if (instance != null) {
            IRenderer renderer = instance.getRenderer(this);
            if (renderer != null) {
                if (renderer.getViewDistance() == 64 /* the default */) {
                    return new AABB(worldPosition.offset(-1, 0, -1), worldPosition.offset(2, 2, 2));
                }
                int viewDistHalf = renderer.getViewDistance() / 2;
                return new AABB(worldPosition).inflate(viewDistHalf);
            }
        }
        return new AABB(worldPosition.offset(-1, 0, -1), worldPosition.offset(2, 2, 2));
    }

    public Level level() {
        return level;
    }

    public BlockPos pos() {
        return worldPosition;
    }

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        metaMachine.saveCustomPersistedData(tag, forDrop);
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        metaMachine.loadCustomPersistedData(tag);
    }

    @Override
    public int getPaintingColor() {
        return metaMachine.getPaintingColor();
    }

    @Override
    public void setPaintingColor(int color) {
        metaMachine.setPaintingColor(color);
    }

    @Override
    public int getDefaultPaintingColor() {
        return metaMachine.getDefaultPaintingColor();
    }

    @Override
    public void appendWailaTooltip(CompoundTag data, ITooltip iTooltip, BlockAccessor blockAccessor, IPluginConfig iPluginConfig) {
        if (metaMachine instanceof IWailaDisplayProvider provider) {
            provider.appendWailaTooltip(data, iTooltip, blockAccessor, iPluginConfig);
        }
    }

    @Override
    public void appendWailaData(CompoundTag data, BlockAccessor blockAccessor) {
        if (metaMachine instanceof IWailaDisplayProvider provider) {
            provider.appendWailaData(data, blockAccessor);
        }
    }

    @Override
    public FieldDataManager getFieldDataManager() {
        return metaMachine.getFieldDataManager();
    }
}
