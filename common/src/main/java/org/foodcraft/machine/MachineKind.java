package org.foodcraft.machine;

import java.util.Arrays;
import org.foodcraft.Catalog;

public enum MachineKind {
    MILLING_MACHINE("milling_machine",3,new int[]{0},new int[]{1},2,-1,200,0),
    CUTTING_BOARD("cutting_board",5,new int[]{1,2,3},new int[]{4},-1,-1,1,0),
    POT("pot",14,new int[]{0,1,2,3,4,5,6,7,8,9,10,11},new int[]{12,13},-1,-1,500,0),
    FRYING_PAN("frying_pan",4,new int[]{0},new int[]{1,2},-1,-1,400,0),
    PRESSURE_COOKER("pressure_cooker",6,new int[]{0,1,2},new int[]{5},4,3,480,2),
    DEEP_FRYER("deep_fryer",4,new int[]{0},new int[]{3},1,2,400,2),
    DRINK_MAKER("drink_maker",5,new int[]{1},new int[]{2},3,0,350,1),
    FERMENTING_BARREL("fermenting_barrel",6,new int[]{0,1,2},new int[]{5},-1,3,3600,8),
    STOVE("stove",1,new int[]{},new int[]{},0,-1,0,0);

    public final String id;
    public final int size, fuelSlot, liquidSlot, ticks, liquidCost;
    public final int[] inputs, outputs;
    MachineKind(String id,int size,int[] inputs,int[] outputs,int fuelSlot,int liquidSlot,int ticks,int liquidCost) {
        this.id=id;this.size=size;this.inputs=inputs;this.outputs=outputs;
        this.fuelSlot=fuelSlot;this.liquidSlot=liquidSlot;this.ticks=ticks;this.liquidCost=liquidCost;
    }
    public boolean heatedExternally(){return this==POT||this==FRYING_PAN;}
    public boolean output(int slot){return Arrays.stream(outputs).anyMatch(i->i==slot);}
    public boolean input(int slot){return Arrays.stream(inputs).anyMatch(i->i==slot);}
    public int[][] layout(){return Catalog.LAYOUTS.get(id);}
    public static MachineKind byId(String id){return Arrays.stream(values()).filter(k->k.id.equals(id)).findFirst().orElseThrow();}
}
