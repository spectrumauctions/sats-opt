package org.spectrumauctions.sats.opt.model.gsvm;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import org.spectrumauctions.sats.core.model.Bidder;
import org.spectrumauctions.sats.core.model.Bundle;
import org.spectrumauctions.sats.core.model.gsvm.GSVMBidder;
import org.spectrumauctions.sats.core.model.gsvm.GSVMLicense;
import org.spectrumauctions.sats.core.model.gsvm.GSVMWorld;
import org.spectrumauctions.sats.opt.model.EfficientAllocator;
import org.spectrumauctions.sats.opt.model.ModelMIP;
import org.spectrumauctions.sats.opt.vcg.external.vcg.ItemAllocation;
import org.spectrumauctions.sats.opt.vcg.external.vcg.ItemAllocation.ItemAllocationBuilder;

import edu.harvard.econcs.jopt.solver.IMIPResult;
import edu.harvard.econcs.jopt.solver.client.SolverClient;
import edu.harvard.econcs.jopt.solver.mip.CompareType;
import edu.harvard.econcs.jopt.solver.mip.Constraint;
import edu.harvard.econcs.jopt.solver.mip.VarType;
import edu.harvard.econcs.jopt.solver.mip.Variable;

public class GSVMStandardMIP extends ModelMIP implements EfficientAllocator<ItemAllocation<GSVMLicense>> {

	private int n; // number of agents
	private int m; // number of items

	private Variable[][][] G;
	private double[][] value;
	private int[] tauHat;

	private List<GSVMBidder> population;
	private Map<Long, GSVMLicense> licenseMap;
	private GSVMWorld world;

	private boolean allowAssigningLicensesWithZeroBasevalue;

	public GSVMStandardMIP(GSVMWorld world, List<GSVMBidder> population) {
		this(world, population, true);
	}

	public GSVMStandardMIP(GSVMWorld world, List<GSVMBidder> population,
			boolean allowAssigningLicensesWithZeroBasevalue) {
		m = world.getLicenses().size();
		licenseMap = new HashMap<>(m);
		world.getLicenses().stream().forEach(license -> licenseMap.put(license.getId(), license));

		this.allowAssigningLicensesWithZeroBasevalue = allowAssigningLicensesWithZeroBasevalue;

		n = population.size();
		this.population = population;
		this.world = world;
		tauHat = new int[n];
		value = new double[n][m];
		getMip().setObjectiveMax(true);
		initValues();
		initVariables();
	}

	@Override
	public ItemAllocation<GSVMLicense> calculateAllocation() {
		SolverClient solver = new SolverClient();
		IMIPResult result = solver.solve(getMip());

		Map<Bidder<GSVMLicense>, Bundle<GSVMLicense>> allocation = new HashMap<>();

		for (int i = 0; i < n; i++) {
			GSVMBidder bidder = population.get(i);
			Bundle<GSVMLicense> bundle = new Bundle<>();
			for (int j = 0; j < m; j++) {
				if (allowAssigningLicensesWithZeroBasevalue || value[i][j] > 0) {
					for (int tau = 0; tau < tauHat[i]; tau++) {
						if (result.getValue(G[i][j][tau]) == 1) {
							bundle.add(licenseMap.get((long) j));
						}
					}
				}
			}
			allocation.put(bidder, bundle);
		}

		ItemAllocationBuilder<GSVMLicense> builder = new ItemAllocationBuilder<GSVMLicense>().withWorld(world)
				.withTotalValue(BigDecimal.valueOf(result.getObjectiveValue())).withAllocation(allocation);

		return builder.build();
	}

	public void build() {
		// build objective term
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < m; j++) {
				if (allowAssigningLicensesWithZeroBasevalue || value[i][j] > 0) {
					for (int tau = 0; tau < tauHat[i]; tau++) {
						getMip().addObjectiveTerm(calculateComplementarityMarkup(tau + 1) * value[i][j], G[i][j][tau]);
					}
				}
			}
		}

		// build Supply/Eval Constraint (1)
		for (int j = 0; j < m; j++) {
			Constraint constraint = new Constraint(CompareType.LEQ, 1, "SupplyConstraint j=" + j);
			for (int i = 0; i < n; i++) {
				if (allowAssigningLicensesWithZeroBasevalue || value[i][j] > 0) {
					for (int tau = 0; tau < tauHat[i]; tau++) {
						constraint.addTerm(1, G[i][j][tau]);
					}
				}
			}
			getMip().add(constraint);
		}

		// build Tau Constraint (2)
		for (int j = 0; j < m; j++) {
			for (int i = 0; i < n; i++) {
				Constraint constraint = new Constraint(CompareType.GEQ, 0);
				// build left part: Number of items agent i is allocated
				for (int k = 0; k < m; k++) {
					if (allowAssigningLicensesWithZeroBasevalue || value[i][k] > 0) {
						for (int tau = 0; tau < tauHat[i]; tau++) {
							constraint.addTerm(1, G[i][k][tau]);
						}
					}
				}
				// build right part: Activate tau that matches the number of
				// allocated items
				for (int tau = 0; tau < tauHat[i]; tau++) {
					if (allowAssigningLicensesWithZeroBasevalue || value[i][j] > 0) {
						constraint.addTerm(-(tau + 1), G[i][j][tau]);
					}
				}
				getMip().add(constraint);
			}
		}
	}

	private void initValues() {

		for (int i = 0; i < n; i++) {
			int tauCounter = 0;
			for (int j = 0; j < m; j++) {
				value[i][j] = getValue(i, j).orElse(0);
				if (allowAssigningLicensesWithZeroBasevalue || value[i][j] > 0) {
					tauCounter++;
				}
			}
			tauHat[i] = tauCounter;
		}
	}

	private OptionalDouble getValue(int i, int j) {
		return population.stream().filter(bidder -> bidder.getId() == i).mapToDouble(bidder -> {
			BigDecimal val = bidder.getBaseValues().get((long) j);
			return val == null ? 0 : val.doubleValue();
		}).reduce((element, otherElement) -> {
			throw new IllegalStateException(
					"Error: Multiple values for agent: " + i + " and license: " + j + " in population");
		});
	}

	private void initVariables() {
		G = new Variable[n][][];
		for (int i = 0; i < n; i++) {
			G[i] = new Variable[m][];
			for (int j = 0; j < m; j++) {
				// only init variables where agent i has a positive base-value
				if (allowAssigningLicensesWithZeroBasevalue || value[i][j] > 0) {
					G[i][j] = new Variable[tauHat[i]];
					for (int tau = 0; tau < tauHat[i]; tau++) {
						G[i][j][tau] = new Variable("g_i[" + i + "]j[" + j + "]t[" + tau + "]", VarType.BOOLEAN, 0, 1);
						getMip().add(G[i][j][tau]);
					}
				}
			}
		}
	}

	private double calculateComplementarityMarkup(int tau) {
		if (tau < 1) {
			throw new IllegalArgumentException("Error: tau has to be >=1");
		}
		return 1 + (tau - 1) * 0.2;
	}

}
