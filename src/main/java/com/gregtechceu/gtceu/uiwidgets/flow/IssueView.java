package com.gregtechceu.gtceu.uiwidgets.flow;

import com.gregtechceu.gtceu.uipro.flow.FlowState;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import com.gto.datasynclib.datastream.codec.StreamCodec;
import com.gto.datasynclib.util.ByteBufCodecExtends;
import org.jetbrains.annotations.Nullable;

public record IssueView(RecipeIssue issue, @Nullable Component label, @Nullable FlowState stateOverride) {

    public static final StreamCodec<FriendlyByteBuf, IssueView> CODEC = new StreamCodec<>() {

        @Override
        public void encode(FriendlyByteBuf buf, IssueView value) {
            buf.writeVarInt(value.issue.ordinal());
            buf.writeBoolean(value.label != null);
            if (value.label != null) ByteBufCodecExtends.COMPONENT_CODEC.encode(buf, value.label);
            buf.writeByte(value.stateOverride == null ? -1 : value.stateOverride.ordinal());
        }

        @Override
        public IssueView decode(FriendlyByteBuf buf) {
            var issue = RecipeIssue.of(buf.readVarInt());
            var label = buf.readBoolean() ? ByteBufCodecExtends.COMPONENT_CODEC.decode(buf) : null;
            int state = buf.readByte();
            return label == null && state < 0 ? issue.view() : new IssueView(issue, label, state < 0 ? null : FlowState.of(state));
        }
    };

    public static IssueView of(RecipeIssue issue, @Nullable Component label) {
        return label == null ? issue.view() : new IssueView(issue, label, null);
    }

    public static IssueView of(RecipeIssue issue, @Nullable Component label, FlowState state) {
        return new IssueView(issue, label, state);
    }

    public FlowState state() {
        return stateOverride != null ? stateOverride : issue.state();
    }

    public Component text() {
        return label != null ? label : Component.translatable(issue.key());
    }
}
