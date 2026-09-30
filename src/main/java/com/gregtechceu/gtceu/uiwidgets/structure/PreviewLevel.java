package com.gregtechceu.gtceu.uiwidgets.structure;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;

import com.lowdragmc.lowdraglib.utils.DummyWorld;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

@OnlyIn(Dist.CLIENT)
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
final class PreviewLevel extends DummyWorld {

    static final int ALL_LAYERS = Integer.MIN_VALUE;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    @Nullable
    private final Holder<Biome> biome;
    private View view;

    PreviewLevel(Level level) {
        super(level);
        this.biome = level.registryAccess().registryOrThrow(Registries.BIOME).getHolder(Biomes.PLAINS).orElse(null);
        this.view = new View(this, new Long2ObjectOpenHashMap<>(), new Long2ObjectOpenHashMap<>(), ALL_LAYERS);
    }

    View view() {
        return view;
    }

    void setBlocks(Long2ObjectOpenHashMap<BlockState> states) {
        var entities = new Long2ObjectOpenHashMap<BlockEntity>();
        var parts = new ReferenceOpenHashSet<IMultiPart>();
        IMultiController controller = null;
        for (var it = states.long2ObjectEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            var state = entry.getValue();
            if (!state.hasBlockEntity() || !(state.getBlock() instanceof EntityBlock block)) continue;
            var pos = BlockPos.of(entry.getLongKey());
            try {
                var entity = block.newBlockEntity(pos, state);
                if (entity == null) continue;
                entity.setLevel(this);
                entities.put(entry.getLongKey(), entity);
                if (entity instanceof MetaMachineBlockEntity metaMachineBlock) {
                    if (metaMachineBlock.metaMachine instanceof IMultiController multiController) {
                        controller = multiController;
                        controller.setFormed();
                    } else if (metaMachineBlock.metaMachine instanceof IMultiPart multiPart) {
                        parts.add(multiPart);
                    }
                }
            } catch (Throwable t) {
                GTCEu.LOGGER.warn("structure preview failed to create block entity for {}", state, t);
            }
        }
        if (controller != null) {
            var finalController = controller;
            parts.forEach(entry -> entry.addedToController(finalController));
        }
        view = new View(this, states, entities, ALL_LAYERS);
    }

    void setLayer(int onlyY) {
        if (view.onlyY != onlyY) view = new View(this, view.states, view.entities, onlyY);
    }

    void clear() {
        view = new View(this, new Long2ObjectOpenHashMap<>(), new Long2ObjectOpenHashMap<>(), ALL_LAYERS);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return view.getBlockState(pos);
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return view.getBlockEntity(pos);
    }

    @Nullable
    @Override
    public BlockEntity getExistingBlockEntity(BlockPos pos) {
        return view.getBlockEntity(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return view.getBlockState(pos).getFluidState();
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        return view.getBlockTint(pos, resolver);
    }

    static final class View implements BlockAndTintGetter {

        private final PreviewLevel level;
        final Long2ObjectOpenHashMap<BlockState> states;
        final Long2ObjectOpenHashMap<BlockEntity> entities;
        final int onlyY;

        private View(PreviewLevel level, Long2ObjectOpenHashMap<BlockState> states, Long2ObjectOpenHashMap<BlockEntity> entities, int onlyY) {
            this.level = level;
            this.states = states;
            this.entities = entities;
            this.onlyY = onlyY;
        }

        boolean visible(long pos) {
            return onlyY == ALL_LAYERS || BlockPos.getY(pos) == onlyY;
        }

        BlockState get(long pos) {
            if (!visible(pos)) return AIR;
            var state = states.get(pos);
            return state == null ? AIR : state;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return get(pos.asLong());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            long key = pos.asLong();
            return visible(key) ? entities.get(key) : null;
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            return level.getShade(direction, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return level.getLightEngine();
        }

        @Override
        public int getBrightness(LightLayer layer, BlockPos pos) {
            return 15;
        }

        @Override
        public int getRawBrightness(BlockPos pos, int amount) {
            return 15;
        }

        @Override
        public boolean canSeeSky(BlockPos pos) {
            return true;
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            var biome = level.biome;
            return biome == null ? -1 : resolver.getColor(biome.value(), pos.getX(), pos.getZ());
        }

        @Override
        public int getHeight() {
            return level.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return level.getMinBuildHeight();
        }
    }
}
