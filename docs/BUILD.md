# 构建与独立实例

## 固定版本

| 工具 / 依赖 | 版本 |
|---|---|
| Java | 17；本机使用 Microsoft 17.0.20.1+1 |
| Gradle Wrapper | 8.8；发行 ZIP 的 SHA-256 已写入 wrapper 配置 |
| ForgeGradle | 6.0.42 |
| Fabric Loom | 1.7.4 |
| Minecraft | 1.20.1，Mojang 官方映射 |
| Forge | 47.4.10 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.92.2+1.20.1 |
| 可选 JEI | 15.12.2.51 |
| 可选 CraftTweaker | 14.0.60 |
| 历史资源重新生成 | Python 3.10+、Pillow 12.3.0；普通构建无需 Pillow |

最初计划的 Fabric API 0.92.12 与 JEI 15.62 制品使用更新的 Loom 元数据，Loom 1.7.4 拒绝其映射依赖。实际构建验证后将 API 和 JEI 固定为上表兼容版本，保留原定的 Gradle / ForgeGradle / Loom 组合。CraftTweaker 14.0.60 已用实际发布 JAR 验证。

## 工程结构

- `core/`：纯 Java 的库存消耗与产出计划、输出边界、快照一致性和火候判断；独立 JUnit 测试。
- `common/`：双端共同编译的注册目录、农业、食物、机器、菜单、配方、资源、JEI 客户端插件与验证夹具。
- `forge/`：延迟注册、配置、客户端入口、Capability、事件与 GameTest 入口。
- `fabric/`：直接注册、配置、客户端入口、Transfer API、事件与 GameTest 入口。
- `tools/`：源清单生成、资源校验、双端运行清单比较、独立客户端启动和构建环境脚本。

两端直接编译共同源码，没有额外的跨加载器运行时依赖。屏幕和 JEI 代码只由客户端入口引用，专用服务器可独立加载。

工程位于 `outputs/` 时默认使用其上层工作区的 `work/`；源码包解压到其他目录时默认使用工程相邻的 `foodcraft-work/`。PowerShell 脚本、Gradle 运行实例与客户端启动器均遵守 `FOODCRAFT_WORK_DIR`，不会假定使用者保留本机的目录层级。

交付源码包已在工程外的另一个目录解压，使用独立 JDK 17 实际执行 `:core:test :forge:build :fabric:build`；两个重建 JAR 的 SHA-256 与交付 JAR 完全相同。该次重建复用已校验的 Gradle 下载缓存，未重新测试空缓存网络下载。第二轮证据见 [重建日志](evidence/round2/portable-build.log) 和 [哈希比较](evidence/round2/portable-reproducibility.json)。

## 命令

```powershell
# 脚本自动配置工程专用 JDK
.\build.ps1
.\verify.ps1
.\verify.ps1 -Soak

# 已配置 JAVA_HOME 时也可直接使用 wrapper
.\gradlew.bat :core:test -PfoodcraftLoader=none
.\gradlew.bat :forge:build -PfoodcraftLoader=forge
.\gradlew.bat :fabric:build -PfoodcraftLoader=fabric
.\gradlew.bat :forge:runGameTestServer -PfoodcraftLoader=forge
.\gradlew.bat :fabric:runGametest -PfoodcraftLoader=fabric
```

Linux / macOS 可以用 Java 17 与 `./gradlew` 执行同样的 Gradle 任务；本次实际验收环境是 Windows，其他操作系统未实际运行验证。

本机 Java 下载 Maven 时出现 TLS 传输失败，验收使用了只监听 `127.0.0.1` 的缓存代理。代理通过 curl 的正常 HTTPS 校验向官方 Maven 源取文件，再通过工程的 `-PfoodcraftMavenMirror` 参数提供给 Gradle；没有关闭 curl 的证书校验。ForgeGradle 独立的证书连通性探测在该环境被跳过，实际依赖下载仍经 HTTPS 校验。正常网络环境不需要该本机适配。

`gradle/verification-metadata.xml` 保存实际解析的上游依赖 SHA-256；这是本次下载后记录的校验基线，不声称全部获得了独立的上游签名证明。Loom 本地重映射、合并的 Minecraft 和 Forge 本地开发输出单独列入 `trusted-artifacts`，原因写在 XML 中：这些文件由已固定的工具与输入生成，跨机器生成的归档字节可能不同，不能当成远程依赖校验。外部原始制品继续校验。可选运行依赖和工具链的哈希见 `dependency-checksums.json`。

## 重建历史资源

```powershell
python tools/fetch_legacy.py --work ../../work
python -m pip install -r tools/requirements.txt
python tools/migrate.py --legacy ../../work/legacy/FoodCraft-523515c4988e485adbcf5eb51c73caec71d5361d
python tools/check_content.py
```

生成器先核对固定源文件的 SHA-256，再清理自身管理的生成文件，防止残留旧 ID。末尾会无损转换锅与桃子贴图，避免降低 Minecraft 图集的 mip 级别；模型 UV 同步调整。已生成的资源随源码交付，普通构建不需要下载旧源码或安装 Pillow。

缓存完整时脚本支持 `-Offline`。本机第二轮实际命令为 `verify.ps1 -Offline -MavenMirror http://127.0.0.1:8984`，并设置 `JAVA_TOOL_OPTIONS=-Dnet.minecraftforge.gradle.check.certs=false -Dfile.encoding=UTF-8 -Duser.language=en`。离线模式使用缓存中已校验的镜像坐标，无需运行缓存代理；新环境应先正常在线解析依赖。

## 发布 JAR 客户端验证

`tools/qa_launcher.py` 使用 Mojang、Forge、Fabric 的官方启动元数据，创建工程外的独立实例，加载 `dist/` 中的实际 JAR。测试用户名为 `FoodCraftQA1` / `FoodCraftQA2`，测试专用服务器只监听本机。

客户端窗口放在独立 Windows 桌面上，启动器不申请切换桌面的权限，也不调用 `SwitchDesktop`。仅隔离桌面仍不足以阻止游戏播放声音或 GLFW 改动全局光标，因此启动器还强制写入测试实例主音量 0，并在启动前安装测试专用 Java agent：拦截 GLFW 光标定位、鼠标锁定 / 原始鼠标模式、主动窗口聚焦，以及 OpenAL 音源播放。agent 不打包进 FoodCraft JAR，不修改玩家正常游戏的输入或音量。

agent 使用项目独立 JDK 17 的 javac / jar，在外部 `work/qa-guard/<源码哈希>/` 自动构建，不依赖额外库。它要求显式测试标志，未知绑定结构直接终止测试进程；完成时记录拦截数量，启动器必须读到 `glfw=true audio=true` 的本次证据。截图仍来自 Minecraft 自身的帧缓冲，GUI 测试通过游戏内部事件接口完成。

启动器为每次运行创建独立的完成标记目录，任何失败标记、进程非零退出或缺少本次完成标记都会让命令失败，避免复用上次通过的证据。

```powershell
python tools/qa_launcher.py --loader forge --java C:/path/jdk17/bin/java.exe --mode single
python tools/qa_launcher.py --loader fabric --java C:/path/jdk17/bin/java.exe --mode single --compat jei crafttweaker
python tools/qa_launcher.py --loader forge --java C:/path/jdk17/bin/java.exe --mode multi --server 127.0.0.1:25575 --username FoodCraftQA1
python tools/qa_launcher.py --loader fabric --java C:/path/jdk17/bin/java.exe --mode multi --server 127.0.0.1:25576 --username FoodCraftQA1 --compat jei crafttweaker --details
```

单人验证夹具需 `work/run-forge/saves/FoodCraftQA` 或 `work/run-fabric-client/saves/FoodCraftQA` 作为测试世界；开发 GameTest 实例可复制为该名称，复制时排除 `session.lock`。多人测试为专用服务器添加 JVM 参数 `-Dfoodcraft.qa.server=true` 才会启用测试命令与测试玩家授权，普通安装不会启用这些入口。
