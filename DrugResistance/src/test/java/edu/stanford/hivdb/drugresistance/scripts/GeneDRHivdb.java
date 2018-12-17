/*

    Copyright (C) 2017 Stanford HIVDB team

    Sierra is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Sierra is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package edu.stanford.hivdb.drugresistance.scripts;

import java.sql.SQLException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import edu.stanford.hivdb.drugresistance.GeneDR;
import edu.stanford.hivdb.drugresistance.database.MutationComboScores;
import edu.stanford.hivdb.drugresistance.database.MutationScores;
import edu.stanford.hivdb.drugs.Drug;
import edu.stanford.hivdb.drugs.DrugClass;
import edu.stanford.hivdb.mutations.Gene;
import edu.stanford.hivdb.mutations.Mutation;

import edu.stanford.hivdb.mutations.MutationSet;

/**
 * Initialize with gene, mutClassification, mutComments, individual mut scores, combination mut scores
 * Calculate the total scores for each drug in the drug classes associated with the gene
 * Translate the total scores to one of five qualitative levels of resistance
 * Populate the maps containing the scores for each mutation for each drug
 */

public class GeneDRHivdb extends GeneDR {
	// Data structures generated by methods to provide improved access to the data
	private Map<DrugClass, Map<Drug, Double>> totalDrugScores;

	/**
	 * Instantiates all of the drug resistance data for a gene using HIVDB_Scores including:
	 * mutClassifications: classification type => list of mutations
	 * mutComments: mutation => comment
	 * drugClassDrugMutScores: DrugClass=>Drug=>Mutation=>score
	 * drugClassDrugComboMutScores: DrugClass=>Drug=>List<Mutation>=>score
	 * @param gene, mutTypes, mutComments, mutScores, comboMutScores
	 * Data structures generated by methods to provide improved access to the data include:
	 * 	totalDrugScores, drugClassMutDrugScores, drugClassComboMutDrugScores,
	 *  drugClassMutAllDrugScores, drugClassComboMutAllDrugScores
	 * @throws SQLException
	 *
	 */
	public GeneDRHivdb (Gene gene, MutationSet mutations) throws SQLException {
		super(gene, mutations);

		// Get resistance data by querying HIVDB_Scores
		// There is no comparable method in Resistance that accomplishes each of the following steps
		drugClassDrugMutScores =
			MutationScores.getDrugClassMutScoresForMutSet(gene, mutations);
		drugClassDrugComboMutScores =
			MutationComboScores.getComboMutDrugScoresForMutSet(gene, mutations);
		postConstructor();
		populateTotalDrugScores();

	}

	@Override
	public Map<Drug, Double> getDrugClassTotalDrugScores(DrugClass drugClass) {
		return totalDrugScores.get(drugClass);
	}

	@Override
	public Double getTotalDrugScore(Drug drug) {
		return totalDrugScores.get(drug.getDrugClass()).getOrDefault(drug, .0);
	}

	@Override
	public Integer getDrugLevel(Drug drug) {
		int score = getTotalDrugScore(drug).intValue();
		int level;
		if (score >= 60) 	{
			level = 5;
		} else if (score >=30) {
			level = 4;
		} else if (score >=15) {
			level = 3;
		} else if (score >= 10) {
			level = 2;
		} else {
			level = 1;
		}
		return level;
	}

	@Override
	public String getDrugLevelText(Drug drug) {
		int score = getTotalDrugScore(drug).intValue();
		String interpretation = "";
		if (score >= 60) 	{
			interpretation = "High-level resistance";
		} else if (score >=30) {
			interpretation = "Intermediate resistance";
		} else if (score >=15) {
			interpretation = "Low-level resistance";
		} else if (score >= 10) {
			interpretation = "Potential low-level resistance";
		} else {
			interpretation = "Susceptible";
		}
		return interpretation;
	}

	@Override
	public String getDrugLevelSIR(Drug drug) {
		int score = getTotalDrugScore(drug).intValue();
		String sir;
		if (score >= 60) {
			sir = "R";
		} else if (score >= 15) {
			sir = "I";
		} else {
			sir = "S";
		}
		return sir;
	}

	// Get all scores mutScores and comboMutScores to compute the totalScore for each drug.
	// This method is not required by GeneDrugResistanceAsi because the Asi computes the total scores
	// @return HashMap totalDrugScores: DrugClass => Drug => score
	private Map<DrugClass, Map<Drug, Double>> populateTotalDrugScores() {
		Map<DrugClass, Map<Drug, Double>> resultScores = new EnumMap<>(DrugClass.class);
		for (DrugClass drugClass : drugClassMutAllDrugScores.keySet()) {   // Initialize totalDrugScores to 0
			for (Drug drug : drugClass.getDrugsForHivdbTesting()) {
				if(!resultScores.containsKey(drugClass)) {
					resultScores.put(drugClass, new HashMap<Drug, Double>());
				}
				resultScores.get(drugClass).put(drug, 0.0);
			}
		}

		for (DrugClass drugClass : drugClassMutDrugScores.keySet()) {    // Add individual mutation scores
			if (drugClassMutDrugScores.containsKey(drugClass)) {
				for (Mutation mut : drugClassMutDrugScores.get(drugClass).keySet()) {
					for (Drug drug : drugClassMutDrugScores.get(drugClass).get(mut).keySet()) {
						Double score = drugClassMutDrugScores.get(drugClass).get(mut).get(drug);
						Double subTotal = resultScores.get(drugClass).get(drug);
						subTotal += score;
						resultScores.get(drugClass).put(drug,  subTotal);
					}
				}
			}
		}

		for (DrugClass drugClass : drugClassComboMutDrugScores.keySet()) {     // Add the combination mutation scores
			if (drugClassComboMutDrugScores.containsKey(drugClass)) {
				for (MutationSet comboMuts : drugClassComboMutDrugScores.get(drugClass).keySet()) {
					for (Drug drug : drugClassComboMutDrugScores.get(drugClass).get(comboMuts).keySet()) {
						Double score = drugClassComboMutDrugScores.get(drugClass).get(comboMuts).get(drug);
						Double subTotal = resultScores.get(drugClass).get(drug);
						subTotal += score;
						resultScores.get(drugClass).put(drug, subTotal);
					}
				}
			}
		}
		return resultScores;
	}

}
