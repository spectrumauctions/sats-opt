/**
 * Copyright by Michael Weiss, weiss.michael@gmx.ch
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.spectrumauctions.sats.opt.model.smrvm;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;

import edu.harvard.econcs.jopt.solver.mip.CompareType;
import edu.harvard.econcs.jopt.solver.mip.Constraint;
import edu.harvard.econcs.jopt.solver.mip.MIP;
import edu.harvard.econcs.jopt.solver.mip.VarType;
import edu.harvard.econcs.jopt.solver.mip.Variable;
import org.spectrumauctions.sats.core.bidlang.generic.Band;
import org.spectrumauctions.sats.core.model.Bidder;
import org.spectrumauctions.sats.core.model.smrvm.SMRVMBand;
import org.spectrumauctions.sats.core.model.smrvm.SMRVMBidder;
import org.spectrumauctions.sats.core.model.smrvm.SMRVMRegionsMap;
import org.spectrumauctions.sats.core.model.mrvm.MRVMRegionsMap.Region;
import org.spectrumauctions.sats.core.model.smrvm.SMRVMWorld;
import org.spectrumauctions.sats.core.util.math.ContinuousPiecewiseLinearFunction;
import org.spectrumauctions.sats.opt.imip.PartialMIP;
import org.spectrumauctions.sats.opt.imip.PiecewiseLinearPartialMIP;

/**
 * @author Michael Weiss
 *
 */
public abstract class BidderPartialMIP extends PartialMIP {


    private static final String regionalOmegaPrefix = "aux_Omega";
    private static final String regionalCapacityFractionPrefix ="aux_c";
    private static final String regionalCapacityPrefix = "aux_cap";
    private static final String qualityPrefix = "aux_quality";
    
    /**
     * used by {@link #generateQualityConstraints()} to generate input-variables for quality function
     */
    private static final String qualityFunctionNamePrefix = "aux_qual";

    
    private Map<Region, Variable> omegaVariables;
    private Map<Region, Variable> cVariables;
    private Map<Region, Map<Band,Variable>> capVariables;
    private Map<Region, Variable> qualityVariables;
    protected final WorldPartialMip worldPartialMip;
    private final SMRVMBidder bidder;
    
    public BidderPartialMIP(SMRVMBidder bidder, WorldPartialMip worldMip){
        this.bidder = bidder;
        this.worldPartialMip = worldMip;
        initVariables();
    }
        
    private void initVariables() {
        this.omegaVariables = createOmegaVariables();
        this.cVariables = createCVariables();
        this.capVariables = createCapVariables();
        this.qualityVariables = createQualityVariables();
    }

    private Map<Region, Variable> createOmegaVariables() {
        Map<Region, Variable> result = new HashMap<>();
        for(Region region : bidder.getWorld().getRegionsMap().getRegions()){
            String varName = regionalOmegaPrefix.concat(createIndex(bidder, region));
            Variable var = new Variable(varName, VarType.DOUBLE, 0, MIP.MAX_VALUE);
            result.put(region, var);
        }
        return result;
    }

    private Map<Region, Variable> createCVariables() {
        Map<Region, Variable> result = new HashMap<>();
        for(Region region : bidder.getWorld().getRegionsMap().getRegions()){
            String varName = regionalCapacityFractionPrefix.concat(createIndex(bidder, region));
            Variable var = new Variable(varName, VarType.DOUBLE, 0, MIP.MAX_VALUE);
            result.put(region, var);
        }
        return result;
    }

    private Map<Region, Map<Band, Variable>> createCapVariables() {
        Map<Region, Map<Band, Variable>> result = new HashMap<>();
        for(Region region : bidder.getWorld().getRegionsMap().getRegions()){
            Map<Band, Variable> bandCapacityVariables = new HashMap<>();
            for(SMRVMBand band : bidder.getWorld().getBands()){
                String varName = regionalCapacityPrefix.concat(createIndex(bidder, region, band));
                Variable var = new Variable(varName, VarType.DOUBLE, 0, MIP.MAX_VALUE);
                bandCapacityVariables.put(band, var);
            }
            result.put(region, Collections.unmodifiableMap(bandCapacityVariables));
        }
        return result;
    }


    private Map<Region, Variable> createQualityVariables() {
        Map<Region, Variable> result = new HashMap<>();
        for(Region region : bidder.getWorld().getRegionsMap().getRegions()){
            String varName = qualityPrefix.concat(createIndex(bidder, region));
            Variable var = new Variable(varName, VarType.DOUBLE, 0, MIP.MAX_VALUE);
            result.put(region, var);
        }
        return result;
    }


    /**
     * @return
     * @throws NullPointerException if no variable is defined for this region
     */
    Variable getOmegaVariable(Region region){
        Variable var = omegaVariables.get(region);
        Preconditions.checkNotNull(var);
        return var;
    }
    
    /**
     * @return
     * @throws NullPointerException if no variable is defined for this region
     */
    Variable getCVariable(Region region){
        Variable var = cVariables.get(region);
        Preconditions.checkNotNull(var);
        return var;
    }
    
    /**
     * @return
     * @throws NullPointerException if no variable is defined for this region
     */
    Variable getCapVariable(Region region, Band band){
        Variable var = capVariables.get(region).get(band);
        Preconditions.checkNotNull(var);
        return var;
    }
    
    /**
     * @return
     * @throws NullPointerException if no variable is defined for this region
     */
    Variable getQualitiyVariable(Region region){
        Variable var = qualityVariables.get(region);
        Preconditions.checkNotNull(var);
        return var;
    }
                
            
    static String createIndex(Bidder<?> bidder, Region region){
        StringBuilder builder =  new StringBuilder("_b");
        builder.append(bidder.getId());
        builder.append(",r");
        builder.append(region.getId());
        return builder.toString();
    }
    
    static String createIndex(Bidder<?> bidder, Band band){
        StringBuilder builder =  new StringBuilder("_b");
        builder.append(bidder.getId());
        builder.append(",band_");
        builder.append(band.getName());
        return builder.toString();
    }
    
    static String createIndex(Bidder<?> bidder, Region region, Band band){
        StringBuilder builder =  new StringBuilder("_b");
        builder.append(bidder.getId());
        builder.append(",r");
        builder.append(region.getId());
        builder.append(",band_");
        builder.append(band.getName());
        return builder.toString();
    }

    
    Set<PartialMIP> generateQualityConstraints(){
        Set<PartialMIP> result = new HashSet<>();
        ContinuousPiecewiseLinearFunction qualityFunction = bidder.qualityFunction;
        for(Region region : bidder.getWorld().getRegionsMap().getRegions()){
            String varName = qualityFunctionNamePrefix.concat("_input").concat(createIndex(bidder, region));
            Variable qualityInput = new Variable(varName, VarType.DOUBLE, -bidder.getBeta().doubleValue(), 1-bidder.getBeta().doubleValue());
            Variable qualityOutput = getQualitiyVariable(region);
            String helperVariablesPrefix = new StringBuilder(qualityFunctionNamePrefix).append("_helpervar").append(createIndex(bidder, region)).append("_").toString();
            PiecewiseLinearPartialMIP linFctMIP =
                    new PiecewiseLinearPartialMIP(
                            qualityFunction, 
                            qualityInput, 
                            qualityOutput,
                            helperVariablesPrefix);
            // Input of function has form c_{i,r} - beta_i,
            // i.e., the capacity in a region minus the target share in that region
            Constraint inputGeneration =  new Constraint(CompareType.EQ, bidder.getBeta().doubleValue());
            inputGeneration.addTerm(-1, qualityInput);
            inputGeneration.addTerm(1, getCVariable(region));
            linFctMIP.addVariable(qualityInput);
            linFctMIP.addConstraint(inputGeneration);
            result.add(linFctMIP);
        }
        return result;
    }
    
    /**
     * 
     * @return
     */
    Set<Constraint> generateOmegaConstraints(){
        Set<Constraint> result = new HashSet<>();
        for(Region region : bidder.getWorld().getRegionsMap().getRegions()){
            double alpha = bidder.getAlpha().doubleValue();
            double beta = bidder.getBeta().doubleValue();
            double population = region.getPopulation();
            double scaledAlpha = alpha / worldPartialMip.getScalingFactor();
            Constraint omega = new Constraint(CompareType.EQ, 0);
            omega.addTerm(-1, getOmegaVariable(region));
            omega.addTerm(scaledAlpha*beta*population, getQualitiyVariable(region));
            result.add(omega);
        }
        return result;
    }
    
    /**
     * Generates the constraints of the form <br>
     * c_{i,r} = 1/maxCap * \sum_{b \in B} cap_{i,r,b}, <br>
     * i.e.,<br>
     * 0 = (-1) * maxCap * c_{i,r} + \sum_{b \in B} cap_{i,r,b} 
     * 
     * @return
     */
    Set<Constraint> generateCConstraints(){
        Set<Constraint> result = new HashSet<>();
        double maxCap = bidder.getWorld().getMaximumRegionalCapacity().doubleValue();
        for(Region region : bidder.getWorld().getRegionsMap().getRegions()){
            Constraint regionalCConstraint = new Constraint(CompareType.EQ, 0);
            regionalCConstraint.addTerm((-1)*maxCap, getCVariable(region));
            for(Band band : bidder.getWorld().getBands()){
                regionalCConstraint.addTerm(1, getCapVariable(region, band));
            }
            result.add(regionalCConstraint);
        }
        return result;
    }
    
    Set<PartialMIP> generateCapConstraints(){
        Set<PartialMIP> result = new HashSet<>();
        for(SMRVMBand band : bidder.getWorld().getBands()){
            ContinuousPiecewiseLinearFunction func = capacity(band);
            for(Region region : bidder.getWorld().getRegionsMap().getRegions()){
                Variable input = worldPartialMip.getXVariable(bidder, region, band);
                Variable output = getCapVariable(region, band);
                String auxiliaryVariableName = new StringBuilder("aux_cap_helper_")
                        .append(createIndex(bidder, region, band))
                        .append("_").
                        toString();
                PiecewiseLinearPartialMIP partialMip = 
                        new PiecewiseLinearPartialMIP(func, 
                                input, 
                                output, 
                                auxiliaryVariableName);
                result.add(partialMip);
            }
        }
        return result;
    }
    /**
     * Returns a continuous piecwise linear function which represents the capacity of a band for all possible input quantities.
     * @param band
     * @return
     */
    ContinuousPiecewiseLinearFunction capacity(SMRVMBand band){
        //Must ensure all BigDecimals have the same scale, as they are used as keys in a Map
        final int scale = 0;
        Map<BigDecimal, BigDecimal> breakpoints = new HashMap<>();
        breakpoints.put(BigDecimal.ZERO, SMRVMWorld.capOfBand(band, 0)); //
        BigDecimal lastSynergy = band.getSynergy(0);
        for(int quantity = 1; quantity < band.getNumberOfLots(); quantity++){
            BigDecimal synergy = band.getSynergy(quantity);
            if(synergy.compareTo(lastSynergy) != 0){
                band.getWorld();
                // Synergy Breakpoint: Store both last quantity with previous 
                // synergy (to account for piecewise constant synergies)
                // and new quantity in breakpoints 
                BigDecimal lowerQuantityCapacity = SMRVMWorld.capOfBand(band, quantity-1);
                // Note, if there's only one capacity with the previous synergy, 
                // an equivalent entry already exists and is overwritten in map
                BigDecimal key = new BigDecimal(quantity-1).setScale(scale);
                breakpoints.put(key, lowerQuantityCapacity);
                
                // Do the same for the new quantity
                key = new BigDecimal(quantity).setScale(scale);
                BigDecimal thisQuantityCapacity = SMRVMWorld.capOfBand(band, quantity);
                breakpoints.put(key, thisQuantityCapacity);
            }
        }
        //Add a breakpoint at the end of the function
        BigDecimal key = new BigDecimal(band.getNumberOfLots()).setScale(scale);
        BigDecimal thisQuantityCapacity = SMRVMWorld.capOfBand(band, band.getNumberOfLots());
        breakpoints.put(key, thisQuantityCapacity);
        
        ContinuousPiecewiseLinearFunction result = new ContinuousPiecewiseLinearFunction(breakpoints);
        return result;
    }
    
       
    public void appendVariablesToMip(MIP mip){
        super.appendVariablesToMip(mip);
        for(Variable var : omegaVariables.values()){
            mip.add(var);
        }
        for(Variable var : cVariables.values()){
            mip.add(var);
        }
        for(Map<Band, Variable> innerMap : capVariables.values()){
            for(Variable var : innerMap.values()){
                mip.add(var); 
            }
        }
        for(Variable var : qualityVariables.values()){
            mip.add(var);
        }
        for(PartialMIP partialMip : generateCapConstraints()){
            partialMip.appendVariablesToMip(mip);
        }
        for(PartialMIP partialMip : generateQualityConstraints()){
            partialMip.appendVariablesToMip(mip);
        }
    }
    
    public void appendConstraintsToMip(MIP mip){
        super.appendConstraintsToMip(mip);
        for(Constraint constraint : generateOmegaConstraints()){
            mip.add(constraint);
        }
        for(Constraint constraint : generateCConstraints()){
            mip.add(constraint);
        }
        for(PartialMIP partialMip : generateCapConstraints()){
            partialMip.appendConstraintsToMip(mip);
        }
        for(PartialMIP partialMip : generateQualityConstraints()){
            partialMip.appendConstraintsToMip(mip);
        }
    }
    
    
    

}
