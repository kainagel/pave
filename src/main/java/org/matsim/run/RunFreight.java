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

import java.io.IOException;
import java.net.URL;
import java.util.*;

import com.graphhopper.jsprit.analysis.toolbox.Plotter;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.freight.carrier.Carrier;
import org.matsim.contrib.freight.carrier.CarrierCapabilities;
import org.matsim.contrib.freight.carrier.CarrierImpl;
import org.matsim.contrib.freight.carrier.CarrierPlan;
import org.matsim.contrib.freight.carrier.CarrierPlanXmlWriterV2;
import org.matsim.contrib.freight.carrier.CarrierService;
import org.matsim.contrib.freight.carrier.CarrierShipment;
import org.matsim.contrib.freight.carrier.CarrierVehicle;
import org.matsim.contrib.freight.carrier.CarrierVehicleType;
import org.matsim.contrib.freight.carrier.CarrierVehicleTypeLoader;
import org.matsim.contrib.freight.carrier.CarrierVehicleTypes;
import org.matsim.contrib.freight.carrier.Carriers;
import org.matsim.contrib.freight.carrier.TimeWindow;
import org.matsim.contrib.freight.carrier.CarrierCapabilities.FleetSize;
import org.matsim.contrib.freight.jsprit.MatsimJspritFactory;
import org.matsim.contrib.freight.jsprit.NetworkBasedTransportCosts;
import org.matsim.contrib.freight.jsprit.NetworkRouter;
import org.matsim.contrib.freight.jsprit.NetworkBasedTransportCosts.Builder;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.examples.ExamplesUtils;
import org.matsim.utils.leastcostpathtree.LeastCostPathTree;
import org.matsim.vehicles.EngineInformationImpl;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.EngineInformation.FuelType;

import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.SchrimpfFactory;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.util.Solutions;
import com.graphhopper.jsprit.io.problem.VrpXMLWriter;
import ovgu.data.entity.RouteElement;
import ovgu.utilities.DistanceMatrix;
import ovgu.utilities.RouteHandler;
import ovgu.utilities.Settings;
import ovgu.vrptw.vrpSolver;

public class RunFreight {
	/*
	 * todos:<ul>
	 *       <li> do not overwrite output dir by matsim </li>
	 */
	private static final Logger log = Logger.getLogger(RunFreight.class);
	
	enum Optim {jsprit, ovgu }
	final static Optim optim = Optim.ovgu ;
	
	private static URL scenarioUrl ;
	static{
		scenarioUrl = ExamplesUtils.getTestScenarioURL( "freight-chessboard-9x9" ) ;
	}


	//	Config config = ConfigUtils.loadConfig(configFileName );

	public static void main(String[] args) throws IOException {
		/*
		 * some preparation - set logging level
		 */
		//		Logger.getRootLogger().setLevel(Level.DEBUG);
		Logger.getRootLogger().setLevel(Level.INFO);

		/*
		 * Some Preparation for MATSim
		 */
		Config config = ConfigUtils.createConfig();
		config.setContext(scenarioUrl);
		config.network().setInputFile("grid9x9.xml");
		
		config.controler().setOutputDirectory("./output/freight");
		config.controler().setOverwriteFileSetting( OverwriteFileSetting.deleteDirectoryIfExists );
		new OutputDirectoryHierarchy( config.controler().getOutputDirectory(), config.controler().getRunId(), config.controler().getOverwriteFileSetting() ) ;
		config.controler().setOverwriteFileSetting( OverwriteFileSetting.overwriteExistingFiles );
		// (the directory structure is needed for jsprit output, which is before the controler starts.  Maybe there is a better alternative ...)
	
		config.global().setRandomSeed(4177);
		
		config.controler().setLastIteration(1);
		Scenario scenario = ScenarioUtils.createScenario(config);
		
		OutputDirectoryLogging.initLoggingWithOutputDirectory(config.controler().getOutputDirectory() +"/Logs");

		//Create carrier with services
		Carriers carriers = new Carriers() ;
		Carrier carrierWServices = CarrierImpl.newInstance(Id.create("carrier", Carrier.class));
		carrierWServices.getServices().add(createMatsimService("Service1", "i(3,9)R", 2));
		carrierWServices.getServices().add(createMatsimService("Service2", "i(4,9)R", 2));

		//Create vehicle and assign it to the Carrier
		CarrierVehicleType carrierVehType = createCarrierVehType();
		CarrierVehicleTypes vehicleTypes = new CarrierVehicleTypes() ;
		vehicleTypes.getVehicleTypes().put(carrierVehType.getId(), carrierVehType);

		final Id<Link> depotLinkId = Id.createLinkId( "i(6,0)" );
		CarrierVehicle carrierVehicle = CarrierVehicle.Builder.newInstance(Id.create("gridVehicle", org.matsim.vehicles.Vehicle.class ),
			  depotLinkId ).setEarliestStart(0.0 ).setLatestEnd(36000.0 ).setTypeId(carrierVehType.getId() ).build();
		CarrierCapabilities.Builder ccBuilder = CarrierCapabilities.Builder.newInstance()
													 .addType(carrierVehType)
													 .addVehicle(carrierVehicle)
													 .setFleetSize(FleetSize.INFINITE);
		carrierWServices.setCarrierCapabilities(ccBuilder.build());

		// Add carrier to carriers
		carriers.addCarrier(carrierWServices);

		// assign vehicle types to the carriers
		new CarrierVehicleTypeLoader(carriers).loadVehicleTypes(vehicleTypes) ;

		//load Network and build netbasedCosts for jsprit
		Network network = NetworkUtils.createNetwork();
		new MatsimNetworkReader(network).readURL(IOUtils.newUrl(scenarioUrl ,"grid9x9.xml"));

		switch( optim ) {
			case jsprit:
				/*
				 * Prepare and run jasprit
				 */

				Builder netBuilder = NetworkBasedTransportCosts.Builder.newInstance( network, vehicleTypes.getVehicleTypes().values() );
				final NetworkBasedTransportCosts netBasedCosts = netBuilder.build() ;
				netBuilder.setTimeSliceWidth(1800) ; // !!!!, otherwise it will not do anything.

				for (Carrier carrier : carriers.getCarriers().values()) {
					//Build VRP for jsprit
					VehicleRoutingProblem.Builder vrpBuilder = MatsimJspritFactory.createRoutingProblemBuilder(carrier, network);
					vrpBuilder.setRoutingCost(netBasedCosts) ;
					VehicleRoutingProblem problem = vrpBuilder.build();

					// get the algorithm out-of-the-box, search solution with jsprit and get the best one.
					VehicleRoutingAlgorithm algorithm = new SchrimpfFactory().createAlgorithm(problem);
					Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();
					VehicleRoutingProblemSolution bestSolution = Solutions.bestOf(solutions);

					//Routing bestPlan to Network
					CarrierPlan carrierPlanServicesAndShipments = MatsimJspritFactory.createPlan(carrier, bestSolution) ;
					NetworkRouter.routePlan(carrierPlanServicesAndShipments,netBasedCosts) ;
					carrier.setSelectedPlan(carrierPlanServicesAndShipments) ;

					new VrpXMLWriter(problem, solutions).write(config.controler().getOutputDirectory()+ "/servicesAndShipments_solutions_" + carrier.getId().toString() + ".xml");
					new Plotter( problem, bestSolution ).plot( config.controler().getOutputDirectory()+ "/solution_" + carrier.getId().toString() + ".png", carrier.getId().toString() );
				}
				new CarrierPlanXmlWriterV2(carriers).write( config.controler().getOutputDirectory()+ "servicesAndShipments_jsprit_plannedCarriers.xml") ;
				break;
			case ovgu:

				for ( Carrier carrier : carriers.getCarriers().values() ){

					List<Id<Link>> locations = new ArrayList<>() ;
					// yy todo make above a list of links, not a list of linkIDs

					locations.add( depotLinkId ) ;
					for( CarrierService service : carrier.getServices() ){
						locations.add( service.getLocationLinkId() ) ;
					}
					for( CarrierShipment shipment : carrier.getShipments() ){
						locations.add( shipment.getFrom() ) ;
						locations.add( shipment.getTo() ) ;
					}

					float [][] matrix = new float[locations.size()][locations.size()] ;

					List<Id<Link>> list = new ArrayList<>() ;
					for( Id<Link> linkId : locations ){
						list.add(linkId) ;
					}
					Collections.sort( list ) ;

					TravelTime tt = new FreeSpeedTravelTime();
					TravelDisutility tc = new FreespeedTravelTimeAndDisutility( config.planCalcScore() );
					LeastCostPathTree tree = new LeastCostPathTree( tt, tc );
					for( Id<Link> startLinkId : locations ){
						Node originNode = network.getLinks().get( startLinkId ).getToNode() ;
						double time = 8. * 3600.;
						tree.calculate( scenario.getNetwork(), originNode, time );
						int startIndex = Collections.binarySearch( list, startLinkId ) ;
						Gbl.assertIf( startIndex>=0 );
						Map<Id<Node>, LeastCostPathTree.NodeData> result = tree.getTree();
						for( Id<Link> destLinkId : locations ){
							Node destNode = network.getLinks().get( destLinkId ).getFromNode() ;
							LeastCostPathTree.NodeData abc = result.get( destNode.getId() );
							int destIndex = Collections.binarySearch( list, destLinkId ) ;
							Gbl.assertIf( destIndex >=0  );
							matrix[startIndex][destIndex] = (float) abc.getCost();
						}
					}

					DistanceMatrix mtx = new DistanceMatrix();
					mtx.setDistanceMatrix( matrix );
					vrpSolver vrpSolver = new vrpSolver( mtx ) ;


					ArrayList<RouteElement> currentRequests = new ArrayList<>(  ) ;
					for( CarrierService service : carrier.getServices() ){
						int timeWindowIndex = (int) (service.getServiceStartTimeWindow().getStart()/7200.);
						RouteElement re = new RouteElement( currentRequests.size(), timeWindowIndex, timeWindowIndex ) ;
						int locationIndex = Collections.binarySearch( list, service.getLocationLinkId() );;
						re.setPositionDistanceMatrix( locationIndex );
						re.setStartTimeWindow( (float) service.getServiceStartTimeWindow().getStart() );
						re.setEndTimeWindow( (float) service.getServiceStartTimeWindow().getEnd() );
						// yyyyyy there is some material missing here.
						currentRequests.add(re) ;
					}


					ArrayList<ArrayList<RouteElement>> finalRoutes = null;
					switch( Settings.algorithm ){
						case 0:
							finalRoutes = vrpSolver.startInsertion( currentRequests );
							break;
						case 1:
							finalRoutes = vrpSolver.startLMNS( currentRequests );
							break;
						case 2:
							finalRoutes = vrpSolver.startALNS( currentRequests );
							break;
					}
					RouteHandler.printRoute( finalRoutes );

					// in principle, matsim should generate routes in prepare for sim, i.e. just sequences of activities and legs should be enough at this point.
					// it may, however, not work in that way for carrier because it comes from outside, then we need to fix it in the core, not here.

				}

				break;
		}



		//--------- now start a MATsim run:




		Controler controler = new Controler(scenario);

		controler.run();

		log.info("#### Finished ####");

	}

	/**
	 * Create vehicleType 
	 * @return CarrierVehicleType
	 */
	private static CarrierVehicleType createCarrierVehType() {
		CarrierVehicleType carrierVehType = CarrierVehicleType.Builder.newInstance(Id.create("gridType", VehicleType.class))
												  .setCapacity(3)
												  .setMaxVelocity(10)
												  .setCostPerDistanceUnit(0.0001)
												  .setCostPerTimeUnit(0.001)
												  .setFixCost(130)
												  .setEngineInformation(new EngineInformationImpl(FuelType.diesel, 0.015))
												  .build();
		return carrierVehType;
	}

	private static CarrierShipment createMatsimShipment(String id, String from, String to, int size) {
		Id<CarrierShipment> shipmentId = Id.create(id, CarrierShipment.class);
		Id<Link> fromLinkId = null;
		Id<Link> toLinkId= null;

		if(from != null ) {
			fromLinkId = Id.create(from, Link.class);
		}
		if(to != null ) {
			toLinkId = Id.create(to, Link.class);
		}

		return CarrierShipment.Builder.newInstance(shipmentId, fromLinkId, toLinkId, size)
							.setDeliveryServiceTime(30.0)
							.setDeliveryTimeWindow(TimeWindow.newInstance(3600.0, 36000.0))
							.setPickupServiceTime(5.0)
							.setPickupTimeWindow(TimeWindow.newInstance(0.0, 7200.0))
							.build();
	}

	private static CarrierService createMatsimService(String id, String to, int size) {
		return CarrierService.Builder.newInstance(Id.create(id, CarrierService.class), Id.create(to, Link.class))
						     .setCapacityDemand(size)
						     .setServiceDuration(31.0)
						     .setServiceStartTimeWindow(TimeWindow.newInstance(3601.0, 36001.0))
						     .build();
	}

}
