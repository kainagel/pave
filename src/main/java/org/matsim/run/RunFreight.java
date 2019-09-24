/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2018 by the members listed in the COPYING,        *
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

import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.SchrimpfFactory;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.util.Solutions;
import com.graphhopper.jsprit.io.problem.VrpXMLWriter;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.freight.Freight;
import org.matsim.contrib.freight.carrier.*;
import org.matsim.contrib.freight.carrier.CarrierCapabilities.FleetSize;
import org.matsim.contrib.freight.jsprit.MatsimJspritFactory;
import org.matsim.contrib.freight.jsprit.NetworkBasedTransportCosts;
import org.matsim.contrib.freight.jsprit.NetworkBasedTransportCosts.Builder;
import org.matsim.contrib.freight.jsprit.NetworkRouter;
import org.matsim.contrib.freight.utils.FreightUtils;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.examples.ExamplesUtils;
import org.matsim.vehicles.EngineInformation.FuelType;
import org.matsim.vehicles.EngineInformationImpl;
import org.matsim.vehicles.VehicleType;

import ovgu.pave.core.Core;
import ovgu.pave.model.input.Input;
import ovgu.pave.model.solution.Solution;

import java.io.IOException;
import java.net.URL;
import java.util.*;

class RunFreight {

	private static final Logger log = Logger.getLogger(RunFreight.class);

	enum Optim {
		jsprit, ovgu
	}

	final static Optim optim = Optim.ovgu;

	private static URL scenarioUrl;
	static {
		scenarioUrl = ExamplesUtils.getTestScenarioURL("freight-chessboard-9x9");
	}

	public static void main(String[] args) throws IOException {
		/*
		 * some preparation - set logging level
		 */
		Logger.getRootLogger().setLevel(Level.DEBUG);
		// Logger.getRootLogger().setLevel(Level.INFO);

		/*
		 * Some Preparation for MATSim
		 */
		Config config = prepareConfig();
		Scenario scenario = ScenarioUtils.loadScenario(config);

		OutputDirectoryLogging.initLoggingWithOutputDirectory(config.controler().getOutputDirectory() + "/Logs");

		// Create carrier with services
		Carriers carriers = new Carriers();
		Carrier carrierWShipments = CarrierImpl.newInstance(Id.create("carrier", Carrier.class));
		// TODO: Geht derzeit nur als "int" für ovgu... kmt/aug19
		carrierWShipments.getShipments().add(createMatsimShipment("1", "i(6,0)", "i(3,9)R", 2));
		carrierWShipments.getShipments().add(createMatsimShipment("2", "i(6,0)", "i(4,9)R", 2));

		// Create vehicle type
		CarrierVehicleType carrierVehType = createCarrierVehType();
		CarrierVehicleTypes vehicleTypes = new CarrierVehicleTypes();
		vehicleTypes.getVehicleTypes().put(carrierVehType.getId(), carrierVehType);

		// create vehicle
		final Id<Link> depotLinkId = Id.createLinkId("i(6,0)");
		CarrierVehicle carrierVehicle = CarrierVehicle.Builder
				.newInstance(Id.create("gridVehicle", org.matsim.vehicles.Vehicle.class), depotLinkId)
				.setEarliestStart(0.0).setLatestEnd(36000.0).setTypeId(carrierVehType.getId()).build();

		// capabilities -> assign vehicles or vehicle types to carrier
		CarrierCapabilities.Builder ccBuilder = CarrierCapabilities.Builder.newInstance().addType(carrierVehType)
				.addVehicle(carrierVehicle).setFleetSize(FleetSize.FINITE);
		carrierWShipments.setCarrierCapabilities(ccBuilder.build());

		// Add carrier to carriers
		carriers.addCarrier(carrierWShipments);

		// load vehicle types for the carriers
		new CarrierVehicleTypeLoader(carriers).loadVehicleTypes(vehicleTypes);

		new CarrierPlanXmlWriterV2(carriers).write(config.controler().getOutputDirectory() + "/carriers-wo-plans.xml");
		new CarrierVehicleTypeWriter(CarrierVehicleTypes.getVehicleTypes(carriers))
				.write(config.controler().getOutputDirectory() + "/carrierTypes.xml");

		// matrix costs between locations (cost matrix)
		Network network = scenario.getNetwork();
		Builder netBuilder = NetworkBasedTransportCosts.Builder.newInstance(network,
				vehicleTypes.getVehicleTypes().values());
		final NetworkBasedTransportCosts netBasedCosts = netBuilder.build();

		// time dependent network (1800 = 30 min) --> (option live request)
		netBuilder.setTimeSliceWidth(1800);

		switch (optim) {
		case jsprit:
			log.info("Starting with jsprit algorithm");
			/*
			 * Prepare and run jsprit
			 */

			for (Carrier carrier : carriers.getCarriers().values()) {
				// Build VRP for jsprit
				VehicleRoutingProblem.Builder vrpBuilder = MatsimJspritFactory.createRoutingProblemBuilder(carrier,
						network);
				vrpBuilder.setRoutingCost(netBasedCosts);
				VehicleRoutingProblem problem = vrpBuilder.build();

				// get the algorithm out-of-the-box, search solution with jsprit and get the
				// best one.
				VehicleRoutingAlgorithm algorithm = new SchrimpfFactory().createAlgorithm(problem);
				Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();
				VehicleRoutingProblemSolution bestSolution = Solutions.bestOf(solutions);

				// Routing bestPlan to Network
				CarrierPlan carrierPlanServicesAndShipments = MatsimJspritFactory.createPlan(carrier, bestSolution);
				NetworkRouter.routePlan(carrierPlanServicesAndShipments, netBasedCosts);
				carrier.setSelectedPlan(carrierPlanServicesAndShipments);

				new VrpXMLWriter(problem, solutions).write(config.controler().getOutputDirectory()
						+ "/servicesAndShipments_solutions_" + carrier.getId().toString() + ".xml");
//				new Plotter( problem, bestSolution ).plot( config.controler().getOutputDirectory()+ "/solution_" + carrier.getId().toString() + ".png", carrier.getId().toString() );
			}
			break;
		case ovgu:
			log.info("Starting with OVGU algorithm");
			for (Carrier carrier : carriers.getCarriers().values()) {
				MatsimOvguFactory factory = new MatsimOvguFactory(carrier);

				log.info("convert MATSim to OVGU");
				Input input = factory.createOVGUInput(network, config);

				log.info("run algorithm");
				// run OVGU core with default config and input data
				Core core = new Core();
				core.initConfig("./scenarios/ovgu/defaultConfig.xml");
				core.initInput(input);
				core.initNetwork();
				core.run();

				log.info("handle alg solution");
				Solution solution = core.getSolution();
				Collection<ScheduledTour> tours = factory.createMatsimTours(solution);
				CarrierPlan carrierPlan = new CarrierPlan(carrier, tours);
				carrierPlan.setScore((double) (solution.getScore() * (-1)));
				NetworkRouter.routePlan(carrierPlan, netBasedCosts);
				carrier.setSelectedPlan(carrierPlan);
			} // carrier
		} // ovgu

		new CarrierPlanXmlWriterV2(carriers)
				.write(config.controler().getOutputDirectory() + "/servicesAndShipments_plannedCarriers.xml");

		// --------- now register freight and start a MATsim run:
		scenario.addScenarioElement(FreightUtils.CARRIERS, carriers);
		Controler controler = new Controler(scenario);
		Freight.configure(controler);

		controler.run();

		log.info("#### Finished ####");

	}

	/**
	 * @return
	 */
	private static Config prepareConfig() {
		Config config = ConfigUtils.createConfig();
		config.setContext(scenarioUrl);
		config.network().setInputFile("grid9x9.xml");

		config.controler().setOutputDirectory("./output/freight");
		config.controler().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);
		new OutputDirectoryHierarchy(config.controler().getOutputDirectory(), config.controler().getRunId(),
				config.controler().getOverwriteFileSetting());
		config.controler().setOverwriteFileSetting(OverwriteFileSetting.overwriteExistingFiles);
		// (the directory structure is needed for jsprit output, which is before the
		// controler starts. Maybe there is a better alternative ...)

		config.global().setRandomSeed(4177);

		config.controler().setLastIteration(0);
		return config;
	}

	/**
	 * Create vehicleType
	 * 
	 * @return CarrierVehicleType
	 */
	private static CarrierVehicleType createCarrierVehType() {
		CarrierVehicleType carrierVehType = CarrierVehicleType.Builder
				.newInstance(Id.create("gridType", VehicleType.class)).setCapacity(5).setMaxVelocity(10) // m/s
				.setCostPerDistanceUnit(0.0001).setCostPerTimeUnit(0.001).setFixCost(130)
				.setEngineInformation(new EngineInformationImpl(FuelType.diesel, 0.015)).build();
		return carrierVehType;
	}

	private static CarrierShipment createMatsimShipment(String id, String from, String to, int size) {
		Id<CarrierShipment> shipmentId = Id.create(id, CarrierShipment.class);
		Id<Link> fromLinkId = null;
		Id<Link> toLinkId = null;

		if (from != null) {
			fromLinkId = Id.create(from, Link.class);
		}
		if (to != null) {
			toLinkId = Id.create(to, Link.class);
		}

		return CarrierShipment.Builder.newInstance(shipmentId, fromLinkId, toLinkId, size).setDeliveryServiceTime(30.0)
				.setDeliveryTimeWindow(TimeWindow.newInstance(3600.0, 36000.0)).setPickupServiceTime(5.0)
				.setPickupTimeWindow(TimeWindow.newInstance(0.0, 7200.0)).build();
	}

	private static CarrierService createMatsimService(String id, String to, int size) {
		return CarrierService.Builder.newInstance(Id.create(id, CarrierService.class), Id.create(to, Link.class))
				.setCapacityDemand(size).setServiceDuration(31.0)
				.setServiceStartTimeWindow(TimeWindow.newInstance(3601.0, 36001.0)).build();
	}

}
