package com.gregtechceu.gtceu.uipro.elements;

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

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * 进度条：下凹的轨道里按比例填色，左侧名称、右侧"当前 / 总量"（缩写数字），完成时数值变绿。
 * 可带一段"加成"（{@link Progress#bonusPermille}）：画在已完成部分之后、颜色更浅并缓慢呼吸，表示将由加成补上的进度。
 * <pre>
 * [████████▒▒▒░░░░░░░ 名称            123K/1M]
 * </pre>
 * 标准高度 {@link #HEIGHT}，宽度固定或 {@link com.gregtechceu.gtceu.uipro.LayoutStyle#AUTO}（被父元素拉伸）。
 * 进度由服务端取值下发（{@link SyncValue}），构造时不调用 getter；填充色是两端相同的常量。
 * 放在区块里纵向堆叠，行距 {@link UISizes#GAP}。
 */
public class ProgressBar extends UIElement {

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

    public static final SyncValue.Codec<Progress> PROGRESS = new SyncValue.Codec<>() {

        @Override
        public void write(FriendlyByteBuf buf, Progress value) {
            buf.writeVarLong(value.current());
            buf.writeVarLong(value.total());
            buf.writeVarInt(value.bonusPermille());
        }

        @Override
        public Progress read(FriendlyByteBuf buf) {
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
        this.detail = addSyncValue(SyncValue.of(detail, SyncValue.COMPONENT, Component.empty()));
        return this;
    }

    public Progress getProgress() {
        return progress.getValue();
    }

    @OnlyIn(Dist.CLIENT)
    private String valueText(Progress value) {
        if (!value.equals(formatted)) {
            formatted = value;
            long shown = value.current() + value.bonus();
            valueText = FormattingUtil.formatNumberReadable(shown) + "/" + FormattingUtil.formatNumberReadable(value.total());
        }
        return valueText;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        int x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
        var value = progress.getValue();
        UITheme.PROGRESS_TRACK.draw(graphics, mouseX, mouseY, x, y, w, h);
        int inner = w - 2;
        int filled = Math.round(inner * value.ratio());
        if (filled > 0) graphics.fill(x + 1, y + 1, x + 1 + filled, y + h - 1, color);
        if (value.bonusPermille() > 0 && filled < inner) {
            int bonus = Math.min(inner - filled, Math.round(inner * value.bonusPermille() / 1000f));
            double phase = (System.currentTimeMillis() % BONUS_PULSE_MS) / (double) BONUS_PULSE_MS;
            int alpha = (int) (0x60 + 0x40 * (0.5 - 0.5 * Math.cos(phase * 2 * Math.PI)));
            graphics.fill(x + 1 + filled, y + 1, x + 1 + filled + bonus, y + h - 1, alpha << 24 | (color & 0xFFFFFF));
        }

        var font = Minecraft.getInstance().font;
        String valueText = valueText(value);
        // 字形占行高 8 的上 6~7 行，按行高居中后正好落在 1 像素边框之内的正中
        int textY = y + (h - 8) / 2;
        int valueWidth = Math.min(font.width(valueText), (w - 2 * UISizes.TEXT_PADDING) / 2);
        String shownValue = UITheme.clip(font, valueText, valueWidth);
        graphics.drawString(font, shownValue, x + w - UISizes.TEXT_PADDING + 1 - font.width(shownValue), textY,
                value.isComplete() ? UITheme.STATUS_TEXT_GOOD : UITheme.TEXT, false);
        if (labelText == null) labelText = label.getString();
        String shownLabel = UITheme.clip(font, labelText, w - 3 * UISizes.TEXT_PADDING - font.width(shownValue));
        graphics.drawString(font, shownLabel, x + UISizes.TEXT_PADDING - 1, textY, UITheme.TEXT, false);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        if (!tooltipTexts.isEmpty() || gui == null || gui.getModularUIGui() == null || !isMouseOverElement(mouseX, mouseY)) return;
        if (detail != null && !detail.getValue().getString().isEmpty()) {
            gui.getModularUIGui().setHoverTooltip(List.of(label, detail.getValue()), ItemStack.EMPTY, null, null);
            return;
        }
        var font = Minecraft.getInstance().font;
        String valueText = valueText(progress.getValue());
        if (font.width(label.getString()) + font.width(valueText) + 3 * UISizes.TEXT_PADDING > getSizeWidth()) {
            gui.getModularUIGui().setHoverTooltip(List.of(label.copy().append(" ").append(valueText)), ItemStack.EMPTY, null, null);
        }
    }
}
