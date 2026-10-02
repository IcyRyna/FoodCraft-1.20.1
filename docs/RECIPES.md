# 数据包与 CraftTweaker 配方接口

普通合成使用原版 shaped / shapeless 配方。机器配方使用 8 个独立的类型：`foodcraft:milling_machine`、`foodcraft:cutting_board`、`foodcraft:pot`、`foodcraft:frying_pan`、`foodcraft:pressure_cooker`、`foodcraft:deep_fryer`、`foodcraft:drink_maker`、`foodcraft:fermenting_barrel`。

灶炉使用燃料表，没有独立食物配方类型。

## JSON 格式

```json
{
  "type": "foodcraft:milling_machine",
  "inputs": [
    {"slot": 0, "count": 2, "ingredient": {"item": "minecraft:wheat"}}
  ],
  "exclusive_slots": [0],
  "result": {"item": "foodcraft:mianfen", "count": 2},
  "time": 200,
  "water": 0,
  "milk": false,
  "cold": false,
  "min_heat": 0,
  "max_heat": 1000,
  "experience": 0.0
}
```

`inputs` 的 `slot` 是机器槽位索引，不能用输出槽或重复指定同一槽。`ingredient` 支持原版物品、物品标签与原版 Ingredient 数组。`count` 必须为 1–64；成品数量不能超过物品最大堆叠数。

`exclusive_slots` 中没有被本配方使用的槽位必须为空，用来保留旧菜板、锅、酿桶对食材顺序和空槽的要求。省略时不额外检查空槽。

`time` 为正整数 tick；省略时使用机器默认值。`water` 为 0–8 的完整液体单位。`milk` 和 `cold` 用于饮料机。`min_heat` / `max_heat` 为锅和平底锅所需的累计热量区间，边界包含在成功范围内。无效数据会在加载时明确报错。

在数据包的 `data/<namespace>/recipes/<path>.json` 保存配方。用相同 ID 覆盖旧配方，或创建新 ID 添加配方，然后执行 `/reload`。机器核对输入、数量、输出 NBT、时长、液体、火候等内容指纹；内容变化会归零该批进度，即使输出槽阻塞也立即更新。内容未改变的存档继续原进度。GUI 客户端使用原版配方同步，JEI 更新被替换的机器配方。

## 标签兼容

原 OreDictionary 名称转换为 `foodcraft:legacy/...` 标签，通用矿物与木材使用 `forge:ingots/...`、`forge:gems/...` 和原版木板、原木标签。Fabric 同时包含 `c` 对应标签。两端可继续使用这些标签，也可在数据包中扩展标签的物品列表。

菜板的合成使用 `minecraft:planks`，支持原版全部木板类别。外部整合包可以把其他模组食材加入对应标签，不需要修改 Java 代码。

## CraftTweaker 14.0.60

无需安装 CraftTweaker 时也能启动。安装后，通用 `IRecipeManager` 的 JSON 接口可以修改本模组的注册 RecipeType：

```zenscript
<recipetype:foodcraft:milling_machine>.removeByName("foodcraft:milling_machine/000_fan");
<recipetype:foodcraft:milling_machine>.addJsonRecipe("custom_flour", {
    "type": "foodcraft:milling_machine",
    "inputs": [{"slot": 0, "count": 2, "ingredient": {"item": "minecraft:wheat"}}],
    "exclusive_slots": [0],
    "result": {"item": "foodcraft:mianfen", "count": 2},
    "time": 200
});
```

把脚本放入实例的 `scripts/`，按 [CraftTweaker 1.20.1 文档](https://docs.blamejared.com/1.20.1/en/getting_started/) 加载或重载脚本。添加后的 ID 位于 `crafttweaker:` 命名空间。

`examples/crafttweaker/validation.zs` 是已执行通过的验收脚本：删除原碾磨米饭配方，并新增 2 根木棍加工为 2 个面包、耗时 5 tick 的测试配方。它用于独立验收实例，不作为普通玩法的默认配方。
