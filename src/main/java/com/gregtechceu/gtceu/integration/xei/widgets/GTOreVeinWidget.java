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
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class GTOreVeinWidget extends UIElement implements ILocalUI {

    private static final String SHARE = "gtceu.jei.vein.share";
    private static final String ANY_DIMENSION = "gtceu.jei.vein.any_dimension";

    private static final int ORE_GAP = 2;
    private static final int SHARE_HEIGHT = UISizes.SMALL_TEXT_HEIGHT;
    private static final int ROCK_HEIGHT = UISizes.SLOT + 2;
    private static final int BEDROCK_HEIGHT = 6;
    private static final int POOL_HEIGHT = 22;
    private static final int DIMENSION_ROWS = 2;
    private static final int DIMENSION_SINGLE_ROW = 3;
    private static final int ROCK_TINT = 0xFFE6E6E6;
    private static final int STRATUM_OUTLINE = 0xFF373737;
    private static final int POOL_SHADE = 0x70000000;
    private static final int POOL_SURFACE = 0x60FFFFFF;
    private static final int PIPE_WIDTH = 8;
    private static final int PIPE_FILL = 0xFFA8A8A8;
    private static final int PIPE_HIGHLIGHT = 0xFFE8E8E8;
    private static final int PIPE_SHADE = 0xFF6E6E6E;
    private static final int WELL_RING = 0xFFD6D6D6;
    private static final ResourceLocation BEDROCK = new ResourceLocation("block/bedrock");

    public record DisplaySlot(Widget hole, ItemStack item, FluidStack fluid, IngredientIO io, List<Component> tooltip,
                              @Nullable String overlay) {}

    private final VeinInfo info;
    private final PageFrame frame;
    private final List<DisplaySlot> displaySlots = new ArrayList<>();

    public GTOreVeinWidget(VeinInfo info, PageFrame frame) {
        this.info = info;
        this.frame = frame;
        int width = Math.max(frame.minWidth(), oreRowWidth(info.entries().size()) + 2 * UITheme.PANEL_PADDING);
        int pageWidth = width + (width & 1);
        layout(l -> l.column().width(pageWidth).minHeight(frame.fillHeight()).gapAll(UISizes.SECTION_GAP));
        setClientSideWidget();
        addChild(createStage(pageWidth));
        var lower = new UIElement().layout(l -> l.row().gapAll(UISizes.SECTION_GAP).minHeight(frame.notchHeight()));
        var specs = new RecipeSpecPanel();
        specs.layout(l -> l.flexGrow(1).flexShrink(1).minWidth(0));
        for (var spec : info.specs()) {
            var value = spec.value();
            specs.value(Component.translatable(spec.labelKey()), () -> value);
        }
        lower.addChild(specs);
        lower.addChild(createDimensions());
        if (frame.sideButtons() > 0) lower.addChild(UIElement.spacer(PageFrame.NOTCH_WIDTH, 0));
        addChild(lower);
    }

    public List<DisplaySlot> getDisplaySlots() {
        return displaySlots;
    }

    private static int orePerRow(int innerWidth) {
        return Math.max(1, (innerWidth + ORE_GAP) / (UISizes.SLOT + ORE_GAP));
    }

    private static int oreRowWidth(int count) {
        int shown = Math.min(count, orePerRow(UISizes.CONTENT_WIDTH - 2 * UITheme.PANEL_PADDING));
        return Math.max(0, shown * (UISizes.SLOT + ORE_GAP) - ORE_GAP);
    }

    private Widget createStage(int width) {
        int inner = width - 2 * UITheme.PANEL_PADDING;
        var stage = new UIElement().layout(l -> l.column().paddingAll(UITheme.PANEL_PADDING).gapAll(UISizes.GAP));
        stage.setBackground(UITheme.PANEL);
        stage.addChild(new Header(info, inner));
        if (!info.fluid().isEmpty()) {
            stage.addChild(createReservoir(inner));
        } else if (!info.entries().isEmpty()) {
            stage.addChild(createOres(inner));
        }
        return stage;
    }

    private Widget createOres(int inner) {
        var entries = info.entries();
        int total = Math.max(1, info.totalWeight());
        int perRow = orePerRow(inner - 2);
        var rows = new UIElement().layout(l -> l.column().gapAll(UISizes.GAP));
        for (int start = 0; start < entries.size(); start += perRow) {
            var band = new Stratum(info.rock(), inner, ROCK_HEIGHT);
            band.layout(l -> l.row().gapAll(ORE_GAP).alignItems(AlignItems.CENTER).justifyContent(AlignContent.CENTER));
            var labels = new UIElement().layout(l -> l.row().width(inner).gapAll(ORE_GAP).justifyContent(AlignContent.CENTER));
            for (int i = start; i < Math.min(entries.size(), start + perRow); i++) {
                var entry = entries.get(i);
                double share = entry.weight() * 100.0 / total;
                var hole = new ImageWidget(0, 0, UISizes.SLOT, UISizes.SLOT, UITheme.ITEM_SLOT);
                displaySlots.add(new DisplaySlot(hole, entry.stack(), FluidStack.EMPTY, IngredientIO.OUTPUT,
                        List.of(Component.translatable(SHARE, FormattingUtil.formatNumber2Places(share) + "%")), null));
                band.addChild(hole);
                labels.addChild(new ShareLabel(shareText(share), entry.color()));
            }
            rows.addChild(band);
            rows.addChild(labels);
        }
        return rows;
    }

    private static String shareText(double share) {
        if (share > 0 && share < 1) return "<1%";
        return Math.round(share) + "%";
    }

    private Widget createReservoir(int inner) {
        var hole = new ImageWidget(0, 0, UISizes.SLOT, UISizes.SLOT, UITheme.ITEM_SLOT);
        displaySlots.add(new DisplaySlot(hole, ItemStack.EMPTY, info.fluid(), IngredientIO.OUTPUT, List.of(), null));
        var reservoir = new Reservoir(info.fluid(), inner);
        reservoir.layout(l -> l.column().alignItems(AlignItems.CENTER).justifyContent(AlignContent.CENTER).paddingTop(BEDROCK_HEIGHT));
        reservoir.addChild(hole);
        return reservoir;
    }

    private Widget createDimensions() {
        var panel = new UIElement().layout(l -> l.column().paddingAll(UITheme.PANEL_PADDING).gapAll(UISizes.GAP)
                .alignItems(AlignItems.CENTER).justifyContent(AlignContent.CENTER));
        panel.setBackground(UITheme.PANEL);
        var dimensions = info.dimensions();
        if (dimensions == null || dimensions.isEmpty()) {
            panel.addChild(Label.translatable(ANY_DIMENSION, 2 * UISizes.SLOT));
            return panel;
        }
        int columns = dimensions.size() <= DIMENSION_SINGLE_ROW ? dimensions.size() : (dimensions.size() + DIMENSION_ROWS - 1) / DIMENSION_ROWS;
        boolean showTier = ConfigHolder.INSTANCE.compat.showDimensionTier;
        for (int start = 0; start < dimensions.size(); start += columns) {
            var row = UIElement.row(UISizes.SLOT);
            for (int i = start; i < Math.min(dimensions.size(), start + columns); i++) {
                var marker = dimensions.get(i);
                var hole = new ImageWidget(0, 0, UISizes.SLOT, UISizes.SLOT, UITheme.ITEM_SLOT);
                String tier = showTier ? "T" + (marker.tier >= DimensionMarker.MAX_TIER ? "?" : marker.tier) : null;
                displaySlots.add(new DisplaySlot(hole, marker.getIcon(), FluidStack.EMPTY, IngredientIO.CATALYST, List.of(), tier));
                row.addChild(hole);
            }
            panel.addChild(row);
        }
        return panel;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        GTRecipeWidget.drawPageCard(graphics, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight(), frame);
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
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

    private static final class ShareLabel extends UIElement {

        private final String text;
        private final int color;

        private ShareLabel(String text, int rgb) {
            this.text = text;
            this.color = 0xFF000000 | UITheme.lightBackgroundColor(rgb);
            layout(l -> l.size(UISizes.SLOT, SHARE_HEIGHT));
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            var font = Minecraft.getInstance().font;
            float scale = UISizes.SMALL_TEXT_SCALE;
            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(getPositionX() + UISizes.SLOT / 2f, getPositionY(), 0);
            pose.scale(scale, scale, 1);
            graphics.drawString(font, text, -font.width(text) / 2, 0, color, false);
            pose.popPose();
        }
    }

    private static final class Stratum extends UIElement {

        private final ResourceLocation texture;

        private Stratum(ResourceLocation texture, int width, int height) {
            this.texture = texture;
            layout(l -> l.size(width, height));
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            int x = getPositionX(), y = getPositionY(), width = getSizeWidth(), height = getSizeHeight();
            graphics.fill(x, y, x + width, y + height, STRATUM_OUTLINE);
            drawTiled(graphics, sprite(texture), x + 1, y + 1, width - 2, height - 2, ROCK_TINT);
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        }
    }

    private static final class Reservoir extends UIElement {

        private final FluidStack fluid;

        private Reservoir(FluidStack fluid, int width) {
            this.fluid = fluid;
            layout(l -> l.size(width, BEDROCK_HEIGHT + POOL_HEIGHT));
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            int x = getPositionX(), y = getPositionY(), width = getSizeWidth(), height = getSizeHeight();
            graphics.fill(x, y, x + width, y + height, STRATUM_OUTLINE);
            drawTiled(graphics, sprite(BEDROCK), x + 1, y + 1, width - 2, BEDROCK_HEIGHT - 1, ROCK_TINT);
            DrawerHelper.drawFluidForGui(graphics, FluidHelperImpl.toFluidStack(fluid), x + 1, y + BEDROCK_HEIGHT, width - 2, height - BEDROCK_HEIGHT - 1);
            graphics.fill(x + 1, y + BEDROCK_HEIGHT, x + width - 1, y + height - 1, POOL_SHADE);
            graphics.fill(x + 1, y + BEDROCK_HEIGHT, x + width - 1, y + BEDROCK_HEIGHT + 1, POOL_SURFACE);
            int slotX = x + (width - UISizes.SLOT) / 2;
            int slotY = y + BEDROCK_HEIGHT + (height - BEDROCK_HEIGHT - UISizes.SLOT) / 2;
            int pipeX = x + (width - PIPE_WIDTH) / 2;
            graphics.fill(pipeX, y, pipeX + PIPE_WIDTH, slotY, STRATUM_OUTLINE);
            graphics.fill(pipeX + 1, y, pipeX + PIPE_WIDTH - 1, slotY, PIPE_FILL);
            graphics.fill(pipeX + 1, y, pipeX + 3, slotY, PIPE_HIGHLIGHT);
            graphics.fill(pipeX + PIPE_WIDTH - 3, y, pipeX + PIPE_WIDTH - 1, slotY, PIPE_SHADE);
            graphics.fill(slotX - 2, slotY - 2, slotX + UISizes.SLOT + 2, slotY + UISizes.SLOT + 2, STRATUM_OUTLINE);
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
