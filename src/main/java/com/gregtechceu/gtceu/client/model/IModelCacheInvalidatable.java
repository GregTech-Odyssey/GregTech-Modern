package com.gregtechceu.gtceu.client.model;

/** 资源代际变化时失效模型缓存，由 {@code IModelRendererMixin} 为所有 LDLib {@code IModelRenderer} 实现。 */
public interface IModelCacheInvalidatable {

    void invalidateStaleCaches();

    default void onCachesInvalidated() {}
}
