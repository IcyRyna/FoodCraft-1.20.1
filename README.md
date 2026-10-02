# FoodCraft 食物工艺 · Minecraft 1.20.1

FoodCraft 1.7.10 的 Forge / Fabric 移植版，版本 **2.0.0**。

## 下载与安装

在 [Releases](https://github.com/IcyRyna/FoodCraft-1.20.1/releases/latest) 下载对应加载器的 JAR：

- Forge：Minecraft 1.20.1 + Forge 47.4.10。
- Fabric：Minecraft 1.20.1 + Fabric Loader 0.19.5 + Fabric API 0.92.2+1.20.1。
- JEI 15.12.2.51 和 CraftTweaker 14.0.60 为可选兼容；JEI 配方界面在客户端使用。

将对应 JAR 放入实例的 `mods` 文件夹。专用服务器使用相同加载器及其依赖。
源码及资源均在本仓库，Releases 另附完整源码包及 SHA-256 校验文件。

## 内容

九种机器、作物和果树、独有食物与饮料、工具、机器配方和容器返还机制。
羊肉与熟羊肉使用原版物品。面向 1.20.1 新存档及本移植版存档。

机器说明见 [MACHINES.md](docs/MACHINES.md)，内容对照见 [content-manifest.csv](docs/content-manifest.csv)。

## 构建

安装 JDK 17，设置 `JAVA_HOME`，然后执行：

```sh
./gradlew :core:test :forge:build :fabric:build
```

Windows 使用 `gradlew.bat`。构建产物位于 `forge/build/libs` 和 `fabric/build/libs`。
Gradle Wrapper 固定 8.8；ForgeGradle 6.0.42、Fabric Loom 1.7.4。
源码中已包含最终资源，常规构建无需重新运行旧版内容生成器。

## 验证与已知边界

历史四轮验收和本次源码恢复校对的范围见 [VERIFICATION.md](docs/VERIFICATION.md)。
曾有一次 Forge 立即重连的 FML 登录超时，根因未定位；等待旧连接关闭后的回归通过。
macOS 缺少实机，未验证；任意整合包组合、磁盘写入故障及无限期运行不属于已通过范围。

## 原作者与许可证

原项目：[FlyInTheSky10/FoodCraft](https://github.com/FlyInTheSky10/FoodCraft)，
基线提交 `523515c4988e485adbcf5eb51c73caec71d5361d`。
保留原 GPL 许可证和原作者署名，见 [LICENSE](LICENSE) 与 [NOTICE.md](NOTICE.md)。
本移植版本由 IcyRyna 发布。
