/* *********************************************************************** *
 * project: org.matsim.*
 * Controler.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.run.drtBlocking;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.freight.FreightConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.events.handler.EventHandler;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.router.TripStructureUtils;

import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class RunDrtBlockingBerlin {

	private static final String INPUT_CONFIG = "scenarios/berlin-v5.5-10pct/input/drtBlocking/blckBase1.output_config.xml";
	private static final String INPUT_NETWORK_CHANGE_EVENTS = "blckBase1.networkChangeEvents.xml.gz";
	private static final String INPUT_DRT_PLANS = "blckBase1.output_plans_drtOnly_splitAgents.xml.gz";

	//TODO
//	private static final String CARRIERS_PLANS_PLANNED = "D:/svn/shared-svn/projects/pave/matsim-input-files/S7_fleetMultiUse/berlin5.5_1pct_pave_drtBlockingcarriers_planned.xml";
//	private static final String CARRIERS_PLANS_PLANNED = "C:/Users/simon/tubCloud/Shared/MA-Meinhardt/InputDRT/carriers_services_openBerlinNet_LichtenbergNord.xml";
	private static final String CARRIERS_PLANS_PLANNED = "C:/Users/simon/tubCloud/Shared/MA-Meinhardt/InputDRT/carriers_services_openBerlinNet_LichtenbergNord_breitereServiceFenster.xml";
//	private static final String CARRIERS_PLANS_PLANNED = "C:/Users/simon/tubCloud/Shared/MA-Meinhardt/InputDRT/carriers_services_openBerlinNet_LichtenbergNord_planned.xml";
	private static final String CARRIERS_PLANS = "berlin-carriers.xml";
	//TODO
//	private static final String CARRIER_VEHICLE_TYPES = "D:/svn/shared-svn/projects/pave/matsim-input-files/S7_fleetMultiUse/berlin-vehicleTypes.xml";
	private static final String CARRIER_VEHICLE_TYPES = "C:/Users/simon/tubCloud/MA/InputDRT/carrier_vehicleTypes.xml";

	private static final boolean RUN_TOURPLANNING = false;

	//TODO
//	private static final String OUTPUT_DIR = "./output/berlin-v5.5-10pct/drtBlockingTest";
//	private static final String OUTPUT_DIR = "./output/berlin-v5.5-10pct/drtBlockingTest_30Blockings";
	private static final String OUTPUT_DIR = "./output/berlin-v5.5-10pct/drtBlockingTest_30Blockings_ServiceWidthIncr";

	public static void main(String[] args) {


		Config config = RunDrtBlocking.prepareConfig(INPUT_CONFIG, CARRIERS_PLANS_PLANNED, CARRIER_VEHICLE_TYPES);

		config.network().setChangeEventsInputFile(INPUT_NETWORK_CHANGE_EVENTS);
		config.network().setTimeVariantNetwork(true);
		config.controler().setOutputDirectory(OUTPUT_DIR);
		config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);

		config.plans().setInputFile(INPUT_DRT_PLANS);

		Scenario scenario = RunDrtBlocking.prepareScenario(config, true);

//		makePeopleUseDRTForRandomLegs(scenario.getPopulation());
		Controler controler = RunDrtBlocking.prepareControler(scenario);
		controler.run();

//		RunBerlinScenario.runAnalysis(controler);
	}

	private static void makePeopleUseDRTForRandomLegs(Population population){
		List<? extends Person> persons = population.getPersons().values().stream()
				.filter(person -> person.getAttributes().getAttribute("subpopulation").equals("person"))
				.collect(Collectors.toList());

		Random random = MatsimRandom.getLocalInstance();
		for (int i = 0; i <= 200; i++){
			Person person = persons.get(random.nextInt(persons.size()));
			persons.remove(person);
			TripStructureUtils.getTrips(person.getSelectedPlan()).forEach(trip -> {
				if(trip.getTripElements().size() == 1){
					Leg leg = trip.getLegsOnly().get(0);
					leg.setMode("drt");
					leg.setRoute(null);
					TripStructureUtils.setRoutingMode(leg, "drt");
				}
			});
//			TripStructureUtils.getLegs(person.getSelectedPlan()).forEach(leg -> {
//				leg.setMode("drt");
//				leg.getAttributes().putAttribute("routingMode", "drt");
//			});
		}
	}


}