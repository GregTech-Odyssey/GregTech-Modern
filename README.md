# GregTech Modern GTO Edition

本项目是为 [GregTech Odyssey](https://github.com/GregTech-Odyssey) 整合包专门维护的格雷科技现代版（GregTech CEu Modern）版本，在原版基础上针对整合包的玩法与运行环境做了大量的适配与调整。

格雷科技现代版的原作者为 GregTechCEu 团队及各位贡献者，原项目地址：<https://github.com/GregTechCEu/GregTech-Modern>。本版本的改动均由 GregTech Odyssey 团队维护，并非原作者发布的版本；如在使用中遇到问题，烦请反馈至 [GregTech Odyssey 整合包仓库](https://github.com/GregTech-Odyssey/GregTech-Odyssey/issues)，以免给原项目带来不便。

模组 ID 仍为 `gtceu`，与原版及依赖原版的附属模组保持兼容。

## 开发者

本版本发布在 GregTech Odyssey 的 Maven 仓库：

```groovy
repositories {
    maven {
        url = 'https://gtodyssey.com/maven/releases'
        content {
            includeGroup 'com.gregtechceu.gtceu'
        }
    }
}

dependencies {
    implementation fg.deobf("com.gregtechceu.gtceu:gtceu-1.20.1-forge-1.20.1:${gtm_version}")
}
```

参与开发需要 IntelliJ IDEA 的 [Lombok 插件](https://plugins.jetbrains.com/plugin/6317-lombok)，推荐同时安装 [Minecraft Development 插件](https://plugins.jetbrains.com/plugin/8327-minecraft-development)。

## 许可证

本项目依据 [GNU LGPL v3.0](LICENSE) 开源，与原项目相同。

## Credited Works
- Most textures are originally from [Gregtech: Refreshed](https://modrinth.com/resourcepack/gregtech-refreshed) by @ULSTICK. With some consistency edits and additions by @Ghostipedia.
- Some textures are originally from the **[ZedTech GTCEu Resourcepack](https://github.com/brachy84/zedtech-ceu)**, with some changes made by the community.
- New material item textures by @TTFTCUTS and @Rosethorns.
- Wooden Forms, World Accelerators, and the Extreme Combustion Engine are from the **[GregTech: New Horizons Modpack](https://www.curseforge.com/minecraft/modpacks/gt-new-horizons)**.
- Primitive Water Pump is from the **[IMPACT: GREGTECH EDITION Modpack](https://gt-impact.github.io/#/)**.
- Ender Fluid Link Cover, Auto-Maintenance Hatch, Optical Fiber, and Data Bank Textures are from **[TecTech](https://github.com/Technus/TecTech)**.
- Steam Grinder is from **[GregTech++](https://www.curseforge.com/minecraft/mc-mods/gregtech-gt-gtplusplus)**.
- Certificate of Not Being a Noob Anymore is from **[Crops++](https://www.curseforge.com/minecraft/mc-mods/berries)**.

See something we forgot to credit? Reach out to us on Discord, or open an issue and ask for appropriate credit, we will happily mark it here
