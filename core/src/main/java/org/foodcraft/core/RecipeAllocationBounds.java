package org.foodcraft.core;

/** Necessary stock bounds for ordered slots with overlapping ingredients and unsplittable NBT stacks. */
public final class RecipeAllocationBounds {
    private RecipeAllocationBounds() {}
    public static int maximumBatches(int[] demands,int[] stock,int[] eligibleMasks,int[] stackLimits,int requested){
        if(demands.length<1||demands.length>12||stock.length!=eligibleMasks.length||stock.length!=stackLimits.length||requested<0||requested>64)
            throw new IllegalArgumentException("Invalid allocation dimensions");
        int full=(1<<demands.length)-1,bound=requested;
        long[] subsetStock=new long[full+1],subsetDemand=new long[full+1];
        long total=0;
        for(int group=0;group<stock.length;group++){
            if(stock[group]<0||eligibleMasks[group]<0||eligibleMasks[group]>full||stackLimits[group]<1)
                throw new IllegalArgumentException("Invalid allocation stock");
            subsetStock[eligibleMasks[group]]+=stock[group];total+=stock[group];
        }
        for(int input=0;input<demands.length;input++){
            if(demands[input]<1||demands[input]>64)throw new IllegalArgumentException("Invalid allocation demand");
            int largest=0;
            for(int group=0;group<stock.length;group++)if((eligibleMasks[group]&(1<<input))!=0)
                largest=Math.max(largest,Math.min(stock[group],stackLimits[group]));
            bound=Math.min(bound,largest/demands[input]);
        }
        // Zeta transform: stock whose accepted-input mask is wholly inside each subset.
        for(int bit=0;bit<demands.length;bit++)for(int mask=0;mask<=full;mask++)
            if((mask&(1<<bit))!=0)subsetStock[mask]+=subsetStock[mask^(1<<bit)];
        for(int subset=1;subset<=full;subset++){
            int bit=Integer.numberOfTrailingZeros(subset);
            subsetDemand[subset]=subsetDemand[subset&(subset-1)]+demands[bit];
            long available=total-subsetStock[full^subset];
            bound=(int)Math.min(bound,available/subsetDemand[subset]);
        }
        int[] minimumDemand=new int[full+1];
        minimumDemand[0]=Integer.MAX_VALUE;
        for(int subset=1;subset<=full;subset++){
            int bit=Integer.numberOfTrailingZeros(subset);
            minimumDemand[subset]=Math.min(demands[bit],minimumDemand[subset&(subset-1)]);
        }
        // An NBT group can fill only whole slots. Aggregate item counts alone
        // miss fragmented stock, e.g. ten groups of 17 cannot fill twelve slots
        // of 12. Apply the cardinality bound to every input subset, including
        // subsets that exclude a cheaper ingredient accepted by the same group.
        for(int batches=bound;batches>=1;batches--){
            int[] usable=new int[stock.length];
            for(int group=0;group<stock.length;group++)for(int input=0;input<demands.length;input++)
                if((eligibleMasks[group]&(1<<input))!=0&&demands[input]*batches<=stackLimits[group])usable[group]|=1<<input;
            boolean possible=true;
            for(int subset=1;subset<=full&&possible;subset++){
                int neededSlots=Integer.bitCount(subset),availableSlots=0;
                for(int group=0;group<stock.length&&availableSlots<neededSlots;group++){
                    int eligible=usable[group]&subset;
                    if(eligible!=0)availableSlots+=Math.min(Integer.bitCount(eligible),stock[group]/(minimumDemand[eligible]*batches));
                }
                possible=availableSlots>=neededSlots;
            }
            if(possible)return batches;
        }
        return 0;
    }
}
