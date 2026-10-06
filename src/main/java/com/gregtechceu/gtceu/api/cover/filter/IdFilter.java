package com.gregtechceu.gtceu.api.cover.filter;

import com.gregtechceu.gtceu.data.lang.LangHandler;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.Level;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.InfoIcon;
import com.gregtechceu.gtceu.uipro.elements.TextField;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * 按注册 id 过滤（物品 / 流体共用这套条目处理），形状参考 {@link TagFilter}/{@link TagItemFilter}：条目存在过滤器物品
 * 自己的 NBT 里，配置界面就是一个输入框（可以写多个条目）。
 *
 * <p>
 * 条目用逗号 / 分号 / 空白分隔。<b>条目在 {@link #setIds(String)} 时就编译好</b>（拆成「命名空间前缀 + 路径前缀」的
 * 数组），匹配阶段只做 {@link String#startsWith(String)} 比较、不产生任何临时对象；再配合子类里按 AE uid 缓存的结果，
 * 同一个物品 / 流体只会真的算一次。
 *
 * <p>
 * 匹配规则：
 * <ul>
 * <li>带冒号（{@code minecraft:iron}）＝ 命名空间和路径都按前缀匹配，也就是完整 id 的前缀；</li>
 * <li>不带冒号（{@code minecraft}）＝ 模组 id 前缀，匹配该模组全部；同时也当路径前缀用（{@code iron} 能命中各模组的
 * {@code iron_*}）。</li>
 * </ul>
 */
public abstract class IdFilter<T, S extends Filter<T, S>> implements Filter<T, S> {

    private static final Pattern SEPARATOR = Pattern.compile("[\\s,;]+");
    private static final IdEntry[] NO_ENTRIES = new IdEntry[0];

    /** 玩家写的原文（输入框直接绑这个）。 */
    @Getter
    protected String idFilterExpression = "";
    /** 解析后的条目：小写、去空、去重、保持书写顺序（保存 / 显示用）。 */
    protected List<String> entries = List.of();
    /** 编译好的匹配结构（匹配只用它）。 */
    private IdEntry[] parsed = NO_ENTRIES;
    protected Consumer<S> itemWriter = filter -> {};
    protected Consumer<S> onUpdated = filter -> itemWriter.accept(filter);

    protected IdFilter() {}

    @Override
    public boolean isBlank() {
        return parsed.length == 0;
    }

    public CompoundTag saveFilter() {
        if (isBlank()) {
            return null;
        }
        var tag = new CompoundTag();
        tag.putString("ids", String.join(", ", entries));
        return tag;
    }

    /** 写条目；输入框里保留玩家原文，匹配用的是编译后的数组。 */
    public void setIds(String text) {
        idFilterExpression = text == null ? "" : text;
        entries = parse(text);
        parsed = compile(entries);
        onUpdated.accept((S) this);
    }

    /** 读配置用：和 {@link #setIds} 一样，只是不触发 {@code onUpdated}（加载时别回头写物品 NBT）。 */
    protected void loadIds(String text) {
        idFilterExpression = text == null ? "" : text;
        entries = parse(text);
        parsed = compile(entries);
    }

    /** 把输入框的文本切成条目：按逗号 / 分号 / 空白分隔，转小写、去空、去重。 */
    public static List<String> parse(String text) {
        if (text == null || text.isBlank()) return List.of();
        var result = new LinkedHashSet<String>();
        for (String part : SEPARATOR.split(text.trim().toLowerCase(Locale.ROOT))) {
            if (!part.isEmpty()) result.add(part);
        }
        return List.copyOf(result);
    }

    private static IdEntry[] compile(List<String> entries) {
        if (entries.isEmpty()) return NO_ENTRIES;
        var out = new ArrayList<IdEntry>(entries.size() * 2);
        for (String entry : entries) {
            int colon = entry.indexOf(':');
            if (colon < 0) {
                // 不带冒号：既当模组 id（命名空间前缀），也当路径前缀（iron -> 各模组的 iron_*）
                out.add(new IdEntry(entry, ""));
                out.add(new IdEntry(null, entry));
            } else {
                out.add(new IdEntry(entry.substring(0, colon), entry.substring(colon + 1)));
            }
        }
        return out.toArray(NO_ENTRIES);
    }

    /**
     * 一条原文是否命中某个 id（单条便捷入口；过滤器内部走编译好的数组，不会每次重新编译）。
     */
    public static boolean idMatches(String entry, ResourceLocation id) {
        if (entry == null || entry.isEmpty()) return false;
        for (IdEntry compiled : compile(parse(entry))) {
            if (compiled.matches(id)) return true;
        }
        return false;
    }

    /** 编译后的条目：命名空间前缀（{@code null} 表示不限）和路径前缀（空串表示不限），都是前缀匹配。 */
    private record IdEntry(String namespace, String path) {

        boolean matches(ResourceLocation id) {
            if (namespace != null && !id.getNamespace().startsWith(namespace)) return false;
            return path.isEmpty() || id.getPath().startsWith(path);
        }
    }

    /** 写的条目里有没有命中这个 id 的（无分配：数组遍历 + 前缀比较）。 */
    protected boolean matchesAny(ResourceLocation id) {
        for (IdEntry entry : parsed) {
            if (entry.matches(id)) return true;
        }
        return false;
    }

    @Override
    public void setOnUpdated(Consumer<S> onUpdated) {
        this.onUpdated = filter -> {
            this.itemWriter.accept(filter);
            onUpdated.accept(filter);
        };
    }

    @Override
    public Widget createConfigUI() {
        var field = TextField.of(0, this::getIdFilterExpression, this::setIds);
        field.layout(l -> l.flexGrow(1));
        var row = UIElement.centeredRow(UISizes.SLOT_SIZE).addChildren(field,
                InfoIcon.of(Level.NORMAL, LangHandler.getMultiLang("cover.id_filter.info").toArray(new Component[0])));
        return UIElement.column(LayoutStyle.AUTO).layout(l -> l.gapAll(UISizes.GAP)).addChild(row);
    }
}
