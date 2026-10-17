package com.gregtechceu.gtceu.api.tooltip;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import com.gto.datasynclib.datastream.codec.StreamCodec;
import com.gto.datasynclib.util.ByteBufCodecExtends;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 带角色标记的提示行内容：客户端提示框按像素对齐绘制，其余本地渲染场合按纯文本回退。
 * <b>仅用于物品/方块 tooltip（悬停提示框），不是通用的文字渲染设施</b>：机器界面、说明页、显示行、聊天一律用普通
 * {@code Component} 与 uipro 文本组件，不要拿这里的行或规格去拼。
 * 原版 Component.Serializer 仍然不认此内容类型，所以原版 JSON（存盘、原版网络包）喂不了它；网络传输
 * 改走 DataSyncLib 的二进制组件编码：{@link #STREAM_CODEC} 已在 {@code CommonProxy.initDataSync()}
 * 注册，只为物品提示随物品同步时不出错，不代表可以拿它做界面文字。
 */
public final class TipContents implements ComponentContents {

    /**
     * 线格式叫得出名字的角色：{@link TipRole} 声明的全部常量。id 是线格式里的名字，这张表只用来把名字
     * 读回常量本身（{@link #equals} 和 {@code TipTooltipLayout} 里的 {@code role() == TipRole.X} 都要求
     * 拿到的是那个常量），不参与顺序编码。
     */
    private static final TipRole[] ROLES = {
            TipRole.HEADING, TipRole.SUMMARY, TipRole.STAT, TipRole.LORE, TipRole.PLAIN, TipRole.METER,
            TipRole.FOOTER, TipRole.FEATURE, TipRole.REQUIREMENT, TipRole.ATTENTION, TipRole.BONUS,
            TipRole.PENALTY, TipRole.WARNING, TipRole.SUPPORTED, TipRole.UNSUPPORTED, TipRole.DETAIL,
            TipRole.FORMULA, TipRole.CONTROL_LEFT, TipRole.CONTROL_RIGHT, TipRole.CONTROL_MIDDLE,
            TipRole.CONTROL
    };

    /**
     * 本内容的网络编码，注册在 {@code CommonProxy.initDataSync()}，由 DataSyncLib 的二进制组件编码
     * （{@link ByteBufCodecExtends#COMPONENT_CODEC}）在遇到内容类型是 {@code TipContents} 的组件时调用。
     *
     * <pre>
     * tip   := role body meter
     * role  := UTF 角色 id, VarInt depth
     * body  := 一个组件（二进制组件编码）
     * meter := byte 有无；有则接 VarInt 段数，再逐段 float ratio, int left, int right
     * </pre>
     *
     * <p>角色写 {@link TipRole#id()} 这个名字而不是序号：{@code TipRole} 是 record，没有 ordinal，只能自造
     * 一个按声明顺序的下标，而插入或重排常量会让旧下标全部错位；名字稳定、自解释，抓包也读得懂。一个 id
     * 唯一确定标记、颜色和缩进里的角色部分，只有 {@code depth} 能被改（{@link TipRole#withDepth(int)}），
     * 所以它跟着 id 走一个 VarInt，读回时用 {@code withDepth} 还原：depth 与声明一致时它返回常量本身，
     * 于是 {@link #equals} 成立，{@code TipTooltipLayout} 里 {@code role() == TipRole.X} 那样的引用比较也
     * 照样成立。</p>
     *
     * <p>写得出来的角色必须是 {@link #ROLES} 里的常量或它的 depth 变体；别的角色这套线格式表达不了，
     * 编码时抛 {@link IllegalArgumentException}，而不是悄悄换个角色发出去。读回未知 id 同样抛异常
     * （对端版本比本端新）。</p>
     *
     * <p>{@link Meter} 的三个数组是平行的，{@code ratios[i]}、{@code lefts[i]}、{@code rights[i]} 同属一段，
     * 逐段写；{@code segments(...)} 造的仪表左右同色，这份重复照写，线格式不为此加标志位。</p>
     */
    public static final StreamCodec<FriendlyByteBuf, TipContents> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public void encode(FriendlyByteBuf buf, TipContents obj) {
            writeRole(buf, obj.role);
            ByteBufCodecExtends.COMPONENT_CODEC.encode(buf, obj.body);
            writeMeter(buf, obj.meter);
        }

        @Override
        public TipContents decode(FriendlyByteBuf buf) {
            return new TipContents(readRole(buf), ByteBufCodecExtends.COMPONENT_CODEC.decode(buf), readMeter(buf));
        }
    };

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

    // ===== 线格式 =====

    /**
     * 写角色：id 这个名字，加一个只有 {@link TipRole#withDepth(int)} 能改的 depth。声明里没有的角色
     * （或改了标记、颜色的同名角色）这套线格式表达不了，与其读回一个不同的角色，不如在这里就失败。
     */
    private static void writeRole(FriendlyByteBuf buf, TipRole role) {
        var declared = roleById(role.id());
        if (declared == null || !declared.withDepth(role.depth()).equals(role)) {
            throw new IllegalArgumentException("Tip role " + role + " is not a declared role or a depth of one");
        }
        buf.writeUtf(role.id());
        buf.writeVarInt(role.depth());
    }

    private static TipRole readRole(FriendlyByteBuf buf) {
        var id = buf.readUtf();
        var role = roleById(id);
        if (role == null) {
            throw new IllegalArgumentException("Unknown tip role id " + id);
        }
        // depth 与声明一致时 withDepth 返回常量本身，equals 与 == 都保持成立
        return role.withDepth(buf.readVarInt());
    }

    private static TipRole roleById(String id) {
        for (var role : ROLES) {
            if (role.id().equals(id)) return role;
        }
        return null;
    }

    /**
     * 写仪表：先一个有无字节，有则段数加每段的三个数。三个数组是平行的，不平行写出来就读不回同一份
     * 仪表，所以在这里挡下来。
     */
    private static void writeMeter(FriendlyByteBuf buf, @Nullable Meter meter) {
        if (meter == null) {
            buf.writeBoolean(false);
            return;
        }
        var ratios = meter.ratios();
        var lefts = meter.lefts();
        var rights = meter.rights();
        if (lefts.length != ratios.length || rights.length != ratios.length) {
            throw new IllegalArgumentException("A meter's arrays must be parallel: " + ratios.length + " ratios, " +
                    lefts.length + " lefts, " + rights.length + " rights");
        }
        buf.writeBoolean(true);
        buf.writeVarInt(ratios.length);
        for (int i = 0; i < ratios.length; i++) {
            buf.writeFloat(ratios[i]);
            buf.writeInt(lefts[i]);
            buf.writeInt(rights[i]);
        }
    }

    @Nullable
    private static Meter readMeter(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) return null;
        int count = buf.readVarInt();
        var ratios = new float[count];
        var lefts = new int[count];
        var rights = new int[count];
        for (int i = 0; i < count; i++) {
            ratios[i] = buf.readFloat();
            lefts[i] = buf.readInt();
            rights[i] = buf.readInt();
        }
        return new Meter(ratios, lefts, rights);
    }
}
