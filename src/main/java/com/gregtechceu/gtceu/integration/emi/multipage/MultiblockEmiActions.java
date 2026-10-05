package com.gregtechceu.gtceu.integration.emi.multipage;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.integration.emi.recipe.Ae2PatternBuilder;
import com.gregtechceu.gtceu.uipro.elements.Button;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;
import com.gregtechceu.gtceu.uiwidgets.patternbuilder.PatternBuilderModel;
import com.gregtechceu.gtceu.uiwidgets.patternbuilder.PatternBuilderPanel;
import com.gregtechceu.gtceu.uiwidgets.structure.StructureBuildFlow;
import com.gregtechceu.gtceu.uiwidgets.structure.StructurePlans;
import com.gregtechceu.gtceu.uiwidgets.structure.StructurePreviewScreen;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import appeng.menu.me.common.MEStorageMenu;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.runtime.EmiFavorites;
import dev.emi.emi.runtime.EmiPersistentData;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.RecipeScreen;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

@OnlyIn(Dist.CLIENT)
public final class MultiblockEmiActions {

    public static final String FAVORITE = "gtceu.structure_preview.favorite";
    public static final String TREE = "gtceu.structure_preview.tree";
    public static final String TREE_TITLE = "gtceu.structure_tree.title";
    public static final String TREE_CONFIRM = "gtceu.structure_tree.confirm";
    public static final String TREE_INCLUDE = "gtceu.structure_tree.include";
    public static final String TREE_BLOCKED = "gtceu.structure_tree.blocked";
    public static final String TREE_CATEGORY = "gtceu.structure_tree.category";

    private MultiblockEmiActions() {}

    public static List<Widget> of(EmiRecipe recipe, MultiblockMachineDefinition definition) {
        var favorite = Button.icon(UITheme.switching(() -> isFavorite(recipe), WidgetIcons.FAVORITE_OFF, WidgetIcons.FAVORITE_ON))
                .setOnClientClick(() -> toggleFavorite(recipe, definition));
        favorite.tooltips(FAVORITE);
        return List.of(favorite);
    }

    public static void blockFavoriteKey(WidgetHolder widgets) {
        widgets.add(new FavoriteKeyBlocker(new Bounds(0, 0, widgets.getWidth(), widgets.getHeight())));
    }

    public static void register() {
        StructurePreviewScreen.addActionProvider(MultiblockEmiActions::treeAction);
    }

    private static EmiStack output(EmiRecipe recipe, MultiblockMachineDefinition definition) {
        var outputs = recipe.getOutputs();
        return outputs.isEmpty() ? EmiStack.of(definition.asStack()) : outputs.get(0);
    }

    private static boolean sameRecipe(@Nullable EmiRecipe favorite, EmiRecipe recipe) {
        return favorite == recipe || favorite != null && recipe.getId() != null && Objects.equals(favorite.getId(), recipe.getId());
    }

    public static boolean isFavorite(EmiRecipe recipe) {
        for (var favorite : EmiFavorites.favorites) {
            if (sameRecipe(favorite.getRecipe(), recipe)) return true;
        }
        return false;
    }

    public static void toggleFavorite(EmiRecipe recipe, MultiblockMachineDefinition definition) {
        if (EmiFavorites.favorites.removeIf(favorite -> sameRecipe(favorite.getRecipe(), recipe))) {
            EmiPersistentData.save();
        } else {
            EmiFavorites.addFavorite(output(recipe, definition), recipe);
        }
        EmiScreenManager.repopulatePanels(SidebarType.FAVORITES);
    }

    @Nullable
    private static StructurePreviewScreen.Action treeAction(MultiblockMachineDefinition definition, @Nullable Screen origin) {
        if (!(origin instanceof RecipeScreen)) return null;
        var recipe = EmiApi.getRecipeManager().getRecipe(definition.getId());
        if (recipe == null) return null;
        var icon = definition.asStack();
        return StructureBuildFlow.action(TREE, (layout, values, navigator, preview) -> {
            var model = model(origin, StructurePlans.modelBuilder(icon, layout, true));
            model.selectMinimum();
            return new PatternBuilderPanel(model, icon, icon.getHoverName(), Integer.MAX_VALUE, navigator.maxHeight(),
                    () -> showTree(origin, new MultiblockTreeRecipe(treeId(definition), model.getInputs(), output(recipe, definition))),
                    navigator::close, new PatternBuilderPanel.Footer(TREE_TITLE, TREE_CONFIRM, TREE_INCLUDE, TREE_BLOCKED, false, false, navigator::close, true));
        });
    }

    private static PatternBuilderModel model(@Nullable Screen origin, PatternBuilderModel.Builder builder) {
        if (origin instanceof RecipeScreen recipes && recipes.old != null && recipes.old.getMenu() instanceof MEStorageMenu storage) {
            return Ae2PatternBuilder.model(storage, storage.getClientRepo(), builder);
        }
        return builder.build(StructureBuildFlow.inventoryStock(Minecraft.getInstance().player));
    }

    private static ResourceLocation treeId(MultiblockMachineDefinition definition) {
        var id = definition.getId();
        return new ResourceLocation(id.getNamespace(), "/multiblock_tree/" + id.getPath());
    }

    private static void showTree(@Nullable Screen origin, MultiblockTreeRecipe tree) {
        var minecraft = Minecraft.getInstance();
        minecraft.setScreen(origin);
        BoM.setGoal(tree);
        EmiApi.viewRecipeTree();
    }

    private static final class FavoriteKeyBlocker extends dev.emi.emi.api.widget.Widget {

        private final Bounds bounds;

        private FavoriteKeyBlocker(Bounds bounds) {
            this.bounds = bounds;
        }

        @Override
        public Bounds getBounds() {
            return bounds;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {}

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return EmiConfig.favorite.matchesKey(keyCode, scanCode);
        }
    }
}
