package com.gto.datasynclib.util;

import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.gto.datasynclib.CombinationCodec;
import com.gto.datasynclib.datasream.codec.ByteStreamCodec;
import com.gto.datasynclib.datasream.stream.ByteBufWrapper;
import com.gto.datasynclib.datasream.stream.ByteDataStream;
import io.netty.buffer.ByteBuf;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

@UtilityClass
public class StreamCodecs {

    public final ByteStreamCodec<ResourceLocation> RESOURCE_LOCATION_CODEC = ByteStreamCodec.of((obj, stream) -> {
        stream.writeUTF(obj.getNamespace());
        stream.writeUTF(obj.getPath());
    }, stream -> GTUtil.getResourceLocation(stream.readUTF(), stream.readUTF()));

    public static final ByteStreamCodec<BlockPos> BLOCK_POS_CODEC = ByteStreamCodec.of((obj, stream) -> {
        stream.writeLong(obj.asLong());
    }, stream -> BlockPos.of(stream.readLong()));

    static {
        ByteStreamCodec.registerCodec(ResourceLocation.class, RESOURCE_LOCATION_CODEC);
        ByteStreamCodec.registerCodec(BlockPos.class, BLOCK_POS_CODEC);
    }

    public static final ByteStreamCodec<CompoundTag> COMPOUND_TAG_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(CompoundTag obj, ByteDataStream stream) throws IOException {
            obj.write(stream);
        }

        @Override
        public CompoundTag decode(ByteDataStream stream) throws IOException {
            return CompoundTag.TYPE.load(stream, 0, NbtAccounter.UNLIMITED);
        }

        static {
            ByteStreamCodec.registerCodec(CompoundTag.class, COMPOUND_TAG_CODEC);
        }
    };

    public static final ByteStreamCodec<ItemStack> ITEM_STACK_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(ItemStack obj, ByteDataStream stream) throws IOException {
            if (obj.isEmpty()) {
                stream.writeBoolean(false);
            } else {
                stream.writeBoolean(true);
                Item item = obj.getItem();
                CombinationCodec.ITEM_CODEC.streamWriter.encode(item, stream);
                stream.writeByte(obj.getCount());
                if (item.isDamageable(obj) || item.shouldOverrideMultiplayerNbt()) {
                    stream.writeBoolean(true);
                    COMPOUND_TAG_CODEC.encode(obj.getShareTag(), stream);
                } else {
                    stream.writeBoolean(false);
                }
            }
        }

        @Override
        public ItemStack decode(ByteDataStream stream) throws IOException {
            if (!stream.readBoolean()) {
                return ItemStack.EMPTY;
            } else {
                var item = CombinationCodec.ITEM_CODEC.streamReader.decode(stream);
                int i = stream.readByte();
                ItemStack itemstack = new ItemStack(item, i);
                if (stream.readBoolean()) itemstack.readShareTag(COMPOUND_TAG_CODEC.decode(stream));
                return itemstack;
            }
        }

        static {
            ByteStreamCodec.registerCodec(ItemStack.class, ITEM_STACK_CODEC);
        }
    };

    public static final ByteStreamCodec<FluidStack> FLUID_STACK_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(FluidStack obj, ByteDataStream stream) throws IOException {
            if (obj.isEmpty()) {
                stream.writeBoolean(false);
            } else {
                stream.writeBoolean(true);
                CombinationCodec.FLUID_CODEC.streamWriter.encode(obj.getFluid(), stream);
                stream.writeVarInt(obj.getAmount());
                if (obj.getTag() != null) {
                    stream.writeBoolean(true);
                    COMPOUND_TAG_CODEC.encode(obj.getTag(), stream);
                } else {
                    stream.writeBoolean(false);
                }
            }
        }

        @Override
        public FluidStack decode(ByteDataStream stream) throws IOException {
            if (!stream.readBoolean()) {
                return FluidStack.EMPTY;
            } else {
                var fluid = CombinationCodec.FLUID_CODEC.streamReader.decode(stream);
                int i = stream.readByte();
                var fluidStack = new FluidStack(fluid, i);
                if (stream.readBoolean()) fluidStack.setTag(COMPOUND_TAG_CODEC.decode(stream));
                return fluidStack;
            }
        }

        static {
            ByteStreamCodec.registerCodec(FluidStack.class, FLUID_STACK_CODEC);
        }
    };

    public static <T> ByteStreamCodec<T> of(Registry<T> registry) {
        return ByteStreamCodec.of((obj, stream) -> stream.writeVarInt(registry.getId(obj)), stream -> registry.byId(stream.readVarInt()));
    }

    public FriendlyByteBuf getByteBuf(ByteDataStream stream) {
        if (stream instanceof ByteBufWrapper(ByteBuf buf) && buf instanceof FriendlyByteBuf byteBuf) {
            return byteBuf;
        }
        return null;
    }

    public void writeNbt(@Nullable CompoundTag tag, ByteDataStream stream) throws IOException {
        if (tag == null) {
            stream.writeBoolean(false);
        } else {
            stream.writeBoolean(true);
            COMPOUND_TAG_CODEC.encode(tag, stream);
        }
    }

    public CompoundTag readNbt(ByteDataStream stream) throws IOException {
        if (stream.readBoolean()) return COMPOUND_TAG_CODEC.decode(stream);
        return null;
    }
}
