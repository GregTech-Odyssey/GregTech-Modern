package com.gregtechceu.gtceu.api.cover.filter;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.data.lang.LangHandler;
import com.gregtechceu.gtceu.utils.TagExprFilter;

import com.lowdragmc.lowdraglib.gui.widget.*;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.TagKey;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public abstract class TagFilter<T, S extends Filter<T, S>> implements Filter<T, S> {

    private static final Pattern DOUBLE_WILDCARD = Pattern.compile("\\*{2,}");
    private static final Pattern DOUBLE_AND = Pattern.compile("&{2,}");
    private static final Pattern DOUBLE_OR = Pattern.compile("\\|{2,}");
    private static final Pattern DOUBLE_NOT = Pattern.compile("!{2,}");
    private static final Pattern DOUBLE_XOR = Pattern.compile("\\^{2,}");
    private static final Pattern DOUBLE_SPACE = Pattern.compile(" {2,}");
    protected String oreDictFilterExpression = "";
    protected Consumer<S> itemWriter = filter -> {};
    protected Consumer<S> onUpdated = filter -> itemWriter.accept(filter);
    protected TagExprFilter.TagExprParser.MatchExpr matchExpr = null;

    protected TagFilter() {}

    @Override
    public boolean isBlank() {
        return oreDictFilterExpression.isBlank();
    }

    public CompoundTag saveFilter() {
        if (isBlank()) {
            return null;
        }
        var tag = new CompoundTag();
        tag.putString("oreDict", oreDictFilterExpression);
        return tag;
    }

    public void setOreDict(String oreDict) {
        this.oreDictFilterExpression = oreDict;
        matchExpr = TagExprFilter.parseExpression(oreDictFilterExpression);
        onUpdated.accept((S) this);
    }

    public WidgetGroup openConfigurator(int x, int y) {
        TextField textFieldWidget;
        WidgetGroup group = new WidgetGroup(x, y, 18 * 3 + 25, 18 * 3); // 80 55
        group.addWidget(new ImageWidget(0, 0, 20, 20, GuiTextures.INFO_ICON).setHoverTooltips(LangHandler.getMultiLang("cover.tag_filter.info").toArray(new MutableComponent[0])));
        group.addWidget(textFieldWidget = (TextField) new TextField(0, 29, 18 * 3 + 25, 12, () -> oreDictFilterExpression, this::setOreDict).setMaxStringLength(64).setValidator(input -> {
            // remove all operators that are double
            input = DOUBLE_WILDCARD.matcher(input).replaceAll("*");
            input = DOUBLE_AND.matcher(input).replaceAll("&");
            input = DOUBLE_OR.matcher(input).replaceAll("|");
            input = DOUBLE_NOT.matcher(input).replaceAll("!");
            input = DOUBLE_XOR.matcher(input).replaceAll("^");
            input = DOUBLE_SPACE.matcher(input).replaceAll(" ");
            // move ( and ) so it doesn't create invalid expressions f.e. xxx (& yyy) => xxx & (yyy)
            // append or prepend ( and ) if the amount is not equal
            StringBuilder builder = new StringBuilder();
            int unclosed = 0;
            char last = ' ';
            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);
                if (c == ' ') {
                    if (last != '(') builder.append(" ");
                    continue;
                }
                if (c == '(') unclosed++;
                else if (c == ')') {
                    unclosed--;
                    if (last == '&' || last == '|' || last == '^') {
                        int l = builder.lastIndexOf(" " + last);
                        int l2 = builder.lastIndexOf(String.valueOf(last));
                        builder.insert(l == l2 - 1 ? l : l2, ")");
                        continue;
                    }
                    if (i > 0 && builder.charAt(builder.length() - 1) == ' ') {
                        builder.deleteCharAt(builder.length() - 1);
                    }
                } else if ((c == '&' || c == '|' || c == '^') && last == '(') {
                    builder.deleteCharAt(builder.lastIndexOf("("));
                    builder.append(c).append(" (");
                    continue;
                }
                builder.append(c);
                last = c;
            }
            if (unclosed > 0) {
                builder.append(")".repeat(unclosed));
            } else if (unclosed < 0) {
                unclosed = -unclosed;
                for (int i = 0; i < unclosed; i++) {
                    builder.insert(0, "(");
                }
            }
            input = builder.toString();
            input = input.replaceAll(" {2,}", " ");
            return input;
        }));

        DraggableScrollableWidgetGroup container = new DraggableScrollableWidgetGroup(130, 0, 140, 100);
        container.setClientSideWidget().setActive(false).setVisible(false).setBackground(GuiTextures.BACKGROUND_INVERSE);
        var handler = getStackHandlerWidget(container, textFieldWidget);
        return group.addWidget(container).addWidget((Widget) handler);
    }

    private @NotNull StackHandlerWidget<T, S> getStackHandlerWidget(DraggableScrollableWidgetGroup container, TextField textFieldWidget) {
        var handler = getItemHandler();
        handler.setOnContentsChanged(
                () -> {
                    container.clearAllWidgets();
                    if (!handler.isEmpty()) {
                        container.setVisible(true).setActive(true);
                        var tags = handler.getTags()
                                .map(tag -> tag.location().toString())
                                .toList();
                        Widget[] newWidgets = createTagLabelContainer(textFieldWidget, tags);
                        container.addWidgets(newWidgets);
                    } else {
                        container.setVisible(false).setActive(false);
                    }
                });
        return handler;
    }

    abstract StackHandlerWidget<T, S> getItemHandler();

    /**
     * 创建标签展示和交互容器，供子类 openConfigurator 使用。
     */
    protected Widget[] createTagLabelContainer(TextField textFieldWidget, List<String> tags) {
        var atomicI = new AtomicInteger(0);
        var container = new Widget[tags.size()];
        for (String tag : tags) {
            container[atomicI.get()] = (new LabelWidget(4, atomicI.getAndIncrement() * 12 + 4, tag) {

                @Override
                public boolean mouseReleased(double mouseX, double mouseY, int button) {
                    if (isMouseOverElement(mouseX, mouseY)) {
                        if (button == 0) {
                            textFieldWidget.setDirectly(tag);
                        } else if (button == 1) {
                            Minecraft.getInstance().keyboardHandler.setClipboard(tag);
                        }
                        playButtonClickSound();
                        return true;
                    }
                    return super.mouseReleased(mouseX, mouseY, button);
                }
            }.setTextColor(0x39c5bb).setHoverTooltips(Component.translatable("gtocore.part.extendae.tag_filter.tooltip")).setClientSideWidget());
        }
        return container;
    }

    @Override
    public void setOnUpdated(Consumer<S> onUpdated) {
        this.onUpdated = filter -> {
            this.itemWriter.accept(filter);
            onUpdated.accept(filter);
        };
    }

    public String getOreDictFilterExpression() {
        return this.oreDictFilterExpression;
    }

    protected interface StackHandlerWidget<STACK, FILTER extends Filter<STACK, FILTER>> {

        STACK getStack();

        void setOnContentsChanged(Runnable runnable);

        boolean isEmpty();

        Stream<TagKey<?>> getTags();
    }

    protected static class TextField extends TextFieldWidget {

        public TextField(int x, int y, int width, int height, Supplier<String> textSupplier, Consumer<String> textConsumer) {
            super(x, y, width, height, textSupplier, textConsumer);
        }

        public void setDirectly(String newTextString) {
            this.setCurrentString(newTextString);
            this.writeClientAction(1, buf -> buf.writeUtf(newTextString));
        }
    }
}
