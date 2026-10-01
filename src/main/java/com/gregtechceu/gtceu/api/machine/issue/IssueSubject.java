package com.gregtechceu.gtceu.api.machine.issue;

import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.info.RecipeInfo;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import org.jetbrains.annotations.Nullable;

public record IssueSubject(IO io, @Nullable RecipeInfo capability, int index) {

    public static final IssueSubject NONE = new IssueSubject(IO.NONE, null, -1);

    private static final IO[] IOS = IO.values();

    public static IssueSubject of(IO io, @Nullable RecipeInfo capability, int index) {
        if (io == IO.NONE && capability == null && index < 0) return NONE;
        return new IssueSubject(io, capability, index);
    }

    public boolean isNone() {
        return this == NONE || (io == IO.NONE && capability == null && index < 0);
    }

    public boolean matches(IO io, @Nullable RecipeInfo capability, int index) {
        return this.io == io && this.capability == capability && this.index == index;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeByte(io.ordinal());
        buf.writeUtf(capability == null ? "" : capability.name);
        buf.writeVarInt(index + 1);
    }

    public static IssueSubject read(FriendlyByteBuf buf) {
        var io = ioOf(buf.readByte());
        var capability = capabilityOf(buf.readUtf());
        int index = buf.readVarInt() - 1;
        return of(io, capability, index);
    }

    public void writeNbt(CompoundTag tag) {
        if (io != IO.NONE) tag.putByte("io", (byte) io.ordinal());
        if (capability != null) tag.putString("cap", capability.name);
        if (index >= 0) tag.putInt("idx", index);
    }

    public static IssueSubject readNbt(CompoundTag tag) {
        var io = tag.contains("io") ? ioOf(tag.getByte("io")) : IO.NONE;
        var capability = tag.contains("cap") ? capabilityOf(tag.getString("cap")) : null;
        int index = tag.contains("idx") ? tag.getInt("idx") : -1;
        return of(io, capability, index);
    }

    private static IO ioOf(int ordinal) {
        return ordinal >= 0 && ordinal < IOS.length ? IOS[ordinal] : IO.NONE;
    }

    @Nullable
    private static RecipeInfo capabilityOf(String name) {
        return name.isEmpty() ? null : GTRegistries.RECIPE_INFOS.get(name);
    }
}
