package org.foodcraft.test;

import com.google.gson.*;
import net.minecraft.server.level.ServerLevel;
import org.foodcraft.Catalog;
import org.foodcraft.FoodCraft;
import org.foodcraft.recipe.MachineRecipe;
import java.nio.file.*;
import java.util.Comparator;

/** Canonical data exported from real registries and the active server recipe manager. */
public final class VerificationSnapshot {
    private VerificationSnapshot(){}
    public static void export(ServerLevel level){
        String directory=System.getProperty("foodcraft.export.dir","");if(directory.isEmpty())return;
        JsonObject snapshot=new JsonObject();JsonArray items=new JsonArray(),recipes=new JsonArray(),blocks=new JsonArray();
        Catalog.ENTRIES.stream().filter(entry->!entry.kind().equals("debug")).sorted(Comparator.comparing(Catalog.Entry::id)).forEach(entry->{
            var item=FoodCraft.item(entry.id());JsonObject value=new JsonObject();value.addProperty("id",entry.id());value.addProperty("stack_size",item.getMaxStackSize());value.addProperty("durability",item.getMaxDamage());
            var food=item.getFoodProperties();if(food!=null){value.addProperty("nutrition",food.getNutrition());value.addProperty("saturation",food.getSaturationModifier());value.addProperty("always_edible",food.canAlwaysEat());}
            items.add(value);
        });
        Catalog.ENTRIES.stream().filter(Catalog.Entry::block).sorted(Comparator.comparing(Catalog.Entry::id)).forEach(entry->{
            var state=FoodCraft.BLOCKS.get(entry.path()).defaultBlockState();JsonObject value=new JsonObject();value.addProperty("id",entry.id());
            value.addProperty("hardness",state.getDestroySpeed(level,net.minecraft.core.BlockPos.ZERO));value.addProperty("step_sound",state.getSoundType().getStepSound().getLocation().toString());
            var shape=state.getShape(level,net.minecraft.core.BlockPos.ZERO).bounds();value.add("selection_bounds",new Gson().toJsonTree(new double[]{shape.minX,shape.minY,shape.minZ,shape.maxX,shape.maxY,shape.maxZ}));blocks.add(value);
        });
        level.getRecipeManager().getRecipes().stream().filter(recipe->recipe.getId().getNamespace().equals("foodcraft")).sorted(Comparator.comparing(recipe->recipe.getId().toString())).forEach(recipe->{
            JsonObject value=new JsonObject();value.addProperty("id",recipe.getId().toString());value.addProperty("type",recipe.getType().toString());
            var result=recipe.getResultItem(level.registryAccess());value.addProperty("result",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(result.getItem()).toString());value.addProperty("count",result.getCount());
            JsonArray inputs=new JsonArray();
            if(recipe instanceof MachineRecipe machine){
                for(var input:machine.inputs){JsonObject ingredient=new JsonObject();ingredient.addProperty("slot",input.slot());ingredient.addProperty("count",input.count());ingredient.add("ingredient",input.ingredient().toJson());inputs.add(ingredient);}
                value.add("exclusive",new Gson().toJsonTree(machine.exclusive));value.addProperty("time",machine.time);value.addProperty("water",machine.water);value.addProperty("milk",machine.milk);value.addProperty("cold",machine.cold);value.addProperty("min_heat",machine.minHeat);value.addProperty("max_heat",machine.maxHeat);
            }else{
                recipe.getIngredients().forEach(ingredient->inputs.add(ingredient.toJson()));
                if(recipe instanceof net.minecraft.world.item.crafting.AbstractCookingRecipe cooking){value.addProperty("cooking_time",cooking.getCookingTime());value.addProperty("experience",cooking.getExperience());}
                if(recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped){value.addProperty("width",shaped.getWidth());value.addProperty("height",shaped.getHeight());}
            }
            value.add("inputs",inputs);recipes.add(value);
        });
        snapshot.add("items",items);snapshot.add("recipes",recipes);snapshot.add("blocks",blocks);
        try{Path path=Path.of(directory).resolve("runtime-content.json");Files.createDirectories(path.getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(snapshot));}
        catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        System.out.println("FOODCRAFT RUNTIME SNAPSHOT items="+items.size()+" recipes="+recipes.size());
    }
}
