package com.gregtechceu.gtceu.api.tooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 带角色标记的提示行内容：客户端提示框按像素对齐绘制，其余本地渲染场合按纯文本回退。
 * 原版 Component.Serializer 不认此内容类型，进网络包或存盘会抛异常，只能留在客户端 tooltip 里。
 */
public final class TipContents implements ComponentContents {

    private final TipRole role;
    private final Component body;
    private final Component styledBody;
    private final Component fallback;
    @Nullable
    private final Meter meter;

    public record Meter(float[] ratios, int[] lefts, int[] rights) {}

    private TipContents(TipRole role, Component body) {
        this(role, body, null);
    }

    private TipContents(TipRole role, Component body, @Nullable Meter meter) {
        this.role = role;
        this.body = body;
        this.meter = meter;
        this.styledBody = role.bodyColor() < 0 ? body : Component.empty().withStyle(s -> s.withColor(role.bodyColor())).append(body);
        MutableComponent text = Component.empty();
        if (role.depth() > 0) {
            text.append("  ".repeat(role.depth()));
            if (role.marker() != TipMarker.NONE) {
                text.append(Component.literal(role.marker().fallback).withStyle(s -> s.withColor(role.markerColor())));
            }
        }
        this.fallback = text.append(styledBody);
    }

    public static MutableComponent line(TipRole role, Component body) {
        return MutableComponent.create(new TipContents(role, body));
    }

    public static MutableComponent meter(Component body, float ratio, int left, int right) {
        return MutableComponent.create(new TipContents(TipRole.METER, body,
                new Meter(new float[] { ratio }, new int[] { left }, new int[] { right })));
    }

    public static MutableComponent segments(Component body, float[] ratios, int[] colors) {
        return MutableComponent.create(new TipContents(TipRole.METER, body, new Meter(ratios, colors, colors)));
    }

    @Nullable
    public Meter meter() {
        return meter;
    }

    public TipRole role() {
        return role;
    }

    public Component body() {
        return body;
    }

    public Component styledBody() {
        return styledBody;
    }

    @Override
    public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> consumer, Style style) {
        return fallback.visit(consumer, style);
    }

    @Override
    public <T> Optional<T> visit(FormattedText.ContentConsumer<T> consumer) {
        return fallback.visit(consumer);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof TipContents other && role.equals(other.role) && body.equals(other.body));
    }

    @Override
    public int hashCode() {
        return 31 * role.hashCode() + body.hashCode();
    }

    @Override
    public String toString() {
        return "tip{" + role.id() + ", " + body + "}";
    }
}
