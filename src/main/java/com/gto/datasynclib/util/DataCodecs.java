package com.gto.datasynclib.util;

import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.gto.datasynclib.datasream.codec.DataCodec;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.DataOps;
import com.gto.datasynclib.datasream.data.MapData;
import com.gto.datasynclib.datasream.data.StringData;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DataCodecs {

    public final DataCodec<ResourceLocation> RESOURCE_LOCATION_CODEC = DataCodec.of(obj -> StringData.valueOf(obj.toString()), data -> GTUtil.getResourceLocation(data.getString()));

    static {
        DataCodec.registerCodec(ResourceLocation.class, RESOURCE_LOCATION_CODEC);
    }

    public static final DataCodec<BlockPos> BLOCK_POS_CODEC = new DataCodec<>() {

        @Override
        public BlockPos decode(Data data) {
            var map = (MapData) data;
            return new BlockPos(map.getInt("X"), map.getInt("Y"), map.getInt("Z"));
        }

        @Override
        public Data encode(BlockPos obj) {
            var mapData = new MapData();
            mapData.putInt("X", obj.getX());
            mapData.putInt("Y", obj.getY());
            mapData.putInt("Z", obj.getZ());
            return mapData;
        }

        static {
            DataCodec.registerCodec(BlockPos.class, BLOCK_POS_CODEC);
        }
    };

    public static final DataCodec<CompoundTag> COMPOUND_TAG_CODEC = new DataCodec<>() {

        @Override
        public CompoundTag decode(Data data) {
            return (CompoundTag) DataOps.INSTANCE.convertTo(NbtOps.INSTANCE, data);
        }

        @Override
        public Data encode(CompoundTag obj) {
            return NbtOps.INSTANCE.convertTo(DataOps.INSTANCE, obj);
        }

        static {
            DataCodec.registerCodec(CompoundTag.class, COMPOUND_TAG_CODEC);
        }
    };

    public static final DataCodec<ItemStack> ITEM_STACK_CODEC = new DataCodec<>() {

        @Override
        public ItemStack decode(Data data) {
            return ItemStack.of(COMPOUND_TAG_CODEC.decode(data));
        }

        @Override
        public Data encode(ItemStack obj) {
            return COMPOUND_TAG_CODEC.encode(obj.save(new CompoundTag()));
        }

        static {
            DataCodec.registerCodec(ItemStack.class, ITEM_STACK_CODEC);
        }
    };

    public static final DataCodec<FluidStack> FLUID_STACK_CODEC = new DataCodec<>() {

        @Override
        public FluidStack decode(Data data) {
            return FluidStack.loadFluidStackFromNBT(COMPOUND_TAG_CODEC.decode(data));
        }

        @Override
        public Data encode(FluidStack obj) {
            return COMPOUND_TAG_CODEC.encode(obj.writeToNBT(new CompoundTag()));
        }

        static {
            DataCodec.registerCodec(FluidStack.class, FLUID_STACK_CODEC);
        }
    };

    public static <T> DataCodec<T> of(Registry<T> registry) {
        return DataCodec.of(obj -> RESOURCE_LOCATION_CODEC.encode(registry.getKey(obj)), data -> registry.get(RESOURCE_LOCATION_CODEC.decode(data)));
    }
}
