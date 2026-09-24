package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uipro.utils.TextLayout;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import dev.vfyjxf.taffy.geometry.FloatSize;
import dev.vfyjxf.taffy.util.MeasureFunc;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * 自动换行的文字块，文字由服务端下发（对应 LDLib2 {@code Label.bindDataSource(componentS2C)}）。
 * <p>
 * 默认原版容器正文色（深灰、无阴影）；单行高 {@link #HEIGHT}。尺寸由 Taffy 测量函数给出：
 * 按可用宽度（不超过 {@code maxWidth}）换行后的实际宽高，文字变了重新测量。文字只能在客户端测量，服务端按 {@code maxWidth} 单行估计。
 */
public class Label extends UIElement {

    private static final int PADDING_Y = 1;
    private static final int LINE_GAP = 1;
    /** 单行文字的高度。 */
    public static final int HEIGHT = UISizes.TEXT_HEIGHT;
    /** 单行标签占的总高度（文字 + 上下各 1 像素留白）。 */
    public static final int LINE_HEIGHT = HEIGHT + 2 * PADDING_Y;

    private static final int DEFAULT_COLOR = UITheme.TEXT;

    private final SyncValue<Component> text;
    private final int maxWidth;
    private int color = DEFAULT_COLOR;
    @Nullable
    private TextLayout.Block block;
    private int blockWidth = -1;
    @Nullable
    private Component blockText;

    public Label(Supplier<Component> text, int maxWidth) {
        this.maxWidth = maxWidth;
        this.text = addSyncValue(SyncValue.ofComponent(text).onChanged(value -> markLayoutDirty()));
        markLayoutDirty();
    }

    public static Label of(Supplier<Component> text, int maxWidth) {
        return new Label(text, maxWidth);
    }

    public static Label translatable(String key, int maxWidth) {
        var component = Component.translatable(key);
        return new Label(() -> component, maxWidth);
    }

    public Label setColor(int color) {
        this.color = color;
        return this;
    }

    @Override
    protected MeasureFunc getMeasure() {
        return (known, available) -> {
            float limit = maxWidth;
            if (!Float.isNaN(known.width)) limit = Math.min(limit, known.width);
            else if (available.width.isDefinite()) limit = Math.min(limit, available.width.getValue());
            if (text == null || !isRemote()) {
                return new FloatSize(Float.isNaN(known.width) ? limit : known.width, Float.isNaN(known.height) ? LINE_HEIGHT : known.height);
            }
            var laid = layoutText((int) Math.max(1, limit));
            return new FloatSize(Float.isNaN(known.width) ? laid.width() : known.width,
                    Float.isNaN(known.height) ? laid.height() + 2 * PADDING_Y : known.height);
        };
    }

    @OnlyIn(Dist.CLIENT)
    private TextLayout.Block layoutText(int width) {
        var current = text.getValue();
        if (block == null || blockWidth != width || blockText != current) {
            block = TextLayout.layout(current.getString(), width, LINE_GAP);
            blockWidth = width;
            blockText = current;
        }
        return block;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        var laid = layoutText(Math.max(1, Math.min(maxWidth, getSizeWidth())));
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(getPositionX(), getPositionY() + PADDING_Y, 0);
        TextLayout.draw(graphics, laid, LINE_GAP, color);
        pose.popPose();
    }
}
