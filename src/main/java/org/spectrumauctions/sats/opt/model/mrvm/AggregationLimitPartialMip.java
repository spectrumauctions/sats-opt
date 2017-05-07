package org.spectrumauctions.sats.opt.model.mrvm;

import edu.harvard.econcs.jopt.solver.mip.CompareType;
import edu.harvard.econcs.jopt.solver.mip.Constraint;
import edu.harvard.econcs.jopt.solver.mip.VarType;
import edu.harvard.econcs.jopt.solver.mip.Variable;
import org.spectrumauctions.sats.core.model.mrvm.*;
import org.spectrumauctions.sats.core.model.mrvm.MRVMRegionsMap.Region;
import org.spectrumauctions.sats.opt.imip.PartialMIP;

import java.util.Collection;

/**
 * Created by Michael Weiss on 07.05.2017.
 */
public class AggregationLimitPartialMip extends PartialMIP {

    public AggregationLimitPartialMip(Collection<MRVMBidder> bidders, MRVMWorldPartialMip worldPartialMip){
      MRVMWorld world = bidders.iterator().next().getWorld();
      MRVMBand lp = null;
      MRVMBand hp = null;
        for (MRVMBand band : world.getBands()) {
            if (band.getName().equals(MRVMWorldSetup.MRVMWorldSetupBuilder.LOW_PAIRED_NAME)) {
                lp = band;
            } else if (band.getName().equals(MRVMWorldSetup.MRVMWorldSetupBuilder.HIGH_PAIRED_NAME)) {
                hp = band;
            }
        }
        if (lp == null || hp == null) {
            throw new NullPointerException("Band not found. Please deactivate Aggregration Limits should you not use the default MRVM configuration");
        }
        for (MRVMBidder bidder : bidders) {
            for (Region region : world.getRegionsMap().getRegions()) {
                Variable varLp = worldPartialMip.getXVariable(bidder, region, lp);
                Variable varHp = worldPartialMip.getXVariable(bidder, region, hp);
                Constraint constraint = new Constraint(CompareType.LEQ, 2);
                constraint.addTerm(1, varLp);
                constraint.addTerm(1, varHp);
                addConstraint(constraint);
                if (bidder instanceof MRVMNationalBidder) {
                    String varName = new StringBuilder().append("AgggregationLimitXhat_i=").append(bidder.getId())
                            .append("_r=").append(region.getId()).append("b=").append("LP").toString();

                    // Initiate and Constrain variable indication if at least one licences in band lp
                    Variable hatVarLP = new Variable(varName, VarType.BOOLEAN, 0, 1);
                    Constraint upperLimitConstraint = new Constraint(CompareType.LEQ, 0);
                    upperLimitConstraint.addTerm(1, hatVarLP);
                    upperLimitConstraint.addTerm(-1, varLp);
                    addConstraint(upperLimitConstraint);
                    addVariable(hatVarLP);
                    Constraint lowerLimitConstraint = new Constraint(CompareType.GEQ, 0);
                    lowerLimitConstraint.addTerm(1, hatVarLP);
                    lowerLimitConstraint.addTerm((-1d) / (double) lp.getNumberOfLots(), varLp);
                    addConstraint(lowerLimitConstraint);
                    // Initiate and Constrain variable indication if at least one licences in band hp
                    varName = new StringBuilder().append("AgggregationLimitXhat_i=").append(bidder.getId())
                            .append("_r=").append(region.getId()).append("b=").append("HP").toString();
                    Variable hatVarHP = new Variable(varName, VarType.BOOLEAN, 0, 1);
                    upperLimitConstraint = new Constraint(CompareType.LEQ, 0);
                    upperLimitConstraint.addTerm(1, hatVarHP);
                    upperLimitConstraint.addTerm(-1, varHp);
                    addVariable(hatVarHP);
                    addConstraint(upperLimitConstraint);
                    lowerLimitConstraint = new Constraint(CompareType.GEQ, 0);
                    lowerLimitConstraint.addTerm(1, hatVarHP);
                    lowerLimitConstraint.addTerm((-1.) / (double) hp.getNumberOfLots(), varHp);
                    addConstraint(lowerLimitConstraint);
                    // Must only have license in one of the two paired bands
                    Constraint onlyOne = new Constraint(CompareType.LEQ, 1);
                    onlyOne.addTerm(1, hatVarHP);
                    onlyOne.addTerm(1, hatVarLP);
                    addConstraint(onlyOne);
                    // Not more than one in hp
                    Constraint maxOneInHP = new Constraint(CompareType.LEQ, 1);
                    maxOneInHP.addTerm(1, varHp);
                    addConstraint(maxOneInHP);
                }
            }
        }

    }


//    public static void appendAggregationLimits(Collection<MRMBidder> bidders, MRM_MIP mip) {
//        MRMWorld world = bidders.iterator().next().getWorld();
//        MRMBand lp = null;
//        MRMBand hp = null;
//        for (MRMBand band : world.getBands()) {
//            if (band.getName().equals(LOW_PAIRED_NAME)) {
//                lp = band;
//            } else if (band.getName().equals(HIGH_PAIRED_NAME)) {
//                hp = band;
//            }
//        }
//        if (lp == null || hp == null) {
//            throw new RuntimeException("Band not found");
//        }
//        for (MRMBidder bidder : bidders) {
//            for (Region region : world.getRegionsMap().getRegions()) {
//                Variable varLp = mip.getWorldPartialMip().getXVariable(bidder, region, lp);
//                Variable varHp = mip.getWorldPartialMip().getXVariable(bidder, region, hp);
//                Constraint constraint = new Constraint(CompareType.LEQ, 2);
//                constraint.addTerm(1, varLp);
//                constraint.addTerm(1, varHp);
//                mip.addConstraint(constraint);
//                if (bidder instanceof MRMGlobalBidder) {
//                    String varName = new StringBuilder().append("AgggregationLimitXhat_i=").append(bidder.getId())
//                            .append("_r=").append(region.getId()).append("b=").append("LP").toString();
//
//                    // Initiate and Constrain variable indication if at least one licences in band lp
//                    Variable hatVarLP = new Variable(varName, VarType.BOOLEAN, 0, 1);
//                    Constraint upperLimitConstraint = new Constraint(CompareType.LEQ, 0);
//                    upperLimitConstraint.addTerm(1, hatVarLP);
//                    upperLimitConstraint.addTerm(-1, varLp);
//                    mip.addConstraint(upperLimitConstraint);
//                    mip.addVariable(hatVarLP);
//                    Constraint lowerLimitConstraint = new Constraint(CompareType.GEQ, 0);
//                    lowerLimitConstraint.addTerm(1, hatVarLP);
//                    lowerLimitConstraint.addTerm((-1d) / (double) lp.getNumberOfLots(), varLp);
//                    mip.addConstraint(lowerLimitConstraint);
//                    // Initiate and Constrain variable indication if at least one licences in band hp
//                    varName = new StringBuilder().append("AgggregationLimitXhat_i=").append(bidder.getId())
//                            .append("_r=").append(region.getId()).append("b=").append("HP").toString();
//                    Variable hatVarHP = new Variable(varName, VarType.BOOLEAN, 0, 1);
//                    upperLimitConstraint = new Constraint(CompareType.LEQ, 0);
//                    upperLimitConstraint.addTerm(1, hatVarHP);
//                    upperLimitConstraint.addTerm(-1, varHp);
//                    mip.addVariable(hatVarHP);
//                    mip.addConstraint(upperLimitConstraint);
//                    lowerLimitConstraint = new Constraint(CompareType.GEQ, 0);
//                    lowerLimitConstraint.addTerm(1, hatVarHP);
//                    lowerLimitConstraint.addTerm((-1.) / (double) hp.getNumberOfLots(), varHp);
//                    mip.addConstraint(lowerLimitConstraint);
//                    // Must only have license in one of the two paired bands
//                    Constraint onlyOne = new Constraint(CompareType.LEQ, 1);
//                    onlyOne.addTerm(1, hatVarHP);
//                    onlyOne.addTerm(1, hatVarLP);
//                    mip.addConstraint(onlyOne);
//                    // Not more than one in hp
//                    Constraint maxOneInHP = new Constraint(CompareType.LEQ, 1);
//                    maxOneInHP.addTerm(1, varHp);
//                    mip.addConstraint(maxOneInHP);
//                }
//            }
//        }
//
//    }

}
