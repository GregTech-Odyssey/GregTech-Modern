package com.gregtechceu.gtceu.uipro.styletemplate;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.uipro.utils.UIPreferences;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Client-side registry for resource-pack-defined new UI styles.
 * <p>
 * Colour schemes live at {@code assets/<namespace>/uipro/color_schemes/<id>.json}; material packs live at
 * {@code assets/<namespace>/uipro/texture_packs/<id>.json}. Both are discovered on every resource reload, so a
 * resource pack can add, replace, or remove styles without adding code to GTCEu.
 */
public final class UIStyleManager {

    public static final ResourceLocation DEFAULT_ID = GTCEu.id("default");
    public static final String COLOR_SCHEME_DIRECTORY = "uipro/color_schemes";
    public static final String TEXTURE_PACK_DIRECTORY = "uipro/texture_packs";
    public static final String COLOR_SCHEME_PREFERENCE = "uipro.color_scheme";
    public static final String TEXTURE_PACK_PREFERENCE = "uipro.texture_pack";

    /** Supported palette keys and their fallback colours. Values are ARGB. */
    static final Map<String, Integer> DEFAULT_COLORS = Map.ofEntries(
            Map.entry("text", 0xFF202020),
            Map.entry("text_secondary", 0xFF555555),
            Map.entry("panel_text", 0xFF202020),
            Map.entry("link_text", 0xFF1B5E9E),
            Map.entry("field_text", 0xFFFFFFFF),
            Map.entry("placeholder_text", 0xFF8A8A8A),
            Map.entry("screen_text", 0xFFE2E6E8),
            Map.entry("window_fill", 0xFFC6C6C6),
            Map.entry("window_outline", 0xFF181A1B),
            Map.entry("tab_fill", 0xFFA8A8A8),
            Map.entry("tab_hover_fill", 0xFFB9B9B9),
            Map.entry("tab_pressed_fill", 0xFFADADAD),
            Map.entry("segment_hover", 0x20000000),
            Map.entry("slot_fill", 0xFF8B8B8B),
            Map.entry("fluid_slot_fill", 0xFF6A6A6A),
            Map.entry("panel_fill", 0xFFBDBDBD),
            Map.entry("panel_outline", 0xFF8B8B8B),
            Map.entry("status_panel_fill", 0xFFB5B5B5),
            Map.entry("progress_track_fill", 0xFFA4A4A4),
            Map.entry("display_screen_fill", 0xFF21262A),
            Map.entry("bevel_dark", 0xFF7A7A7A),
            Map.entry("bevel_light", 0xFFFFFFFF),
            Map.entry("button_tint", 0xFFC0C0C0),
            Map.entry("latched_fill", 0xFF969696),
            Map.entry("status_online", 0xFF55DD55),
            Map.entry("status_offline", 0xFFDD4444),
            Map.entry("status_warning", 0xFFE8B830),
            Map.entry("status_text_good", 0xFF2E7D1E),
            Map.entry("status_text_warning", 0xFF8C5A00),
            Map.entry("status_text_error", 0xFFA81C1C),
            Map.entry("status_lamp_outline", 0xFF373737),
            Map.entry("selection_color", 0xFFFFC83D),
            Map.entry("selection_outline", 0xFF373737),
            Map.entry("dock_outline", 0xFF373737),
            Map.entry("dock_highlight", 0xFFFFFFFF),
            Map.entry("dock_separator", 0xFF8A8A8A),
            Map.entry("canvas_fill", 0xFFB4B4B4),
            Map.entry("canvas_grid_line", 0x14000000),
            Map.entry("canvas_grid_accent", 0x26000000),
            Map.entry("canvas_minimap_fill", 0xE6C6C6C6),
            Map.entry("canvas_minimap_border", 0xFF373737),
            Map.entry("canvas_minimap_viewport", 0xFFFFC83D),
            Map.entry("canvas_item_block", 0xFF8B8B8B),
            Map.entry("slot_hover_overlay", 0x80FFFFFF),
            Map.entry("slot_bevel_dark", 0xFF373737),
            Map.entry("slot_bevel_light", 0xFFFFFFFF),
            Map.entry("divider", 0xFF8B8B8B),
            Map.entry("card_fill", 0xFFC6C6C6),
            Map.entry("card_outline", 0xFF000000),
            Map.entry("card_highlight", 0xFFFFFFFF),
            Map.entry("card_shadow", 0xFF555555),
            Map.entry("flow_node_fill", 0xFFC6C6C6),
            Map.entry("flow_node_outline", 0xFF373737),
            Map.entry("flow_node_highlight", 0xFFDCDCDC),
            Map.entry("flow_node_shade", 0xFFA2A2A2),
            Map.entry("flow_wire_idle", 0xFF8A8A8A),
            Map.entry("flow_wire_disabled", 0xFF9A9A9A),
            Map.entry("button_text", 0xFF222222),
            Map.entry("button_text_disabled", 0xFF666666));

    static final Map<String, String> FALLBACK_KEYS = Map.of(
            "slot_bevel_dark", "bevel_dark",
            "slot_bevel_light", "bevel_light",
            "divider", "panel_outline",
            "card_fill", "window_fill",
            "flow_node_fill", "window_fill");

    private static final TexturePack DEFAULT_TEXTURE_PACK = new TexturePack(
            GTCEu.id("textures/gui/uipro/ore_styles.png"),
            GTCEu.id("textures/gui/uipro/status_icons.png"),
            GTCEu.id("textures/gui/uipro/callout_icons.png"),
            GTCEu.id("textures/gui/uipro/view_icons.png"),
            GTCEu.id("textures/gui/uipro/slot_readonly.png"));

    private static Map<ResourceLocation, ColorScheme> colorSchemes = Map.of(DEFAULT_ID, new ColorScheme("Default", Collections.emptyMap()));
    private static Map<ResourceLocation, TexturePack> texturePacks = Map.of(DEFAULT_ID, DEFAULT_TEXTURE_PACK);
    private static ResourceLocation activeColorScheme = DEFAULT_ID;
    private static ResourceLocation activeTexturePack = DEFAULT_ID;

    private UIStyleManager() {}

    public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) UIStyleManager::reload);
    }

    public static Map<ResourceLocation, ColorScheme> colorSchemes() {
        return colorSchemes;
    }

    public static Map<ResourceLocation, TexturePack> texturePacks() {
        return texturePacks;
    }

    public static ResourceLocation activeColorScheme() {
        return activeColorScheme;
    }

    public static ResourceLocation activeTexturePack() {
        return activeTexturePack;
    }

    /** Selects a client-local colour scheme immediately and persists it in {@code ui_preferences.json}. */
    public static void selectColorScheme(ResourceLocation id) {
        UIPreferences.put(COLOR_SCHEME_PREFERENCE, id.toString());
        applySelected();
    }

    /** Selects a client-local material pack immediately and persists it in {@code ui_preferences.json}. */
    public static void selectTexturePack(ResourceLocation id) {
        UIPreferences.put(TEXTURE_PACK_PREFERENCE, id.toString());
        applySelected();
    }

    private static void reload(ResourceManager manager) {
        var loadedColors = new LinkedHashMap<ResourceLocation, ColorScheme>();
        loadedColors.put(DEFAULT_ID, new ColorScheme("Default", Collections.emptyMap()));
        loadColorSchemes(manager, loadedColors);
        colorSchemes = Map.copyOf(loadedColors);

        var loadedTextures = new LinkedHashMap<ResourceLocation, TexturePack>();
        loadedTextures.put(DEFAULT_ID, DEFAULT_TEXTURE_PACK);
        loadTexturePacks(manager, loadedTextures);
        texturePacks = Map.copyOf(loadedTextures);
        applySelected();
    }

    private static void loadColorSchemes(ResourceManager manager, Map<ResourceLocation, ColorScheme> destination) {
        manager.listResources(COLOR_SCHEME_DIRECTORY, location -> location.getPath().endsWith(".json")).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> read(entry.getKey(), entry.getValue(), root -> {
                    var colors = new LinkedHashMap<String, Integer>();
                    var supplied = root.getAsJsonObject("colors");
                    if (supplied != null) {
                        for (var color : supplied.entrySet()) {
                            if (!DEFAULT_COLORS.containsKey(color.getKey())) {
                                GTCEu.LOGGER.warn("Ignoring unknown UI colour '{}' in {}", color.getKey(), entry.getKey());
                                continue;
                            }
                            parseColor(color.getValue()).ifPresentOrElse(value -> colors.put(color.getKey(), value),
                                    () -> GTCEu.LOGGER.warn("Ignoring invalid UI colour '{}' in {}", color.getKey(), entry.getKey()));
                        }
                    }
                    destination.put(styleId(entry.getKey(), COLOR_SCHEME_DIRECTORY),
                            new ColorScheme(readName(root, entry.getKey()), Map.copyOf(colors)));
                }));
    }

    private static void loadTexturePacks(ResourceManager manager, Map<ResourceLocation, TexturePack> destination) {
        manager.listResources(TEXTURE_PACK_DIRECTORY, location -> location.getPath().endsWith(".json")).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> read(entry.getKey(), entry.getValue(), root -> {
                    var supplied = root.getAsJsonObject("textures");
                    var pack = new TexturePack(
                            texture(manager, supplied, "atlas", DEFAULT_TEXTURE_PACK.atlas(), entry.getKey()),
                            texture(manager, supplied, "status_icons", DEFAULT_TEXTURE_PACK.statusIcons(), entry.getKey()),
                            texture(manager, supplied, "callout_icons", DEFAULT_TEXTURE_PACK.calloutIcons(), entry.getKey()),
                            texture(manager, supplied, "view_icons", DEFAULT_TEXTURE_PACK.viewIcons(), entry.getKey()),
                            texture(manager, supplied, "disabled_hatch", DEFAULT_TEXTURE_PACK.disabledHatch(), entry.getKey()));
                    destination.put(styleId(entry.getKey(), TEXTURE_PACK_DIRECTORY), pack);
                }));
    }

    private static void read(ResourceLocation file, Resource resource, java.util.function.Consumer<JsonObject> consumer) {
        try (Reader reader = resource.openAsReader()) {
            var element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) throw new IllegalArgumentException("root must be an object");
            consumer.accept(element.getAsJsonObject());
        } catch (Exception e) {
            GTCEu.LOGGER.warn("Failed to load UI style definition {}", file, e);
        }
    }

    private static String readName(JsonObject root, ResourceLocation file) {
        var name = root.get("name");
        return name != null && name.isJsonPrimitive() ? name.getAsString() : file.toString();
    }

    private static ResourceLocation texture(ResourceManager manager, JsonObject supplied, String key, ResourceLocation fallback,
                                            ResourceLocation definition) {
        if (supplied == null || !supplied.has(key)) return fallback;
        var location = ResourceLocation.tryParse(supplied.get(key).getAsString());
        if (location == null || manager.getResource(location).isEmpty()) {
            GTCEu.LOGGER.warn("UI texture '{}' in {} is unavailable; using {}", supplied.get(key), definition, fallback);
            return fallback;
        }
        return location;
    }

    private static Optional<Integer> parseColor(JsonElement value) {
        try {
            String text = value.getAsString().trim();
            if (text.startsWith("#")) text = text.substring(1);
            if (text.length() != 6 && text.length() != 8) return Optional.empty();
            long parsed = Long.parseLong(text, 16);
            return Optional.of((int) (text.length() == 6 ? parsed | 0xFF000000L : parsed));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static ResourceLocation styleId(ResourceLocation file, String directory) {
        String path = file.getPath().substring(directory.length() + 1, file.getPath().length() - ".json".length());
        return new ResourceLocation(file.getNamespace(), path);
    }

    private static void applySelected() {
        var selectedColors = selected(COLOR_SCHEME_PREFERENCE, configuredColorScheme(), colorSchemes, true);
        var selectedTextures = selected(TEXTURE_PACK_PREFERENCE, configuredTexturePack(), texturePacks, true);
        activeColorScheme = selectedColors;
        activeTexturePack = selectedTextures;
        UITheme.applyStyle(colorSchemes.get(selectedColors).colors(), texturePacks.get(selectedTextures));
    }

    private static <T> ResourceLocation selected(String preference, String configured, Map<ResourceLocation, T> available, boolean logMissing) {
        var requested = ResourceLocation.tryParse(UIPreferences.get(preference, configured));
        if (requested != null && available.containsKey(requested)) return requested;
        if (logMissing && requested != null && !requested.equals(DEFAULT_ID)) {
            GTCEu.LOGGER.warn("Selected UI style {} is not available; using {}", requested, DEFAULT_ID);
        }
        return DEFAULT_ID;
    }

    private static String configuredColorScheme() {
        return ConfigHolder.INSTANCE == null ? DEFAULT_ID.toString() : ConfigHolder.INSTANCE.client.newUiColorScheme;
    }

    private static String configuredTexturePack() {
        return ConfigHolder.INSTANCE == null ? DEFAULT_ID.toString() : ConfigHolder.INSTANCE.client.newUiTexturePack;
    }

    public record ColorScheme(String name, Map<String, Integer> colors) {

        public ColorScheme {
            Objects.requireNonNull(name);
            colors = Map.copyOf(colors);
        }
    }

    /** All five textures are optional in a definition and fall back to the built-in material pack independently. */
    public record TexturePack(ResourceLocation atlas, ResourceLocation statusIcons, ResourceLocation calloutIcons,
                              ResourceLocation viewIcons, ResourceLocation disabledHatch) {

        public TexturePack {
            Objects.requireNonNull(atlas);
            Objects.requireNonNull(statusIcons);
            Objects.requireNonNull(calloutIcons);
            Objects.requireNonNull(viewIcons);
            Objects.requireNonNull(disabledHatch);
        }
    }
}
