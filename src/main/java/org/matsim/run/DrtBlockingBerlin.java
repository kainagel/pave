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

package org.matsim.run;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.routing.pt.raptor.RaptorIntermodalAccessEgress;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.av.robotaxi.fares.drt.DrtFareModule;
import org.matsim.contrib.drt.analysis.DrtModeAnalysisModule;
import org.matsim.contrib.drt.routing.MultiModeDrtMainModeIdentifier;
import org.matsim.contrib.drt.run.*;
import org.matsim.contrib.dvrp.run.DvrpModule;
import org.matsim.contrib.dvrp.run.DvrpQSimComponents;
import org.matsim.contrib.freight.FreightConfigGroup;
import org.matsim.contrib.freight.carrier.CarrierPlanXmlWriterV2;
import org.matsim.contrib.freight.carrier.CarrierUtils;
import org.matsim.contrib.freight.utils.FreightUtils;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.router.AnalysisMainModeIdentifier;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.drtBlockings.DrtBlockingModule;
import org.matsim.run.drt.OpenBerlinIntermodalPtDrtRouterAnalysisModeIdentifier;
import org.matsim.run.drt.OpenBerlinIntermodalPtDrtRouterModeIdentifier;
import org.matsim.run.drt.RunDrtOpenBerlinScenario;
import org.matsim.run.drt.intermodalTripFareCompensator.IntermodalTripFareCompensatorsModule;
import org.matsim.run.drt.ptRoutingModes.PtIntermodalRoutingModesModule;

import javax.management.InvalidAttributeValueException;
import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class DrtBlockingBerlin {

	private static final String BERLIN_V5_5_1PCT_DRT_CONFIG = "../../svn/shared-svn/projects/pave/matsim-input-files/S7_fleetMultiUse/berlin-drt-v5.5-1pct.config.xml";

	private static boolean runTourPlanning = true;

	public static void main(String[] args) {

		Config config = RunDrtOpenBerlinScenario.prepareConfig(new String[]{BERLIN_V5_5_1PCT_DRT_CONFIG});
//		config.qsim().setSimStarttimeInterpretation(QSimConfigGroup.StarttimeInterpretation.onlyUseStarttime);
		config.controler().setLastIteration(0);
//		this is not set by RunBerlinScenario, but vsp consistency checker needs it...
//		config.planCalcScore().setFractionOfIterationsToStartScoreMSA(0.8);
		config.controler().setOutputDirectory("output/berlin5.5_1pct_pave_drtBlocking");
		config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);

		SwissRailRaptorConfigGroup srrConfig = ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class);

		//no intermodal access egress for the time being here!
		srrConfig.setUseIntermodalAccessEgress(false);


//		DrtConfigGroup freightDRTCfg = new DrtConfigGroup();
//		freightDRTCfg.setMode("freightDRT");
//		freightDRTCfg.setMaxWaitTime(2 * 3600);
//		freightDRTCfg.setMaxTravelTimeAlpha(1);
//		freightDRTCfg.setMaxTravelTimeBeta(15 * 60);
//		freightDRTCfg.setStopDuration(60);
//		freightDRTCfg.setEstimatedDrtSpeed(27.78);
//		freightDRTCfg.setVehiclesFile(standardDRTCfg.getVehiclesFile());
//
//		freightDRTCfg.setRejectRequestIfMaxWaitOrTravelTimeViolated(false);
//		multiModeDrtConfigGroup.addParameterSet(freightDRTCfg);


//		drtCfg.setVehiclesFile("D:/git/pave/scenarios/berlin/input/vehicles-10-cap-4.xml");

		FreightConfigGroup freightCfg = ConfigUtils.addOrGetModule(config, FreightConfigGroup.class);
		if (runTourPlanning){
			freightCfg.setCarriersFile("berlin-carriers.xml");
		} else {
			freightCfg.setCarriersFile("berlin5.5_1pct_pave_drtBlockingcarriers_planned.xml");
		}
		freightCfg.setCarriersVehicleTypesFile("berlin-vehicleTypes.xml");

		DrtConfigGroup drtCfg = DrtConfigGroup.getSingleModeDrtConfig(config);

		Scenario scenario = RunDrtOpenBerlinScenario.prepareScenario(config);

		FreightUtils.loadCarriersAccordingToFreightConfig(scenario);

		if(runTourPlanning){
			try {
				FreightUtils.getCarriers(scenario).getCarriers().values().forEach(carrier -> {
					CarrierUtils.setCarrierMode(carrier, drtCfg.getMode());
					CarrierUtils.setJspritIterations(carrier, 20);
				});
				FreightUtils.runJsprit(scenario, freightCfg);
				new File(config.controler().getOutputDirectory()).mkdirs();
				new CarrierPlanXmlWriterV2(FreightUtils.getCarriers(scenario)).write(config.controler().getOutputDirectory() + "carriers_planned.xml");
			} catch (InvalidAttributeValueException e) {
				e.printStackTrace();
			}
		}

		makePeopleUseDRTForRandomLegs(scenario.getPopulation());

		Controler controler = prepareControler(scenario);

		controler.run();

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


	static Controler prepareControler(Scenario scenario){
		Controler controler = RunBerlinScenario.prepareControler( scenario ) ;

		configureDRT(scenario, controler);

		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				// use a main mode identifier which knows how to handle intermodal trips generated by the used sbb pt raptor router
				// the SwissRailRaptor already binds its IntermodalAwareRouterModeIdentifier, however drt obviuosly replaces it
				// with its own implementation
				// So we need our own main mode indentifier which replaces both :-(
				bind(MainModeIdentifier.class).to(OpenBerlinIntermodalPtDrtRouterModeIdentifier.class);
				bind(AnalysisMainModeIdentifier.class).to(OpenBerlinIntermodalPtDrtRouterAnalysisModeIdentifier.class);
			}
		});

		controler.addOverridingModule(new IntermodalTripFareCompensatorsModule());
		controler.addOverridingModule(new PtIntermodalRoutingModesModule());

		return controler;
	}

	private static void configureDRT(Scenario scenario, Controler controler) {
		MultiModeDrtConfigGroup multiModeDrtCfg = MultiModeDrtConfigGroup.get(scenario.getConfig());
		DrtConfigGroup drtCfg = DrtConfigGroup.getSingleModeDrtConfig(scenario.getConfig());

		// at the moment, we only configure our 1 drt mode!
		// if you want to use several drt modes AND drt blocking, take care that DrtBlockingModeModule does the QSimBindings for it's mode, so do not use MultiModeDrtModule!
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				install(new DvrpModule());
				install(new DrtModeModule(drtCfg));
//				install(new DrtModeAnalysisModule(drtCfg)); TODO: we have to write a custom OccupanceProfileCalculator that can handle FreightTasks...
				install(new DrtBlockingModule(drtCfg));
				bind(MainModeIdentifier.class).toInstance(new MultiModeDrtMainModeIdentifier(multiModeDrtCfg));
			}
		});
		controler.configureQSimComponents(DvrpQSimComponents.activateAllModes(MultiModeDrtConfigGroup.get(controler.getConfig())));

		// Add drt-specific fare module
		controler.addOverridingModule(new DrtFareModule());
	}


}
