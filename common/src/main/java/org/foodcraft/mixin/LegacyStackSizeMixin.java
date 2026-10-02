package org.foodcraft.mixin;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.SnowballItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin({EggItem.class,SnowballItem.class})
public abstract class LegacyStackSizeMixin {
    // The legacy mod raised these two vanilla stack limits before registering recipes.
    // Constructor names are stable across both mappings; no fragile field accessor is needed.
    @ModifyVariable(method="<init>",at=@At("HEAD"),argsOnly=true,ordinal=0,remap=false)
    private static Item.Properties foodcraft$legacyStackLimit(Item.Properties properties){
        return properties.stacksTo(64);
    }
}
