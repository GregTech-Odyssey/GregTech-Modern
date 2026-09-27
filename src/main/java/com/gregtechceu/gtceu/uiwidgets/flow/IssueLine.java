package com.gregtechceu.gtceu.uiwidgets.flow;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.flow.FlowState;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class IssueLine extends UIElement {

    public static final int HEIGHT = UISizes.CONTROL_HEIGHT;
    private static final int ICON = UISizes.CONTROL_HEIGHT;

    @Nullable
    private final Component label;
    private final SyncValue<IssueView> view;
    @Nullable
    private String labelText;
    @Nullable
    private IssueView shown;
    private String text = "";

    public IssueLine(int width, @Nullable Component label, Supplier<IssueView> view) {
        this.label = label;
        layout(l -> l.size(width, HEIGHT));
        this.view = addSyncValue(SyncValue.of(view, IssueView.CODEC, RecipeIssue.IDLE.view()));
    }

    public IssueView getView() {
        return view.getValue();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        var current = view.getValue();
        if (current != shown) {
            shown = current;
            text = current.text().getString();
        }
        var font = Minecraft.getInstance().font;
        int x = getPositionX(), y = getPositionY(), w = getSizeWidth();
        var icon = current.issue().icon();
        int iconSpace = icon == null ? 0 : ICON + UISizes.GAP;
        int textY = y + (HEIGHT - 8) / 2;
        int start;
        String shownText;
        if (label != null) {
            if (labelText == null) labelText = label.getString();
            int valueWidth = Math.min(font.width(text) + iconSpace, w - font.width(labelText) - UISizes.TEXT_PADDING);
            shownText = UITheme.clip(font, text, Math.max(0, valueWidth - iconSpace));
            start = x + w - iconSpace - font.width(shownText);
            graphics.drawString(font, UITheme.clip(font, labelText, Math.max(0, start - x - UISizes.TEXT_PADDING)), x, textY, UITheme.TEXT_SECONDARY, false);
        } else {
            shownText = UITheme.clip(font, text, Math.max(0, w - iconSpace));
            start = x + (w - iconSpace - font.width(shownText)) / 2;
        }
        if (icon != null) icon.draw(graphics, mouseX, mouseY, start, y, ICON, ICON);
        graphics.drawString(font, shownText, start + iconSpace, textY, color(current.state()), false);
    }

    private static int color(FlowState state) {
        return switch (state) {
            case DISABLED, IDLE -> UITheme.TEXT_SECONDARY;
            default -> state.level().textColor();
        };
    }
}
