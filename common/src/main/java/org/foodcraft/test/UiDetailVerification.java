package org.foodcraft.test;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.foodcraft.FoodCraft;
import org.foodcraft.machine.MachineKind;
import org.foodcraft.machine.MachineMenu;
import org.foodcraft.recipe.MachineRecipe;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;

/** Uses JEI's real rendered transfer button event; never calls an OS input API. */
public final class UiDetailVerification {
    private static List<MachineRecipe> recipes;
    private static int index, phase, ticks;
    private static boolean maximumPass,reloadRequested;
    private static MachineRecipe beforeReload;
    private static String confirmedTransfer="";
    private static String preparedRecipe="";
    private static int preparedMenu=-1;
    private static int localeIndex, heatIndex, heatPhase, heatTicks;
    private static String heatRecipe="";
    private static java.util.concurrent.CompletableFuture<Void> reload;
    private static int chunkPhase,chunkTicks;
    private static final java.util.concurrent.atomic.AtomicBoolean chunkUnloaded=new java.util.concurrent.atomic.AtomicBoolean(),chunkVerified=new java.util.concurrent.atomic.AtomicBoolean();
    private static final String[] LOCALES={"en_us","zh_cn","zh_tw"};
    private UiDetailVerification() {}

    public static boolean tick(Minecraft mc) {
        if(localeIndex<LOCALES.length){
            if(reload==null){
                mc.getLanguageManager().setSelected(LOCALES[localeIndex]);mc.options.languageCode=LOCALES[localeIndex];
                reload=mc.reloadResourcePacks();return false;
            }
            if(!reload.isDone()||mc.getOverlay()!=null)return false;
            reload.join();verifyBakedModels(mc,LOCALES[localeIndex]);localeIndex++;reload=null;return false;
        }
        if(chunkPhase<4){verifyChunkLifecycle(mc);return false;}
        if(heatIndex<6){verifyHeatControl(mc);return false;}
        if(!FoodCraft.platform.modLoaded("jei"))return true;
        if(recipes==null){
            recipes=FoodCraft.RECIPE_TYPES.values().stream().flatMap(type->mc.level.getRecipeManager().getAllRecipesFor(type).stream())
                    .sorted(Comparator.comparing((MachineRecipe recipe)->!recipe.getId().getNamespace().equals("crafttweaker")).thenComparing(recipe->recipe.getId().toString())).toList();
        }
        if(index>=recipes.size()){
            if(maximumPass){System.out.println("FOODCRAFT DETAIL JEI MAXIMUM BUTTONS PASS types="+recipes.size()+" batches=3 reload=true native_events=true");return true;}
            if(!reloadRequested){
                reloadRequested=true;beforeReload=recipes.get(0);System.out.println("FOODCRAFT DETAIL JEI BUTTONS PASS recipes="+recipes.size()+" native_events=true");
                mc.player.connection.sendCommand("reload");
                ticks=0;return false;
            }
            ticks++;
            if(mc.level.getRecipeManager().byKey(beforeReload.getId()).orElse(null)==beforeReload){if(ticks>1200)throw new IllegalStateException("Native recipe reload did not synchronize");return false;}
            org.foodcraft.compat.FoodCraftJei.checkReload();if(!org.foodcraft.compat.FoodCraftJei.verifyRuntime())return false;
            recipes=FoodCraft.RECIPE_TYPES.values().stream().flatMap(type->mc.level.getRecipeManager().getAllRecipesFor(type).stream()).sorted(Comparator.comparing(r->r.getId().toString())).collect(java.util.stream.Collectors.toMap(r->r.kind,r->r,(first,next)->first,()->new java.util.EnumMap<MachineKind,MachineRecipe>(MachineKind.class))).values().stream().toList();
            maximumPass=true;index=0;phase=0;ticks=0;System.out.println("FOODCRAFT DETAIL RECIPE RELOAD PASS jei_runtime=true");return false;
        }
        ticks++;var recipe=recipes.get(index);
        if(phase==0){
            preparedRecipe="";preparedMenu=-1;
            mc.player.connection.sendCommand("foodcraftqa transfer_prepare "+recipe.getId());
            phase=1;ticks=0;return false;
        }
        if(phase==1){
            if(!(mc.player.containerMenu instanceof MachineMenu menu)||menu.kind!=recipe.kind||menu.containerId!=preparedMenu||!preparedRecipe.equals(recipe.getId().toString())){
                if(ticks>300)throw new IllegalStateException("JEI test menu failed to open "+recipe.getId());return false;
            }
            if(ticks<12)return false;
            org.foodcraft.compat.FoodCraftJei.showRecipeForVerification(recipe);phase=2;ticks=0;return false;
        }
        if(phase==2){
            if(ticks<12)return false;
            System.setProperty("foodcraft.qa.shift",Boolean.toString(maximumPass));
            try{clickJeiButton(mc);}catch(ReflectiveOperationException exception){throw new IllegalStateException("JEI native button event failed",exception);}finally{System.clearProperty("foodcraft.qa.shift");}
            phase=3;ticks=0;return false;
        }
        if(phase==3){
            if(ticks<12)return false;confirmedTransfer="";
            int batches=maximumPass?3:1;
            mc.player.connection.sendCommand("foodcraftqa "+(maximumPass?"transfer_max_assert ":"transfer_assert ")+recipe.getId());
            phase=4;ticks=0;return false;
        }
        if(!confirmedTransfer.equals(recipe.getId()+" "+(maximumPass?3:1))){if(ticks>600)throw new IllegalStateException("JEI transfer confirmation missing: "+recipe.getId());return false;}
        if(!(mc.player.containerMenu instanceof MachineMenu menu))throw new IllegalStateException("JEI closed the container instead of returning to it");
        for(int slot:recipe.kind.inputs){
            var input=recipe.inputs.stream().filter(value->value.slot()==slot).findFirst().orElse(null);
            var stack=menu.inventory.getItem(slot);
            if(input==null?!stack.isEmpty():!input.ingredient().test(stack)||stack.getCount()!=input.count()*(maximumPass?3:1))
                throw new IllegalStateException("JEI native transfer wrong count/slot "+recipe.getId()+" slot="+slot+" actual="+stack+" expected="+(input==null?0:input.count()));
        }
        System.out.println("FOODCRAFT DETAIL JEI CLIENT TRANSFER PASS "+recipe.getId());
        mc.player.closeContainer();index++;phase=0;ticks=0;return false;
    }

    public static void onSystemMessage(String message){
        if(!Boolean.getBoolean("foodcraft.qa.details"))return;
        if(message.startsWith("FOODCRAFT_TRANSFER_READY ")){
            var values=message.substring("FOODCRAFT_TRANSFER_READY ".length()).split(" ");preparedRecipe=values[0];preparedMenu=Integer.parseInt(values[1]);
        }
        if(message.startsWith("FOODCRAFT_TRANSFER_CONFIRMED "))confirmedTransfer=message.substring("FOODCRAFT_TRANSFER_CONFIRMED ".length());
        if(message.startsWith("FOODCRAFT DETAIL CHUNK UNLOADED"))chunkUnloaded.set(true);
        if(message.startsWith("FOODCRAFT DETAIL CHUNK RELOAD PASS"))chunkVerified.set(true);
    }
    private static void verifyChunkLifecycle(Minecraft mc){
        chunkTicks++;
        if(chunkPhase==0){runFixture(mc,"chunk_prepare",ClientTestFixture::chunkPrepare);chunkPhase=1;chunkTicks=0;return;}
        if(chunkPhase==1){if(chunkTicks<40)return;runFixture(mc,"chunk_leave",ClientTestFixture::chunkLeave);chunkPhase=2;chunkTicks=0;return;}
        if(chunkPhase==2){
            if(chunkTicks>1500)throw new IllegalStateException("Player-distance chunk did not actually unload");
            if(chunkUnloaded.get()){runFixture(mc,"chunk_verify",ClientTestFixture::chunkVerify);chunkPhase=3;chunkTicks=0;return;}
            if(chunkTicks%20==0)runFixture(mc,"chunk_poll",ClientTestFixture::chunkPoll);return;
        }
        if(chunkTicks>500)throw new IllegalStateException("Native chunk reload verification did not complete");
        if(chunkVerified.get())chunkPhase=4;
    }
    private static void runFixture(Minecraft mc,String command,java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){
        mc.player.connection.sendCommand("foodcraftqa "+command);
    }

    private static void verifyBakedModels(Minecraft mc,String locale){
        int items=0,states=0;
        try(var resource=mc.getResourceManager().getResource(new net.minecraft.resources.ResourceLocation("foodcraft","lang/"+locale+".json")).orElseThrow().openAsReader()){
            var translations=com.google.gson.JsonParser.parseReader(resource).getAsJsonObject();
            for(var entry:org.foodcraft.Catalog.ENTRIES){
                if(entry.kind().equals("debug"))continue;
                var item=FoodCraft.item(entry.id());var model=mc.getItemRenderer().getModel(new ItemStack(item),mc.level,mc.player,0);
                verifyModel(model,null,entry.id());items++;
                if(entry.id().startsWith("foodcraft:")&&!translations.has(item.getDescriptionId()))throw new IllegalStateException("Missing native language key "+locale+" "+item.getDescriptionId());
                if(entry.block())for(var state:FoodCraft.BLOCKS.get(entry.path()).getStateDefinition().getPossibleStates()){
                    verifyModel(mc.getBlockRenderer().getBlockModel(state),state,state.toString());states++;
                }
            }
        }catch(java.io.IOException exception){throw new java.io.UncheckedIOException(exception);}
        System.out.println("FOODCRAFT DETAIL BAKED MODELS PASS locale="+locale+" items="+items+" block_states="+states+" missing_models=0 missing_sprites=0 missing_language_keys=0");
    }

    private static void verifyModel(net.minecraft.client.resources.model.BakedModel model,net.minecraft.world.level.block.state.BlockState state,String label){
        if(model==null||model.getParticleIcon().contents().name().getPath().equals("missingno"))throw new IllegalStateException("Missing baked model/particle "+label);
        int quads=0;
        for(int side=-1;side<6;side++)for(var quad:model.getQuads(state,side<0?null:net.minecraft.core.Direction.values()[side],net.minecraft.util.RandomSource.create(1))){
            var sprite=quad.getSprite().contents();
            if(sprite.name().getPath().equals("missingno"))throw new IllegalStateException("Missing baked face sprite "+label);
            if(sprite.name().getNamespace().equals("foodcraft")&&(sprite.width()%16!=0||sprite.height()%16!=0))throw new IllegalStateException("FoodCraft sprite lowers atlas mipmap level: "+sprite.name());
            quads++;
        }
        if(quads==0&&!model.isCustomRenderer())throw new IllegalStateException("Baked model has no visible geometry "+label);
    }

    private static void verifyHeatControl(Minecraft mc){
        heatTicks++;var kind=heatIndex<3?MachineKind.POT:MachineKind.FRYING_PAN;
        int expected=new int[]{100,50,0}[heatIndex%3];
        if(heatPhase==0){
            var recipe=mc.level.getRecipeManager().getAllRecipesFor(FoodCraft.RECIPE_TYPES.get(kind)).get(0);
            heatRecipe=recipe.getId().toString();preparedRecipe="";preparedMenu=-1;
            mc.player.connection.sendCommand("foodcraftqa transfer_prepare "+recipe.getId());
            heatPhase=1;heatTicks=0;return;
        }
        if(heatPhase==1){
            if(!(mc.screen instanceof org.foodcraft.client.MachineScreen screen)||screen.getMenu().kind!=kind||screen.getMenu().containerId!=preparedMenu||!preparedRecipe.equals(heatRecipe)){if(heatTicks>300)throw new IllegalStateException("Heat test menu did not open");return;}
            if(heatTicks<6)return;
            int left=(screen.width-176)/2,top=(screen.height-166)/2,y=kind==MachineKind.POT?65:19;
            double offset=new double[]{0,6.5,13}[heatIndex%3];
            if(!screen.mouseClicked(left+88,top+y+offset,0))throw new IllegalStateException("Heat control did not handle its click region");
            heatPhase=2;heatTicks=0;return;
        }
        if(heatTicks<6)return;
        if(!(mc.player.containerMenu instanceof MachineMenu menu)||menu.containerId!=preparedMenu||menu.data.get(6)!=expected){if(heatTicks>300)throw new IllegalStateException("Heat GUI value failed to synchronize: expected="+expected);return;}
        System.out.println("FOODCRAFT DETAIL HEAT GUI PASS machine="+kind.id+" power="+expected);
        mc.player.closeContainer();heatIndex++;heatPhase=0;heatTicks=0;
    }

    private static void clickJeiButton(Minecraft mc)throws ReflectiveOperationException{
        Object screen=mc.screen;
        if(screen==null||!screen.getClass().getName().equals("mezz.jei.gui.recipes.RecipesGui"))throw new IllegalStateException("JEI recipe screen is not active");
        Object layouts=field(screen,"layouts");
        var entries=(List<?>)field(layouts,"recipeLayoutsWithButtons");
        if(entries.size()!=1)throw new IllegalStateException("Expected one explicitly selected JEI recipe, got "+entries.size());
        Object transfer=entries.get(0).getClass().getMethod("transferButton").invoke(entries.get(0));
        var button=(net.minecraft.client.gui.components.AbstractWidget)field(transfer,"button");
        if(!button.active||!button.visible)throw new IllegalStateException("JEI transfer button is disabled");
        Class<?> inputType=Class.forName("mezz.jei.gui.input.InputType");
        Object execute=java.util.Arrays.stream(inputType.getEnumConstants()).filter(value->value.toString().equals("EXECUTE")).findFirst().orElseThrow();
        Class<?> inputClass=Class.forName("mezz.jei.gui.input.UserInput");
        var optional=(java.util.Optional<?>)inputClass.getMethod("fromVanilla",double.class,double.class,int.class,inputType)
                .invoke(null,button.getX()+button.getWidth()/2.0,button.getY()+button.getHeight()/2.0,0,execute);
        Object input=optional.orElseThrow();
        Object handler=transfer.getClass().getMethod("createInputHandler").invoke(transfer);
        Class<?> contract=Class.forName("mezz.jei.gui.input.IUserInputHandler");
        var method=java.util.Arrays.stream(contract.getMethods()).filter(value->value.getName().equals("handleUserInput")).findFirst().orElseThrow();
        Object bindings=field(screen,"keyBindings");
        var result=(java.util.Optional<?>)method.invoke(handler,mc.screen,input,bindings);
        if(result.isEmpty())throw new IllegalStateException("JEI did not handle the transfer button event");
    }

    private static Object field(Object object,String name)throws ReflectiveOperationException{
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass()){
            try{Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(object);}catch(NoSuchFieldException ignored){ }
        }
        throw new NoSuchFieldException(name);
    }
}
