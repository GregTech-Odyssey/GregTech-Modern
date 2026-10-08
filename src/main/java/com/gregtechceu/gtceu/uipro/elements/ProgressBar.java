package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.IHoverOwner;
import com.gregtechceu.gtceu.uipro.Level;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.render.UIClip;
import com.gregtechceu.gtceu.uipro.render.UIDraw;
import com.gregtechceu.gtceu.uipro.render.UILayers;
import com.gregtechceu.gtceu.uipro.render.UIPixels;
import com.gregtechceu.gtceu.uipro.render.UIText;
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

import com.gto.datasynclib.datastream.codec.StreamCodec;
import com.gto.datasynclib.util.StreamCodecs;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
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
    private static final int TEXT_ON_LIGHT_FILL = 0xFF202020, TEXT_ON_DARK_FILL = 0xFFFFFFFF;

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

    public static final StreamCodec<FriendlyByteBuf, Range> RANGE = new StreamCodec<>() {

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

    public record Callout(boolean shown, Level level, Component text, List<Component> detail) {

        public static final Callout HIDDEN = new Callout(false, Level.NORMAL, Component.empty(), List.of());

        public static Callout of(Level level, Component text, List<Component> detail) {
            return new Callout(true, level, text, detail);
        }

        public boolean isShown() {
            return shown;
        }

        public boolean hasTooltip() {
            return !detail.isEmpty();
        }

        public CalloutBubble.Tone getTone() {
            return CalloutBubble.Tone.of(level);
        }
    }

    public static final StreamCodec<FriendlyByteBuf, Callout> CALLOUT = new StreamCodec<>() {

        @Override
        public void encode(FriendlyByteBuf buf, Callout value) {
            buf.writeBoolean(value.shown());
            if (!value.shown()) return;
            buf.writeVarInt(value.level().ordinal());
            StreamCodecs.COMPONENT_CODEC.encode(buf, value.text());
            buf.writeVarInt(value.detail().size());
            for (var line : value.detail()) StreamCodecs.COMPONENT_CODEC.encode(buf, line);
        }

        @Override
        public Callout decode(FriendlyByteBuf buf) {
            if (!buf.readBoolean()) return Callout.HIDDEN;
            var level = Level.of(buf.readVarInt());
            var text = StreamCodecs.COMPONENT_CODEC.decode(buf);
            int size = buf.readVarInt();
            var detail = new ArrayList<Component>(size);
            for (int i = 0; i < size; i++) detail.add(StreamCodecs.COMPONENT_CODEC.decode(buf));
            return Callout.of(level, text, detail);
        }
    };

    private record Marker(long position, int color, @Nullable SyncValue<Callout> callout) {}

    public static final StreamCodec<FriendlyByteBuf, Progress> PROGRESS = new StreamCodec<>() {

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
    @Nullable
    private IntSupplier clientColor;
    private final SyncValue<Progress> progress;
    @Nullable
    private SyncValue<Component> detail;
    /// 数值文字只在进度变化时格式化一次，不在每帧里拼字符串
    @Nullable
    private Progress formatted;
    private String valueText = "";
    private boolean percent;
    private boolean currentOnly;
    private boolean ticks;
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
    protected ProgressBar(int width, Component label, int color, Supplier<Progress> progress) {
        this.label = label;
        this.color = color;
        layout(l -> l.size(width, HEIGHT));
        this.progress = addSyncValue(SyncValue.of(progress, PROGRESS, Progress.EMPTY));
    }

    public static ProgressBar of(int width, Component label, int color, Supplier<Progress> progress) {
        return new ProgressBar(width, label, color, progress);
    }

    public ProgressBar bindClientColor(IntSupplier color) {
        this.clientColor = color;
        return this;
    }

    /** 服务端下发的悬停说明（如加成从哪里来）；为空时悬停只在名称被截断时显示全文。 */
    public ProgressBar bindDetail(Supplier<Component> detail) {
        this.detail = addSyncValue(SyncValue.ofComponent(detail, Component.empty()));
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

    public ProgressBar ticks() {
        this.ticks = true;
        return this;
    }

    public ProgressBar setUnit(String unit) {
        this.unit = unit;
        return this;
    }

    public ProgressBar addMarker(long position, int color) {
        markers.add(new Marker(position, color, null));
        return this;
    }

    public ProgressBar addMarker(long position, int color, Supplier<Callout> callout) {
        markers.add(new Marker(position, color, addSyncValue(SyncValue.of(callout, CALLOUT, Callout.HIDDEN))));
        return this;
    }

    public ProgressBar bindRange(Supplier<Range> range, int color) {
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
            } else if (ticks) {
                valueText = value.total() <= 0 ? "—" : seconds(seconds(new StringBuilder(), shown).append('/'), value.total()).append('s').toString();
            } else if (currentOnly) {
                valueText = readable(shown);
            } else {
                valueText = readable(shown) + "/" + readable(value.total());
            }
        }
        return valueText;
    }

    private static StringBuilder seconds(StringBuilder builder, long ticks) {
        long tenths = ticks / 2;
        return builder.append(tenths / 10).append('.').append(tenths % 10);
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
        int fill = clientColor == null ? color : clientColor.getAsInt();
        UIDraw.progressTrack(graphics, x, y, w, h);
        int inner = w - 2;
        int filled = UIDraw.progressFill(graphics, x, y, w, h, 0, ticks && value.total() <= 0 ? 0 : value.ratio(), fill);
        if (range != null) {
            var shown = range.getValue();
            UIDraw.progressRange(graphics, x, y, w, h, shown.from(), shown.to(), value.total(), rangeColor);
        }
        for (var marker : markers) UIDraw.progressMarker(graphics, x, y, w, h, marker.position(), value.total(), marker.color());
        if (value.bonusPermille() > 0 && filled < inner) {
            int bonus = Math.min(inner - filled, Math.round(inner * value.bonusPermille() / 1000f));
            UIDraw.progressBonus(graphics, x, y, h, filled, bonus, fill);
        }
        if (labelText == null) labelText = label.getString();
        drawText(graphics, x, y, w, h, filled, value, fill);
        if (hasTooltip()) {
            int ix = infoIconX();
            var icon = infoTone().icon();
            if (icon != null) icon.draw(graphics, 0, 0, UIPixels.center(ix, h, CalloutBubble.ICON), UIPixels.center(y, h, CalloutBubble.ICON), CalloutBubble.ICON, CalloutBubble.ICON);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private void drawText(GuiGraphics graphics, int x, int y, int w, int h, int filled, Progress value, int fill) {
        String shown = valueText(value);
        int valueColor = value.isComplete() ? UITheme.STATUS_TEXT_GOOD : UITheme.TEXT;
        if (filled <= 0) {
            UIText.drawLabelValue(graphics, x, y, w, h, labelText, shown, valueColor);
            return;
        }
        int split = x + 1 + filled;
        int onFill = textOn(fill);
        UIClip.push(graphics, x, y, split - x, h);
        UIText.drawLabelValue(graphics, x, y, w, h, labelText, shown, onFill, value.isComplete() ? UITheme.STATUS_TEXT_GOOD : onFill);
        UIClip.pop(graphics);
        UIClip.push(graphics, split, y, x + w - split, h);
        UIText.drawLabelValue(graphics, x, y, w, h, labelText, shown, valueColor);
        UIClip.pop(graphics);
    }

    private static int textOn(int fill) {
        int r = fill >> 16 & 0xFF, g = fill >> 8 & 0xFF, b = fill & 0xFF;
        return r * 299 + g * 587 + b * 114 > 140_000 ? TEXT_ON_LIGHT_FILL : TEXT_ON_DARK_FILL;
    }

    @OnlyIn(Dist.CLIENT)
    private void drawCallouts(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean wasShown = bubbleShown;
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
            if (!overBar && !(wasShown && overBubble)) continue;
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, UILayers.PAGE_OVERLAY);
            CalloutBubble.drawLabel(graphics, font, bx, by, cx, callout.getTone(), text);
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
        var level = Level.NORMAL;
        for (var marker : markers) {
            if (marker.callout() != null) level = Level.worst(level, marker.callout().getValue().level());
        }
        return CalloutBubble.Tone.of(level);
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
    public boolean hasOwnTooltip(int mouseX, int mouseY) {
        if (super.hasOwnTooltip(mouseX, mouseY) || bubbleShown || hasTooltip() && isOverInfoIcon(mouseX, mouseY)) return true;
        if (detail != null && !detail.getValue().getString().isEmpty()) return true;
        var font = Minecraft.getInstance().font;
        return font.width(label.getString()) + font.width(valueText(progress.getValue())) + 3 * UISizes.TEXT_PADDING > trackWidth();
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
