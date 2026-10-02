package org.foodcraft.compat;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.constants.VanillaTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.foodcraft.FoodCraft;
import org.foodcraft.machine.MachineKind;
import org.foodcraft.machine.MachineMenu;
import org.foodcraft.recipe.MachineRecipe;
import java.util.List;
import java.util.Map;
import java.util.EnumMap;

/** Optional client-only integration. No common/server entrypoint references this class. */
@JeiPlugin
public final class FoodCraftJei implements IModPlugin {
    private static final Map<MachineKind,RecipeType<MachineRecipe>> TYPES=new EnumMap<>(MachineKind.class);
    private static final Map<MachineKind,List<MachineRecipe>> registered=new EnumMap<>(MachineKind.class);
    private static IJeiRuntime runtime;
    private static int lastHash;
    private static boolean diagnosticPrinted;
    private static boolean forceRefresh;
    static{for(MachineKind kind:MachineKind.values())if(kind!=MachineKind.STOVE)TYPES.put(kind,RecipeType.create(FoodCraft.MOD_ID,kind.id,MachineRecipe.class));}
    @Override public ResourceLocation getPluginUid(){return FoodCraft.id("jei");}
    @Override public void registerCategories(IRecipeCategoryRegistration registration){
        TYPES.keySet().forEach(kind->registration.addRecipeCategories(new Category(kind,registration.getJeiHelpers().getGuiHelper())));
    }
    @Override public void registerRecipes(IRecipeRegistration registration){
        var level=Minecraft.getInstance().level;if(level==null)return;
        for(var type:TYPES.entrySet()){
            var recipes=level.getRecipeManager().getAllRecipesFor(FoodCraft.RECIPE_TYPES.get(type.getKey()));
            registration.addRecipes(type.getValue(),recipes);registered.put(type.getKey(),List.copyOf(recipes));
        }
        lastHash=level.getRecipeManager().getRecipes().hashCode();
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration){
        TYPES.forEach((kind,type)->registration.addRecipeCatalyst(new ItemStack(FoodCraft.item(kind.id)),type));
    }
    @Override public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration){
        var helper=registration.getTransferHelper();
        TYPES.forEach((kind,type)->registration.addRecipeTransferHandler(new mezz.jei.api.recipe.transfer.IRecipeTransferHandler<MachineMenu,MachineRecipe>(){
            public Class<MachineMenu> getContainerClass(){return MachineMenu.class;}
            public java.util.Optional<net.minecraft.world.inventory.MenuType<MachineMenu>> getMenuType(){return java.util.Optional.of(FoodCraft.MENUS.get(kind));}
            public RecipeType<MachineRecipe> getRecipeType(){return type;}
            public mezz.jei.api.recipe.transfer.IRecipeTransferError transferRecipe(MachineMenu menu,MachineRecipe recipe,IRecipeSlotsView slots,net.minecraft.world.entity.player.Player player,boolean maximum,boolean transfer){
                if(menu.kind!=kind||recipe.kind!=kind)return helper.createInternalError();
                var prepared=org.foodcraft.machine.MachineTransfers.prepare(menu,recipe,maximum);
                if(!prepared.successful())return helper.createUserErrorWithTooltip(Component.translatable(prepared.failure()==org.foodcraft.machine.MachineTransfers.Failure.INVENTORY_FULL
                        ?"screen.foodcraft.transfer.full":"screen.foodcraft.transfer.missing"));
                if(transfer)org.foodcraft.machine.MachineTransfers.send(new org.foodcraft.machine.MachineTransfers.Request(menu.containerId,recipe.getId(),maximum));
                return null;
            }
        },type));
    }
    @Override public void onRuntimeAvailable(IJeiRuntime value){runtime=value;diagnosticPrinted=false;forceRefresh=true;checkReload();}
    @Override public void onRuntimeUnavailable(){runtime=null;registered.clear();}
    public static boolean verifyRuntime(){
        if(runtime==null)return false;
        checkReload();
        int recipes=0;
        for(var entry:TYPES.entrySet()){
            if(runtime.getRecipeManager().getRecipeCategory(entry.getValue())==null)return false;
            long count=runtime.getRecipeManager().createRecipeLookup(entry.getValue()).get().count();
            if(count==0){
                if(!diagnosticPrinted){var mc=Minecraft.getInstance();System.out.println("FOODCRAFT JEI WAIT category="+entry.getKey()+" level="+mc.level.getRecipeManager().getAllRecipesFor(FoodCraft.RECIPE_TYPES.get(entry.getKey())).size()+" cached="+registered.getOrDefault(entry.getKey(),List.of()).size()+" including_hidden="+runtime.getRecipeManager().createRecipeLookup(entry.getValue()).includeHidden().get().count());diagnosticPrinted=true;}
                return false;
            }recipes+=count;
        }
        System.out.println("FOODCRAFT JEI PASS categories="+TYPES.size()+" recipes="+recipes);return true;
    }
    public static void showCategory(MachineKind kind){if(runtime!=null&&TYPES.containsKey(kind))runtime.getRecipesGui().showTypes(List.of(TYPES.get(kind)));}
    public static void showRecipeForVerification(MachineRecipe recipe){
        if(runtime==null)throw new IllegalStateException("JEI runtime unavailable");
        runtime.getRecipesGui().showRecipes(runtime.getRecipeManager().getRecipeCategory(TYPES.get(recipe.kind)),List.of(recipe),List.of());
    }
    public static void checkReload(){
        var level=Minecraft.getInstance().level;if(runtime==null||level==null)return;
        int hash=level.getRecipeManager().getRecipes().hashCode();if(hash==lastHash&&!forceRefresh)return;
        for(var type:TYPES.entrySet()){
            List<MachineRecipe> old=registered.getOrDefault(type.getKey(),List.of());
            var recipes=List.copyOf(level.getRecipeManager().getAllRecipesFor(FoodCraft.RECIPE_TYPES.get(type.getKey())));
            var existing=runtime.getRecipeManager().createRecipeLookup(type.getValue()).includeHidden().get().collect(java.util.stream.Collectors.toSet());
            runtime.getRecipeManager().hideRecipes(type.getValue(),old.stream().filter(recipe->!recipes.contains(recipe)).toList());
            runtime.getRecipeManager().addRecipes(type.getValue(),recipes.stream().filter(recipe->!existing.contains(recipe)).toList());
            runtime.getRecipeManager().unhideRecipes(type.getValue(),recipes);registered.put(type.getKey(),recipes);
        }
        lastHash=hash;forceRefresh=false;
    }
    private static final class Category implements IRecipeCategory<MachineRecipe>{
        private final MachineKind kind;private final IDrawable background,icon;
        Category(MachineKind kind,IGuiHelper gui){this.kind=kind;background=gui.createBlankDrawable(160,85);icon=gui.createDrawableIngredient(VanillaTypes.ITEM_STACK,new ItemStack(FoodCraft.item(kind.id)));}
        @Override public RecipeType<MachineRecipe> getRecipeType(){return TYPES.get(kind);}
        @Override public Component getTitle(){return Component.translatable("container.foodcraft."+kind.id);}
        @Override public IDrawable getBackground(){return background;}
        @Override public IDrawable getIcon(){return icon;}
        @Override public void setRecipe(IRecipeLayoutBuilder builder,MachineRecipe recipe,IFocusGroup focus){
            for(var input:recipe.inputs){int[] c=kind.layout()[input.slot()];builder.addSlot(RecipeIngredientRole.INPUT,c[1]-8,c[2]-8).setSlotName("input_"+input.slot()).addItemStacks(java.util.Arrays.stream(input.ingredient().getItems()).map(stack->stack.copyWithCount(input.count())).toList());}
            int[] output=kind.layout()[kind.outputs[0]];builder.addSlot(RecipeIngredientRole.OUTPUT,output[1]-8,output[2]-8).addItemStack(recipe.result);
            if(kind==MachineKind.CUTTING_BOARD){int[] tool=kind.layout()[0];builder.addSlot(RecipeIngredientRole.CATALYST,tool[1]-8,tool[2]-8).addItemStacks(List.of(new ItemStack(FoodCraft.item("caidao")),new ItemStack(FoodCraft.item("caidao_hj")),new ItemStack(FoodCraft.item("caidao_zs")),new ItemStack(FoodCraft.item("caidao_lbs"))));}
        }
        @Override public void draw(MachineRecipe recipe,IRecipeSlotsView slots,GuiGraphics graphics,double mouseX,double mouseY){
            var font=Minecraft.getInstance().font;
            graphics.drawString(font,Component.literal((recipe.time/20.0)+" s"),2,72,0xff666666,false);
            if(recipe.water>0)graphics.drawString(font,Component.translatable(kind==MachineKind.DEEP_FRYER?"screen.foodcraft.jei.oil":recipe.milk?"screen.foodcraft.jei.milk":"screen.foodcraft.jei.water",recipe.water),45,72,0xff666666,false);
            if(kind.heatedExternally())graphics.drawString(font,Component.literal(recipe.minHeat+"–"+recipe.maxHeat),85,72,0xff666666,false);
        }
    }
}
