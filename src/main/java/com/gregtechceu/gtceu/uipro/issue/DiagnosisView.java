package com.gregtechceu.gtceu.uipro.issue;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.issue.DiagnosisEntry;
import com.gregtechceu.gtceu.api.machine.issue.DiagnosisResult;
import com.gregtechceu.gtceu.api.machine.issue.DiagnosisState;
import com.gregtechceu.gtceu.api.machine.issue.DiagnosisTarget;
import com.gregtechceu.gtceu.api.machine.issue.IssueLines;
import com.gregtechceu.gtceu.api.machine.issue.IssueText;
import com.gregtechceu.gtceu.api.machine.issue.MachineDiagnosis;
import com.gregtechceu.gtceu.api.machine.issue.MachineIssue;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.info.CWURecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.EURecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.FluidRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
import com.gregtechceu.gtceu.uipro.Level;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.elements.StatusLine;
import com.gregtechceu.gtceu.uipro.render.UIDraw;
import com.gregtechceu.gtceu.uipro.render.UIPixels;
import com.gregtechceu.gtceu.uipro.render.UIText;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uiwidgets.icon.IssueIcons;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import dev.vfyjxf.taffy.geometry.FloatSize;
import dev.vfyjxf.taffy.util.MeasureFunc;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 运行诊断面板：显示第二级诊断结果（主要原因、逐项需要/现有/状态、机器级问题），数据由服务端经同步值下发，行数只由结果数据决定。
 */
public class DiagnosisView extends UIElement {

    private static final int ROW_HEIGHT = UISizes.STATUS_LINE_HEIGHT;
    private static final int HEADER_HEIGHT = UISizes.CONTROL_HEIGHT;
    private static final int ICON = UISizes.CONTROL_HEIGHT;
    private static final int PADDING = UISizes.PANEL_PADDING;
    private static final int PADDING_BOTTOM = UISizes.PANEL_PADDING_BOTTOM;

    private final SyncValue<DiagnosisResult> result;
    @Nullable
    private DiagnosisResult shown;
    @Nullable
    private Row[] rows;

    public DiagnosisView(int width, Supplier<DiagnosisResult> result) {
        layout(l -> l.width(width));
        setBackground(UITheme.STATUS_PANEL);
        this.result = addSyncValue(SyncValue.of(result, DiagnosisResult.STREAM_CODEC, DiagnosisResult.EMPTY).onChanged(value -> markLayoutDirty()));
        markLayoutDirty();
    }

    public static DiagnosisView of(int width, IRecipeLogicMachine machine) {
        return new DiagnosisView(width, new MachineSource(machine));
    }

    public DiagnosisResult getResult() {
        return result.getValue();
    }

    private static int heightOf(DiagnosisResult result) {
        var primary = result.primary();
        int height = PADDING + PADDING_BOTTOM + HEADER_HEIGHT;
        if (primary != null && !primary.isCustom()) height += ROW_HEIGHT;
        if (result.recipe() != null) height += ROW_HEIGHT;
        height += result.entries().size() * ROW_HEIGHT;
        for (var issue : result.issues()) if (!issue.equals(primary)) height += HEADER_HEIGHT;
        return height;
    }

    @Override
    protected MeasureFunc getMeasure() {
        return (known, available) -> {
            float width = !Float.isNaN(known.width) ? known.width : available.width.isDefinite() ? available.width.getValue() : UISizes.CONTENT_WIDTH;
            float height = !Float.isNaN(known.height) ? known.height : heightOf(result == null ? DiagnosisResult.EMPTY : result.getValue());
            return new FloatSize(width, height);
        };
    }

    @OnlyIn(Dist.CLIENT)
    private Row[] rows() {
        var current = result.getValue();
        if (current != shown) {
            shown = current;
            rows = build(current);
        }
        return rows;
    }

    @OnlyIn(Dist.CLIENT)
    private static Row[] build(DiagnosisResult result) {
        var list = new ArrayList<Row>();
        var primary = result.primary();
        if (primary != null) {
            list.add(issueRow(primary));
            var detail = IssueText.detail(primary);
            if (detail != null) list.add(Row.sentence(detail));
        } else {
            var title = Component.translatable(result.isWorking() ? "gtceu.diagnosis.working" : "gtceu.diagnosis.none");
            list.add(Row.header(null, title, result.isWorking() ? Level.GOOD : Level.NORMAL, List.of(title)));
        }
        var recipe = result.recipe();
        if (recipe != null) {
            var target = targetText(result);
            var tooltip = new ArrayList<Component>(2);
            tooltip.add(target);
            if (recipe.registered) tooltip.add(gray(Component.literal(recipe.id.toString())));
            list.add(Row.line(Component.translatable("gtceu.diagnosis.target"), target, Level.NORMAL, tooltip));
        }
        for (var entry : result.entries()) list.add(entryRow(result, entry));
        for (var issue : result.issues()) {
            if (!issue.equals(primary)) list.add(issueRow(issue));
        }
        return list.toArray(new Row[0]);
    }

    @OnlyIn(Dist.CLIENT)
    private static Row issueRow(MachineIssue issue) {
        var title = IssueText.title(issue);
        var detail = IssueLines.detail(issue);
        return Row.header(IssueIcons.iconFor(issue), title, issue.severity().level(), detail == null ? List.of(title) : List.of(title, detail));
    }

    @OnlyIn(Dist.CLIENT)
    private static Row entryRow(DiagnosisResult result, DiagnosisEntry entry) {
        var name = entryName(entry);
        var value = entryValue(entry);
        var tooltip = new ArrayList<Component>(5);
        tooltip.add(name);
        if (isNumeric(entry)) tooltip.add(gray(Component.translatable("gtceu.diagnosis.value.hint")));
        var issue = entry.toIssue(result.recipe());
        if (isRunningInput(result, entry)) {
            tooltip.add(gray(Component.translatable(entry.isOk() ? "gtceu.flow.issue.input_next_ok.desc" : "gtceu.flow.issue.next_short.desc")));
        } else if (issue != null) {
            tooltip.add(IssueLines.title(issue));
            var detail = IssueLines.detail(issue);
            if (detail != null) tooltip.add(detail);
        } else if (entry.state() == DiagnosisState.SKIPPED) {
            tooltip.add(gray(Component.translatable("gtceu.diagnosis.skipped.desc")));
        }
        if (entry.detail() != null && entry.subject().io() != IO.NONE) tooltip.add(gray(entry.detail()));
        var level = entry.state() == DiagnosisState.SKIPPED ? Level.NORMAL : entry.isOk() ? Level.GOOD : entry.severity().level();
        return Row.line(name, value, level, tooltip);
    }

    @OnlyIn(Dist.CLIENT)
    private static Component entryName(DiagnosisEntry entry) {
        var subject = entry.subject();
        if (subject.capability() == EURecipeInfo.INSTANCE) {
            return switch (subject.index()) {
                case MachineDiagnosis.EU_VOLTAGE -> Component.translatable("gtceu.diagnosis.voltage");
                case MachineDiagnosis.EU_POWER -> Component.translatable("gtceu.diagnosis.power");
                case MachineDiagnosis.EU_BUFFER -> Component.translatable("gtceu.diagnosis.buffer");
                case MachineDiagnosis.EU_OUTPUT -> Component.translatable("gtceu.diagnosis.energy_space");
                default -> Component.translatable("gtceu.diagnosis.energy");
            };
        }
        if (subject.capability() == CWURecipeInfo.INSTANCE) return Component.translatable("gtceu.diagnosis.computation");
        if (entry.label() != null) return entry.label();
        if (subject.io() == IO.NONE && subject.index() < 0) return Component.translatable("gtceu.diagnosis.tier");
        return Component.literal("—");
    }

    @OnlyIn(Dist.CLIENT)
    private static Component entryValue(DiagnosisEntry entry) {
        var subject = entry.subject();
        var state = entry.state();
        if (state == DiagnosisState.SKIPPED) return Component.translatable("gtceu.diagnosis.state.skipped");
        if (state == DiagnosisState.MISSING_HANDLER && entry.type() != null) return Component.translatable(entry.type().titleKey);
        if (subject.io() == IO.NONE && subject.index() >= 0) {
            if (entry.detail() != null) return entry.detail();
            return Component.translatable(state == DiagnosisState.OK ? "gtceu.diagnosis.state.met" : "gtceu.diagnosis.state.unmet");
        }
        if (subject.io() == IO.OUT && subject.capability() != EURecipeInfo.INSTANCE) {
            return Component.translatable(switch (state) {
                case OK -> "gtceu.diagnosis.state.fits";
                case VOIDED -> "gtceu.diagnosis.state.voided";
                default -> "gtceu.diagnosis.state.full";
            });
        }
        var have = IssueText.number(entry.have());
        var need = IssueText.number(entry.need());
        if (subject.capability() == EURecipeInfo.INSTANCE) {
            return switch (subject.index()) {
                case MachineDiagnosis.EU_VOLTAGE -> Component.translatable("gtceu.diagnosis.value", IssueText.tierName(entry.need()), IssueText.tierName(entry.have()));
                case MachineDiagnosis.EU_BUFFER, MachineDiagnosis.EU_OUTPUT -> Component.translatable("gtceu.diagnosis.value.eu", need, have);
                default -> Component.translatable("gtceu.diagnosis.value.eut", need, have);
            };
        }
        if (subject.capability() == CWURecipeInfo.INSTANCE) return Component.translatable("gtceu.diagnosis.value.cwut", need, have);
        if (subject.capability() == FluidRecipeInfo.INSTANCE) return Component.translatable("gtceu.diagnosis.value.mb", need, have);
        if (subject.io() == IO.NONE) return Component.translatable("gtceu.diagnosis.value", IssueText.tierName(entry.need()), IssueText.tierName(entry.have()));
        return Component.translatable("gtceu.diagnosis.value", need, have);
    }

    private static boolean isRunningInput(DiagnosisResult result, DiagnosisEntry entry) {
        var capability = entry.subject().capability();
        return result.target() == DiagnosisTarget.RUNNING && entry.subject().io() == IO.IN &&
                (capability == ItemRecipeInfo.INSTANCE || capability == FluidRecipeInfo.INSTANCE);
    }

    private static boolean isNumeric(DiagnosisEntry entry) {
        var subject = entry.subject();
        var state = entry.state();
        if (state == DiagnosisState.SKIPPED || (state == DiagnosisState.MISSING_HANDLER && entry.type() != null)) return false;
        if (subject.io() == IO.NONE && subject.index() >= 0) return false;
        return subject.io() != IO.OUT || subject.capability() == EURecipeInfo.INSTANCE;
    }

    @OnlyIn(Dist.CLIENT)
    private static Component targetText(DiagnosisResult result) {
        return Component.translatable(switch (result.target()) {
            case RUNNING -> "gtceu.diagnosis.target.running";
            case LOCKED -> "gtceu.diagnosis.target.locked";
            case REPORTED -> "gtceu.diagnosis.target.reported";
            case MACHINE -> "gtceu.diagnosis.target.machine";
            case LAST -> "gtceu.diagnosis.target.last";
            default -> "gtceu.diagnosis.target.none";
        });
    }

    private static Component gray(Component text) {
        return text.copy().withStyle(ChatFormatting.GRAY);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        var font = Minecraft.getInstance().font;
        int x = getPositionX() + PADDING;
        int width = getSizeWidth() - 2 * PADDING;
        int y = getPositionY() + PADDING;
        for (var row : rows()) {
            row.fit(font, width);
            int textY = UIText.centerY(y, row.height);
            if (row.kind == Row.HEADER) {
                int textX = x;
                if (row.icon != null) {
                    row.icon.draw(graphics, mouseX, mouseY, x, y, ICON, ICON);
                    textX += ICON + UISizes.GAP;
                }
                graphics.drawString(font, row.shownName, textX, textY, row.level.getTextColor(), false);
            } else if (row.kind == Row.SENTENCE) {
                graphics.drawString(font, row.shownName, x, textY, UITheme.TEXT_SECONDARY, false);
            } else {
                graphics.drawString(font, row.shownName, x, textY, UITheme.TEXT_SECONDARY, false);
                int valueX = x + width - font.width(row.shownValue);
                graphics.drawString(font, row.shownValue, valueX, textY, row.level.getTextColor(), false);
                if (row.level.hasLamp()) {
                    UIDraw.lamp(graphics, valueX - StatusLine.LAMP_SIZE - StatusLine.LAMP_GAP, UIPixels.center(y, row.height, StatusLine.LAMP_SIZE), StatusLine.LAMP_SIZE, row.level.getLampColor());
                }
            }
            y += row.height;
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        if (gui == null || gui.getModularUIGui() == null || !isMouseOverElement(mouseX, mouseY)) return;
        int y = getPositionY() + PADDING;
        for (var row : rows()) {
            if (mouseY >= y && mouseY < y + row.height) {
                gui.getModularUIGui().setHoverTooltip(row.tooltip, ItemStack.EMPTY, null, null);
                return;
            }
            y += row.height;
        }
    }

    private record MachineSource(IRecipeLogicMachine machine) implements Supplier<DiagnosisResult> {

        @Override
        public DiagnosisResult get() {
            return MachineDiagnosis.of(machine);
        }
    }

    private static final class Row {

        private static final int HEADER = 0;
        private static final int SENTENCE = 1;
        private static final int LINE = 2;

        private final int kind;
        private final int height;
        @Nullable
        private final IGuiTexture icon;
        private final String name;
        private final String value;
        private final Level level;
        private final List<Component> tooltip;
        private int fittedWidth = -1;
        private String shownName = "";
        private String shownValue = "";

        private Row(int kind, int height, @Nullable IGuiTexture icon, String name, String value, Level level, List<Component> tooltip) {
            this.kind = kind;
            this.height = height;
            this.icon = icon;
            this.name = name;
            this.value = value;
            this.level = level;
            this.tooltip = tooltip;
        }

        private static Row header(@Nullable IGuiTexture icon, Component title, Level level, List<Component> tooltip) {
            return new Row(HEADER, HEADER_HEIGHT, icon, title.getString(), "", level, tooltip);
        }

        private static Row sentence(Component text) {
            return new Row(SENTENCE, ROW_HEIGHT, null, text.getString(), "", Level.NORMAL, List.of(text));
        }

        private static Row line(Component name, Component value, Level level, List<Component> tooltip) {
            return new Row(LINE, ROW_HEIGHT, null, name.getString(), value.getString(), level, tooltip);
        }

        @OnlyIn(Dist.CLIENT)
        private void fit(Font font, int width) {
            if (width == fittedWidth) return;
            fittedWidth = width;
            if (kind == HEADER) {
                shownName = UIText.fit(name, icon == null ? width : width - ICON - UISizes.GAP);
            } else if (kind == SENTENCE) {
                shownName = UIText.fit(name, width);
            } else {
                int lamp = level.hasLamp() ? StatusLine.LAMP_SIZE + StatusLine.LAMP_GAP : 0;
                int valueWidth = Math.min(font.width(value), width / 2);
                shownValue = UIText.fit(value, valueWidth);
                shownName = UIText.fit(name, width - font.width(shownValue) - lamp - UISizes.TEXT_PADDING);
            }
        }
    }
}
