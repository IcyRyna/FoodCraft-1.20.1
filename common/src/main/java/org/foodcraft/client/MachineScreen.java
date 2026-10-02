package org.foodcraft.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.foodcraft.machine.MachineKind;
import org.foodcraft.machine.MachineMenu;
import java.util.Map;

/** Legacy slot geometry and atlas regions, with authoritative menu data for every gauge. */
public final class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private static final Map<MachineKind,String> TEXTURES=Map.of(
        MachineKind.MILLING_MACHINE,"repairtable",MachineKind.CUTTING_BOARD,"caiban",MachineKind.POT,"guo",MachineKind.FRYING_PAN,"pdg",
        MachineKind.PRESSURE_COOKER,"gyg",MachineKind.DEEP_FRYER,"yzj",MachineKind.DRINK_MAKER,"tpj",MachineKind.FERMENTING_BARREL,"nt",MachineKind.STOVE,"zl");
    private final ResourceLocation texture;
    public MachineScreen(MachineMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title);imageWidth=176;imageHeight=166;inventoryLabelY=72;
        texture=new ResourceLocation("foodcraft","textures/gui/container/"+TEXTURES.get(menu.kind)+".png");
    }
    @Override protected void init(){super.init();titleLabelX=(imageWidth-font.width(title))/2;}
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){
        g.drawString(font,title,titleLabelX,titleLabelY,0xff404040,false);
        if(menu.kind==MachineKind.MILLING_MACHINE)g.drawString(font,playerInventoryTitle,inventoryLabelX,inventoryLabelY,0xff404040,false);
    }
    private int burnTime(){return (menu.data.get(2)&65535)|((menu.data.get(3)&65535)<<16);}
    private int initialBurnTime(){return (menu.data.get(10)&65535)|((menu.data.get(11)&65535)<<16);}
    private void region(GuiGraphics g,int x,int y,int u,int v,int width,int height) {
        if(width>0&&height>0)g.blit(texture,leftPos+x,topPos+y,u,v,width,height);
    }
    private int[] progressPosition(){return switch(menu.kind) {
        case MILLING_MACHINE->new int[]{76,19};case POT->new int[]{93,19};case FRYING_PAN->new int[]{72,39};
        case PRESSURE_COOKER->new int[]{118,30};case DEEP_FRYER->new int[]{92,30};case DRINK_MAKER->new int[]{58,30};default->new int[]{-1,-1};
    };}
    @Override protected void renderBg(GuiGraphics g,float delta,int mouseX,int mouseY) {
        region(g,0,0,0,0,imageWidth,imageHeight);
        int[] arrow=progressPosition();int progress=menu.progress(),time=menu.totalTime();
        if(arrow[0]>=0&&time>0)region(g,arrow[0],arrow[1],176,14,(int)Math.min(24,(long)progress*24/time),16);
        int burn=initialBurnTime()==0?0:Math.min(12,(int)((long)burnTime()*12/initialBurnTime()));
        int[] flame=switch(menu.kind){case MILLING_MACHINE->new int[]{81,49};case STOVE->new int[]{81,48};
            case PRESSURE_COOKER,DEEP_FRYER->new int[]{122,74};case DRINK_MAKER->new int[]{145,35};default->new int[]{-1,-1};};
        if(flame[0]>=0&&burnTime()>0)region(g,flame[0],flame[1]-burn,176,12-burn,14,burn+2);
        if(menu.kind==MachineKind.STOVE&&burnTime()>0)region(g,83,22,176,14,9,9);
        if(menu.kind==MachineKind.DRINK_MAKER&&menu.data.get(9)>0){int cold=Math.min(11,menu.data.get(9)*11/2500);region(g,146,65-cold,190,11-cold,13,cold+2);}
        if(menu.kind.liquidSlot>=0){int water=Math.max(0,Math.min(56,(int)Math.floor(menu.liquidUnits()*7)));region(g,18,72-water,menu.data.get(5)==0?176:187,89-water,11,water);}
        if(menu.kind==MachineKind.FERMENTING_BARREL&&progress>0)g.drawString(font,Component.translatable("screen.foodcraft.remaining",Math.max(0,time-progress)/20),leftPos+53,topPos+53,0xff404040,false);
        if(menu.kind.heatedExternally()){
            int fireY=menu.kind==MachineKind.POT?65:19;
            int power=menu.data.get(6)*14/100;region(g,81,fireY+14-power,176,15-power,14,power);
            int heatX=menu.kind==MachineKind.POT?48:46,heatY=menu.kind==MachineKind.POT?38:62;
            region(g,heatX,heatY,176,31,Math.min(77,menu.data.get(8)*77/1000),3);
            if(progress>0){region(g,heatX+Math.min(77,menu.data.get(12)*77/1000),heatY-1,176,34,1,5);region(g,heatX+Math.min(77,menu.data.get(13)*77/1000),heatY-1,177,34,1,5);}
            if(menu.kind==MachineKind.POT)g.drawString(font,Component.translatable("screen.foodcraft.skill",menu.data.get(7)),leftPos+7,topPos+68,0xff404040,false);
        }
    }
    @Override public boolean mouseClicked(double mouseX,double mouseY,int button) {
        int y=menu.kind==MachineKind.POT?65:19;
        if(button==0&&menu.kind.heatedExternally()&&mouseX>=leftPos+81&&mouseX<leftPos+95&&mouseY>=topPos+y&&mouseY<topPos+y+14){
            int power=Math.max(0,Math.min(100,100-(int)Math.round((mouseY-topPos-y)*100/13.0)));
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId,2+power);return true;
        }
        return super.mouseClicked(mouseX,mouseY,button);
    }
    private boolean inside(int mouseX,int mouseY,int x,int y,int w,int h){return mouseX>=leftPos+x&&mouseX<leftPos+x+w&&mouseY>=topPos+y&&mouseY<topPos+y+h;}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta) {
        renderBackground(g);super.render(g,mouseX,mouseY,delta);renderTooltip(g,mouseX,mouseY);
        Component tip=null;int[] arrow=progressPosition();
        if(menu.kind.liquidSlot>=0&&inside(mouseX,mouseY,18,14,11,58))tip=Component.translatable(menu.kind==MachineKind.DEEP_FRYER?"screen.foodcraft.oil":menu.data.get(5)==0?"screen.foodcraft.water":"screen.foodcraft.milk",menu.liquidLabel());
        else if(menu.kind.heatedExternally()&&inside(mouseX,mouseY,81,menu.kind==MachineKind.POT?65:19,14,14))tip=Component.translatable("screen.foodcraft.heat",menu.data.get(6));
        else if(arrow[0]>=0&&inside(mouseX,mouseY,arrow[0],arrow[1],24,16))tip=Component.translatable("screen.foodcraft.progress",menu.progress(),menu.totalTime());
        else if(hoveredSlot!=null&&hoveredSlot.index<menu.kind.size&&!hoveredSlot.hasItem()){
            int slot=hoveredSlot.index;String role=menu.kind.output(slot)?"output":slot==menu.kind.fuelSlot?"fuel":slot==menu.kind.liquidSlot?"liquid":
                menu.kind==MachineKind.CUTTING_BOARD&&slot==0?"knife":menu.kind==MachineKind.DRINK_MAKER&&slot==4?"ice":"ingredient";
            tip=Component.translatable("screen.foodcraft.slot."+role);
        }
        if(tip!=null)g.renderTooltip(font,tip,mouseX,mouseY);
    }
}
