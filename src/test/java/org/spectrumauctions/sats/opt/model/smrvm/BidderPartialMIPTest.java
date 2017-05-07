/**
 * Copyright by Michael Weiss, weiss.michael@gmx.ch
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.spectrumauctions.sats.opt.model.smrvm;

import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import edu.harvard.econcs.jopt.solver.IMIPResult;
import edu.harvard.econcs.jopt.solver.client.SolverClient;
import edu.harvard.econcs.jopt.solver.mip.CompareType;
import edu.harvard.econcs.jopt.solver.mip.Constraint;
import edu.harvard.econcs.jopt.solver.mip.MIP;
import edu.harvard.econcs.jopt.solver.mip.VarType;
import edu.harvard.econcs.jopt.solver.mip.Variable;
import org.spectrumauctions.sats.core.model.smrvm.*;
import org.spectrumauctions.sats.core.model.mrvm.MRVMRegionsMap.Region;
import org.spectrumauctions.sats.core.util.math.ContinuousPiecewiseLinearFunction;
import org.spectrumauctions.sats.core.util.random.JavaUtilRNGSupplier;
import org.spectrumauctions.sats.opt.imip.PartialMIP;
import org.spectrumauctions.sats.opt.imip.PiecewiseLinearPartialMIP;

/**
 * @author Michael Weiss
 *
 */
public class BidderPartialMIPTest {
    
    private static List<SMRVMBidder> bidders;
    private static WorldPartialMip worldPartialMip;
    private static Map<SMRVMBidder, BidderPartialMIP> bidderPartialMips;

    /**
     * @throws Exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        SMRVMWorld world = new SMRVMWorld(WorldGen.getSimpleWorldBuilder(), new JavaUtilRNGSupplier(153578351L));
        SMRVMLocalBidderSetup setup = WorldGen.getSimpleLocalBidderSetup();
        bidders = world.createPopulation(setup, null, null, new JavaUtilRNGSupplier(15434684L));
        double scalingFactor = SMRVM_MIP.calculateScalingFactor(bidders);
        double biggestScaledValue = SMRVM_MIP.biggestUnscaledPossibleValue(bidders).doubleValue() / scalingFactor;
        worldPartialMip = new WorldPartialMip(bidders, biggestScaledValue, scalingFactor);

        bidderPartialMips = new HashMap<>();
        for(SMRVMBidder bidder : bidders){
            SMRVMLocalBidder localBidder = (SMRVMLocalBidder) bidder;
            bidderPartialMips.put(bidder, new LocalBidderPartialMip(localBidder, worldPartialMip));
        }
    }

    /**
     * Test method for {@link BidderPartialMIP#generateOmegaConstraints()}.
     */
    @Test
    public void testGenerateOmegaConstraints() {
        MIP mip = new MIP();
        worldPartialMip.appendVariablesToMip(mip);
        mip.setObjectiveMax(true);
        
        Map<SMRVMBidder, BidderPartialMIP> partialMips = new HashMap<>();
 
        for(SMRVMBidder bidder : bidders){
            BidderPartialMIP bidderPartialMIP = bidderPartialMips.get(bidder);
            partialMips.put(bidder, bidderPartialMIP);
            // Fix quality variables (put quality to 1/(1+regionId))
            for(Region region : bidder.getWorld().getRegionsMap().getRegions()){
                Constraint quality = new Constraint(CompareType.EQ, 1./(1+region.getId()));
                quality.addTerm(1, bidderPartialMIP.getQualitiyVariable(region));
                mip.add(quality);
            }                
            // Append Omega Variables   
            for(Constraint constraint : bidderPartialMIP.generateOmegaConstraints()){
                mip.add(constraint);
            }
            bidderPartialMIP.appendVariablesToMip(mip);
        }
             
        // Append Omegas to Objective
        for(Entry<SMRVMBidder, BidderPartialMIP> partialMip : partialMips.entrySet()){
            for(Region region : partialMip.getKey().getWorld().getRegionsMap().getRegions()){
                mip.addObjectiveTerm(1, partialMip.getValue().getOmegaVariable(region));
            }
        }
        
        // Solve MIP
        SolverClient solverClient = new SolverClient();
        IMIPResult result = solverClient.solve(mip);      
        
        // Test omega values        
        boolean noAssertions = true;
        for(Entry<SMRVMBidder, BidderPartialMIP> partialMip : partialMips.entrySet()){
            SMRVMBidder bidder = partialMip.getKey();
            for(Region region : bidder.getWorld().getRegionsMap().getRegions()){
                double omega = result.getValue(partialMip.getValue().getOmegaVariable(region));
                double alpha = bidder.getAlpha().doubleValue();
                double beta = bidder.getBeta().doubleValue();
                double population = region.getPopulation();
                double expected = 1./(1+region.getId())*alpha*beta*population;
                Assert.assertEquals(expected, omega, 0.0000001);
                noAssertions = false;
            }  
        }
        Assert.assertFalse(noAssertions);
    }
    
    @Test 
    public void testQualityWithMinCapacity(){
        testQuality(0.);
    }
    
    @Test 
    public void testQualityWithCapacityMedium(){
        testQuality(.2);
        testQuality(.3);
        testQuality(.5);
    }
    
    @Test 
    public void testQualityWithMaxCapacity(){
        testQuality(1.);
    }

    private void testQuality(double capacityShare){
        MIP mip = new MIP();
        worldPartialMip.appendVariablesToMip(mip);
        mip.setObjectiveMax(true);
        
        Map<SMRVMBidder, BidderPartialMIP> partialMips = new HashMap<>();
 
        for(SMRVMBidder bidder : bidders){
            BidderPartialMIP bidderPartialMIP = bidderPartialMips.get(bidder);
            partialMips.put(bidder, bidderPartialMIP);
            // Fix c-variable
            for(Region region : bidder.getWorld().getRegionsMap().getRegions()){
                Constraint c = new Constraint(CompareType.EQ, capacityShare);
                c.addTerm(1, bidderPartialMIP.getCVariable(region));
                mip.add(c);
            } 
            // Add constraints under test
            for(PartialMIP qualityPartial : bidderPartialMIP.generateQualityConstraints()){
                // Append generated constraints to MIP. Note: Variables are already added through BidderPartialMIP
                qualityPartial.appendConstraintsToMip(mip);
            }
            bidderPartialMIP.appendVariablesToMip(mip);
        }
        
        // Append Qualities to Objective
        for(Entry<SMRVMBidder, BidderPartialMIP> partialMip : partialMips.entrySet()){
            for(Region region : partialMip.getKey().getWorld().getRegionsMap().getRegions()){
                mip.addObjectiveTerm(1, partialMip.getValue().getQualitiyVariable(region));
            }
        }
        
        // Solve MIP
        SolverClient solverClient = new SolverClient();
        IMIPResult result = solverClient.solve(mip);    
        
        // Test Qualities
        boolean noAssertions = true;
        for(Entry<SMRVMBidder, BidderPartialMIP> partialMip : partialMips.entrySet()){
            SMRVMBidder bidder = partialMip.getKey();
            for(Region region : bidder.getWorld().getRegionsMap().getRegions()){
                BigDecimal functionInput = BigDecimal.valueOf(capacityShare).subtract(bidder.getBeta());
                double quality = result.getValue(partialMip.getValue().getQualitiyVariable(region));
                double expected = bidder.qualityFunction.getY(functionInput).doubleValue();
                Assert.assertEquals(expected, quality, 0.0000001);
                noAssertions = false;
            }  
        }
        Assert.assertFalse(noAssertions);
    }

    /**
     * Test method for {@link ch.uzh.ifi.ce.mweiss.specvalopt.model.SMRVM.BidderPartialMIP#generateCConstraints()}.
     */
    @Test
    public void testGenerateCConstraints() {
        MIP mip = new MIP();
        worldPartialMip.appendVariablesToMip(mip);
        worldPartialMip.appendConstraintsToMip(mip);
        mip.setObjectiveMax(true);
        for(SMRVMBidder bidder : bidders){
            BidderPartialMIP bidderPartialMIP = bidderPartialMips.get(bidder);
            bidderPartialMIP.appendVariablesToMip(mip);
            for(Region region : bidder.getWorld().getRegionsMap().getRegions()){
                mip.addObjectiveTerm(1, bidderPartialMIP.getCVariable(region));
            }
            for(PartialMIP capConstrainingMip: bidderPartialMIP.generateCapConstraints()){
                capConstrainingMip.appendConstraintsToMip(mip);
            }
            for(Constraint constraint : bidderPartialMIP.generateCConstraints()){
                mip.add(constraint);
            }
        }
       
        SolverClient solverClient = new SolverClient();
        IMIPResult result = solverClient.solve(mip);
        // Since the synergies in this very simple setting induce weakly super-additive value functions, the (possibly added) shares in each region are exacly one, hence the objective value should be 5
        Assert.assertEquals(5, result.getObjectiveValue(), 0.00001);
    }

    /**
     * Test method for {@link ch.uzh.ifi.ce.mweiss.specvalopt.model.SMRVM.BidderPartialMIP#generateCapConstraints()}.
     */
    @Test
    public void testGenerateCapConstraints() {
        MIP mip = new MIP();
        worldPartialMip.appendVariablesToMip(mip);
        worldPartialMip.appendConstraintsToMip(mip);
        
        mip.setObjectiveMax(true);
        for(SMRVMBidder bidder : bidders){
            BidderPartialMIP bidderPartialMIP = bidderPartialMips.get(bidder);
            bidderPartialMIP.appendVariablesToMip(mip);
            //Add an additional constraint which states how much total capacity each bidder got, choose possible negative to make sure result does not get influenced.
            Variable bidderCapacity = new Variable("CAPTOTAL_".concat(String.valueOf(bidder.getId())), VarType.DOUBLE, -MIP.MAX_VALUE, MIP.MAX_VALUE);
            Constraint bidderConstraint = new Constraint(CompareType.EQ, 0);
            bidderConstraint.addTerm((-1), bidderCapacity);
            for(Region region : bidder.getWorld().getRegionsMap().getRegions()){
                for(SMRVMBand band : bidder.getWorld().getBands()){
                    mip.addObjectiveTerm(1, bidderPartialMIP.getCapVariable(region, band));
                    bidderConstraint.addTerm(1, bidderPartialMIP.getCapVariable(region, band));
                }
            }
            mip.add(bidderCapacity);
            mip.add(bidderConstraint);
            for(PartialMIP capConstrainingMip: bidderPartialMIP.generateCapConstraints()){
                capConstrainingMip.appendConstraintsToMip(mip);
            }
        }
           
        SolverClient solverClient = new SolverClient();
        IMIPResult result = solverClient.solve(mip);
        Assert.assertEquals(700, result.getObjectiveValue(), 0.000007);
    }
    
    /**
     * Test method for {@link ch.uzh.ifi.ce.mweiss.specvalopt.model.SMRVM.BidderPartialMIP#generateCapConstraints()}.
     */
    @Test
    public void testPerBandCapacitiesWithoutPreconstrainedInput() {
       for(SMRVMBand band : bidders.iterator().next().getWorld().getBands()){
           double maxCap = capacityOfOneBandWithoutPreconstrainedInput(band);
           if(band.getName().equals(WorldGen.BAND_A_NAME)){
               Assert.assertEquals(80.0, maxCap, 0.00001);
           }else if(band.getName().equals(WorldGen.BAND_B_NAME)){
               Assert.assertEquals(60.0, maxCap, 0.00001);
           }else{
               fail("unknown band");
           }
       }
    }
   
    
    @Test
    public void testCapVariablesOfTwoBandWithoutPreConstrainedInput(){
        MIP mip = new MIP();
        mip.setObjectiveMax(true);
        worldPartialMip.appendVariablesToMip(mip);
        
        SMRVMBidder bidder = bidders.iterator().next();
        Region region = bidder.getWorld().getRegionsMap().getRegion(0);
        BidderPartialMIP bidderPartialMIP = bidderPartialMips.get(bidder);
        
        for(SMRVMBand band : bidder.getWorld().getBands()){
            ContinuousPiecewiseLinearFunction fct = bidderPartialMIP.capacity(band);
            Variable input = worldPartialMip.getXVariable(bidder, region, band);
            Variable output = bidderPartialMIP.getCapVariable(region, band);
            String auxiliaryVariableName = new StringBuilder("aux_cap_helper_")
                    .append("twoBandTest_")
                    .append(band.getName())
                    .toString();
            PiecewiseLinearPartialMIP partialMip =
                    new PiecewiseLinearPartialMIP(fct, 
                            input, 
                            output, 
                            auxiliaryVariableName);
            partialMip.addVariable(output);
            mip.addObjectiveTerm(1, output);
            partialMip.appendVariablesToMip(mip);
            partialMip.appendConstraintsToMip(mip);
        }
        
        SolverClient solverClient = new SolverClient();
        IMIPResult result = solverClient.solve(mip);
        Assert.assertEquals(140, result.getObjectiveValue(), 0.00001);
    }
    
    
    private double capacityOfOneBandWithoutPreconstrainedInput(SMRVMBand band){
        MIP mip = new MIP();
        mip.setObjectiveMax(true);
        worldPartialMip.appendVariablesToMip(mip);
        
        SMRVMBidder bidder = bidders.iterator().next();
        Region region = band.getWorld().getRegionsMap().getRegion(0);
        BidderPartialMIP bidderPartialMIP = bidderPartialMips.get(bidder);
        
        
        ContinuousPiecewiseLinearFunction fct = bidderPartialMIP.capacity(band);
        Variable input = worldPartialMip.getXVariable(bidder, region, band);
        Variable output = bidderPartialMIP.getCapVariable(region, band);
        String auxiliaryVariableName = new StringBuilder("aux_cap_helper_")
                .append("ONE-SETTING-TEST_").
                toString();
        PiecewiseLinearPartialMIP partialMip = 
                new PiecewiseLinearPartialMIP(fct, 
                        input, 
                        output, 
                        auxiliaryVariableName);
        partialMip.addVariable(output);
        mip.addObjectiveTerm(1, output);
        partialMip.appendVariablesToMip(mip);
        partialMip.appendConstraintsToMip(mip);
        
        SolverClient solverClient = new SolverClient();
        IMIPResult result = solverClient.solve(mip);
        return result.getObjectiveValue();
    }
    
    

}
