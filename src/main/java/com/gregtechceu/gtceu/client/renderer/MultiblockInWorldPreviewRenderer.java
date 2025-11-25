package com.gregtechceu.gtceu.client.renderer;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib.client.scene.forge.WorldSceneRendererImpl;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.fast.fastcollection.O2OOpenCacheHashMap;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static net.minecraft.world.level.block.RenderShape.INVISIBLE;

@OnlyIn(Dist.CLIENT)
public class MultiblockInWorldPreviewRenderer {

    private enum CacheState {
        UNUSED,
        COMPILING,
        COMPILED
    }

    private static final AtomicReference<Object> BUFFERS = new AtomicReference<>();
    @Nullable
    private static TrackedDummyWorld LEVEL = null;
    @Nullable
    private static Thread THREAD = null;
    private static final AtomicInteger LEFT_TICK = new AtomicInteger(-1);

    /**
     * It will be cached by lombok#@Getter(lazy=true)
     */
    private static VertexBuffer[] initBuffers() {
        List<RenderType> layers = RenderType.chunkBufferLayers();
        var buffers = new VertexBuffer[layers.size()];
        for (int j = 0; j < layers.size(); ++j) {
            buffers[j] = new VertexBuffer(VertexBuffer.Usage.STATIC);
        }
        return buffers;
    }

    private static final AtomicReference<CacheState> CACHE_STATE = new AtomicReference<>(CacheState.UNUSED);
    @Nullable
    private static BlockPos LAST_POS = null;
    private static int LAST_LAYER = -1;

    public static void cleanPreview() {
        CACHE_STATE.set(CacheState.UNUSED);
        LEVEL = null;
        LEFT_TICK.set(-1);
        LAST_POS = null;
        LAST_LAYER = -1;
    }

    public static void removePreview(BlockPos pos) {
        if (LAST_POS != null && LAST_POS.equals(pos)) {
            cleanPreview();
        }
    }

    /**
     * Show the multiblock preview in the world by the given pos, side, and shape info.
     */
    public static void showPreview(BlockPos pos, Direction front, Direction up, MultiblockShapeInfo shapeInfo, int duration) {
        Map<BlockPos, BlockInfo> blockMap = new O2OOpenCacheHashMap<>();
        LEVEL = new TrackedDummyWorld();
        var blocks = shapeInfo.getBlocks();
        BlockPos controllerPatternPos = null;
        var maxY = 0;
        // find the pos of controller
        for (int x = 0; x < blocks.length; x++) {
            BlockInfo[][] aisle = blocks[x];
            maxY = Math.max(maxY, aisle.length);
            for (int y = 0; y < aisle.length; y++) {
                BlockInfo[] column = aisle[y];
                for (int z = 0; z < column.length; z++) {
                    BlockInfo blockInfo = column[z];
                    if (blockInfo != null) {
                        var blockState = blockInfo.getBlockState();
                        // if its controller record its position offset.
                        if (blockState.getBlock() instanceof MetaMachineBlock machineBlock && machineBlock.getDefinition() instanceof MultiblockMachineDefinition) {
                            controllerPatternPos = new BlockPos(x, y, z);
                        }
                    }
                }
            }
        }
        if (controllerPatternPos == null) {
            // if there is no controller found
            return;
        }
        if (LAST_POS != null && LAST_POS.equals(pos)) {
            LAST_LAYER++;
            if (LAST_LAYER >= maxY) {
                LAST_LAYER = -1;
            }
        } else {
            LAST_LAYER = -1;
        }
        LAST_POS = pos;
        for (int x = 0; x < blocks.length; x++) {
            BlockInfo[][] aisle = blocks[x];
            for (int y = 0; y < aisle.length; y++) {
                BlockInfo[] column = aisle[y];
                if (LAST_LAYER != -1 && LAST_LAYER != y) {
                    continue;
                }
                for (int z = 0; z < column.length; z++) {
                    BlockInfo blockInfo = column[z];
                    if (blockInfo != null) {
                        var offset = new BlockPos(x, y, z).subtract(controllerPatternPos);
                        // rotation
                        offset = switch (front) {
                            case NORTH, UP, DOWN -> offset.rotate(Rotation.NONE);
                            case SOUTH -> offset.rotate(Rotation.CLOCKWISE_180);
                            case EAST -> offset.rotate(Rotation.COUNTERCLOCKWISE_90);
                            case WEST -> offset.rotate(Rotation.CLOCKWISE_90);
                        };
                        Rotation r = up == Direction.NORTH ? Rotation.NONE : up == Direction.EAST ? Rotation.CLOCKWISE_90 : up == Direction.SOUTH ? Rotation.CLOCKWISE_180 : up == Direction.WEST ? Rotation.COUNTERCLOCKWISE_90 : Rotation.NONE;
                        offset = rotateByFrontAxis(offset, front, r);
                        blockMap.put(pos.offset(offset), blockInfo);
                    }
                }
            }
        }
        LEVEL.addBlocks(blockMap);
        prepareBuffers(LEVEL, blockMap, duration);
    }

    private static BlockPos rotateByFrontAxis(BlockPos pos, Direction front, Rotation rotation) {
        if (front.getAxis() == Direction.Axis.X) {
            return switch (rotation) {
                case CLOCKWISE_90 -> new BlockPos(-pos.getX(), -front.getAxisDirection().getStep() * pos.getZ(), front.getAxisDirection().getStep() * -pos.getY());
                case CLOCKWISE_180 -> new BlockPos(-pos.getX(), -pos.getY(), pos.getZ());
                case COUNTERCLOCKWISE_90 -> new BlockPos(-pos.getX(), front.getAxisDirection().getStep() * pos.getZ(), front.getAxisDirection().getStep() * pos.getY());
                default -> new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
            };
        } else if (front.getAxis() == Direction.Axis.Y) {
            return switch (rotation) {
                case CLOCKWISE_90 -> new BlockPos(pos.getY(), -front.getAxisDirection().getStep() * pos.getZ(), -front.getAxisDirection().getStep() * pos.getX());
                case CLOCKWISE_180 -> new BlockPos(front.getAxisDirection().getStep() * pos.getX(), -front.getAxisDirection().getStep() * pos.getZ(), pos.getY());
                case COUNTERCLOCKWISE_90 -> new BlockPos(-pos.getY(), -front.getAxisDirection().getStep() * pos.getZ(), front.getAxisDirection().getStep() * pos.getX());
                default -> new BlockPos(-front.getAxisDirection().getStep() * pos.getX(), -front.getAxisDirection().getStep() * pos.getZ(), -pos.getY());
            };
        } else if (front.getAxis() == Direction.Axis.Z) {
            return switch (rotation) {
                case CLOCKWISE_90 -> new BlockPos(front.getAxisDirection().getStep() * pos.getY(), -front.getAxisDirection().getStep() * pos.getX(), pos.getZ());
                case CLOCKWISE_180 -> new BlockPos(-pos.getX(), -pos.getY(), pos.getZ());
                case COUNTERCLOCKWISE_90 -> new BlockPos(front.getAxisDirection().getStep() * -pos.getY(), front.getAxisDirection().getStep() * pos.getX(), pos.getZ());
                default -> pos;
            };
        }
        return pos;
    }

    public static void onClientTick() {
        if (LEFT_TICK.get() > 0) {
            if (LEFT_TICK.decrementAndGet() <= 0) {
                cleanPreview();
            }
        }
    }

    public static void renderInWorldPreview(PoseStack poseStack, Camera camera) {
        if (CACHE_STATE.get() == CacheState.COMPILED && LEVEL != null) {
            poseStack.pushPose();
            Vec3 projectedView = camera.getPosition();
            poseStack.translate(-projectedView.x, -projectedView.y, -projectedView.z);
            for (int i = 0; i < RenderType.chunkBufferLayers().size(); i++) {
                VertexBuffer vertexbuffer = getBUFFERS()[i];
                // some of stupid mod doesn't check if the buffer is invalid
                if (vertexbuffer.isInvalid() || vertexbuffer.getFormat() == null) continue;
                var layer = RenderType.chunkBufferLayers().get(i);
                // render cache vbo
                layer.setupRenderState();
                poseStack.pushPose();
                ShaderInstance shaderInstance = RenderSystem.getShader();
                for (int j = 0; j < 12; ++j) {
                    int k = RenderSystem.getShaderTexture(j);
                    shaderInstance.setSampler("Sampler" + j, k);
                }
                // setup shader uniform
                if (shaderInstance.MODEL_VIEW_MATRIX != null) {
                    shaderInstance.MODEL_VIEW_MATRIX.set(poseStack.last().pose());
                }
                if (shaderInstance.PROJECTION_MATRIX != null) {
                    shaderInstance.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
                }
                if (shaderInstance.COLOR_MODULATOR != null) {
                    shaderInstance.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
                }
                if (shaderInstance.FOG_START != null) {
                    shaderInstance.FOG_START.set(Float.MAX_VALUE);
                }
                if (shaderInstance.FOG_END != null) {
                    shaderInstance.FOG_END.set(RenderSystem.getShaderFogEnd());
                }
                if (shaderInstance.FOG_COLOR != null) {
                    shaderInstance.FOG_COLOR.set(RenderSystem.getShaderFogColor());
                }
                if (shaderInstance.FOG_SHAPE != null) {
                    shaderInstance.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
                }
                if (shaderInstance.TEXTURE_MATRIX != null) {
                    shaderInstance.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
                }
                if (shaderInstance.GAME_TIME != null) {
                    shaderInstance.GAME_TIME.set(RenderSystem.getShaderGameTime());
                }
                RenderSystem.setupShaderLights(shaderInstance);
                shaderInstance.apply();
                RenderSystem.setShaderColor(1, 1, 1, 1);
                if (layer == RenderType.translucent()) {
                    // SOLID
                    RenderSystem.enableBlend();
                    RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    RenderSystem.depthMask(false);
                } else {
                    // TRANSLUCENT
                    RenderSystem.enableDepthTest();
                    RenderSystem.disableBlend();
                    RenderSystem.depthMask(true);
                }
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                vertexbuffer.bind();
                vertexbuffer.draw();
                poseStack.popPose();
                shaderInstance.clear();
                VertexBuffer.unbind();
                layer.clearRenderState();
            }
            poseStack.popPose();
        }
    }

    private static void prepareBuffers(TrackedDummyWorld level, Map<BlockPos, BlockInfo> renderedBlocks, int duration) {
        if (THREAD != null) {
            THREAD.interrupt();
        }
        CACHE_STATE.set(CacheState.COMPILING);
        // call it to init the buffers
        getBUFFERS();
        THREAD = Thread.startVirtualThread(() -> {
            var dispatcher = Minecraft.getInstance().getBlockRenderer();
            ModelBlockRenderer.enableCaching();
            PoseStack poseStack = new PoseStack();
            for (int i = 0; i < RenderType.chunkBufferLayers().size(); i++) {
                if (Thread.interrupted()) return;
                var layer = RenderType.chunkBufferLayers().get(i);
                var buffer = new BufferBuilder(layer.bufferSize());
                buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
                renderBlocks(level, poseStack, dispatcher, layer, new WorldSceneRenderer.VertexConsumerWrapper(buffer), renderedBlocks);
                var builder = buffer.end();
                var vertexBuffer = getBUFFERS()[i];
                Runnable toUpload = () -> {
                    if (!vertexBuffer.isInvalid()) {
                        vertexBuffer.bind();
                        vertexBuffer.upload(builder);
                        VertexBuffer.unbind();
                    }
                };
                CompletableFuture.runAsync(toUpload, runnable -> RenderSystem.recordRenderCall(runnable::run));
            }
            ModelBlockRenderer.clearCache();
            if (Thread.interrupted()) return;
            CACHE_STATE.set(CacheState.COMPILED);
            THREAD = null;
            LEFT_TICK.set(duration);
        });
    }

    private static void renderBlocks(TrackedDummyWorld level, PoseStack poseStack, BlockRenderDispatcher dispatcher, RenderType layer, WorldSceneRenderer.VertexConsumerWrapper wrapperBuffer, Map<BlockPos, BlockInfo> renderedBlocks) {
        for (var e : renderedBlocks.entrySet()) {
            var pos = e.getKey();
            BlockState state = e.getValue().getBlockState();
            Block block = state.getBlock();
            if (block == Blocks.AIR) continue;
            // render blocks
            if (state.getRenderShape() != INVISIBLE && ItemBlockRenderTypes.getRenderLayers(state).contains(layer)) {
                poseStack.pushPose();
                poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
                poseStack.translate(0.5, 0.5, 0.5);
                poseStack.scale(0.8F, 0.8F, 0.8F);
                poseStack.translate(-0.5, -0.5, -0.5);
                level.setRenderFilter(p -> p.equals(pos));
                WorldSceneRendererImpl.renderBlocksForge(dispatcher, state, pos, level, poseStack, wrapperBuffer, GTValues.RNG, layer);
                level.setRenderFilter(GTUtil.FAVORABLE);
                poseStack.popPose();
            }
            // render fluids
            FluidState fluidState = state.getFluidState();
            if (!fluidState.isEmpty() && ItemBlockRenderTypes.getRenderLayer(fluidState) == layer) {
                wrapperBuffer.addOffset((pos.getX() - (pos.getX() & 15)), (pos.getY() - (pos.getY() & 15)), (pos.getZ() - (pos.getZ() & 15)));
                dispatcher.renderLiquid(pos, level, wrapperBuffer, state, fluidState);
            }
            wrapperBuffer.clerOffset();
            wrapperBuffer.clearColor();
        }
    }

    public static VertexBuffer[] getBUFFERS() {
        Object $value = MultiblockInWorldPreviewRenderer.BUFFERS.get();
        if ($value == null) {
            synchronized (MultiblockInWorldPreviewRenderer.BUFFERS) {
                $value = MultiblockInWorldPreviewRenderer.BUFFERS.get();
                if ($value == null) {
                    final VertexBuffer[] actualValue = initBuffers();
                    $value = actualValue == null ? MultiblockInWorldPreviewRenderer.BUFFERS : actualValue;
                    MultiblockInWorldPreviewRenderer.BUFFERS.set($value);
                }
            }
        }
        return (VertexBuffer[]) ($value == MultiblockInWorldPreviewRenderer.BUFFERS ? null : $value);
    }
}
