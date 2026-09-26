package com.gregtechceu.gtceu.integration.emi.orevein;

import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockore.BedrockOreDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.integration.xei.widgets.GTOreVeinWidget;
import com.gregtechceu.gtceu.integration.xei.widgets.GTRecipeWidget.PageFrame;
import com.gregtechceu.gtceu.integration.xei.widgets.VeinInfo;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;

import com.lowdragmc.lowdraglib.emi.ModularEmiRecipe;
import com.lowdragmc.lowdraglib.emi.ModularForegroundRenderWidget;
import com.lowdragmc.lowdraglib.emi.ModularWrapperWidget;
import com.lowdragmc.lowdraglib.jei.IngredientIO;
import com.lowdragmc.lowdraglib.jei.ModularWrapper;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class VeinEmiRecipe implements EmiRecipe {

    private static final int TIER_COLOR = 0xFFFFFFFF;

    private final EmiRecipeCategory category;
    private final ResourceLocation id;
    private final Supplier<VeinInfo> info;
    private final Supplier<List<EmiStack>> outputs;
    @Nullable
    private VeinInfo cachedInfo;
    @Nullable
    private List<EmiStack> cachedOutputs;
    @Nullable
    private Size compactSize;

    public VeinEmiRecipe(EmiRecipeCategory category, ResourceLocation id, Supplier<VeinInfo> info, Supplier<List<EmiStack>> outputs) {
        this.category = category;
        this.id = id;
        this.info = info;
        this.outputs = outputs;
    }

    protected VeinEmiRecipe(VeinEmiRecipe other) {
        this(other.category, other.id, other.info, other.outputs);
    }

    public static VeinEmiRecipe oreVein(GTOreDefinition definition) {
        return new VeinEmiRecipe(GTOreVeinEmiCategory.CATEGORY, GTRegistries.ORE_VEINS.getKey(definition).withPrefix("/ore_vein_diagram/"),
                () -> VeinInfo.of(definition),
                () -> GTOreVeinWidget.getContainedOresAndBlocks(definition).stream().map(EmiStack::of).toList());
    }

    public static VeinEmiRecipe bedrockFluid(BedrockFluidDefinition definition) {
        return new VeinEmiRecipe(GTBedrockFluidEmiCategory.CATEGORY, GTRegistries.BEDROCK_FLUID_DEFINITIONS.getKey(definition).withPrefix("/bedrock_fluid_diagram/"),
                () -> VeinInfo.of(definition),
                () -> List.of(EmiStack.of(definition.getStoredFluid().get())));
    }

    public static VeinEmiRecipe bedrockOre(BedrockOreDefinition definition) {
        return new VeinEmiRecipe(GTBedrockOreEmiCategory.CATEGORY, GTRegistries.BEDROCK_ORE_DEFINITIONS.getKey(definition).withPrefix("/bedrock_ore_diagram/"),
                () -> VeinInfo.of(definition),
                () -> VeinInfo.of(definition).entries().stream().map(entry -> EmiStack.of(entry.stack())).toList());
    }

    public VeinInfo getInfo() {
        var result = cachedInfo;
        if (result == null) cachedInfo = result = info.get();
        return result;
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return category;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return List.of();
    }

    @Override
    public List<EmiStack> getOutputs() {
        var result = cachedOutputs;
        if (result == null) cachedOutputs = result = outputs.get();
        return result;
    }

    @Override
    public boolean supportsRecipeTree() {
        return false;
    }

    public Size measure(PageFrame frame) {
        var page = new GTOreVeinWidget(getInfo(), frame);
        return new Size(page.getSizeWidth(), page.getSizeHeight());
    }

    protected Size compactSize() {
        var size = compactSize;
        if (size == null) compactSize = size = measure(PageFrame.COMPACT);
        return size;
    }

    @Override
    public int getDisplayWidth() {
        return compactSize().width;
    }

    @Override
    public int getDisplayHeight() {
        return compactSize().height;
    }

    protected PageFrame frameFor(WidgetHolder widgets) {
        return PageFrame.COMPACT;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        var page = new GTOreVeinWidget(getInfo(), frameFor(widgets));
        var modular = new ModularWrapper<>(page);
        modular.setRecipeWidget(0, 0);
        synchronized (ModularEmiRecipe.CACHE_OPENED) {
            ModularEmiRecipe.CACHE_OPENED.add(modular);
        }
        List<Widget> slots = new ArrayList<>();
        for (var slot : page.getDisplaySlots()) {
            var position = slot.hole().getPosition();
            EmiStack stack = slot.fluid().isEmpty() ? EmiStack.of(slot.item()) : EmiStack.of(slot.fluid().getFluid(), slot.fluid().getTag());
            var widget = new DisplaySlotWidget(stack, position.x, position.y, slot.overlay()).drawBack(false);
            if (slot.io() == IngredientIO.CATALYST) widget.catalyst(true);
            else widget.recipeContext(this);
            slot.tooltip().forEach(widget::appendTooltip);
            slots.add(widget);
        }
        widgets.add(new ModularWrapperWidget(modular, slots));
        slots.forEach(widgets::add);
        widgets.add(new ModularForegroundRenderWidget(modular));
    }

    private static final class DisplaySlotWidget extends SlotWidget {

        @Nullable
        private final String overlay;

        private DisplaySlotWidget(EmiStack stack, int x, int y, @Nullable String overlay) {
            super(stack, x, y);
            this.overlay = overlay;
        }

        @Override
        public void drawStack(GuiGraphics draw, int mouseX, int mouseY, float delta) {
            getStack().render(draw, x + 1, y + 1, delta, EmiIngredient.RENDER_ICON);
        }

        @Override
        public void drawOverlay(GuiGraphics draw, int mouseX, int mouseY, float delta) {
            super.drawOverlay(draw, mouseX, mouseY, delta);
            if (overlay == null) return;
            var font = Minecraft.getInstance().font;
            float scale = UISizes.SMALL_TEXT_SCALE;
            var pose = draw.pose();
            pose.pushPose();
            pose.translate(x + UISizes.SLOT - 1 - font.width(overlay) * scale, y + UISizes.SLOT - 1 - UISizes.SMALL_TEXT_HEIGHT, 200);
            pose.scale(scale, scale, 1);
            draw.drawString(font, overlay, 0, 0, TIER_COLOR, true);
            pose.popPose();
        }
    }
}
