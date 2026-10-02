// Executable compatibility regression. Use only in an isolated validation instance.
<recipetype:foodcraft:milling_machine>.removeByName("foodcraft:milling_machine/000_fan");
<recipetype:foodcraft:milling_machine>.addJsonRecipe("foodcraft_probe", {
    "type": "foodcraft:milling_machine",
    "inputs": [{"slot": 0, "count": 2, "ingredient": {"item": "minecraft:stick"}}],
    "exclusive_slots": [0],
    "result": {"item": "minecraft:bread", "count": 2},
    "time": 5,
    "water": 0
});
println("FOODCRAFT_CRAFTTWEAKER_SCRIPT_LOADED");
