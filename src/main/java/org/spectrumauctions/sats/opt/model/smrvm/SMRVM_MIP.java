/**
 * Copyright by Michael Weiss, weiss.michael@gmx.ch
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.spectrumauctions.sats.opt.model.smrvm;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;

import edu.harvard.econcs.jopt.solver.IMIPResult;
import edu.harvard.econcs.jopt.solver.SolveParam;
import edu.harvard.econcs.jopt.solver.client.SolverClient;
import edu.harvard.econcs.jopt.solver.mip.Constraint;
import edu.harvard.econcs.jopt.solver.mip.MIP;
import edu.harvard.econcs.jopt.solver.mip.Variable;
import org.spectrumauctions.sats.core.bidlang.generic.GenericValue;
import org.spectrumauctions.sats.core.model.Bundle;
import org.spectrumauctions.sats.core.model.smrvm.*;
import org.spectrumauctions.sats.core.model.mrvm.MRVMRegionsMap.Region;
import org.spectrumauctions.sats.opt.model.EfficientAllocator;
import org.spectrumauctions.sats.opt.model.GenericAllocation;

/**
 * @author Michael Weiss
 *
 */
public class SMRVM_MIP implements EfficientAllocator<GenericAllocation<SMRVMGenericDefinition>> {
    
    public static boolean PRINT_SOLVER_RESULT = false;
    
    private static SolverClient SOLVER = new SolverClient();
    
    /**
     * If the highest possible value any bidder can have is higher than {@link MIP#MAX_VALUE} - MAXVAL_SAFETYGAP}
     * a non-zero scaling factor for the calculation is chosen.
     */
    public static BigDecimal highestValidVal = BigDecimal.valueOf(MIP.MAX_VALUE - 1000000);
    private WorldPartialMip worldPartialMip;
    private Map<SMRVMBidder, BidderPartialMIP> bidderPartialMips;
    private SMRVMWorld world;
    private MIP mip;
    
    public SMRVM_MIP(Collection<SMRVMBidder> bidders){
        Preconditions.checkNotNull(bidders);
        Preconditions.checkArgument(bidders.size() > 0);
        world = bidders.iterator().next().getWorld();
        mip = new MIP();
        mip.setSolveParam(SolveParam.RELATIVE_OBJ_GAP, 0.001);
        double scalingFactor = calculateScalingFactor(bidders);
        double biggestPossibleValue = biggestUnscaledPossibleValue(bidders).doubleValue() / scalingFactor;
        this.worldPartialMip = new WorldPartialMip(
                bidders, 
                biggestPossibleValue,
                scalingFactor                );
        worldPartialMip.appendToMip(mip);
        bidderPartialMips = new HashMap<>();
        for(SMRVMBidder bidder : bidders){
            BidderPartialMIP bidderPartialMIP;
            if(bidder instanceof SMRVMGlobalBidder){
                SMRVMGlobalBidder globalBidder = (SMRVMGlobalBidder) bidder;
                bidderPartialMIP = new GlobalBidderPartialMip(globalBidder, worldPartialMip);
            }else if (bidder instanceof SMRVMLocalBidder){
                SMRVMLocalBidder globalBidder = (SMRVMLocalBidder) bidder;
                bidderPartialMIP = new LocalBidderPartialMip(globalBidder, worldPartialMip);
            }else{
                SMRVMRegionalBidder globalBidder = (SMRVMRegionalBidder) bidder;
                bidderPartialMIP = new RegionalBidderPartialMip(globalBidder, worldPartialMip);
            }  
            bidderPartialMIP.appendToMip(mip);
            bidderPartialMips.put(bidder, bidderPartialMIP);
        }
    }
    
    public static double calculateScalingFactor(Collection<SMRVMBidder> bidders){
        BigDecimal maxVal = biggestUnscaledPossibleValue(bidders);
        if(maxVal.compareTo(highestValidVal) < 0){
            return 1;
        }else{
            System.out.println("Scaling MIP-CALC");
            return  maxVal.divide(highestValidVal, RoundingMode.HALF_DOWN).doubleValue();           
        }
    }
    
    /**
     * Returns the biggest possible value any of the passed bidders can have
     * @return
     */
    public static BigDecimal biggestUnscaledPossibleValue(Collection<SMRVMBidder> bidders){
        BigDecimal biggestValue = BigDecimal.ZERO;
        for(SMRVMBidder bidder : bidders){
            BigDecimal val = bidder.calculateValue(new Bundle<SMRVMLicense>(bidder.getWorld().getLicenses()));
            if(val.compareTo(biggestValue) > 0){
                biggestValue = val;
            }
        }
        return biggestValue;
    }
    public void addConstraint(Constraint constraint){
        mip.add(constraint);
    }
    
    public void addVariable(Variable variable){
        mip.add(variable);
    }
    
    
    /* (non-Javadoc)
     * @see ch.uzh.ifi.ce.mweiss.specvalopt.model.EfficientAllocator#calculateEfficientAllocation()
     */
    @Override
    public MipResult calculateAllocation() {
        IMIPResult mipResult = SOLVER.solve(mip);
        if(PRINT_SOLVER_RESULT) {
            System.out.println(mipResult);
        }
        MipResult.Builder resultBuilder = new MipResult.Builder(mipResult.getObjectiveValue(), world, mipResult);
        for(SMRVMBidder bidder : bidderPartialMips.keySet()){
            Variable bidderValueVar = worldPartialMip.getValueVariable(bidder);
            double mipUtilityResult = mipResult.getValue(bidderValueVar);
            double unscaledValue = mipUtilityResult * worldPartialMip.getScalingFactor();
            GenericValue.Builder<SMRVMGenericDefinition> valueBuilder = new GenericValue.Builder<>(BigDecimal.valueOf(unscaledValue));
            for(Region region : world.getRegionsMap().getRegions()){
                for(SMRVMBand band : world.getBands()){
                    Variable xVar = worldPartialMip.getXVariable(bidder, region, band);
                    double doubleQuantity = mipResult.getValue(xVar);
                    int quantity = (int) Math.round(doubleQuantity);
                    SMRVMGenericDefinition def = new SMRVMGenericDefinition(band, region);
                    valueBuilder.putQuantity(def, quantity);
                }
            }
            resultBuilder.putGenericValue(bidder, valueBuilder.build());
        }
        return resultBuilder.build();
    }

    public WorldPartialMip getWorldPartialMip() {
        return worldPartialMip;
    }

    public Map<SMRVMBidder, BidderPartialMIP> getBidderPartialMips() {
        return bidderPartialMips;
    }

}
