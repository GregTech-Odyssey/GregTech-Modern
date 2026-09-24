package com.gregtechceu.gtceu.api.recipe.info;

import com.gto.datasynclib.datastream.DataKey2ObjectMap;

/**
 * 以 {@link RecipeInfo} 为键的映射。
 *
 * <p>
 * 查找依据是键的<b>身份</b>（{@link RecipeInfo#mixCode} 加引用比较，见 {@link DataKey2ObjectMap}），
 * 而不是 {@code hashCode}/{@code equals}。这样同名但来自不同注册表的键也能各自独立取值。
 *
 * @param <T> 键对应的值类型
 */
public final class RecipeInfoMap<T> extends DataKey2ObjectMap<RecipeInfo, T> {

    /**
     * {@link DataKey2ObjectMap#get(Object)} 的类型化重载。
     *
     * <p>
     * 父类方法被声明为 {@code final}，因此这里只能是重载而非覆写；作用是让调用点不必
     * 把 {@link RecipeInfo} 向上转型成 {@code Object}。查找逻辑与父类一致。
     *
     * <p>
     * 注意：与父类不同，本方法<b>不做 null 检查</b>，传入 {@code null} 会抛
     * {@link NullPointerException}。
     *
     * @param infoKey 内容种类键，不可为 {@code null}
     * @return 对应的值；不存在时为 {@code null}
     */
    public T get(RecipeInfo infoKey) {
        Object[] key = this.key;
        int mask = this.mask;
        Object curr;
        int pos;
        if ((curr = key[pos = infoKey.mixCode & mask]) == null) {
            return null;
        } else if (infoKey == curr) {
            return this.value[pos];
        } else {
            while ((curr = key[pos = (pos + 1) & mask]) != null) {
                if (infoKey == curr) {
                    return this.value[pos];
                }
            }
            return null;
        }
    }
}
