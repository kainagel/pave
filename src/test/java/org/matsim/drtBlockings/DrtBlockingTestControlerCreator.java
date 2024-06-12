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

package org.matsim.drtBlockings;

import java.util.Collection;
import java.util.concurrent.ExecutionException;

import javax.management.InvalidAttributeValueException;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.contrib.drt.analysis.DrtModeAnalysisModule;
import org.matsim.contrib.drt.optimizer.insertion.ExtensiveInsertionSearchParams;
import org.matsim.contrib.drt.routing.DrtRoute;
import org.matsim.contrib.drt.routing.DrtRouteFactory;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.DrtModeModule;
import org.matsim.contrib.drt.run.DrtModeQSimModule;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpModule;
import org.matsim.contrib.dvrp.run.DvrpQSimComponents;
import org.matsim.contrib.freight.FreightConfigGroup;
import org.matsim.contrib.freight.utils.FreightUtils;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.events.handler.EventHandler;
import org.matsim.core.scenario.ScenarioUtils;

class DrtBlockingTestControlerCreator {


	static Controler createControlerForChessboardTest(Collection<EventHandler> eventHandlers) throws InvalidAttributeValueException {

		String input = "chessboard/";
		Config config = ConfigUtils.loadConfig(input + "chessboard_drtBlocking_config.xml");
		config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);

		config.controler().setLastIteration(0);
		config.qsim().setEndTime(30*3600);

		FreightConfigGroup freightCfg = ConfigUtils.addOrGetModule(config, FreightConfigGroup.class);
		freightCfg.setCarriersFile("chessboard_carriers_drtBlocking.xml");
		freightCfg.setCarriersVehicleTypesFile("chessboard_vehicleTypes.xml");

		DrtConfigGroup drtCfg = prepareDrtConfig(config);

		Scenario scenario = ScenarioUtils.loadScenario(config);

		FreightUtils.loadCarriersAccordingToFreightConfig(scenario);

		try {
			FreightUtils.runJsprit(scenario);
		} catch (ExecutionException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
//        new CarrierPlanXmlWriterV2(FreightUtils.getCarriers(scenario)).write(config.controler().getOutputDirectory() + "carriers_planned.xml");

		setupPopulation(scenario);

		Controler controler = new Controler(scenario);
		configureControler(drtCfg, controler, eventHandlers);
		return controler;
	}

	private static void configureControler(DrtConfigGroup drtCfg, Controler controler, Collection<EventHandler> eventHandlers) {
		controler.addOverridingModule( new DvrpModule() ) ;
		controler.addOverridingModule( new DrtBlockingModule(drtCfg));
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				eventHandlers.forEach(eventHandler -> addEventHandlerBinding().toInstance(eventHandler));
			}
		});
		controler.configureQSimComponents( DvrpQSimComponents.activateModes( drtCfg.getMode() ) ) ;
	}

	private static void setupPopulation(Scenario scenario) {
		scenario.getPopulation()
				.getFactory()
				.getRouteFactories()
				.setRouteFactory(DrtRoute.class, new DrtRouteFactory());

		//delete the old population
		scenario.getPopulation().getPersons().clear();

		PopulationFactory popFactory = scenario.getPopulation().getFactory();

		{
			Id<Link> leftLink = Id.createLinkId(1);
			Id<Link> rightLink = Id.createLinkId(9);
			for (int i = 1; i <= 86; i++) {
				Person person = createPersons(leftLink, rightLink, popFactory, "horizontalTraveller_", i);
				scenario.getPopulation().addPerson(person);
			}
		}

//		{ //there will be only verticalTravellers that travel from firstLink to secondLink, not vice-versa
//			Id<Link> firstLink = Id.createLinkId(12);
//			Id<Link> secondLink = Id.createLinkId(1);
//			{
//				Person person = createPersons(firstLink, secondLink, popFactory, "verticalTraveller_", 30); //dep 9h
//				scenario.getPopulation().addPerson(person);
//			}
//			{
//				Person person = createPersons(firstLink, secondLink, popFactory, "verticalTraveller_", 34); //dep 9.40h
//				scenario.getPopulation().addPerson(person);
//			}
//			{
//				Person person = createPersons(firstLink, secondLink, popFactory, "verticalTraveller_", 38); //dep 10.20h
//				scenario.getPopulation().addPerson(person);
//			}
//			{
//				Person person = createPersons(firstLink, secondLink, popFactory, "verticalTraveller_", 48); //dep 12.00h
//				scenario.getPopulation().addPerson(person);
//			}
//
//		}

	}

	private static Person createPersons(Id<Link> firstLink, Id<Link> secondLink, PopulationFactory popFactory, String idString, int i) {
		Person person = popFactory.createPerson(Id.createPersonId(idString + i));
		Plan plan = popFactory.createPlan();
		Activity act1;
		Activity act2;
		if (i % 2 == 0) {
			act1 = popFactory.createActivityFromLinkId("home", firstLink);
		} else {
			act1 = popFactory.createActivityFromLinkId("home", secondLink);
		}
		act1.setEndTime(4 * 3600 + i * 10 * 60);
		plan.addActivity(act1);

		plan.addLeg(popFactory.createLeg("drt"));

		if (i % 2 == 0) {
			act2 = popFactory.createActivityFromLinkId("work", secondLink);
		} else {
			act2 = popFactory.createActivityFromLinkId("work", firstLink);
		}

		plan.addActivity(act2);

		person.addPlan(plan);
		return person;
	}

	private static DrtConfigGroup prepareDrtConfig(Config config) {
		ConfigUtils.addOrGetModule(config, DvrpConfigGroup.class);

		MultiModeDrtConfigGroup multiModeDrtConfigGroup = ConfigUtils.addOrGetModule(config, MultiModeDrtConfigGroup.class);

		DrtConfigGroup drtCfg = new DrtConfigGroup();//DrtConfigGroup.getSingleModeDrtConfig(config);
		drtCfg.setMode(TransportMode.drt);
		drtCfg.setMaxWaitTime(30 * 60);
		drtCfg.setMaxTravelTimeAlpha(1);
		drtCfg.setMaxTravelTimeBeta(15 * 60);
		drtCfg.setStopDuration(60);
		drtCfg.setVehiclesFile("drtBlockingVehicles.xml");
		drtCfg.addParameterSet(new ExtensiveInsertionSearchParams());
		config.qsim().setSimStarttimeInterpretation(QSimConfigGroup.StarttimeInterpretation.onlyUseStarttime);

		multiModeDrtConfigGroup.addParameterSet(drtCfg);

		return drtCfg;
	}


}
