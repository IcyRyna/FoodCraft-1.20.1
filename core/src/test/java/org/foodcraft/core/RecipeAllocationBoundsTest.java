package org.foodcraft.core;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class RecipeAllocationBoundsTest {
    @Test void fragmentedGroupsCannotFillEnoughUnsplittableSlots(){
        int[] demands=new int[12],stock=new int[10],masks=new int[10],limits=new int[10];
        java.util.Arrays.fill(demands,6);java.util.Arrays.fill(stock,17);
        java.util.Arrays.fill(masks,4095);java.util.Arrays.fill(limits,64);
        assertEquals(1,RecipeAllocationBounds.maximumBatches(demands,stock,masks,limits,64));
        demands[0]=1;
        assertEquals(1,RecipeAllocationBounds.maximumBatches(demands,stock,masks,limits,64));
    }
    @Test void dispersedNbtStockAndHallShortageBoundMaximum(){
        int[] demands=new int[12],stock=new int[12],masks=new int[12],limits=new int[12];
        java.util.Arrays.fill(demands,1);java.util.Arrays.fill(stock,5);stock[11]=4;
        java.util.Arrays.fill(masks,4095);java.util.Arrays.fill(limits,64);
        assertEquals(4,RecipeAllocationBounds.maximumBatches(demands,stock,masks,limits,64));
        assertEquals(1,RecipeAllocationBounds.maximumBatches(new int[]{2,2,1},new int[]{5,64},new int[]{3,4},new int[]{64,64},64));
        assertEquals(0,RecipeAllocationBounds.maximumBatches(new int[]{1},new int[]{64},new int[]{0},new int[]{64},64));
    }
    @Test void boundNeverRejectsBruteForceFeasibleAllocation(){
        Random random=new Random(0xF00DC4AF7L);
        for(int trial=0;trial<5000;trial++){
            int inputs=1+random.nextInt(4),groups=1+random.nextInt(4);
            int[] demands=new int[inputs],stock=new int[groups],masks=new int[groups],limits=new int[groups];
            for(int i=0;i<inputs;i++)demands[i]=1+random.nextInt(3);
            for(int i=0;i<groups;i++){stock[i]=random.nextInt(9);masks[i]=random.nextInt(1<<inputs);limits[i]=1+random.nextInt(8);}
            int actual=0;
            for(int batches=1;batches<=4;batches++)if(feasible(demands,stock.clone(),masks,limits,0,batches))actual=batches;
            int bound=RecipeAllocationBounds.maximumBatches(demands,stock,masks,limits,4);
            assertTrue(bound>=actual&&bound<=4,"Unsafe bound in trial "+trial+": "+actual+"/"+bound);
        }
    }
    private boolean feasible(int[] demands,int[] stock,int[] masks,int[] limits,int input,int batches){
        if(input==demands.length)return true;
        int needed=demands[input]*batches;
        for(int group=0;group<stock.length;group++)if((masks[group]&(1<<input))!=0&&stock[group]>=needed&&limits[group]>=needed){
            stock[group]-=needed;boolean result=feasible(demands,stock,masks,limits,input+1,batches);stock[group]+=needed;
            if(result)return true;
        }
        return false;
    }
    @Test void invalidAndOverflowInputsAreBounded(){
        assertThrows(IllegalArgumentException.class,()->RecipeAllocationBounds.maximumBatches(new int[]{},new int[]{},new int[]{},new int[]{},1));
        assertThrows(IllegalArgumentException.class,()->RecipeAllocationBounds.maximumBatches(new int[]{1},new int[]{1},new int[]{2},new int[]{64},1));
        assertThrows(IllegalArgumentException.class,()->RecipeAllocationBounds.maximumBatches(new int[]{0},new int[]{1},new int[]{1},new int[]{64},1));
        assertEquals(64,RecipeAllocationBounds.maximumBatches(new int[]{1},new int[]{Integer.MAX_VALUE,Integer.MAX_VALUE},new int[]{1,1},new int[]{64,64},64));
    }
}
