package com.gregtechceu.gtceu.uiwidgets.structure;

import com.gregtechceu.gtceu.GTCEu;

import com.lowdragmc.lowdraglib.client.scene.WorldSceneRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.longs.*;
import org.jetbrains.annotations.Nullable;
import org.joml.FrustumIntersection;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@OnlyIn(Dist.CLIENT)
final class StructureRenderer {

    private static final List<RenderType> LAYERS = RenderType.chunkBufferLayers();
    private static final int THREADS = Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() / 4));
    private static final long UPLOAD_NANOS = 3_000_000L;
    private static final int UPLOAD_BYTES = 8 << 20;
    private static final int BUILDER_SIZE = 1 << 14;
    private static final AtomicInteger THREAD_ID = new AtomicInteger();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(THREADS, task -> {
        var thread = new Thread(task, "GTCEu Structure Preview " + THREAD_ID.incrementAndGet());
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private static final ThreadLocal<BufferBuilder[]> BUILDERS = ThreadLocal.withInitial(() -> {
        var builders = new BufferBuilder[LAYERS.size()];
        for (int i = 0; i < builders.length; i++) builders[i] = new BufferBuilder(BUILDER_SIZE);
        return builders;
    });
    @Nullable
    private static BufferBuilder uploader;
    private static boolean errorLogged;

    private final Long2ObjectOpenHashMap<Section> sections = new Long2ObjectOpenHashMap<>();
    private final ConcurrentLinkedQueue<Mesh> done = new ConcurrentLinkedQueue<>();
    private final List<BlockEntity> rendered = new ArrayList<>();
    private final List<Section> visible = new ArrayList<>();
    @Nullable
    private PreviewLevel.View view;
    @Nullable
    private Long2ObjectOpenHashMap<LongArrayList> index;
    private volatile int generation;
    private boolean stale;
    private int outstanding;
    private int total;
    private final Vector3f focus = new Vector3f();
    private final float blockScale;
    private double originX, originY, originZ;
    private float tintR = 1, tintG = 1, tintB = 1, tintA = 1;

    void tint(float r, float g, float b, float a) {
        tintR = r;
        tintG = g;
        tintB = b;
        tintA = a;
    }

    private boolean ghost() {
        return tintA < 1;
    }

    StructureRenderer() {
        this(1);
    }

    StructureRenderer(float blockScale) {
        this.blockScale = blockScale;
    }

    void show(PreviewLevel.View view, boolean blocksChanged, Vector3f focus) {
        if (blocksChanged || index == null) index = buildIndex(view);
        this.view = view;
        this.focus.set(focus);
        rebuild();
    }

    private static Long2ObjectOpenHashMap<LongArrayList> buildIndex(PreviewLevel.View view) {
        var index = new Long2ObjectOpenHashMap<LongArrayList>();
        for (var it = view.states.keySet().iterator(); it.hasNext();) {
            long pos = it.nextLong();
            long key = SectionPos.asLong(SectionPos.blockToSectionCoord(BlockPos.getX(pos)), SectionPos.blockToSectionCoord(BlockPos.getY(pos)),
                    SectionPos.blockToSectionCoord(BlockPos.getZ(pos)));
            var list = index.get(key);
            if (list == null) index.put(key, list = new LongArrayList());
            list.add(pos);
        }
        for (var list : index.values()) list.trim();
        return index;
    }

    private void rebuild() {
        var view = this.view;
        var index = this.index;
        if (view == null || index == null) return;
        int gen = ++generation;
        done.clear();
        stale = false;
        rendered.clear();
        var dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
        for (var it = view.entities.long2ObjectEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            if (!view.visible(entry.getLongKey())) continue;
            var entity = entry.getValue();
            if (dispatcher.getRenderer(entity) != null) rendered.add(entity);
        }
        int sectionY = view.onlyY == PreviewLevel.ALL_LAYERS ? 0 : SectionPos.blockToSectionCoord(view.onlyY);
        var keys = new LongOpenHashSet();
        for (var it = index.keySet().iterator(); it.hasNext();) {
            long key = it.nextLong();
            if (view.onlyY == PreviewLevel.ALL_LAYERS || SectionPos.y(key) == sectionY) keys.add(key);
        }
        for (var it = sections.long2ObjectEntrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            if (!keys.contains(entry.getLongKey())) {
                entry.getValue().close();
                it.remove();
            }
        }
        float fx = focus.x(), fy = focus.y(), fz = focus.z();
        var sorted = keys.toLongArray();
        LongArrays.quickSort(sorted, (a, b) -> Float.compare(distance(a, fx, fy, fz), distance(b, fx, fy, fz)));
        outstanding = total = sorted.length;
        for (long key : sorted) {
            var positions = index.get(key);
            POOL.execute(() -> compile(gen, key, positions, view));
        }
    }

    private static float distance(long key, float x, float y, float z) {
        float dx = (SectionPos.x(key) << 4) + 8 - x, dy = (SectionPos.y(key) << 4) + 8 - y, dz = (SectionPos.z(key) << 4) + 8 - z;
        return dx * dx + dy * dy + dz * dz;
    }

    void releaseGpu() {
        generation++;
        done.clear();
        for (var section : sections.values()) section.close();
        sections.clear();
        visible.clear();
        stale = view != null;
    }

    void cancel() {
        generation++;
        done.clear();
        stale = false;
        outstanding = total = 0;
    }

    void dispose() {
        releaseGpu();
        stale = false;
        view = null;
        index = null;
        rendered.clear();
        outstanding = total = 0;
    }

    boolean hasData() {
        return view != null;
    }

    boolean isReady() {
        return view != null && !stale && outstanding == 0;
    }

    float progress() {
        return total == 0 ? 1 : 1 - outstanding / (float) total;
    }

    private void compile(int gen, long key, LongArrayList positions, PreviewLevel.View view) {
        if (gen != generation) return;
        byte[][] layers = null;
        try {
            layers = tessellate(gen, key, positions, view);
        } catch (Throwable t) {
            logError(t);
        }
        if (gen != generation) return;
        done.add(new Mesh(gen, key, layers == null ? new byte[LAYERS.size()][] : layers));
    }

    @Nullable
    private byte[][] tessellate(int gen, long key, LongArrayList positions, PreviewLevel.View view) {
        var builders = BUILDERS.get();
        var started = new boolean[builders.length];
        int ox = SectionPos.x(key) << 4, oy = SectionPos.y(key) << 4, oz = SectionPos.z(key) << 4;
        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        var random = RandomSource.create();
        var pose = new PoseStack();
        boolean aborted = true;
        var layers = new byte[builders.length][];
        ModelBlockRenderer.enableCaching();
        try {
            for (int i = 0; i < positions.size(); i++) {
                if ((i & 127) == 0 && gen != generation) return null;
                long packed = positions.getLong(i);
                var state = view.get(packed);
                if (state.isAir()) continue;
                var pos = BlockPos.of(packed);
                try {
                    if (state.getRenderShape() == RenderShape.MODEL) {
                        var model = dispatcher.getBlockModel(state);
                        var entity = view.getBlockEntity(pos);
                        var data = model.getModelData(view, pos, state, entity != null ? entity.getModelData() : ModelData.EMPTY);
                        long seed = state.getSeed(pos);
                        random.setSeed(seed);
                        for (var type : model.getRenderTypes(state, random, data)) {
                            int layer = LAYERS.indexOf(type);
                            if (layer < 0) continue;
                            var builder = begin(builders, started, layer);
                            pose.pushPose();
                            pose.translate(pos.getX() - ox, pos.getY() - oy, pos.getZ() - oz);
                            if (blockScale != 1) {
                                pose.translate(0.5f, 0.5f, 0.5f);
                                pose.scale(blockScale, blockScale, blockScale);
                                pose.translate(-0.5f, -0.5f, -0.5f);
                            }
                            dispatcher.getModelRenderer().tesselateBlock(view, model, state, pos, pose, builder, blockScale == 1, random, seed,
                                    OverlayTexture.NO_OVERLAY, data, type);
                            pose.popPose();
                        }
                    }
                    var fluid = state.getFluidState();
                    if (!fluid.isEmpty()) {
                        int layer = LAYERS.indexOf(ItemBlockRenderTypes.getRenderLayer(fluid));
                        if (layer >= 0) dispatcher.renderLiquid(pos, view, begin(builders, started, layer), state, fluid);
                    }
                } catch (Throwable t) {
                    logError(t);
                }
            }
            aborted = false;
        } finally {
            ModelBlockRenderer.clearCache();
            for (int i = 0; i < builders.length; i++) {
                if (started[i]) layers[i] = finish(builders[i], aborted);
            }
        }
        return layers;
    }

    @Nullable
    private static byte[] finish(BufferBuilder builder, boolean discard) {
        try {
            var buffer = builder.endOrDiscardIfEmpty();
            if (buffer == null) return null;
            try {
                if (discard) return null;
                var vertices = buffer.vertexBuffer();
                var bytes = new byte[vertices.remaining()];
                vertices.get(vertices.position(), bytes);
                return bytes;
            } finally {
                buffer.release();
            }
        } catch (Throwable t) {
            builder.discard();
            logError(t);
            return null;
        }
    }

    private static BufferBuilder begin(BufferBuilder[] builders, boolean[] started, int layer) {
        var builder = builders[layer];
        if (!started[layer]) {
            started[layer] = true;
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        }
        return builder;
    }

    private static void logError(Throwable t) {
        if (errorLogged) return;
        errorLogged = true;
        GTCEu.LOGGER.error("structure preview failed to tessellate a block", t);
    }

    void upload() {
        var view = this.view;
        if (view == null) return;
        if (stale) rebuild();
        long deadline = System.nanoTime() + UPLOAD_NANOS;
        int bytes = 0;
        boolean bound = false;
        Mesh mesh;
        while (bytes < UPLOAD_BYTES && System.nanoTime() < deadline && (mesh = done.poll()) != null) {
            if (mesh.generation != generation) continue;
            outstanding--;
            var section = sections.get(mesh.key);
            if (section == null) sections.put(mesh.key, section = new Section(mesh.key));
            for (int i = 0; i < mesh.layers.length; i++) {
                var data = mesh.layers[i];
                if (data == null) {
                    if (section.buffers[i] != null) {
                        section.buffers[i].close();
                        section.buffers[i] = null;
                    }
                    continue;
                }
                var buffer = section.buffers[i];
                if (buffer == null) section.buffers[i] = buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                var builder = uploader();
                builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
                builder.putBulkData(ByteBuffer.wrap(data));
                buffer.bind();
                buffer.upload(builder.end());
                bound = true;
                bytes += data.length;
            }
            if (section.isEmpty()) sections.remove(mesh.key);
        }
        if (bound) VertexBuffer.unbind();
    }

    private static BufferBuilder uploader() {
        if (uploader == null) uploader = new BufferBuilder(BUILDER_SIZE);
        return uploader;
    }

    void render(FrustumIntersection frustum, Vector3f eye, float partialTicks) {
        render(frustum, eye, partialTicks, 0, 0, 0);
    }

    void render(FrustumIntersection frustum, Vector3f eye, float partialTicks, double originX, double originY, double originZ) {
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        visible.clear();
        for (var section : sections.values()) {
            float x = (float) (section.x - originX), y = (float) (section.y - originY), z = (float) (section.z - originZ);
            if (frustum.testAab(x, y, z, x + 16, y + 16, z + 16)) visible.add(section);
        }
        float ex = eye.x(), ey = eye.y(), ez = eye.z();
        visible.sort((a, b) -> Float.compare(distance(a.key, ex, ey, ez), distance(b.key, ex, ey, ez)));
        for (int i = 0; i < LAYERS.size(); i++) {
            var layer = LAYERS.get(i);
            if (layer == RenderType.translucent() && !ghost()) renderBlockEntities(frustum, partialTicks);
            boolean any = false;
            for (var section : visible) {
                if (section.buffers[i] != null) {
                    any = true;
                    break;
                }
            }
            if (!any) continue;
            drawLayer(layer, i, layer == RenderType.translucent());
        }
    }

    private void drawLayer(RenderType layer, int index, boolean backToFront) {
        layer.setupRenderState();
        var shader = RenderSystem.getShader();
        if (shader == null) {
            layer.clearRenderState();
            return;
        }
        for (int j = 0; j < 12; ++j) shader.setSampler("Sampler" + j, RenderSystem.getShaderTexture(j));
        if (shader.MODEL_VIEW_MATRIX != null) shader.MODEL_VIEW_MATRIX.set(RenderSystem.getModelViewMatrix());
        if (shader.PROJECTION_MATRIX != null) shader.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        if (shader.COLOR_MODULATOR != null) shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        if (shader.FOG_START != null) shader.FOG_START.set(RenderSystem.getShaderFogStart());
        if (shader.FOG_END != null) shader.FOG_END.set(RenderSystem.getShaderFogEnd());
        if (shader.FOG_COLOR != null) shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        if (shader.FOG_SHAPE != null) shader.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        if (shader.TEXTURE_MATRIX != null) shader.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
        if (shader.GAME_TIME != null) shader.GAME_TIME.set(RenderSystem.getShaderGameTime());
        RenderSystem.setupShaderLights(shader);
        shader.apply();
        WorldSceneRenderer.setDefaultRenderLayerState(layer);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        if (shader.COLOR_MODULATOR != null) {
            shader.COLOR_MODULATOR.set(tintR, tintG, tintB, tintA);
            shader.COLOR_MODULATOR.upload();
        }
        if (ghost()) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);
        }
        var offset = shader.CHUNK_OFFSET;
        int size = visible.size();
        for (int k = 0; k < size; k++) {
            var section = visible.get(backToFront ? size - 1 - k : k);
            var buffer = section.buffers[index];
            if (buffer == null) continue;
            if (offset != null) {
                offset.set((float) (section.x - originX), (float) (section.y - originY), (float) (section.z - originZ));
                offset.upload();
            }
            buffer.bind();
            buffer.draw();
        }
        if (offset != null) offset.set(0f, 0f, 0f);
        if (ghost()) RenderSystem.depthMask(true);
        shader.clear();
        VertexBuffer.unbind();
        layer.clearRenderState();
    }

    private void renderBlockEntities(FrustumIntersection frustum, float partialTicks) {
        if (rendered.isEmpty()) return;
        var minecraft = Minecraft.getInstance();
        var dispatcher = minecraft.getBlockEntityRenderDispatcher();
        var buffers = minecraft.renderBuffers().bufferSource();
        var pose = new PoseStack();
        WorldSceneRenderer.setDefaultRenderLayerState(null);
        for (var it = rendered.iterator(); it.hasNext();) {
            var entity = it.next();
            var pos = entity.getBlockPos();
            float x = (float) (pos.getX() - originX), y = (float) (pos.getY() - originY), z = (float) (pos.getZ() - originZ);
            if (!frustum.testAab(x, y, z, x + 1, y + 1, z + 1)) continue;
            var renderer = dispatcher.getRenderer(entity);
            if (renderer == null || !entity.hasLevel() || !entity.getType().isValid(entity.getBlockState())) continue;
            pose.pushPose();
            pose.translate(x, y, z);
            if (blockScale != 1) {
                pose.translate(0.5f, 0.5f, 0.5f);
                pose.scale(blockScale, blockScale, blockScale);
                pose.translate(-0.5f, -0.5f, -0.5f);
            }
            try {
                renderer.render(entity, partialTicks, pose, buffers, 0xF000F0, OverlayTexture.NO_OVERLAY);
            } catch (Throwable t) {
                logError(t);
                it.remove();
            }
            pose.popPose();
        }
        buffers.endBatch();
    }

    private record Mesh(int generation, long key, byte[][] layers) {}

    private static final class Section {

        final long key;
        final int x, y, z;
        final VertexBuffer[] buffers = new VertexBuffer[LAYERS.size()];

        Section(long key) {
            this.key = key;
            this.x = SectionPos.x(key) << 4;
            this.y = SectionPos.y(key) << 4;
            this.z = SectionPos.z(key) << 4;
        }

        boolean isEmpty() {
            for (var buffer : buffers) {
                if (buffer != null) return false;
            }
            return true;
        }

        void close() {
            for (int i = 0; i < buffers.length; i++) {
                if (buffers[i] != null) {
                    buffers[i].close();
                    buffers[i] = null;
                }
            }
        }
    }
}
