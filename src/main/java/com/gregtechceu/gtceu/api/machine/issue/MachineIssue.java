package com.gregtechceu.gtceu.api.machine.issue;

import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.gto.datasynclib.datastream.codec.ByteStreamCodec;
import com.gto.datasynclib.util.StreamCodecs;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public record MachineIssue(IssueType type, IssueSeverity severity, IssueStage stage, IssueSubject subject,
                           long a, long b, @Nullable GTRecipeDefinition recipe, @Nullable Supplier<Component> custom) {

    public static final ByteStreamCodec<MachineIssue> STREAM_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(FriendlyByteBuf buf, MachineIssue issue) {
            buf.writeVarInt(issue.type.networkId());
            buf.writeByte(issue.severity.ordinal());
            buf.writeByte(issue.stage.ordinal());
            issue.subject.write(buf);
            buf.writeVarLong(issue.a);
            buf.writeVarLong(issue.b);
            var recipe = issue.recipe;
            buf.writeBoolean(recipe != null);
            if (recipe != null) GTRecipeDefinition.STREAM_CODEC.encode(buf, recipe);
            var custom = issue.custom;
            var text = custom == null ? null : custom.get();
            buf.writeBoolean(text != null);
            if (text != null) StreamCodecs.COMPONENT_CODEC.encode(buf, text);
        }

        @Override
        public MachineIssue decode(FriendlyByteBuf buf) {
            var type = IssueType.byNetworkId(buf.readVarInt());
            var severity = IssueSeverity.of(buf.readByte());
            var stage = IssueStage.of(buf.readByte());
            var subject = IssueSubject.read(buf);
            long a = buf.readVarLong();
            long b = buf.readVarLong();
            var recipe = buf.readBoolean() ? GTRecipeDefinition.STREAM_CODEC.decode(buf) : null;
            Supplier<Component> custom = null;
            if (buf.readBoolean()) custom = constant(StreamCodecs.COMPONENT_CODEC.decode(buf));
            if (type == null) type = GTIssues.CUSTOM;
            return new MachineIssue(type, severity, stage, subject, a, b, recipe, custom);
        }
    };

    public static final ByteStreamCodec<MachineIssue> NULLABLE_STREAM_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(FriendlyByteBuf buf, @Nullable MachineIssue issue) {
            buf.writeBoolean(issue != null);
            if (issue != null) STREAM_CODEC.encode(buf, issue);
        }

        @Override
        @Nullable
        public MachineIssue decode(FriendlyByteBuf buf) {
            return buf.readBoolean() ? STREAM_CODEC.decode(buf) : null;
        }
    };

    public static MachineIssue of(IssueType type) {
        return type.bare();
    }

    public static MachineIssue of(IssueType type, long a, long b) {
        if (a == 0 && b == 0) return type.bare();
        return new MachineIssue(type, type.severity, type.stage, IssueSubject.NONE, a, b, null, null);
    }

    public static MachineIssue of(IssueType type, IssueStage stage, IssueSubject subject, long a, long b, @Nullable GTRecipeDefinition recipe) {
        return new MachineIssue(type, type.severity, stage, subject, a, b, recipe, null);
    }

    public MachineIssue withSeverity(IssueSeverity severity) {
        if (severity == this.severity) return this;
        return new MachineIssue(type, severity, stage, subject, a, b, recipe, custom);
    }

    public IssueCategory category() {
        return type.category;
    }

    public boolean isCustom() {
        return custom != null;
    }

    public boolean isBlocking() {
        return severity == IssueSeverity.BLOCKING;
    }

    public CompoundTag writeNbt() {
        return writeNbt(true);
    }

    public CompoundTag writeNbt(boolean customText) {
        var tag = new CompoundTag();
        tag.putString("id", type.id.toString());
        if (severity != type.severity) tag.putByte("sev", (byte) severity.ordinal());
        if (stage != type.stage) tag.putByte("st", (byte) stage.ordinal());
        if (a != 0 || b != 0) tag.putLongArray("v", new long[] { a, b });
        if (!subject.isNone()) {
            var sub = new CompoundTag();
            subject.writeNbt(sub);
            tag.put("sub", sub);
        }
        if (recipe != null && recipe.registered) tag.putString("r", recipe.id.toString());
        var text = custom == null || !customText ? null : custom.get();
        if (text != null) tag.putString("c", Component.Serializer.toJson(text));
        return tag;
    }

    @Nullable
    public static MachineIssue readNbt(@Nullable CompoundTag tag) {
        if (tag == null || !tag.contains("id")) return null;
        var id = ResourceLocation.tryParse(tag.getString("id"));
        var type = id == null ? null : IssueType.byId(id);
        Supplier<Component> custom = null;
        if (tag.contains("c")) {
            var text = parseText(tag.getString("c"));
            if (text != null) custom = constant(text);
        }
        if (type == null) {
            if (custom == null) return null;
            type = GTIssues.CUSTOM;
        }
        var severity = tag.contains("sev") ? IssueSeverity.of(tag.getByte("sev")) : type.severity;
        var stage = tag.contains("st") ? IssueStage.of(tag.getByte("st")) : type.stage;
        long a = 0, b = 0;
        var values = tag.getLongArray("v");
        if (values.length >= 2) {
            a = values[0];
            b = values[1];
        }
        var subject = tag.contains("sub") ? IssueSubject.readNbt(tag.getCompound("sub")) : IssueSubject.NONE;
        GTRecipeDefinition recipe = null;
        if (tag.contains("r")) {
            var recipeId = ResourceLocation.tryParse(tag.getString("r"));
            if (recipeId != null) recipe = GTRecipeDefinition.RECIPES.get(recipeId);
        }
        if (custom == null && recipe == null && subject.isNone() && a == 0 && b == 0 && severity == type.severity && stage == type.stage) return type.bare();
        return new MachineIssue(type, severity, stage, subject, a, b, recipe, custom);
    }

    @Nullable
    private static Component parseText(String json) {
        try {
            return Component.Serializer.fromJson(json);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Supplier<Component> constant(Component text) {
        return () -> text;
    }
}
