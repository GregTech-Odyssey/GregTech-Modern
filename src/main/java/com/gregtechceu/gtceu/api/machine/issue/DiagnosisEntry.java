package com.gregtechceu.gtceu.api.machine.issue;

import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.info.RecipeInfo;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import com.gto.datasynclib.datastream.codec.ByteStreamCodec;
import com.gto.datasynclib.util.StreamCodecs;
import org.jetbrains.annotations.Nullable;

public record DiagnosisEntry(IssueSubject subject, DiagnosisState state, long need, long have,
                             @Nullable IssueType type, @Nullable Component label, @Nullable Component detail) {

    public static final ByteStreamCodec<DiagnosisEntry> STREAM_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(FriendlyByteBuf buf, DiagnosisEntry entry) {
            entry.subject.write(buf);
            buf.writeByte(entry.state.ordinal());
            buf.writeVarLong(entry.need);
            buf.writeVarLong(entry.have);
            buf.writeVarInt(entry.type == null ? 0 : entry.type.networkId() + 1);
            buf.writeBoolean(entry.label != null);
            if (entry.label != null) StreamCodecs.COMPONENT_CODEC.encode(buf, entry.label);
            buf.writeBoolean(entry.detail != null);
            if (entry.detail != null) StreamCodecs.COMPONENT_CODEC.encode(buf, entry.detail);
        }

        @Override
        public DiagnosisEntry decode(FriendlyByteBuf buf) {
            var subject = IssueSubject.read(buf);
            var state = DiagnosisState.of(buf.readByte());
            long need = buf.readVarLong();
            long have = buf.readVarLong();
            int typeId = buf.readVarInt();
            var type = typeId == 0 ? null : IssueType.byNetworkId(typeId - 1);
            var label = buf.readBoolean() ? StreamCodecs.COMPONENT_CODEC.decode(buf) : null;
            var detail = buf.readBoolean() ? StreamCodecs.COMPONENT_CODEC.decode(buf) : null;
            return new DiagnosisEntry(subject, state, need, have, type, label, detail);
        }
    };

    public static DiagnosisEntry of(IO io, @Nullable RecipeInfo capability, int index, DiagnosisState state, long need, long have, @Nullable IssueType type) {
        return new DiagnosisEntry(IssueSubject.of(io, capability, index), state, need, have, type, null, null);
    }

    public DiagnosisEntry withLabel(@Nullable Component label) {
        return new DiagnosisEntry(subject, state, need, have, type, label, detail);
    }

    public DiagnosisEntry withDetail(@Nullable Component detail) {
        return new DiagnosisEntry(subject, state, need, have, type, label, detail);
    }

    public IssueSeverity severity() {
        return state.severity();
    }

    public boolean isProblem() {
        return state.isProblem();
    }

    public boolean isWarning() {
        return state.isWarning();
    }

    public boolean isOk() {
        return state == DiagnosisState.OK;
    }

    public boolean is(IO io, @Nullable RecipeInfo capability) {
        return subject.io() == io && subject.capability() == capability;
    }

    @Nullable
    public MachineIssue toIssue(@Nullable GTRecipeDefinition recipe) {
        if (type == null || state == DiagnosisState.OK || state == DiagnosisState.SKIPPED) return null;
        return MachineIssue.of(type, type.stage, subject, need, have, recipe).withSeverity(state.severity());
    }
}
