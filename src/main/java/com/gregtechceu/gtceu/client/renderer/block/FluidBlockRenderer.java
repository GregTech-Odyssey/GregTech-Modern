package com.gregtechceu.gtceu.client.renderer.block;

import com.gregtechceu.gtceu.client.util.RenderUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static com.gregtechceu.gtceu.client.util.RenderUtil.*;
import static net.minecraft.util.FastColor.ARGB32.*;

public class FluidBlockRenderer {

    private final Properties properties;

    protected FluidBlockRenderer(Properties properties) {
        this.properties = properties;
    }

    public Vector3f[] transformVertices(Vector3f[] vertices, Direction face) {
        var newVertices = new Vector3f[4];
        float offsetX = properties.offsetX;
        float offsetY = properties.offsetY;
        float offsetZ = properties.offsetZ;
        switch (face) {
            case DOWN, UP -> offsetY += properties.offsetFace;
            case NORTH, SOUTH -> offsetZ += properties.offsetFace;
            case WEST, EAST -> offsetX += properties.offsetFace;
        }
        for (int i = 0; i < 4; i++) newVertices[i] = RenderUtil.transformVertex(vertices[i], face, offsetX, offsetY, offsetZ);
        return newVertices;
    }

    public void drawBlocks(Set<BlockPos> offsets, Matrix4f pose, VertexConsumer consumer, Fluid fluid, RenderUtil.FluidTextureType texture, int combinedOverlay, int combinedLight) {
        var fluidClientInfo = IClientFluidTypeExtensions.of(fluid);
        var sprite = texture.map(fluidClientInfo);
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float u1 = sprite.getU1();
        float v1 = sprite.getV1();
        int color = fluidClientInfo.getTintColor();
        int r = red(color);
        int g = green(color);
        int b = blue(color);
        int a = alpha(color);
        for (var pos : offsets) {
            pose.translate(pos.getX(), pos.getY(), pos.getZ());
            for (var direction : Direction.values()) {
                if (offsets.contains(pos.relative(direction))) continue;
                if (direction != Direction.UP && direction != Direction.DOWN) direction = direction.getOpposite();
                drawFace(pose, consumer, transformVertices(getVertices(direction), direction), getNormal(direction), u0, u1, v0, v1, r, g, b, a, combinedOverlay, combinedLight);
            }
            pose.translate(-pos.getX(), -pos.getY(), -pos.getZ());
        }
    }

    public void drawPlanes(Direction[] faces, Map<Direction, Collection<BlockPos>> directionalOffsets, Matrix4f pose, VertexConsumer consumer, Fluid fluid, RenderUtil.FluidTextureType texture, int combinedOverlay, int combinedLight) {
        for (var face : faces) {
            if (!directionalOffsets.containsKey(face)) continue;
            drawPlane(face, directionalOffsets.get(face), pose, consumer, fluid, texture, combinedOverlay, combinedLight);
        }
    }

    public void drawPlane(Direction face, Collection<BlockPos> offsets, Matrix4f pose, VertexConsumer consumer, Fluid fluid, RenderUtil.FluidTextureType texture, int combinedOverlay, BlockPos origin) {
        var fluidClientInfo = IClientFluidTypeExtensions.of(fluid);
        var sprite = texture.map(fluidClientInfo);
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float u1 = sprite.getU1();
        float v1 = sprite.getV1();
        int color = fluidClientInfo.getTintColor();
        int r = red(color);
        int g = green(color);
        int b = blue(color);
        int a = alpha(color);
        var normal = getNormal(face);
        var vertices = transformVertices(getVertices(face), face);
        BlockPos prevOffset = null;
        for (var offset : offsets) {
            BlockPos currOffset = prevOffset == null ? offset : offset.subtract(prevOffset);
            pose.translate(currOffset.getX(), currOffset.getY(), currOffset.getZ());
            drawFace(pose, consumer, vertices, normal, u0, u1, v0, v1, r, g, b, a, combinedOverlay, RenderUtil.getFluidLight(fluid, origin.offset(currOffset)));
            prevOffset = offset;
        }
    }

    public void drawPlane(Direction face, Collection<BlockPos> offsets, Matrix4f pose, VertexConsumer consumer, Fluid fluid, RenderUtil.FluidTextureType texture, int combinedOverlay, int combinedLight) {
        var fluidClientInfo = IClientFluidTypeExtensions.of(fluid);
        var sprite = texture.map(fluidClientInfo);
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float u1 = sprite.getU1();
        float v1 = sprite.getV1();
        int color = fluidClientInfo.getTintColor();
        int r = red(color);
        int g = green(color);
        int b = blue(color);
        int a = alpha(color);
        var normal = getNormal(face);
        var vertices = transformVertices(getVertices(face), face);
        BlockPos prevOffset = null;
        for (var offset : offsets) {
            BlockPos currOffset = prevOffset == null ? offset : offset.subtract(prevOffset);
            pose.translate(currOffset.getX(), currOffset.getY(), currOffset.getZ());
            drawFace(pose, consumer, vertices, normal, u0, u1, v0, v1, r, g, b, a, combinedOverlay, combinedLight);
            prevOffset = offset;
        }
    }

    public void drawFace(Direction face, Matrix4f pose, VertexConsumer consumer, Fluid fluid, RenderUtil.FluidTextureType texture, int combinedOverlay, int combinedLight) {
        var fluidClientInfo = IClientFluidTypeExtensions.of(fluid);
        var sprite = texture.map(fluidClientInfo);
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float u1 = sprite.getU1();
        float v1 = sprite.getV1();
        int color = fluidClientInfo.getTintColor();
        int r = red(color);
        int g = green(color);
        int b = blue(color);
        int a = alpha(color);
        var normal = getNormal(face);
        var vertices = transformVertices(getVertices(face), face);
        drawFace(pose, consumer, vertices, normal, u0, u1, v0, v1, r, g, b, a, combinedOverlay, combinedLight);
    }

    public void drawFace(Matrix4f pose, VertexConsumer consumer, Vector3f[] vertices, Vector3f normal, float u0, float u1, float v0, float v1, int r, int g, int b, int a, int combinedOverlay, int combinedLight) {
        if (properties.isOverwriteLight()) combinedLight = properties.getLight();
        var vert = vertices[0];
        RenderUtil.vertex(pose, consumer, vert.x, vert.y, vert.z, r, g, b, a, u0, v1, combinedOverlay, combinedLight, normal.x, normal.y, normal.z);
        vert = vertices[1];
        RenderUtil.vertex(pose, consumer, vert.x, vert.y, vert.z, r, g, b, a, u0, v0, combinedOverlay, combinedLight, normal.x, normal.y, normal.z);
        vert = vertices[2];
        RenderUtil.vertex(pose, consumer, vert.x, vert.y, vert.z, r, g, b, a, u1, v0, combinedOverlay, combinedLight, normal.x, normal.y, normal.z);
        vert = vertices[3];
        RenderUtil.vertex(pose, consumer, vert.x, vert.y, vert.z, r, g, b, a, u1, v1, combinedOverlay, combinedLight, normal.x, normal.y, normal.z);
    }

    public static class Properties {

        private float offsetX = 0;
        private float offsetY = 0;
        private float offsetZ = 0;
        private float offsetFace = 0;
        private boolean overwriteLight = false;
        private int light = 0;
        private Direction[] drawFaces = Direction.values();

        public Properties() {}

        public float getOffsetX() {
            return this.offsetX;
        }

        public float getOffsetY() {
            return this.offsetY;
        }

        public float getOffsetZ() {
            return this.offsetZ;
        }

        public float getOffsetFace() {
            return this.offsetFace;
        }

        public boolean isOverwriteLight() {
            return this.overwriteLight;
        }

        public int getLight() {
            return this.light;
        }

        public Direction[] getDrawFaces() {
            return this.drawFaces;
        }

        public void setOffsetX(final float offsetX) {
            this.offsetX = offsetX;
        }

        public void setOffsetY(final float offsetY) {
            this.offsetY = offsetY;
        }

        public void setOffsetZ(final float offsetZ) {
            this.offsetZ = offsetZ;
        }

        public void setOffsetFace(final float offsetFace) {
            this.offsetFace = offsetFace;
        }

        public void setOverwriteLight(final boolean overwriteLight) {
            this.overwriteLight = overwriteLight;
        }

        public void setLight(final int light) {
            this.light = light;
        }

        public void setDrawFaces(final Direction[] drawFaces) {
            this.drawFaces = drawFaces;
        }

        @Override
        public boolean equals(final Object o) {
            if (o == this) return true;
            if (!(o instanceof FluidBlockRenderer.Properties)) return false;
            final FluidBlockRenderer.Properties other = (FluidBlockRenderer.Properties) o;
            if (!other.canEqual((Object) this)) return false;
            if (Float.compare(this.getOffsetX(), other.getOffsetX()) != 0) return false;
            if (Float.compare(this.getOffsetY(), other.getOffsetY()) != 0) return false;
            if (Float.compare(this.getOffsetZ(), other.getOffsetZ()) != 0) return false;
            if (Float.compare(this.getOffsetFace(), other.getOffsetFace()) != 0) return false;
            if (this.isOverwriteLight() != other.isOverwriteLight()) return false;
            if (this.getLight() != other.getLight()) return false;
            if (!java.util.Arrays.deepEquals(this.getDrawFaces(), other.getDrawFaces())) return false;
            return true;
        }

        protected boolean canEqual(final Object other) {
            return other instanceof FluidBlockRenderer.Properties;
        }

        @Override
        public int hashCode() {
            final int PRIME = 59;
            int result = 1;
            result = result * PRIME + Float.floatToIntBits(this.getOffsetX());
            result = result * PRIME + Float.floatToIntBits(this.getOffsetY());
            result = result * PRIME + Float.floatToIntBits(this.getOffsetZ());
            result = result * PRIME + Float.floatToIntBits(this.getOffsetFace());
            result = result * PRIME + (this.isOverwriteLight() ? 79 : 97);
            result = result * PRIME + this.getLight();
            result = result * PRIME + java.util.Arrays.deepHashCode(this.getDrawFaces());
            return result;
        }

        @Override
        public String toString() {
            return "FluidBlockRenderer.Properties(offsetX=" + this.getOffsetX() + ", offsetY=" + this.getOffsetY() + ", offsetZ=" + this.getOffsetZ() + ", offsetFace=" + this.getOffsetFace() + ", overwriteLight=" + this.isOverwriteLight() + ", light=" + this.getLight() + ", drawFaces=" + java.util.Arrays.deepToString(this.getDrawFaces()) + ")";
        }
    }

    public static class Builder {

        private final Properties properties;

        public Builder() {
            properties = new Properties();
        }

        public static Builder create() {
            return new Builder();
        }

        public Builder setFaceOffset(float offset) {
            properties.setOffsetFace(offset);
            return this;
        }

        public Builder setOffset(Vector3f offset) {
            return setOffset(offset.x, offset.y, offset.z);
        }

        public Builder setOffset(float offsetX, float offsetY, float offsetZ) {
            properties.setOffsetX(offsetX);
            properties.setOffsetY(offsetY);
            properties.setOffsetZ(offsetZ);
            return this;
        }

        public Builder setForcedLight(int light) {
            properties.setLight(light);
            properties.setOverwriteLight(true);
            return this;
        }

        public FluidBlockRenderer getRenderer() {
            return new FluidBlockRenderer(properties);
        }
    }

    public Properties getProperties() {
        return this.properties;
    }
}
