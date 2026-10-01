package com.gregtechceu.gtceu.api.gui;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;
import java.util.regex.Pattern;

public class WidgetUtils {

    public static List<Widget> getWidgetsById(WidgetGroup group, String regex) {
        return group.getWidgetsById(Pattern.compile(regex));
    }

    @Nullable
    public static Widget getFirstWidgetById(WidgetGroup group, String regex) {
        return group.getFirstWidgetById(Pattern.compile(regex));
    }

    public static void widgetByIdForEach(WidgetGroup group, String regex, Consumer<Widget> consumer) {
        getWidgetsById(group, regex).forEach(consumer);
    }

    public static <T extends Widget> void widgetByIdForEach(WidgetGroup group, String regex, Class<T> clazz,
                                                            Consumer<T> consumer) {
        for (Widget widget : getWidgetsById(group, regex)) {
            if (clazz.isInstance(widget)) {
                consumer.accept(clazz.cast(widget));
            }
        }
    }

    public static int widgetIdIndex(Widget widget) {
        var id = widget.getId();
        if (id == null) return -1;
        int end = id.length();
        while (end > 0 && id.charAt(end - 1) == '_') end--;
        if (end == 0) return -1;
        try {
            return Integer.parseInt(id.substring(id.lastIndexOf('_', end - 1) + 1, end));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static <T extends Widget> void widgetWithIdForEach(WidgetGroup group, String id, Class<T> clazz, Consumer<T> consumer) {
        var matches = new ArrayList<T>();
        collectWithId(group, id, clazz, matches);
        for (var widget : matches) consumer.accept(widget);
    }

    private static <T extends Widget> void collectWithId(WidgetGroup group, String id, Class<T> clazz, List<T> matches) {
        for (Widget widget : group.widgets) {
            if (clazz.isInstance(widget) && id.equals(widget.getId())) matches.add(clazz.cast(widget));
            if (widget instanceof WidgetGroup child) collectWithId(child, id, clazz, matches);
        }
    }

    public static <T extends Widget> void indexedWidgetForEach(WidgetGroup group, String prefix, Class<T> clazz,
                                                               ObjIntConsumer<T> consumer) {
        var matches = new ArrayList<T>();
        var indices = new IntArrayList();
        collectIndexed(group, prefix, clazz, matches, indices);
        for (int i = 0; i < matches.size(); i++) consumer.accept(matches.get(i), indices.getInt(i));
    }

    private static <T extends Widget> void collectIndexed(WidgetGroup group, String prefix, Class<T> clazz, List<T> matches, IntArrayList indices) {
        for (Widget widget : group.widgets) {
            if (clazz.isInstance(widget)) {
                int index = indexAfterPrefix(widget.getId(), prefix);
                if (index >= 0) {
                    matches.add(clazz.cast(widget));
                    indices.add(index);
                }
            }
            if (widget instanceof WidgetGroup child) collectIndexed(child, prefix, clazz, matches, indices);
        }
    }

    private static int indexAfterPrefix(@Nullable String id, String prefix) {
        int start = prefix.length() + 1;
        if (id == null || id.length() <= start || id.charAt(start - 1) != '_' || !id.startsWith(prefix)) return -1;
        long index = 0;
        for (int i = start; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c < '0' || c > '9') return -1;
            index = index * 10 + (c - '0');
            if (index > Integer.MAX_VALUE) return -1;
        }
        return (int) index;
    }

    public static int getInventoryHeight(boolean includeHotbar) {
        return 64 + (includeHotbar ? 22 : 0);
    }
}
