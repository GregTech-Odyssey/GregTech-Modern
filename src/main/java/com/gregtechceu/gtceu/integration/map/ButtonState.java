package com.gregtechceu.gtceu.integration.map;

import com.gto.fastcollection.O2OOpenCacheHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ButtonState {

    private static final Map<String, Button> buttons = new O2OOpenCacheHashMap<>();
    private static List<Button> sortedButtons;

    public static void toggleButton(Button button) {
        button.enabled = !button.enabled;
        GroupingMapRenderer.getInstance().setLayerActive(button.name, button.enabled);

        // disable all other buttons if one is enabled
        if (button.enabled) {
            for (Map.Entry<String, Button> entry : buttons.entrySet()) {
                String name = entry.getKey();
                if (!name.equals(button.name)) {
                    entry.getValue().enabled = false;
                    GroupingMapRenderer.getInstance().setLayerActive(name, false);
                }
            }
        }
    }

    public static void toggleButton(String buttonName) {
        toggleButton(buttons.get(buttonName));
    }

    public static boolean isEnabled(Button button) {
        return button.enabled;
    }

    public static boolean isEnabled(String buttonName) {
        return buttons.get(buttonName).enabled;
    }

    public static int buttonAmount() {
        return buttons.size();
    }

    public static List<Button> getAllButtons() {
        if (sortedButtons == null) {
            sortedButtons = new ArrayList<>(buttons.values());
        }
        return sortedButtons;
    }

    public static class Button {

        public boolean enabled;
        public final String name;

        public Button(String name) {
            this.enabled = false;
            this.name = name;
        }

        public static void makeButton(String name) {
            buttons.put(name, new Button(name));
        }
    }
}
