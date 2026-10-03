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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
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
        setBlocks(states, null);
    }

    void setBlocks(Long2ObjectOpenHashMap<BlockState> states, @Nullable Long2ObjectOpenHashMap<StructureScene.PartOwner> owners) {
        states = connectShapes(connectShapes(states));
        var entities = new Long2ObjectOpenHashMap<BlockEntity>();
        var parts = new Long2ObjectOpenHashMap<IMultiPart>();
        var controllers = new Long2ObjectOpenHashMap<IMultiController>();
        IMultiController controller = null;
        for (var it = states.long2ObjectEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            var entity = createEntity(entry.getLongKey(), entry.getValue());
            if (entity == null) continue;
            entities.put(entry.getLongKey(), entity);
            if (entity instanceof MetaMachineBlockEntity metaMachineBlock) {
                if (metaMachineBlock.metaMachine instanceof IMultiController multiController) {
                    controller = multiController;
                    controller.setFormed();
                    controllers.put(entry.getLongKey(), multiController);
                } else if (metaMachineBlock.metaMachine instanceof IMultiPart multiPart) {
                    parts.put(entry.getLongKey(), multiPart);
                }
            }
        }
        if (owners != null) {
            for (var it = parts.long2ObjectEntrySet().fastIterator(); it.hasNext();) {
                var entry = it.next();
                var owner = owners.get(entry.getLongKey());
                if (owner == null) continue;
                var found = controllers.get(owner.controller());
                if (found == null) {
                    found = hiddenController(owner);
                    if (found == null) continue;
                    controllers.put(owner.controller(), found);
                }
                entry.getValue().addedToController(found);
            }
        } else if (controller != null) {
            var finalController = controller;
            parts.values().forEach(entry -> entry.addedToController(finalController));
        }
        view = new View(this, states, entities, ALL_LAYERS);
    }

    @Nullable
    private BlockEntity createEntity(long key, BlockState state) {
        if (!state.hasBlockEntity() || !(state.getBlock() instanceof EntityBlock block)) return null;
        try {
            var entity = block.newBlockEntity(BlockPos.of(key), state);
            if (entity != null) entity.setLevel(this);
            return entity;
        } catch (Throwable t) {
            GTCEu.LOGGER.warn("structure preview failed to create block entity for {}", state, t);
            return null;
        }
    }

    @Nullable
    private IMultiController hiddenController(StructureScene.PartOwner owner) {
        if (createEntity(owner.controller(), owner.state()) instanceof MetaMachineBlockEntity entity &&
                entity.metaMachine instanceof IMultiController multiController) {
            multiController.setFormed();
            return multiController;
        }
        return null;
    }

    private Long2ObjectOpenHashMap<BlockState> connectShapes(Long2ObjectOpenHashMap<BlockState> states) {
        view = new View(this, states, new Long2ObjectOpenHashMap<>(), ALL_LAYERS);
        var connected = new Long2ObjectOpenHashMap<BlockState>(states.size());
        for (var it = states.long2ObjectEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            var state = entry.getValue();
            try {
                var updated = Block.updateFromNeighbourShapes(state, this, BlockPos.of(entry.getLongKey()));
                if (updated.getBlock() == state.getBlock()) state = updated;
            } catch (Throwable ignored) {}
            connected.put(entry.getLongKey(), state);
        }
        return connected;
    }

    void setLiveBlocks(Long2ObjectOpenHashMap<BlockState> states, Long2ObjectOpenHashMap<BlockEntity> entities) {
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
