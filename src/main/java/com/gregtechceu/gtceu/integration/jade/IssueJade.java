package com.gregtechceu.gtceu.integration.jade;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.issue.IssueLines;
import com.gregtechceu.gtceu.api.machine.issue.IssueSnapshot;
import com.gregtechceu.gtceu.api.machine.issue.IssueText;
import com.gregtechceu.gtceu.api.machine.issue.IssueType;
import com.gregtechceu.gtceu.api.machine.issue.MachineDiagnosis;
import com.gregtechceu.gtceu.api.machine.issue.MachineIssue;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;

import java.util.ArrayList;

/**
 * Jade 的机器运行问题：服务端写快照 NBT 与机器级常驻问题，客户端按 IssueLines 排版。
 */
public final class IssueJade {

    public static final String KEY = "Issue";
    public static final String EXTRA_KEY = "IssueExtra";
    private static final int MAX_EXTRA = 4;

    private IssueJade() {}

    public static void write(CompoundTag tag, RecipeLogic logic) {
        write(tag, logic.getIssueSnapshot(), logic.machine.self());
    }

    public static void write(CompoundTag tag, IssueSnapshot snapshot, @Nullable MetaMachine machine) {
        boolean mainThread = machine != null && machine.getLevel() instanceof ServerLevel level && level.getServer().isSameThread();
        if (IssueLines.visible(snapshot)) tag.put(KEY, snapshot.writeNbt(mainThread));
        if (!mainThread) return;
        var shown = IssueLines.shown(snapshot);
        var shownType = shown == null ? null : shown.type();
        var extras = new ArrayList<MachineIssue>(2);
        MachineDiagnosis.collectIssues(machine, issue -> {
            if (issue.type() == shownType || extras.size() >= MAX_EXTRA) return;
            for (var other : extras) if (sameContent(other, issue)) return;
            extras.add(issue);
        });
        if (extras.isEmpty()) return;
        var list = new ListTag();
        for (var issue : extras) list.add(issue.writeNbt());
        tag.put(EXTRA_KEY, list);
    }

    private static boolean sameContent(MachineIssue first, MachineIssue second) {
        if (first.type() != second.type() || first.a() != second.a() || first.b() != second.b() || !first.subject().equals(second.subject())) return false;
        return !first.isCustom() || IssueText.title(first).equals(IssueText.title(second));
    }

    @Nullable
    public static IssueSnapshot read(CompoundTag tag) {
        return tag.contains(KEY, Tag.TAG_COMPOUND) ? IssueSnapshot.readNbt(tag.getCompound(KEY)) : null;
    }

    public static void append(ITooltip tooltip, CompoundTag tag, boolean details) {
        var snapshot = read(tag);
        if (snapshot != null && IssueLines.visible(snapshot)) {
            tooltip.add(IssueLines.headline(snapshot));
            var issue = IssueLines.shown(snapshot);
            if (issue == null || details || IssueLines.showsDetail(issue)) {
                var detail = IssueLines.detail(snapshot);
                if (detail != null) tooltip.add(detail);
            }
        }
        var extras = tag.getList(EXTRA_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < extras.size(); i++) {
            var issue = MachineIssue.readNbt(extras.getCompound(i));
            if (issue == null) continue;
            tooltip.add(IssueLines.title(issue));
            if (details || IssueLines.showsDetail(issue)) {
                var detail = IssueLines.detail(issue);
                if (detail != null) tooltip.add(detail);
            }
        }
    }

    public static boolean reports(CompoundTag tag, IssueType type) {
        var snapshot = read(tag);
        if (snapshot != null) {
            var issue = IssueLines.shown(snapshot);
            if (issue != null && issue.type() == type) return true;
        }
        var extras = tag.getList(EXTRA_KEY, Tag.TAG_COMPOUND);
        var id = type.id.toString();
        for (int i = 0; i < extras.size(); i++) {
            if (id.equals(extras.getCompound(i).getString("id"))) return true;
        }
        return false;
    }

    public static boolean reports(BlockAccessor accessor, ResourceLocation providerUid, IssueType type) {
        var data = accessor.getServerData().getCompound(providerUid.toString()).getCompound("null");
        return !data.isEmpty() && reports(data, type);
    }
}
