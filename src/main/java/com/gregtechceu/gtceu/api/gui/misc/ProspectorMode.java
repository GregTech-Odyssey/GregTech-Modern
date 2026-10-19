package com.gregtechceu.gtceu.api.gui.misc;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.data.tag.TagUtil;
import com.gregtechceu.gtceu.api.data.worldgen.bedrock.BedrockVeinEntry;
import com.gregtechceu.gtceu.api.data.worldgen.bedrock.BedrockVeinKind;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockore.BedrockOreDefinition;
import com.gregtechceu.gtceu.api.gui.texture.ProspectingTexture;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib.side.fluid.forge.FluidHelperImpl;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fluids.FluidStack;

import com.gto.datasynclib.datastream.codec.ByteBufCodecs;
import com.gto.datasynclib.datastream.codec.StreamCodec;
import com.gto.datasynclib.util.ByteBufCodecExtends;
import com.gto.fastcollection.fastutil.O2IOpenCacheHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import me.kall.duplicationless.ext.RegistryEntry;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public abstract class ProspectorMode<T> {

    public static ProspectorMode<String> ORE = new ProspectorMode<>("metaitem.prospector.mode.ores", 16) {

        private final Map<BlockState, String> BLOCK_CACHE = new Reference2ObjectOpenHashMap<>();
        private final Map<String, IGuiTexture> ICON_CACHE = new HashMap<>();

        @Override
        public void scan(String[][][] storage, LevelChunk chunk) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            var oreTag = TagUtil.createBlockTag("ores");
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = chunk.getMaxBuildHeight() - 1; y >= chunk.getMinBuildHeight(); y--) {
                        pos.set(x, y, z);
                        var state = chunk.getBlockState(pos);
                        if (state.is(oreTag)) {
                            var itemName = BLOCK_CACHE.computeIfAbsent(state, blockState -> {
                                var name = RegistryEntry.get(blockState.getBlock()).toString();
                                var entry = ChemicalHelper.getMaterialEntry(blockState.getBlock());
                                if (!entry.isEmpty()) {
                                    name = "material_" + entry.material();
                                }
                                return name;
                            });
                            storage[x][z] = ArrayUtils.add(storage[x][z], itemName);
                        }
                    }
                }
            }
        }

        @Override
        public int getItemColor(String item) {
            if (item.startsWith("material_")) {
                var mat = GTMaterials.get(item.substring(9));
                if (!mat.isNull()) {
                    return mat.getMaterialRGB();
                }
            }
            return BuiltInRegistries.BLOCK.get(GTUtil.getResourceLocation(item)).defaultMapColor().col;
        }

        @Override
        public IGuiTexture getItemIcon(String item) {
            return ICON_CACHE.computeIfAbsent(item, name -> {
                if (name.startsWith("material_")) {
                    var mat = GTMaterials.get(name.substring(9));
                    if (!mat.isNull()) {
                        var list = new ArrayList<ItemStack>();
                        for (TagPrefix oreTag : TagPrefix.ORES.keySet()) {
                            for (var block : ChemicalHelper.getBlocks(oreTag, mat)) {
                                list.add(new ItemStack(block));
                            }
                        }
                        return new ItemStackTexture(list.toArray(ItemStack[]::new)).scale(0.8F);
                    }
                }
                return new ItemStackTexture(new ItemStack(BuiltInRegistries.BLOCK.get(GTUtil.getResourceLocation(name)))).scale(0.8F);
            });
        }

        @Override
        public String getDescriptionId(String item) {
            if (item.startsWith("material_")) {
                var mat = GTMaterials.get(item.substring(9));
                if (!mat.isNull()) {
                    return mat.getUnlocalizedName();
                }
            }
            return BuiltInRegistries.BLOCK.get(GTUtil.getResourceLocation(item)).getDescriptionId();
        }

        @Override
        public String getUniqueID(String item) {
            return item;
        }

        @Override
        public void serialize(String item, FriendlyByteBuf buf) {
            buf.writeUtf(item);
        }

        @Override
        public String deserialize(FriendlyByteBuf buf) {
            return buf.readUtf();
        }

        @Override
        public Class<String> getItemClass() {
            return String.class;
        }

        @Override
        public void appendTooltips(List<String[]> items, List<Component> tooltips, String selected) {
            O2IOpenCacheHashMap<String> counter = new O2IOpenCacheHashMap<>();
            for (var array : items) {
                for (String item : array) {
                    if (ProspectingTexture.SELECTED_ALL.equals(selected) || selected.equals(getUniqueID(item))) {
                        counter.addTo(item, 1);
                    }
                }
            }
            counter.forEach((item, count) -> tooltips.add(Component.translatable(getDescriptionId(item)).append(" --- " + count)));
        }
    };

    public static final class FluidInfo {

        public static final StreamCodec<FriendlyByteBuf, FluidInfo> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecExtends.of(BuiltInRegistries.FLUID), FluidInfo::fluid,
                ByteBufCodecs.VAR_INT, FluidInfo::yield,
                ByteBufCodecs.VAR_INT, FluidInfo::left,
                FluidInfo::new);

        private final Fluid fluid;
        private final int yield;
        private int left;

        public static FluidInfo fromNbt(CompoundTag tag) {
            Fluid fluid = BuiltInRegistries.FLUID.get(GTUtil.getResourceLocation(tag.getString("fluid")));
            int left = tag.getInt("left");
            int yield = tag.getInt("yield");
            return new FluidInfo(fluid, yield, left);
        }

        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putString("fluid", GTUtil.FLUID_ID.apply(fluid).toString());
            tag.putInt("left", left);
            tag.putInt("yield", yield);
            return tag;
        }

        public static FluidInfo fromVeinWorldEntry(@NotNull BedrockVeinEntry<BedrockFluidDefinition> entry) {
            var definition = entry.definition();
            if (definition == null) {
                return null;
            }
            return new FluidInfo(definition.fluid(), entry.yield(), entry.remainingPercent());
        }

        public FluidInfo(final Fluid fluid, final int yield, final int left) {
            this.fluid = fluid;
            this.yield = yield;
            this.left = left;
        }

        public Fluid fluid() {
            return this.fluid;
        }

        public int yield() {
            return this.yield;
        }

        public int left() {
            return this.left;
        }

        /**
         * @return {@code this}.
         */
        public ProspectorMode.FluidInfo left(final int left) {
            this.left = left;
            return this;
        }
    }

    public static ProspectorMode<FluidInfo> FLUID = new ProspectorMode<>("metaitem.prospector.mode.fluid", 1) {

        @Override
        public void scan(FluidInfo[][][] storage, LevelChunk chunk) {
            if (chunk.getLevel() instanceof ServerLevel serverLevel) {
                var fluidVein = BedrockVeinKind.FLUID.data(serverLevel).entry(chunk.getPos().x, chunk.getPos().z);
                if (fluidVein.definition() != null) {
                    storage[0][0] = new FluidInfo[] { FluidInfo.fromVeinWorldEntry(fluidVein) };
                }
            }
        }

        @Override
        public int getItemColor(FluidInfo item) {
            var fluidStack = new FluidStack(item.fluid, item.yield);
            if (fluidStack.getFluid() == Fluids.LAVA) {
                return -36864;
            }
            return GTUtil.getFluidColor(fluidStack);
        }

        @Override
        public IGuiTexture getItemIcon(FluidInfo item) {
            return new ItemStackTexture(item.fluid.getBucket());
        }

        @Override
        public String getDescriptionId(FluidInfo item) {
            return new FluidStack(item.fluid, item.yield).getDisplayName().getString();
        }

        @Override
        public String getUniqueID(FluidInfo item) {
            return GTUtil.FLUID_ID.apply(item.fluid).toString();
        }

        @Override
        public void serialize(FluidInfo item, FriendlyByteBuf buf) {
            FluidInfo.STREAM_CODEC.encode(buf, item);
        }

        @Override
        public FluidInfo deserialize(FriendlyByteBuf buf) {
            return FluidInfo.STREAM_CODEC.decode(buf);
        }

        @Override
        public Class<FluidInfo> getItemClass() {
            return FluidInfo.class;
        }

        @Override
        public void appendTooltips(List<FluidInfo[]> items, List<Component> tooltips, String selected) {
            for (var array : items) {
                for (FluidInfo item : array) {
                    tooltips.add(Component.translatable(getDescriptionId(item)).append(" --- %s (%s%%)".formatted(item.yield, item.left)));
                }
            }
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawSpecialGrid(GuiGraphics graphics, FluidInfo[] items, int x, int y, int width, int height) {
            if (items.length > 0) {
                var item = items[0];
                double progress = item.left * 1.0 / Math.max(Math.min(item.left, 100), 1);
                float drawnU = (float) ProgressTexture.FillDirection.DOWN_TO_UP.getDrawnU(progress);
                float drawnV = (float) ProgressTexture.FillDirection.DOWN_TO_UP.getDrawnV(progress);
                float drawnWidth = (float) ProgressTexture.FillDirection.DOWN_TO_UP.getDrawnWidth(progress);
                float drawnHeight = (float) ProgressTexture.FillDirection.DOWN_TO_UP.getDrawnHeight(progress);
                DrawerHelper.drawFluidForGui(graphics, FluidHelperImpl.toFluidStack(new FluidStack(item.fluid(), item.left)), 100, (int) (x + drawnU * width), (int) (y + drawnV * height), ((int) (width * drawnWidth)), ((int) (height * drawnHeight)));
            }
        }
    };

    public record OreInfo(Material material, int weight, int left, int yield) {

        public static final StreamCodec<FriendlyByteBuf, OreInfo> STREAM_CODEC = StreamCodec.composite(
                Material.STREAM_CODEC, OreInfo::material,
                ByteBufCodecs.VAR_INT, OreInfo::weight,
                ByteBufCodecs.VAR_INT, OreInfo::left,
                ByteBufCodecs.VAR_INT, OreInfo::yield,
                OreInfo::new);
    }

    public static ProspectorMode<OreInfo> BEDROCK_ORE = new ProspectorMode<>("metaitem.prospector.mode.bedrock_ore", 1) {

        @Override
        public void scan(OreInfo[][][] storage, LevelChunk chunk) {
            if (chunk.getLevel() instanceof ServerLevel serverLevel) {
                var oreVein = BedrockVeinKind.ORE.data(serverLevel).entry(chunk.getPos().x, chunk.getPos().z);
                var definition = oreVein.definition();
                if (definition != null) {
                    var left = oreVein.remainingPercent();
                    for (var entry : definition.materials()) {
                        storage[0][0] = ArrayUtils.add(storage[0][0], new OreInfo(entry.material(), entry.weight(), left, oreVein.yield()));
                    }
                }
            }
        }

        @Override
        public int getItemColor(OreInfo item) {
            return item.material.getMaterialRGB();
        }

        @Override
        public IGuiTexture getItemIcon(OreInfo item) {
            return new ItemStackTexture(BedrockOreDefinition.dropOf(item.material, 1)).scale(0.8F);
        }

        @Override
        public String getDescriptionId(OreInfo item) {
            return item.material.getUnlocalizedName();
        }

        @Override
        public String getUniqueID(OreInfo item) {
            return item.material.getName();
        }

        @Override
        public void serialize(OreInfo item, FriendlyByteBuf buf) {
            OreInfo.STREAM_CODEC.encode(buf, item);
        }

        @Override
        public OreInfo deserialize(FriendlyByteBuf buf) {
            return OreInfo.STREAM_CODEC.decode(buf);
        }

        @Override
        public Class<OreInfo> getItemClass() {
            return OreInfo.class;
        }

        @Override
        public void appendTooltips(List<OreInfo[]> items, List<Component> tooltips, String selected) {
            for (var array : items) {
                int totalWeight = Arrays.stream(array).mapToInt(OreInfo::weight).sum();
                for (OreInfo item : array) {
                    float chance = (float) item.weight / totalWeight * 100;
                    tooltips.add(Component.translatable(getDescriptionId(item)).append(" (").append(Component.translatable("gtceu.gui.content.chance_base", FormattingUtil.formatNumber2Places(chance))).append(") --- %s (%s%%)".formatted(item.yield, item.left)));
                }
            }
        }
    };
    public final String unlocalizedName;
    public final int cellSize;

    ProspectorMode(@NotNull String unlocalizedName, int cellSize) {
        this.unlocalizedName = unlocalizedName;
        this.cellSize = cellSize;
    }

    public abstract void scan(T[][][] storage, LevelChunk chunk);

    public abstract int getItemColor(T item);

    public abstract IGuiTexture getItemIcon(T item);

    public abstract String getDescriptionId(T item);

    public abstract String getUniqueID(T item);

    public abstract void serialize(T item, FriendlyByteBuf buf);

    public abstract T deserialize(FriendlyByteBuf buf);

    public abstract Class<T> getItemClass();

    public abstract void appendTooltips(List<T[]> items, List<Component> tooltips, String selected);

    @OnlyIn(Dist.CLIENT)
    public void drawSpecialGrid(GuiGraphics graphics, T[] items, int x, int y, int width, int height) {}
}
