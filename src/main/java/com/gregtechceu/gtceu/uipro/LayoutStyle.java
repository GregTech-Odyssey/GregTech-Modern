package com.gregtechceu.gtceu.uipro;

import dev.vfyjxf.taffy.geometry.TaffyRect;
import dev.vfyjxf.taffy.geometry.TaffySize;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import dev.vfyjxf.taffy.style.LengthPercentage;
import dev.vfyjxf.taffy.style.LengthPercentageAuto;
import dev.vfyjxf.taffy.style.TaffyDimension;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import dev.vfyjxf.taffy.style.TaffyPosition;
import dev.vfyjxf.taffy.style.TaffyStyle;

/**
 * 元素布局参数，对应 LDLib2 的 {@code LayoutStyle}：包装 Taffy（taffy-java，与 LDLib2 相同的布局引擎）的 {@link TaffyStyle}，
 * 语义就是 CSS Flexbox。方法名与 LDLib2 保持一致（{@code width / widthAuto / paddingAll / gapAll / flexGrow / alignItems …}），
 * 另有几个本框架常用的简写（{@link #row()}、{@link #column()}、{@link #alignCenter()}…）。
 * <p>
 * 默认值与 LDLib2 相同：纵向排布、{@code flex-shrink: 0}、{@code min-size: 0}、{@code align-content: flex-start}；
 * 交叉轴 {@code align-items} 为 CSS 默认的 stretch——交叉轴尺寸为 auto 的子元素拉满，写了尺寸的不动。
 * <p>
 * 尺寸参数传 {@link #AUTO}（负数）等同于 {@code widthAuto()}。父元素尺寸会变的地方（滚动条显隐、拖拽缩放、窗口随内容变化），
 * 子元素不要按建页时的 {@code getContentWidth()} 算好宽度写死，交给拉伸 / flexGrow。
 */
public final class LayoutStyle {

    /** 自动尺寸：由内容或父元素（拉伸、flexGrow）决定。 */
    public static final int AUTO = -1;

    final TaffyStyle style = new TaffyStyle();
    private final Runnable onChanged;

    LayoutStyle(Runnable onChanged) {
        this.onChanged = onChanged;
        style.display = TaffyDisplay.FLEX;
        style.flexDirection = FlexDirection.COLUMN;
        style.flexShrink = 0;
        style.minSize = TaffySize.all(TaffyDimension.ZERO);
        style.alignContent = AlignContent.FLEX_START;
    }

    /** 给非 {@link UIElement} 控件（{@link ILayoutItem}）用的独立样式，默认为固定尺寸。 */
    public static LayoutStyle fixed(int width, int height, Runnable onChanged) {
        var layout = new LayoutStyle(onChanged);
        layout.style.size = TaffySize.of(dimension(width), dimension(height));
        return layout;
    }

    /** 底层的 Taffy 样式（只读用途；改动请走本类的方法，才会通知重新布局）。 */
    public TaffyStyle taffyStyle() {
        return style;
    }

    private LayoutStyle changed() {
        onChanged.run();
        return this;
    }

    private static TaffyDimension dimension(float value) {
        return value < 0 ? TaffyDimension.AUTO : TaffyDimension.length(value);
    }

    // ==================== 排布方向 ====================

    public LayoutStyle flexDirection(FlexDirection direction) {
        style.flexDirection = direction;
        return changed();
    }

    public LayoutStyle row() {
        return flexDirection(FlexDirection.ROW);
    }

    public LayoutStyle column() {
        return flexDirection(FlexDirection.COLUMN);
    }

    public LayoutStyle flexWrap(FlexWrap wrap) {
        style.flexWrap = wrap;
        return changed();
    }

    public LayoutStyle display(TaffyDisplay display) {
        style.display = display;
        return changed();
    }

    // ==================== 尺寸 ====================

    public LayoutStyle width(float width) {
        style.size.width = dimension(width);
        return changed();
    }

    public LayoutStyle height(float height) {
        style.size.height = dimension(height);
        return changed();
    }

    public LayoutStyle size(float width, float height) {
        style.size = TaffySize.of(dimension(width), dimension(height));
        return changed();
    }

    public LayoutStyle widthAuto() {
        return width(AUTO);
    }

    public LayoutStyle heightAuto() {
        return height(AUTO);
    }

    public LayoutStyle widthPercent(float percent) {
        style.size.width = TaffyDimension.percent(percent / 100f);
        return changed();
    }

    public LayoutStyle heightPercent(float percent) {
        style.size.height = TaffyDimension.percent(percent / 100f);
        return changed();
    }

    public LayoutStyle minWidth(float width) {
        style.minSize.width = width < 0 ? TaffyDimension.ZERO : TaffyDimension.length(width);
        return changed();
    }

    public LayoutStyle minHeight(float height) {
        style.minSize.height = height < 0 ? TaffyDimension.ZERO : TaffyDimension.length(height);
        return changed();
    }

    public LayoutStyle maxWidth(float width) {
        style.maxSize.width = dimension(width);
        return changed();
    }

    public LayoutStyle maxHeight(float height) {
        style.maxSize.height = dimension(height);
        return changed();
    }

    // ==================== 内外边距、间距 ====================

    public LayoutStyle paddingAll(float padding) {
        style.padding = TaffyRect.all(LengthPercentage.length(padding));
        return changed();
    }

    public LayoutStyle paddingTop(float padding) {
        style.padding.top = LengthPercentage.length(padding);
        return changed();
    }

    public LayoutStyle paddingBottom(float padding) {
        style.padding.bottom = LengthPercentage.length(padding);
        return changed();
    }

    public LayoutStyle paddingLeft(float padding) {
        style.padding.left = LengthPercentage.length(padding);
        return changed();
    }

    public LayoutStyle paddingRight(float padding) {
        style.padding.right = LengthPercentage.length(padding);
        return changed();
    }

    public LayoutStyle paddingHorizontal(float padding) {
        style.padding.left = LengthPercentage.length(padding);
        style.padding.right = LengthPercentage.length(padding);
        return changed();
    }

    public LayoutStyle paddingVertical(float padding) {
        style.padding.top = LengthPercentage.length(padding);
        style.padding.bottom = LengthPercentage.length(padding);
        return changed();
    }

    public LayoutStyle marginAll(float margin) {
        style.margin = TaffyRect.all(LengthPercentageAuto.length(margin));
        return changed();
    }

    public LayoutStyle marginTop(float margin) {
        style.margin.top = LengthPercentageAuto.length(margin);
        return changed();
    }

    public LayoutStyle marginBottom(float margin) {
        style.margin.bottom = LengthPercentageAuto.length(margin);
        return changed();
    }

    public LayoutStyle marginLeft(float margin) {
        style.margin.left = LengthPercentageAuto.length(margin);
        return changed();
    }

    public LayoutStyle marginRight(float margin) {
        style.margin.right = LengthPercentageAuto.length(margin);
        return changed();
    }

    /** 行、列间距都设为 {@code gap}（对应 LDLib2 {@code gapAll}）。 */
    public LayoutStyle gapAll(float gap) {
        style.gap = TaffySize.all(LengthPercentage.length(gap));
        return changed();
    }

    /** 纵向相邻行之间的间距（{@code row-gap}）。 */
    public LayoutStyle rowGap(float gap) {
        style.gap.height = LengthPercentage.length(gap);
        return changed();
    }

    /** 横向相邻列之间的间距（{@code column-gap}）。 */
    public LayoutStyle columnGap(float gap) {
        style.gap.width = LengthPercentage.length(gap);
        return changed();
    }

    // ==================== flex ====================

    /**
     * CSS {@code flex: n}（对应 LDLib2 {@code flex(n)}）：从 0 起按权重分走主轴上的全部剩余空间，放不下时同样按权重收缩。
     * 行里"吃满剩下宽度"的名称、输入框用它，内容再长也不会把行撑出去。
     */
    public LayoutStyle flex(float weight) {
        style.flexGrow = weight;
        style.flexShrink = 1;
        style.flexBasis = TaffyDimension.ZERO;
        return changed();
    }

    /** 主轴上按权重分走剩余空间（CSS {@code flex-grow}），起点是自身尺寸。 */
    public LayoutStyle flexGrow(float grow) {
        style.flexGrow = grow;
        return changed();
    }

    public LayoutStyle flexShrink(float shrink) {
        style.flexShrink = shrink;
        return changed();
    }

    public LayoutStyle flexBasis(float basis) {
        style.flexBasis = dimension(basis);
        return changed();
    }

    // ==================== 对齐 ====================

    public LayoutStyle alignItems(AlignItems align) {
        style.alignItems = align;
        return changed();
    }

    public LayoutStyle alignSelf(AlignItems align) {
        style.alignSelf = align;
        return changed();
    }

    public LayoutStyle alignContent(AlignContent align) {
        style.alignContent = align;
        return changed();
    }

    public LayoutStyle justifyContent(AlignContent justify) {
        style.justifyContent = justify;
        return changed();
    }

    public LayoutStyle alignStart() {
        return alignItems(AlignItems.FLEX_START);
    }

    public LayoutStyle alignCenter() {
        return alignItems(AlignItems.CENTER);
    }

    public LayoutStyle alignEnd() {
        return alignItems(AlignItems.FLEX_END);
    }

    public LayoutStyle alignStretch() {
        return alignItems(AlignItems.STRETCH);
    }

    // ==================== 定位 ====================

    public LayoutStyle positionType(TaffyPosition position) {
        style.position = position;
        return changed();
    }

    public LayoutStyle left(float value) {
        style.inset.left = LengthPercentageAuto.length(value);
        return changed();
    }

    public LayoutStyle top(float value) {
        style.inset.top = LengthPercentageAuto.length(value);
        return changed();
    }

    public LayoutStyle right(float value) {
        style.inset.right = LengthPercentageAuto.length(value);
        return changed();
    }

    public LayoutStyle bottom(float value) {
        style.inset.bottom = LengthPercentageAuto.length(value);
        return changed();
    }

    // ==================== 读取声明值 ====================

    /** 声明的宽度（像素）；auto 或百分比时返回 {@link #AUTO}。 */
    public int declaredWidth() {
        return style.size.width.isLength() ? Math.round(style.size.width.getValue()) : AUTO;
    }

    public int declaredHeight() {
        return style.size.height.isLength() ? Math.round(style.size.height.getValue()) : AUTO;
    }

    public int declaredMinWidth() {
        return style.minSize.width.isLength() ? Math.round(style.minSize.width.getValue()) : 0;
    }

    int horizontalPadding() {
        return Math.round(lengthOf(style.padding.left) + lengthOf(style.padding.right));
    }

    int verticalPadding() {
        return Math.round(lengthOf(style.padding.top) + lengthOf(style.padding.bottom));
    }

    private static float lengthOf(LengthPercentage value) {
        return value.isLength() ? value.getValue() : 0;
    }

    public boolean isDisplayed() {
        return style.display != TaffyDisplay.NONE;
    }
}
