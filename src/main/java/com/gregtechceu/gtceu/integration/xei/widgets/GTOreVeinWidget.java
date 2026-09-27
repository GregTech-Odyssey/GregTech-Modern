package com.gregtechceu.gtceu.integration.xei.widgets;

import com.gregtechceu.gtceu.api.data.DimensionMarker;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockore.BedrockOreDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.integration.xei.widgets.GTRecipeWidget.PageFrame;
import com.gregtechceu.gtceu.uipro.ILocalUI;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Label;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uiwidgets.recipe.RecipeSpecPanel;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.jei.IngredientIO;
import com.lowdragmc.lowdraglib.side.fluid.forge.FluidHelperImpl;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fluids.FluidStack;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexWrap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class GTOreVeinWidget extends UIElement implements ILocalUI {

    private static final String SHARE = "gtceu.jei.vein.share";
    private static final String ANY_DIMENSION = "gtceu.jei.vein.any_dimension";
    private static final String PREVIEW = "gtceu.jei.vein.preview";
    private static final String PREVIEW_SCHEMATIC = "gtceu.jei.vein.preview_schematic";
    private static final String PREVIEW_SCALE = "gtceu.jei.vein.preview_scale";

    public static final int ICON = 16;
    private static final int TILE_INNER = 50;
    private static final int TILE = TILE_INNER + 2 * UITheme.PANEL_PADDING;
    private static final int HEADER_GAP = 2;
    private static final int CELL_GAP = 3;
    private static final int BAR_HEIGHT = 6;
    private static final int BAR_MIN_WIDTH = 12;
    private static final int CELL_W = 4;
    private static final int CELL_D = 2;
    private static final int CELL_H = 2;

    private static final int SCREEN_TOP = 0xFF454B52;
    private static final int SCREEN_BOTTOM = 0xFF24272B;
    private static final int SCREEN_FLOOR = 0x14FFFFFF;
    private static final int SCREEN_GRID = 0x10FFFFFF;
    private static final int HOST_COLOR = 0x5C6167;
    private static final int OUTLINE = 0xFF373737;
    private static final int SEGMENT_SEPARATOR = 0x60000000;
    private static final int SEGMENT_HIGHLIGHT = 0x40FFFFFF;
    private static final int SEGMENT_SHADE = 0x30000000;
    private static final int CAPTION_COLOR = 0xFFD8D8D8;
    private static final int BEDROCK_HEIGHT = 8;
    private static final int ROCK_TINT = 0xFFE6E6E6;
    private static final int POOL_SHADE = 0x70000000;
    private static final int POOL_SURFACE = 0x60FFFFFF;
    private static final int PIPE_WIDTH = 8;
    private static final int PIPE_FILL = 0xFFA8A8A8;
    private static final int PIPE_HIGHLIGHT = 0xFFE8E8E8;
    private static final int PIPE_SHADE = 0xFF6E6E6E;
    private static final int WELL_RING = 0xFFD6D6D6;
    private static final ResourceLocation BEDROCK = new ResourceLocation("block/bedrock");

    public record DisplaySlot(Widget hole, int size, ItemStack item, FluidStack fluid, IngredientIO io,
                              List<Component> tooltip, @Nullable String overlay) {}

    private final VeinInfo info;
    private final PageFrame frame;
    private final List<DisplaySlot> displaySlots = new ArrayList<>();

    public GTOreVeinWidget(VeinInfo info, PageFrame frame) {
        this.info = info;
        this.frame = frame;
        int width = frame.minWidth() + (frame.minWidth() & 1);
        layout(l -> l.column().width(width).minHeight(frame.fillHeight()));
        setClientSideWidget();
        boolean strip = !info.entries().isEmpty();
        addChild(new Header(info, width));
        var top = new UIElement().layout(l -> l.row().gapAll(UISizes.SECTION_GAP).marginTop(HEADER_GAP));
        top.addChild(createTile());
        var side = new UIElement().layout(l -> l.column().flexGrow(1).flexShrink(1).minWidth(0).gapAll(UISizes.GAP));
        side.addChild(createSpecs());
        var dimensions = createDimensions();
        if (!strip && frame.sideButtons() > 0) dimensions.addChild(UIElement.spacer(PageFrame.NOTCH_WIDTH, 0));
        side.addChild(dimensions);
        top.addChild(side);
        addChild(top);
        if (strip) {
            var bottom = new UIElement().layout(l -> l.row().gapAll(UISizes.SECTION_GAP).marginTop(UISizes.SECTION_GAP)
                    .minHeight(frame.notchHeight()));
            bottom.addChild(createComposition());
            if (frame.sideButtons() > 0) bottom.addChild(UIElement.spacer(PageFrame.NOTCH_WIDTH, 0));
            addChild(bottom);
        }
    }

    public List<DisplaySlot> getDisplaySlots() {
        return displaySlots;
    }

    private Widget createTile() {
        var tile = new UIElement().layout(l -> l.size(TILE, TILE).paddingAll(UITheme.PANEL_PADDING).flexShrink(0));
        tile.setBackground(UITheme.PANEL);
        if (!info.fluid().isEmpty()) {
            var hole = new ImageWidget(0, 0, UISizes.SLOT, UISizes.SLOT, UITheme.ITEM_SLOT);
            displaySlots.add(new DisplaySlot(hole, UISizes.SLOT, ItemStack.EMPTY, info.fluid(), IngredientIO.OUTPUT, List.of(), null));
            var reservoir = new Reservoir(info.fluid());
            reservoir.layout(l -> l.column().alignItems(AlignItems.CENTER).justifyContent(AlignContent.CENTER).paddingTop(BEDROCK_HEIGHT));
            reservoir.addChild(hole);
            tile.addChild(reservoir);
        } else if (info.preview() != null) {
            tile.addChild(new PreviewScreen(info));
        }
        return tile;
    }

    private Widget createSpecs() {
        var specs = new RecipeSpecPanel();
        for (var spec : info.specs()) {
            var value = spec.value();
            specs.value(Component.translatable(spec.labelKey()), () -> value);
        }
        return specs;
    }

    private UIElement createDimensions() {
        var dimensions = info.dimensions();
        var strip = new UIElement().layout(l -> l.row().flexWrap(FlexWrap.WRAP).paddingLeft(1).alignItems(AlignItems.CENTER));
        if (dimensions == null || dimensions.isEmpty()) {
            strip.addChild(Label.translatable(ANY_DIMENSION, 4 * ICON));
            return strip;
        }
        boolean showTier = ConfigHolder.INSTANCE.compat.showDimensionTier;
        for (var marker : dimensions) {
            var hole = new UIElement().layout(l -> l.size(ICON, ICON));
            String tier = showTier ? "T" + (marker.tier >= DimensionMarker.MAX_TIER ? "?" : marker.tier) : null;
            displaySlots.add(new DisplaySlot(hole, ICON, marker.getIcon(), FluidStack.EMPTY, IngredientIO.CATALYST, List.of(), tier));
            strip.addChild(hole);
        }
        strip.addChild(UIElement.flexSpacer());
        return strip;
    }

    private Widget createComposition() {
        var panel = new UIElement().layout(l -> l.row().flexGrow(1).flexShrink(1).minWidth(0).paddingAll(UITheme.PANEL_PADDING)
                .gapAll(CELL_GAP).alignItems(AlignItems.CENTER));
        panel.setBackground(UITheme.PANEL);
        int total = Math.max(1, info.totalWeight());
        for (var entry : info.entries()) {
            double share = entry.weight() * 100.0 / total;
            var hole = new ImageWidget(0, 0, UISizes.SLOT, UISizes.SLOT, UITheme.ITEM_SLOT);
            displaySlots.add(new DisplaySlot(hole, UISizes.SLOT, entry.stack(), FluidStack.EMPTY, IngredientIO.OUTPUT,
                    List.of(Component.translatable(SHARE, FormattingUtil.formatNumber2Places(share) + "%")), null));
            var cell = new UIElement().layout(l -> l.row().gapAll(1).alignItems(AlignItems.CENTER).flexShrink(0));
            cell.addChild(hole);
            cell.addChild(new ShareText(shareText(share), entry.color()));
            panel.addChild(cell);
        }
        panel.addChild(new CompositionBar(info.entries()));
        return panel;
    }

    private static String shareText(double share) {
        if (share > 0 && share < 1) return "<1%";
        return Math.round(share) + "%";
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        GTRecipeWidget.drawPageCard(graphics, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight(), frame);
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
    }

    private static int shade(int rgb, float factor) {
        int r = Math.min(255, Math.round((rgb >> 16 & 0xFF) * factor));
        int g = Math.min(255, Math.round((rgb >> 8 & 0xFF) * factor));
        int b = Math.min(255, Math.round((rgb & 0xFF) * factor));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static final class Header extends UIElement {

        private final Component name;
        private final Component weight;
        private boolean truncated;

        private Header(VeinInfo info, int width) {
            this.name = info.name();
            this.weight = info.weightText();
            layout(l -> l.size(width, UISizes.TEXT_HEIGHT));
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            var font = Minecraft.getInstance().font;
            int x = getPositionX() + 1, y = getPositionY() + 1, width = getSizeWidth() - 2;
            String weightText = weight.getString();
            int weightWidth = font.width(weightText);
            graphics.drawString(font, weightText, x + width - weightWidth, y, UITheme.TEXT_SECONDARY, false);
            int nameSpace = width - weightWidth - UISizes.TEXT_PADDING;
            String nameText = name.getString();
            truncated = font.width(nameText) > nameSpace;
            graphics.drawString(font, UITheme.clip(font, nameText, nameSpace), x, y, UITheme.TEXT, false);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
            if (!truncated || gui == null || gui.getModularUIGui() == null || !isMouseOverElement(mouseX, mouseY)) return;
            gui.getModularUIGui().setHoverTooltip(List.of(name), ItemStack.EMPTY, null, null);
        }
    }

    private static final class PreviewScreen extends UIElement {

        private final VeinPreview preview;
        private final int[] palette;
        private final List<Component> tooltip;

        private PreviewScreen(VeinInfo info) {
            this.preview = info.preview();
            this.palette = new int[info.entries().size()];
            for (int i = 0; i < palette.length; i++) palette[i] = info.entries().get(i).color();
            int horizontal = VeinPreview.SIZE * VeinPreview.BLOCKS_PER_VOXEL;
            int vertical = VeinPreview.HEIGHT * VeinPreview.BLOCKS_PER_VOXEL;
            this.tooltip = List.of(Component.translatable(preview.isSchematic() ? PREVIEW_SCHEMATIC : PREVIEW),
                    Component.translatable(PREVIEW_SCALE, horizontal, vertical, horizontal).withStyle(ChatFormatting.GRAY));
            layout(l -> l.size(TILE_INNER, TILE_INNER));
        }

        private int color(byte value) {
            int index = VeinPreview.entryIndex(value);
            return index >= 0 && index < palette.length ? palette[index] : HOST_COLOR;
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            int x0 = getPositionX(), y0 = getPositionY();
            graphics.fillGradient(x0, y0, x0 + TILE_INNER, y0 + TILE_INNER, SCREEN_TOP, SCREEN_BOTTOM);
            int size = VeinPreview.SIZE, height = VeinPreview.HEIGHT;
            int ox = x0 + (TILE_INNER - size * CELL_W) / 2;
            int base = y0 + (TILE_INNER - size * CELL_D - height * CELL_H) / 2 + height * CELL_H;
            graphics.fill(ox, base, ox + size * CELL_W, base + size * CELL_D, SCREEN_FLOOR);
            for (int i = 0; i <= size; i += 4) {
                graphics.fill(ox + i * CELL_W - (i == size ? 1 : 0), base, ox + i * CELL_W + (i == size ? 0 : 1), base + size * CELL_D, SCREEN_GRID);
                graphics.fill(ox, base + i * CELL_D - (i == size ? 1 : 0), ox + size * CELL_W, base + i * CELL_D + (i == size ? 0 : 1), SCREEN_GRID);
            }
            for (int z = 0; z < size; z++) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < size; x++) {
                        byte value = preview.get(x, y, z);
                        if (value == VeinPreview.EMPTY) continue;
                        boolean coveredTop = preview.get(x, y + 1, z) != VeinPreview.EMPTY;
                        boolean coveredFront = preview.get(x, y, z + 1) != VeinPreview.EMPTY;
                        if (coveredTop && coveredFront) continue;
                        int rgb = color(value);
                        int left = ox + x * CELL_W;
                        int top = base + z * CELL_D - (y + 1) * CELL_H;
                        float light = 0.86f + 0.3f * y / Math.max(1, height - 1);
                        graphics.fill(left, top, left + CELL_W, top + CELL_D, shade(rgb, light));
                        graphics.fill(left, top, left + CELL_W, top + 1, shade(rgb, light + 0.22f));
                        graphics.fill(left, top + CELL_D, left + CELL_W, top + CELL_D + CELL_H, shade(rgb, light * 0.66f));
                        if (preview.get(x - 1, y, z) == VeinPreview.EMPTY) {
                            graphics.fill(left, top, left + 1, top + CELL_D + CELL_H, shade(rgb, light * 0.5f));
                        }
                        if (preview.get(x + 1, y, z) == VeinPreview.EMPTY) {
                            graphics.fill(left + CELL_W - 1, top + CELL_D, left + CELL_W, top + CELL_D + CELL_H, shade(rgb, light * 0.45f));
                        }
                    }
                }
            }
            var caption = preview.caption();
            if (caption != null) {
                var font = Minecraft.getInstance().font;
                float scale = UISizes.SMALL_TEXT_SCALE;
                var pose = graphics.pose();
                pose.pushPose();
                pose.translate(x0 + 2, y0 + TILE_INNER - 2 - UISizes.SMALL_TEXT_HEIGHT, 0);
                pose.scale(scale, scale, 1);
                graphics.drawString(font, caption, 0, 0, CAPTION_COLOR, true);
                pose.popPose();
            }
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
            if (gui == null || gui.getModularUIGui() == null || !isMouseOverElement(mouseX, mouseY)) return;
            gui.getModularUIGui().setHoverTooltip(tooltip, ItemStack.EMPTY, null, null);
        }
    }

    private static final class ShareText extends UIElement {

        private final String text;
        private final int color;

        private ShareText(String text, int rgb) {
            this.text = text;
            this.color = 0xFF000000 | UITheme.lightBackgroundColor(rgb);
            int width = (int) Math.ceil(Minecraft.getInstance().font.width(text) * UISizes.SMALL_TEXT_SCALE);
            layout(l -> l.size(width, UISizes.SMALL_TEXT_HEIGHT));
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            var font = Minecraft.getInstance().font;
            float scale = UISizes.SMALL_TEXT_SCALE;
            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(getPositionX(), getPositionY(), 0);
            pose.scale(scale, scale, 1);
            graphics.drawString(font, text, 0, 0, color, false);
            pose.popPose();
        }
    }

    private static final class CompositionBar extends UIElement {

        private final List<VeinInfo.Entry> entries;
        private final int total;

        private CompositionBar(List<VeinInfo.Entry> entries) {
            this.entries = entries;
            int sum = 0;
            for (var entry : entries) sum += entry.weight();
            this.total = Math.max(1, sum);
            layout(l -> l.flexGrow(1).flexShrink(1).minWidth(0).height(BAR_HEIGHT));
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            if (getSizeWidth() < BAR_MIN_WIDTH) return;
            UITheme.PROGRESS_TRACK.draw(graphics, mouseX, mouseY, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
            int x0 = getPositionX() + 1, y0 = getPositionY() + 1;
            int inner = getSizeWidth() - 2, height = getSizeHeight() - 2;
            int accumulated = 0, left = x0;
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.get(i);
                accumulated += entry.weight();
                int right = i == entries.size() - 1 ? x0 + inner : x0 + (int) Math.round((double) inner * accumulated / total);
                if (right <= left) continue;
                graphics.fill(left, y0, right, y0 + height, 0xFF000000 | entry.color());
                graphics.fill(left, y0, right, y0 + 1, SEGMENT_HIGHLIGHT);
                graphics.fill(left, y0 + height - 1, right, y0 + height, SEGMENT_SHADE);
                if (i > 0) graphics.fill(left, y0, left + 1, y0 + height, SEGMENT_SEPARATOR);
                left = right;
            }
        }
    }

    private static final class Reservoir extends UIElement {

        private final FluidStack fluid;

        private Reservoir(FluidStack fluid) {
            this.fluid = fluid;
            layout(l -> l.size(TILE_INNER, TILE_INNER));
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            int x = getPositionX(), y = getPositionY(), width = getSizeWidth(), height = getSizeHeight();
            graphics.fill(x, y, x + width, y + height, OUTLINE);
            drawTiled(graphics, sprite(BEDROCK), x + 1, y + 1, width - 2, BEDROCK_HEIGHT - 1, ROCK_TINT);
            DrawerHelper.drawFluidForGui(graphics, FluidHelperImpl.toFluidStack(fluid), x + 1, y + BEDROCK_HEIGHT, width - 2, height - BEDROCK_HEIGHT - 1);
            graphics.fill(x + 1, y + BEDROCK_HEIGHT, x + width - 1, y + height - 1, POOL_SHADE);
            graphics.fill(x + 1, y + BEDROCK_HEIGHT, x + width - 1, y + BEDROCK_HEIGHT + 1, POOL_SURFACE);
            int slotX = x + (width - UISizes.SLOT) / 2;
            int slotY = y + BEDROCK_HEIGHT + (height - BEDROCK_HEIGHT - UISizes.SLOT) / 2;
            int pipeX = x + (width - PIPE_WIDTH) / 2;
            graphics.fill(pipeX, y, pipeX + PIPE_WIDTH, slotY, OUTLINE);
            graphics.fill(pipeX + 1, y, pipeX + PIPE_WIDTH - 1, slotY, PIPE_FILL);
            graphics.fill(pipeX + 1, y, pipeX + 3, slotY, PIPE_HIGHLIGHT);
            graphics.fill(pipeX + PIPE_WIDTH - 3, y, pipeX + PIPE_WIDTH - 1, slotY, PIPE_SHADE);
            graphics.fill(slotX - 2, slotY - 2, slotX + UISizes.SLOT + 2, slotY + UISizes.SLOT + 2, OUTLINE);
            graphics.fill(slotX - 1, slotY - 1, slotX + UISizes.SLOT + 1, slotY + UISizes.SLOT + 1, WELL_RING);
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static TextureAtlasSprite sprite(ResourceLocation texture) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
    }

    @OnlyIn(Dist.CLIENT)
    private static void drawTiled(GuiGraphics graphics, TextureAtlasSprite sprite, int x, int y, int width, int height, int color) {
        RenderSystem.enableBlend();
        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        for (int dx = 0; dx < width; dx += 16) {
            for (int dy = 0; dy < height; dy += 16) {
                int w = Math.min(16, width - dx), h = Math.min(16, height - dy);
                DrawerHelper.drawFluidTexture(graphics, x + dx, y + dy - (16 - h), sprite, 16 - h, 16 - w, 0, color);
            }
        }
    }

    public static List<ItemStack> getContainedOresAndBlocks(GTOreDefinition oreDefinition) {
        return oreDefinition.veinGenerator().getAllEntries().stream().flatMap(entry -> entry.map(state -> Stream.of(state.getBlock().asItem().getDefaultInstance()), material -> {
            Set<ItemStack> ores = new ReferenceOpenHashSet<>();
            ores.add(ChemicalHelper.get(TagPrefix.rawOre, material));
            for (TagPrefix prefix : TagPrefix.ORES.keySet()) {
                ores.add(ChemicalHelper.get(prefix, material));
            }
            return ores.stream();
        })).toList();
    }

    public static String getOreName(GTOreDefinition oreDefinition) {
        ResourceLocation id = GTRegistries.ORE_VEINS.getKey(oreDefinition);
        String path = id.getPath();
        return path.startsWith("ores/") ? path.substring("ores/".length()) : path;
    }

    public static String getFluidName(BedrockFluidDefinition fluid) {
        ResourceLocation id = GTRegistries.BEDROCK_FLUID_DEFINITIONS.getKey(fluid);
        return id.getPath();
    }

    public static String getBedrockOreName(BedrockOreDefinition oreDefinition) {
        ResourceLocation id = GTRegistries.BEDROCK_ORE_DEFINITIONS.getKey(oreDefinition);
        return id.getPath();
    }
}
