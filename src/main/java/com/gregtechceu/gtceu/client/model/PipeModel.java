package com.gregtechceu.gtceu.client.model;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.client.ResourceReloadEpoch;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gregtechceu.gtceu.utils.memoization.CleanableMemoizedSupplier;
import com.gregtechceu.gtceu.utils.memoization.GTMemoizer;

import com.lowdragmc.lowdraglib.client.bakedpipeline.FaceQuad;
import com.lowdragmc.lowdraglib.client.model.ModelFactory;
import com.lowdragmc.lowdraglib.client.renderer.IItemRendererProvider;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class PipeModel {

    public static final ResourceLocation PIPE_BLOCKED_OVERLAY = GTCEu.id("block/pipe/blocked/pipe_blocked");
    public static final ResourceLocation PIPE_BLOCKED_OVERLAY_UP = GTCEu.id("block/pipe/blocked/pipe_blocked_up");
    public static final ResourceLocation PIPE_BLOCKED_OVERLAY_DOWN = GTCEu.id("block/pipe/blocked/pipe_blocked_down");
    public static final ResourceLocation PIPE_BLOCKED_OVERLAY_LEFT = GTCEu.id("block/pipe/blocked/pipe_blocked_left");
    public static final ResourceLocation PIPE_BLOCKED_OVERLAY_RIGHT = GTCEu.id("block/pipe/blocked/pipe_blocked_right");
    public static final ResourceLocation PIPE_BLOCKED_OVERLAY_NU = GTCEu.id("block/pipe/blocked/pipe_blocked_nu");
    public static final ResourceLocation PIPE_BLOCKED_OVERLAY_ND = GTCEu.id("block/pipe/blocked/pipe_blocked_nd");
    public static final ResourceLocation PIPE_BLOCKED_OVERLAY_NL = GTCEu.id("block/pipe/blocked/pipe_blocked_nl");
    public static final ResourceLocation PIPE_BLOCKED_OVERLAY_NR = GTCEu.id("block/pipe/blocked/pipe_blocked_nr");
    public static final ResourceLocation PIPE_BLOCKED_OVERLAY_UD = GTCEu.id("block/pipe/blocked/pipe_blocked_ud");
    public static final ResourceLocation PIPE_BLOCKED_OVERLAY_UL = GTCEu.id("block/pipe/blocked/pipe_blocked_ul");
    public static final ResourceLocation PIPE_BLOCKED_OVERLAY_UR = GTCEu.id("block/pipe/blocked/pipe_blocked_ur");
    public static final ResourceLocation PIPE_BLOCKED_OVERLAY_DL = GTCEu.id("block/pipe/blocked/pipe_blocked_dl");
    public static final ResourceLocation PIPE_BLOCKED_OVERLAY_DR = GTCEu.id("block/pipe/blocked/pipe_blocked_dr");
    public static final ResourceLocation PIPE_BLOCKED_OVERLAY_LR = GTCEu.id("block/pipe/blocked/pipe_blocked_lr");
    private static final EnumMap<Direction, EnumMap<Border, Direction>> FACE_BORDER_MAP = new EnumMap<>(Direction.class);

    static {
        FACE_BORDER_MAP.put(Direction.DOWN, borderMap(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST));
        FACE_BORDER_MAP.put(Direction.UP, borderMap(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST));
        FACE_BORDER_MAP.put(Direction.NORTH, borderMap(Direction.DOWN, Direction.UP, Direction.WEST, Direction.EAST));
        FACE_BORDER_MAP.put(Direction.SOUTH, borderMap(Direction.DOWN, Direction.UP, Direction.WEST, Direction.EAST));
        FACE_BORDER_MAP.put(Direction.WEST, borderMap(Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH));
        FACE_BORDER_MAP.put(Direction.EAST, borderMap(Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH));
    }

    public static final int ITEM_CONNECTIONS = 12;
    public final float thickness;
    public final AABB coreCube;
    public final Map<Direction, AABB> sideCubes;
    private AtomicReferenceArray<VoxelShape> shapeCache;
    public CleanableMemoizedSupplier<ResourceLocation> sideTexture;
    public CleanableMemoizedSupplier<ResourceLocation> endTexture;
    @Nullable
    public CleanableMemoizedSupplier<@Nullable ResourceLocation> secondarySideTexture;
    @Nullable
    public CleanableMemoizedSupplier<@Nullable ResourceLocation> secondaryEndTexture;
    @Setter
    public ResourceLocation sideOverlayTexture;
    @Setter
    public ResourceLocation endOverlayTexture;
    @OnlyIn(Dist.CLIENT)
    private volatile Sprites sprites;

    /** 当前图集解析出的一组 sprite。整体替换而非逐字段置空——bakeQuads 会在多个区块构建线程上并发执行。 */
    @OnlyIn(Dist.CLIENT)
    private static final class Sprites {

        final int epoch;
        final TextureAtlasSprite side;
        final TextureAtlasSprite end;
        @Nullable
        final TextureAtlasSprite secondarySide;
        @Nullable
        final TextureAtlasSprite secondaryEnd;
        @Nullable
        final TextureAtlasSprite sideOverlay;
        @Nullable
        final TextureAtlasSprite endOverlay;
        final Int2ObjectMap<TextureAtlasSprite> restrictors;

        Sprites(int epoch, TextureAtlasSprite side, TextureAtlasSprite end, @Nullable TextureAtlasSprite secondarySide,
                @Nullable TextureAtlasSprite secondaryEnd, @Nullable TextureAtlasSprite sideOverlay,
                @Nullable TextureAtlasSprite endOverlay, Int2ObjectMap<TextureAtlasSprite> restrictors) {
            this.epoch = epoch;
            this.side = side;
            this.end = end;
            this.secondarySide = secondarySide;
            this.secondaryEnd = secondaryEnd;
            this.sideOverlay = sideOverlay;
            this.endOverlay = endOverlay;
            this.restrictors = restrictors;
        }
    }

    @OnlyIn(Dist.CLIENT)
    private Sprites sprites() {
        Sprites current = this.sprites;
        int epoch = ResourceReloadEpoch.get();
        if (current != null && current.epoch == epoch) return current;
        Sprites fresh = buildSprites(epoch);
        this.sprites = fresh;
        itemModelCache.clear();
        return fresh;
    }

    @OnlyIn(Dist.CLIENT)
    private Sprites buildSprites(int epoch) {
        TextureAtlasSprite secondarySide = null;
        if (secondarySideTexture != null && secondarySideTexture.get() != null) {
            secondarySide = ModelFactory.getBlockSprite(secondarySideTexture.get());
        }
        TextureAtlasSprite secondaryEnd = null;
        if (secondaryEndTexture != null && secondaryEndTexture.get() != null) {
            secondaryEnd = ModelFactory.getBlockSprite(secondaryEndTexture.get());
        }
        TextureAtlasSprite sideOverlay = sideOverlayTexture == null ? null : ModelFactory.getBlockSprite(sideOverlayTexture);
        TextureAtlasSprite endOverlay = endOverlayTexture == null ? null : ModelFactory.getBlockSprite(endOverlayTexture);
        return new Sprites(epoch, ModelFactory.getBlockSprite(sideTexture.get()), ModelFactory.getBlockSprite(endTexture.get()),
                secondarySide, secondaryEnd, sideOverlay, endOverlay, buildRestrictors());
    }

    public PipeModel(float thickness, Supplier<ResourceLocation> sideTexture, Supplier<ResourceLocation> endTexture, @Nullable Supplier<@Nullable ResourceLocation> secondarySideTexture, @Nullable Supplier<@Nullable ResourceLocation> secondaryEndTexture) {
        this.sideTexture = GTMemoizer.memoizeCleanable(sideTexture);
        this.endTexture = GTMemoizer.memoizeCleanable(endTexture);
        this.secondarySideTexture = secondarySideTexture != null ? GTMemoizer.memoizeCleanable(secondarySideTexture) : null;
        this.secondaryEndTexture = secondaryEndTexture != null ? GTMemoizer.memoizeCleanable(secondaryEndTexture) : null;
        this.thickness = thickness;
        double min = (1.0 - thickness) / 2;
        double max = min + thickness;
        this.coreCube = new AABB(min, min, min, max, max, max);
        this.sideCubes = new EnumMap<>(Direction.class);
        for (Direction side : GTUtil.DIRECTIONS) {
            var normal = side.getNormal();
            sideCubes.put(side, new AABB(normal.getX() == 0 ? min : normal.getX() > 0 ? max : 0, normal.getY() == 0 ? min : normal.getY() > 0 ? max : 0, normal.getZ() == 0 ? min : normal.getZ() > 0 ? max : 0, normal.getX() == 0 ? max : normal.getX() > 0 ? 1 : min, normal.getY() == 0 ? max : normal.getY() > 0 ? 1 : min, normal.getZ() == 0 ? max : normal.getZ() > 0 ? 1 : min));
        }
    }

    public VoxelShape getShapes(int connections) {
        var cache = this.shapeCache;
        if (cache == null) {
            cache = this.shapeCache = new AtomicReferenceArray<>(64);
        }
        int connectionMask = connections & 63;
        var shape = cache.get(connectionMask);
        if (shape != null) return shape;
        var shapes = new ArrayList<VoxelShape>(7);
        shapes.add(Shapes.create(coreCube));
        for (Direction side : GTUtil.DIRECTIONS) {
            if (PipeBlockEntity.isConnected(connections, side)) {
                shapes.add(Shapes.create(sideCubes.get(side)));
            }
        }
        shape = shapes.stream().reduce(Shapes.empty(), Shapes::or);
        if (cache.compareAndSet(connectionMask, null, shape)) return shape;
        return cache.get(connectionMask);
    }

    @OnlyIn(Dist.CLIENT)
    public List<BakedQuad> bakeQuads(@Nullable Direction side, int connections, int blockedConnections) {
        // 只读一次引用，之后全部走局部变量，避免并发换代时读到半更新的字段。
        Sprites cache = sprites();
        if (side != null) {
            if (thickness == 1) {
                // full block
                List<BakedQuad> quads = new ArrayList<>();
                quads.add(FaceQuad.builder(side, cache.side).cube(coreCube).cubeUV().tintIndex(0).bake());
                if (cache.secondarySide != null) {
                    quads.add(FaceQuad.builder(side, cache.secondarySide).cube(coreCube).cubeUV().tintIndex(0).bake());
                }
                return quads;
            }
            if (PipeBlockEntity.isConnected(connections, side)) {
                // side connected
                List<BakedQuad> quads = new ArrayList<>();
                quads.add(FaceQuad.builder(side, cache.end).cube(sideCubes.get(side).inflate(-0.001)).cubeUV().tintIndex(1).bake());
                if (cache.secondaryEnd != null) {
                    quads.add(FaceQuad.builder(side, cache.secondaryEnd).cube(sideCubes.get(side)).cubeUV().tintIndex(1).bake());
                }
                if (cache.endOverlay != null) {
                    quads.add(FaceQuad.builder(side, cache.endOverlay).cube(sideCubes.get(side)).cubeUV().tintIndex(0).bake());
                }
                if (cache.sideOverlay != null) {
                    for (Direction face : GTUtil.DIRECTIONS) {
                        if (face.getAxis() != side.getAxis()) {
                            quads.add(FaceQuad.builder(face, cache.sideOverlay).cube(sideCubes.get(side)).cubeUV().tintIndex(2).bake());
                        }
                    }
                }
                int borderMask = computeBorderMask(blockedConnections, connections, side);
                if (borderMask != 0) {
                    quads.add(FaceQuad.builder(side, cache.restrictors.get(borderMask)).cube(sideCubes.get(side)).cubeUV().bake());
                }
                return quads;
            }
            return Collections.emptyList();
        }
        List<BakedQuad> quads = new LinkedList<>();
        if (thickness < 1) {
            // non full block
            // render core cube
            for (Direction face : GTUtil.DIRECTIONS) {
                if (!PipeBlockEntity.isConnected(connections, face)) {
                    quads.add(FaceQuad.builder(face, cache.side).cube(coreCube).cubeUV().tintIndex(0).bake());
                    if (cache.secondarySide != null) {
                        quads.add(FaceQuad.builder(face, cache.secondarySide).cube(coreCube).cubeUV().tintIndex(0).bake());
                    }
                }
                // render each connected side
                for (Direction facing : GTUtil.DIRECTIONS) {
                    if (facing.getAxis() != face.getAxis()) {
                        if (PipeBlockEntity.isConnected(connections, facing)) {
                            quads.add(FaceQuad.builder(face, cache.side).cube(sideCubes.get(facing)).cubeUV().tintIndex(0).bake());
                            if (cache.secondarySide != null) {
                                quads.add(FaceQuad.builder(face, cache.secondarySide).cube(sideCubes.get(facing)).cubeUV().tintIndex(0).bake());
                            }
                            if (cache.sideOverlay != null) {
                                quads.add(FaceQuad.builder(face, cache.sideOverlay).cube(sideCubes.get(facing).inflate(0.001)).cubeUV().tintIndex(2).bake());
                            }
                            int borderMask = computeBorderMask(blockedConnections, connections, face);
                            if (borderMask != 0) {
                                quads.add(FaceQuad.builder(face, cache.restrictors.get(borderMask)).cube(coreCube).cubeUV().bake());
                                quads.add(FaceQuad.builder(face, cache.restrictors.get(borderMask)).cube(sideCubes.get(facing)).cubeUV().bake());
                            }
                        }
                    }
                }
            }
        }
        return quads;
    }

    @NotNull
    @OnlyIn(Dist.CLIENT)
    public TextureAtlasSprite getParticleTexture() {
        return sprites().side;
    }

    private final Map<Optional<Direction>, List<BakedQuad>> itemModelCache = new ConcurrentHashMap<>();

    @OnlyIn(Dist.CLIENT)
    public void renderItem(ItemStack stack, ItemDisplayContext transformType, boolean leftHand, PoseStack matrixStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay, BakedModel model) {
        IItemRendererProvider.disabled.set(true);
        // 先完成换代，避免在下面的 computeIfAbsent 回调里清空同一个 map。
        sprites();
        Minecraft.getInstance().getItemRenderer().render(stack, transformType, leftHand, matrixStack, buffer, combinedLight, combinedOverlay, (ItemBakedModel) (state, direction, random) -> itemModelCache.computeIfAbsent(Optional.ofNullable(direction), direction1 -> bakeQuads(direction1.orElse(null), ITEM_CONNECTIONS, 0)));
        IItemRendererProvider.disabled.set(false);
    }

    @OnlyIn(Dist.CLIENT)
    public void registerTextureAtlas(Consumer<ResourceLocation> register) {
        itemModelCache.clear();
        sideTexture.invalidate();
        register.accept(sideTexture.get());
        endTexture.invalidate();
        register.accept(endTexture.get());
        if (secondarySideTexture != null) {
            secondarySideTexture.invalidate();
            if (secondarySideTexture.get() != null) {
                register.accept(secondarySideTexture.get());
            }
        }
        if (secondaryEndTexture != null) {
            secondaryEndTexture.invalidate();
            if (secondaryEndTexture.get() != null) {
                register.accept(secondaryEndTexture.get());
            }
        }
        if (sideOverlayTexture != null) register.accept(sideOverlayTexture);
        if (endOverlayTexture != null) register.accept(endOverlayTexture);
        this.sprites = null;
    }

    private static EnumMap<Border, Direction> borderMap(Direction topSide, Direction bottomSide, Direction leftSide, Direction rightSide) {
        EnumMap<Border, Direction> sideMap = new EnumMap<>(Border.class);
        sideMap.put(Border.TOP, topSide);
        sideMap.put(Border.BOTTOM, bottomSide);
        sideMap.put(Border.LEFT, leftSide);
        sideMap.put(Border.RIGHT, rightSide);
        return sideMap;
    }

    @OnlyIn(Dist.CLIENT)
    private static Int2ObjectMap<TextureAtlasSprite> buildRestrictors() {
        Function<ResourceLocation, TextureAtlasSprite> atlas = ModelFactory::getBlockSprite;
        Int2ObjectMap<TextureAtlasSprite> map = new Int2ObjectOpenHashMap<>();
        addRestrictor(map, atlas.apply(PIPE_BLOCKED_OVERLAY_UP), Border.TOP);
        addRestrictor(map, atlas.apply(PIPE_BLOCKED_OVERLAY_DOWN), Border.BOTTOM);
        addRestrictor(map, atlas.apply(PIPE_BLOCKED_OVERLAY_UD), Border.TOP, Border.BOTTOM);
        addRestrictor(map, atlas.apply(PIPE_BLOCKED_OVERLAY_LEFT), Border.LEFT);
        addRestrictor(map, atlas.apply(PIPE_BLOCKED_OVERLAY_UL), Border.TOP, Border.LEFT);
        addRestrictor(map, atlas.apply(PIPE_BLOCKED_OVERLAY_DL), Border.BOTTOM, Border.LEFT);
        addRestrictor(map, atlas.apply(PIPE_BLOCKED_OVERLAY_NR), Border.TOP, Border.BOTTOM, Border.LEFT);
        addRestrictor(map, atlas.apply(PIPE_BLOCKED_OVERLAY_RIGHT), Border.RIGHT);
        addRestrictor(map, atlas.apply(PIPE_BLOCKED_OVERLAY_UR), Border.TOP, Border.RIGHT);
        addRestrictor(map, atlas.apply(PIPE_BLOCKED_OVERLAY_DR), Border.BOTTOM, Border.RIGHT);
        addRestrictor(map, atlas.apply(PIPE_BLOCKED_OVERLAY_NL), Border.TOP, Border.BOTTOM, Border.RIGHT);
        addRestrictor(map, atlas.apply(PIPE_BLOCKED_OVERLAY_LR), Border.LEFT, Border.RIGHT);
        addRestrictor(map, atlas.apply(PIPE_BLOCKED_OVERLAY_ND), Border.TOP, Border.LEFT, Border.RIGHT);
        addRestrictor(map, atlas.apply(PIPE_BLOCKED_OVERLAY_NU), Border.BOTTOM, Border.LEFT, Border.RIGHT);
        addRestrictor(map, atlas.apply(PIPE_BLOCKED_OVERLAY), Border.TOP, Border.BOTTOM, Border.LEFT, Border.RIGHT);
        return map;
    }

    private static void addRestrictor(Int2ObjectMap<TextureAtlasSprite> map, TextureAtlasSprite sprite, Border... borders) {
        int mask = 0;
        for (Border border : borders) {
            mask |= border.mask;
        }
        map.put(mask, sprite);
    }

    protected static Direction getSideAtBorder(Direction side, Border border) {
        return FACE_BORDER_MAP.get(side).get(border);
    }

    protected static int computeBorderMask(int blockedConnections, int connections, Direction side) {
        int borderMask = 0;
        if (blockedConnections != 0) {
            for (Border border : Border.VALUES) {
                Direction borderSide = getSideAtBorder(side, border);
                if (PipeBlockEntity.isFaceBlocked(blockedConnections, borderSide) && PipeBlockEntity.isConnected(connections, borderSide)) {
                    // only render when the side is blocked *and* connected
                    borderMask |= border.mask;
                }
            }
        }
        return borderMask;
    }

    public enum Border {

        TOP,
        BOTTOM,
        LEFT,
        RIGHT;

        public static final Border[] VALUES = values();
        public final int mask;

        Border() {
            mask = 1 << this.ordinal();
        }
    }
}
