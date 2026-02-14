package com.gregtechceu.gtceu.integration.map.layer;

import com.gregtechceu.gtceu.integration.map.ButtonState;
import com.gregtechceu.gtceu.integration.map.GenericMapRenderer;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class Layers {

    private static final Map<String, BiFunction<String, GenericMapRenderer, ? extends MapRenderLayer>> layers = new HashMap<>();

    public static void registerLayer(BiFunction<String, GenericMapRenderer, ? extends MapRenderLayer> initFunction,
                                     String key) {
        layers.put(key, initFunction);
        ButtonState.Button.makeButton(key);
    }

    public static void addLayersTo(List<MapRenderLayer> layers, GenericMapRenderer renderer) {
        for (var layer : Layers.layers.entrySet()) {
            layers.add(layer.getValue().apply(layer.getKey(), renderer));
        }
    }

    public static Collection<String> allKeys() {
        return layers.keySet();
    }
}
