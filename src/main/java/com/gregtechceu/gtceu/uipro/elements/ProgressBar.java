package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.IHoverOwner;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.gto.datasynclib.datastream.codec.ByteStreamCodec;
import com.gto.datasynclib.util.StreamCodecs;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 进度条：下凹的轨道里按比例填色，左侧名称、右侧"当前 / 总量"（缩写数字），完成时数值变绿。
 * 可带一段"加成"（{@link Progress#bonusPermille}）：画在已完成部分之后、颜色更浅并缓慢呼吸，表示将由加成补上的进度。
 * 
 * <pre>
 * [████████▒▒▒░░░░░░░ 名称            123K/1M]
 * </pre>
 * 
 * 标准高度 {@link #HEIGHT}，宽度固定或 {@link com.gregtechceu.gtceu.uipro.LayoutStyle#AUTO}（被父元素拉伸）。
 * 进度由服务端取值下发（{@link SyncValue}），构造时不调用 getter；填充色是两端相同的常量。
 * 放在区块里纵向堆叠，行距 {@link UISizes#GAP}。
 */
public class ProgressBar extends UIElement implements IHoverOwner {

    public static final int HEIGHT = UISizes.PROGRESS_BAR_HEIGHT;
    private static final long BONUS_PULSE_MS = 2000;

    /**
     * 一条进度。
     *
     * @param current       已完成量
     * @param total         总量（≤0 视为已完成）
     * @param bonusPermille 加成占总量的千分比（0 为没有）
     */
    public record Progress(long current, long total, int bonusPermille) {

        public static final Progress EMPTY = new Progress(0, 0, 0);

        public float ratio() {
            return total <= 0 ? 1 : Math.min(1, Math.max(0, (float) current / total));
        }

        /** 加成折算的量（按千分比，用浮点避免总量很大时相乘溢出）。 */
        public long bonus() {
            return (long) (total * (bonusPermille / 1000.0));
        }

        public boolean isComplete() {
            return total <= 0 || current + bonus() >= total;
        }
    }

    public record Range(long from, long to) {

        public static final Range NONE = new Range(0, 0);

        public boolean isEmpty() {
            return to <= from;
        }
    }

    public static final ByteStreamCodec<Range> RANGE = new ByteStreamCodec<>() {

        @Override
        public void encode(FriendlyByteBuf buf, Range value) {
            buf.writeVarLong(value.from());
            buf.writeVarLong(value.to());
        }

        @Override
        public Range decode(FriendlyByteBuf buf) {
            return new Range(buf.readVarLong(), buf.readVarLong());
        }
    };

    public record Callout(int level, Component text, List<Component> detail) {

        public static final int HIDDEN_LEVEL = 0;
        public static final int INFO = 1;
        public static final int WARNING = 2;
        public static final int DANGER = 3;
        public static final Callout HIDDEN = new Callout(HIDDEN_LEVEL, Component.empty(), List.of());

        public boolean isShown() {
            return level > HIDDEN_LEVEL;
        }

        public boolean hasTooltip() {
            return !detail.isEmpty();
        }

        public boolean isPinned() {
            return level >= DANGER;
        }

        public CalloutBubble.Tone tone() {
            return level >= DANGER ? CalloutBubble.Tone.DANGER : level >= WARNING ? CalloutBubble.Tone.WARNING : CalloutBubble.Tone.INFO;
        }
    }

    public static final ByteStreamCodec<Callout> CALLOUT = new ByteStreamCodec<>() {

        @Override
        public void encode(FriendlyByteBuf buf, Callout value) {
            buf.writeVarInt(value.level());
            StreamCodecs.COMPONENT_CODEC.encode(buf, value.text());
            buf.writeVarInt(value.detail().size());
            for (var line : value.detail()) StreamCodecs.COMPONENT_CODEC.encode(buf, line);
        }

        @Override
        public Callout decode(FriendlyByteBuf buf) {
            int level = buf.readVarInt();
            var text = StreamCodecs.COMPONENT_CODEC.decode(buf);
            int size = buf.readVarInt();
            var detail = new ArrayList<Component>(size);
            for (int i = 0; i < size; i++) detail.add(StreamCodecs.COMPONENT_CODEC.decode(buf));
            return new Callout(level, text, detail);
        }
    };

    private record Marker(long position, int color, @Nullable SyncValue<Callout> callout) {}

    public static final ByteStreamCodec<Progress> PROGRESS = new ByteStreamCodec<>() {

        @Override
        public void encode(FriendlyByteBuf buf, Progress value) {
            buf.writeVarLong(value.current());
            buf.writeVarLong(value.total());
            buf.writeVarInt(value.bonusPermille());
        }

        @Override
        public Progress decode(FriendlyByteBuf buf) {
            return new Progress(buf.readVarLong(), buf.readVarLong(), buf.readVarInt());
        }
    };

    private final Component label;
    /// 名称文字（客户端第一次绘制时取一次，语言切换后重开界面才会更新）
    @Nullable
    private String labelText;
    private final int color;
    private final SyncValue<Progress> progress;
    @Nullable
    private SyncValue<Component> detail;
    /// 数值文字只在进度变化时格式化一次，不在每帧里拼字符串
    @Nullable
    private Progress formatted;
    private String valueText = "";
    private boolean percent;
    private boolean currentOnly;
    @Nullable
    private String unit;
    private final List<Marker> markers = new ArrayList<>(0);
    private boolean bubbleShown;
    private int bubbleMinX, bubbleMinY, bubbleMaxX, bubbleMaxY;
    @Nullable
    private SyncValue<Range> range;
    private int rangeColor;

    /**
     * @param width    宽度（或 AUTO）
     * @param label    名称（两端相同）
     * @param color    填充色（ARGB，两端相同；取亮一些的颜色，上面要压深色字）
     * @param progress 服务端取值
     */
    public ProgressBar(int width, Component label, int color, Supplier<Progress> progress) {
        this.label = label;
        this.color = color;
        layout(l -> l.size(width, HEIGHT));
        this.progress = addSyncValue(SyncValue.of(progress, PROGRESS, Progress.EMPTY));
    }

    /** 服务端下发的悬停说明（如加成从哪里来）；为空时悬停只在名称被截断时显示全文。 */
    public ProgressBar detail(Supplier<Component> detail) {
        this.detail = addSyncValue(SyncValue.of(detail, StreamCodecs.COMPONENT_CODEC, Component.empty()));
        return this;
    }

    public ProgressBar percent() {
        this.percent = true;
        return this;
    }

    public ProgressBar currentOnly() {
        this.currentOnly = true;
        return this;
    }

    public ProgressBar unit(String unit) {
        this.unit = unit;
        return this;
    }

    public ProgressBar marker(long position, int color) {
        markers.add(new Marker(position, color, null));
        return this;
    }

    public ProgressBar marker(long position, int color, Supplier<Callout> callout) {
        markers.add(new Marker(position, color, addSyncValue(SyncValue.of(callout, CALLOUT, Callout.HIDDEN))));
        return this;
    }

    public ProgressBar range(Supplier<Range> range, int color) {
        this.range = addSyncValue(SyncValue.of(range, RANGE, Range.NONE));
        this.rangeColor = color;
        return this;
    }

    public Range getRange() {
        return range == null ? Range.NONE : range.getValue();
    }

    public Progress getProgress() {
        return progress.getValue();
    }

    @OnlyIn(Dist.CLIENT)
    private String valueText(Progress value) {
        if (!value.equals(formatted)) {
            formatted = value;
            long shown = value.current() + value.bonus();
            if (percent) {
                float ratio = value.total() <= 0 ? 1 : Math.min(1, Math.max(0, (float) shown / value.total()));
                valueText = (int) Math.floor(ratio * 100) + "%";
            } else if (currentOnly) {
                valueText = readable(shown);
            } else {
                valueText = readable(shown) + "/" + readable(value.total());
            }
        }
        return valueText;
    }

    private String readable(long amount) {
        return unit == null ? FormattingUtil.formatNumberReadable(amount) : FormattingUtil.formatNumberReadable(amount, false, FormattingUtil.DECIMAL_FORMAT_1F, unit);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        int x = getPositionX(), y = getPositionY(), h = getSizeHeight(), w = trackWidth();
        var value = progress.getValue();
        drawTrack(graphics, x, y, w, h);
        int inner = w - 2;
        int filled = drawFill(graphics, x, y, w, h, 0, value.ratio(), color);
        if (range != null) drawRange(graphics, x, y, w, h, range.getValue(), value.total(), rangeColor);
        for (var marker : markers) drawMarker(graphics, x, y, w, h, marker.position(), value.total(), marker.color());
        if (value.bonusPermille() > 0 && filled < inner) {
            int bonus = Math.min(inner - filled, Math.round(inner * value.bonusPermille() / 1000f));
            double phase = (System.currentTimeMillis() % BONUS_PULSE_MS) / (double) BONUS_PULSE_MS;
            int alpha = (int) (0x60 + 0x40 * (0.5 - 0.5 * Math.cos(phase * 2 * Math.PI)));
            graphics.fill(x + 1 + filled, y + 1, x + 1 + filled + bonus, y + h - 1, alpha << 24 | (color & 0xFFFFFF));
        }
        if (labelText == null) labelText = label.getString();
        drawText(graphics, x, y, w, h, labelText, valueText(value), value.isComplete() ? UITheme.STATUS_TEXT_GOOD : UITheme.TEXT);
        if (hasTooltip()) {
            int ix = infoIconX();
            var icon = infoTone().icon();
            if (icon != null) icon.draw(graphics, 0, 0, ix + (h - CalloutBubble.ICON) / 2, y + (h - CalloutBubble.ICON) / 2, CalloutBubble.ICON, CalloutBubble.ICON);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void drawTrack(GuiGraphics graphics, int x, int y, int w, int h) {
        UITheme.PROGRESS_TRACK.draw(graphics, 0, 0, x, y, w, h);
    }

    @OnlyIn(Dist.CLIENT)
    public static int drawFill(GuiGraphics graphics, int x, int y, int w, int h, int from, float ratio, int color) {
        int filled = Math.min(w - 2 - from, Math.round((w - 2) * Math.max(0, ratio)));
        if (filled <= 0) return from;
        graphics.fill(x + 1 + from, y + 1, x + 1 + from + filled, y + h - 1, color);
        return from + filled;
    }

    @OnlyIn(Dist.CLIENT)
    public static void drawRange(GuiGraphics graphics, int x, int y, int w, int h, Range range, long total, int color) {
        if (range.isEmpty() || total <= 0) return;
        int inner = w - 2;
        int from = (int) Math.round(inner * Math.min(1, Math.max(0, (double) range.from() / total)));
        int to = (int) Math.round(inner * Math.min(1, Math.max(0, (double) range.to() / total)));
        if (to <= from) to = Math.min(inner, from + 1);
        graphics.fill(x + 1 + from, y + 1, x + 1 + to, y + h - 1, (color & 0xFFFFFF) | 0x50000000);
        graphics.fill(x + 1 + from, y + 1, x + 2 + from, y + h - 1, color);
        graphics.fill(x + to, y + 1, x + 1 + to, y + h - 1, color);
    }

    @OnlyIn(Dist.CLIENT)
    public static void drawMarker(GuiGraphics graphics, int x, int y, int w, int h, long position, long total, int color) {
        if (total <= 0) return;
        int inner = w - 2;
        int at = (int) Math.round((inner - 1) * Math.min(1, Math.max(0, (double) position / total)));
        int tick = Math.max(1, (h - 8) / 2);
        graphics.fill(x + 1 + at, y, x + 2 + at, y + tick, color);
        graphics.fill(x + 1 + at, y + h - tick, x + 2 + at, y + h, color);
    }

    @OnlyIn(Dist.CLIENT)
    public static void drawText(GuiGraphics graphics, int x, int y, int w, int h, String label, String value, int valueColor) {
        var font = Minecraft.getInstance().font;
        // 字形占行高 8 的上 6~7 行，按行高居中后正好落在 1 像素边框之内的正中
        int textY = y + (h - 8) / 2;
        int valueWidth = Math.min(font.width(value), (w - 2 * UISizes.TEXT_PADDING) / 2);
        String shownValue = UITheme.clip(font, value, valueWidth);
        graphics.drawString(font, shownValue, x + w - UISizes.TEXT_PADDING + 1 - font.width(shownValue), textY, valueColor, false);
        String shownLabel = UITheme.clip(font, label, w - 3 * UISizes.TEXT_PADDING - font.width(shownValue));
        graphics.drawString(font, shownLabel, x + UISizes.TEXT_PADDING - 1, textY, UITheme.TEXT, false);
    }

    @OnlyIn(Dist.CLIENT)
    private void drawCallouts(GuiGraphics graphics, int mouseX, int mouseY) {
        bubbleShown = false;
        long total = progress.getValue().total();
        if (total <= 0) return;
        var font = Minecraft.getInstance().font;
        int x = getPositionX(), y = getPositionY(), w = trackWidth(), h = getSizeHeight();
        boolean overBar = isMouseOverElement(mouseX, mouseY);
        for (var marker : markers) {
            if (marker.callout() == null) continue;
            var callout = marker.callout().getValue();
            if (!callout.isShown()) continue;
            String text = callout.text().getString();
            int bw = CalloutBubble.labelWidth(font, text);
            int cx = x + 1 + (int) Math.round((w - 3) * Math.min(1, Math.max(0, (double) marker.position() / total)));
            int bx = bw >= w ? x : Math.max(x, Math.min(x + w - bw, cx - bw / 2));
            int by = y + h + CalloutBubble.NOTCH;
            boolean overBubble = mouseX >= bx && mouseX < bx + bw && mouseY >= by - CalloutBubble.NOTCH && mouseY < by + CalloutBubble.LABEL_HEIGHT;
            if (!callout.isPinned() && !overBar && !overBubble) continue;
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, UITheme.PAGE_OVERLAY_Z);
            CalloutBubble.drawLabel(graphics, font, bx, by, cx, callout.tone(), text);
            graphics.pose().popPose();
            if (!bubbleShown) {
                bubbleShown = true;
                bubbleMinX = bx;
                bubbleMinY = by - CalloutBubble.NOTCH;
                bubbleMaxX = bx + bw;
                bubbleMaxY = by + CalloutBubble.LABEL_HEIGHT;
            } else {
                bubbleMinX = Math.min(bubbleMinX, bx);
                bubbleMaxX = Math.max(bubbleMaxX, bx + bw);
            }
        }
    }

    private static final int INFO_GAP = 2;

    private boolean hasTooltip() {
        for (var marker : markers) {
            if (marker.callout() != null && marker.callout().getValue().hasTooltip()) return true;
        }
        return false;
    }

    private int trackWidth() {
        return hasTooltip() ? getSizeWidth() - getSizeHeight() - INFO_GAP : getSizeWidth();
    }

    private CalloutBubble.Tone infoTone() {
        int level = Callout.INFO;
        for (var marker : markers) {
            if (marker.callout() != null) level = Math.max(level, marker.callout().getValue().level());
        }
        return level >= Callout.DANGER ? CalloutBubble.Tone.DANGER : level >= Callout.WARNING ? CalloutBubble.Tone.WARNING : CalloutBubble.Tone.INFO;
    }

    private int infoIconX() {
        return getPositionX() + getSizeWidth() - getSizeHeight();
    }

    @OnlyIn(Dist.CLIENT)
    private boolean isOverInfoIcon(int mouseX, int mouseY) {
        int ix = infoIconX(), iy = getPositionY();
        return mouseX >= ix && mouseX < ix + getSizeHeight() && mouseY >= iy && mouseY < iy + getSizeHeight();
    }

    private List<Component> mergedCalloutDetail() {
        var lines = new ArrayList<Component>();
        for (var marker : markers) {
            if (marker.callout() == null) continue;
            var callout = marker.callout().getValue();
            if (!callout.hasTooltip()) continue;
            if (!lines.isEmpty()) lines.add(Component.empty());
            lines.addAll(callout.detail());
        }
        return lines;
    }

    @Override
    public boolean ownsHover(int mouseX, int mouseY) {
        if (isMouseOverElement(mouseX, mouseY)) return true;
        return bubbleShown && mouseX >= bubbleMinX && mouseX < bubbleMaxX && mouseY >= bubbleMinY && mouseY < bubbleMaxY;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        drawCallouts(graphics, mouseX, mouseY);
        if (!tooltipTexts.isEmpty() || gui == null || gui.getModularUIGui() == null || !isMouseOverElement(mouseX, mouseY)) return;
        if (hasTooltip() && isOverInfoIcon(mouseX, mouseY)) {
            gui.getModularUIGui().setHoverTooltip(mergedCalloutDetail(), ItemStack.EMPTY, null, null);
            return;
        }
        if (detail != null && !detail.getValue().getString().isEmpty()) {
            gui.getModularUIGui().setHoverTooltip(List.of(label, detail.getValue()), ItemStack.EMPTY, null, null);
            return;
        }
        var font = Minecraft.getInstance().font;
        String valueText = valueText(progress.getValue());
        if (font.width(label.getString()) + font.width(valueText) + 3 * UISizes.TEXT_PADDING > trackWidth()) {
            gui.getModularUIGui().setHoverTooltip(List.of(label.copy().append(" ").append(valueText)), ItemStack.EMPTY, null, null);
        }
    }
}
