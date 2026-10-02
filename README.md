# FoodCraft 食物工艺 · Minecraft 1.20.1

FoodCraft 食物工艺把种植、食材加工、烹饪和酿造连成一条完整的食物生产链。你可以种水稻和蔬菜、栽果树，再把收获的食材做成中式主食、甜点、果汁与酒。

## 玩法内容

- **农作物与果树**：种植水稻、糯稻、玉米、花生、豆类、番茄、辣椒等作物，栽种果树、收获水果，为厨房准备原料。
- **九种加工设备**：碾磨机、菜板、锅、平底锅、高压锅、油炸机、饮料制作机、酿桶和灶炉，覆盖磨粉、分切、烹饪、油炸、调制饮料和酿造。
- **丰富的食物与饮品**：制作米饭、馒头、饺子、过桥米线等主食，尝试果酱、月饼、夹心饼干、果味蛋糕，以及果汁、豆浆、咖啡和红酒。
- **有细节的加工过程**：熟鸡肉放入菜板的不同食材槽，会分切出不同部位；锅和平底锅需要灶炉供热并控制火候，过热会烧焦。不同设备还有燃料、水、油、冰和加工时间的要求。
- **工具与自动化**：使用菜刀、扳手和净化水桶，给机器自动投料并提取成品，把农场的收获接入食物加工生产线。

机器操作见 [MACHINES.md](docs/MACHINES.md)，完整内容对照见 [content-manifest.csv](docs/content-manifest.csv)。

## 下载与安装

在 [Releases](https://github.com/IcyRyna/FoodCraft-1.20.1/releases/latest) 下载对应加载器的 JAR：

当前版本 **2.0.0**，由原 FoodCraft 1.7.10 移植至 Minecraft 1.20.1。

- Forge：Minecraft 1.20.1 + Forge 47.4.10。
- Fabric：Minecraft 1.20.1 + Fabric Loader 0.19.5 + Fabric API 0.92.2+1.20.1。
- JEI 15.12.2.51 和 CraftTweaker 14.0.60 为可选兼容；JEI 配方界面在客户端使用。

将对应 JAR 放入实例的 `mods` 文件夹。专用服务器使用相同加载器及其依赖。
源码及资源均在本仓库，Releases 另附完整源码包及 SHA-256 校验文件。
羊肉与熟羊肉使用原版物品。面向 1.20.1 新存档及本移植版存档。

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
