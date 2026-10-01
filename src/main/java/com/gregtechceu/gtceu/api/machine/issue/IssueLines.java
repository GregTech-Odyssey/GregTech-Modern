package com.gregtechceu.gtceu.api.machine.issue;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.uipro.Level;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 标题栏提示、Jade、状态行共用的快照排版：决定显示哪条原因、标题与说明的颜色和写法。
 */
public final class IssueLines {

    private static final IssueSnapshot[] STATUS_ONLY = {
            IssueSnapshot.EMPTY,
            new IssueSnapshot(RecipeLogic.WORKING, null, 0),
            new IssueSnapshot(RecipeLogic.WAITING, null, 0),
            new IssueSnapshot(RecipeLogic.SUSPEND, null, 0)
    };

    private IssueLines() {}

    public static IssueSnapshot statusOnly(int status) {
        return status >= 0 && status < STATUS_ONLY.length ? STATUS_ONLY[status] : IssueSnapshot.EMPTY;
    }

    public static boolean visible(IssueSnapshot snapshot) {
        return snapshot.primary() != null || snapshot.status() != RecipeLogic.WORKING;
    }

    @Nullable
    public static MachineIssue shown(IssueSnapshot snapshot) {
        var primary = snapshot.primary();
        if (primary != null) return primary;
        return switch (snapshot.status()) {
            case RecipeLogic.IDLE -> GTIssues.NO_RECIPE.bare();
            case RecipeLogic.SUSPEND -> GTIssues.PAUSED.bare();
            default -> null;
        };
    }

    public static ChatFormatting color(IssueSeverity severity) {
        return switch (severity) {
            case INFO -> ChatFormatting.WHITE;
            case WARNING -> ChatFormatting.GOLD;
            case BLOCKING -> ChatFormatting.RED;
        };
    }

    public static MutableComponent title(MachineIssue issue) {
        return Component.empty().append(IssueText.title(issue)).withStyle(color(issue.severity()));
    }

    private static MutableComponent titleOffThread(MachineIssue issue) {
        return Component.empty().append(IssueText.titleOffThread(issue)).withStyle(color(issue.severity()));
    }

    @Nullable
    public static Component detail(MachineIssue issue) {
        var detail = IssueText.detail(issue);
        return detail == null ? null : Component.empty().append(detail).withStyle(ChatFormatting.GRAY);
    }

    public static boolean showsDetail(MachineIssue issue) {
        return issue.custom() == null && (issue.type().hasArgs() || issue.severity() == IssueSeverity.BLOCKING);
    }

    public static Component headline(IssueSnapshot snapshot) {
        return headline(snapshot, false);
    }

    public static Component headlineOffThread(IssueSnapshot snapshot) {
        return headline(snapshot, true);
    }

    private static Component headline(IssueSnapshot snapshot, boolean offThread) {
        var issue = shown(snapshot);
        if (issue == null) return Component.translatable("gtceu.issue.ui.waiting.title").withStyle(ChatFormatting.GOLD);
        var title = offThread ? titleOffThread(issue) : title(issue);
        if (snapshot.isWaiting()) return Component.translatable("gtceu.issue.ui.waiting", title).withStyle(ChatFormatting.GOLD);
        return title;
    }

    @Nullable
    public static Component detail(IssueSnapshot snapshot) {
        var issue = shown(snapshot);
        if (issue == null) return Component.translatable("gtceu.issue.ui.waiting.desc").withStyle(ChatFormatting.GRAY);
        return detail(issue);
    }

    public static List<Component> tooltip(IssueSnapshot snapshot) {
        if (!visible(snapshot)) return Collections.emptyList();
        var detail = detail(snapshot);
        if (detail == null) return Collections.singletonList(headline(snapshot));
        var lines = new ArrayList<Component>(2);
        lines.add(headline(snapshot));
        lines.add(detail);
        return lines;
    }

    public static Level level(IssueSnapshot snapshot) {
        var issue = shown(snapshot);
        if (issue != null) return issue.severity().level();
        return snapshot.isWaiting() ? Level.WARNING : Level.NORMAL;
    }

    public static final class Cache {

        @Nullable
        private IssueSnapshot snapshot;
        private List<Component> lines = Collections.emptyList();

        public List<Component> tooltip(IssueSnapshot current) {
            if (current != snapshot) {
                snapshot = current;
                lines = IssueLines.tooltip(current);
            }
            return lines;
        }
    }
}
