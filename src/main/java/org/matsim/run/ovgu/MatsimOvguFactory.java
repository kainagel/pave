//package org.matsim.run.ovgu;
//
//import java.util.ArrayList;
//import java.util.Collection;
//import java.util.List;
//import java.util.Map;
//
//import org.apache.log4j.Logger;
//import org.matsim.api.core.v01.Id;
//import org.matsim.api.core.v01.network.Link;
//import org.matsim.api.core.v01.network.Network;
//import org.matsim.api.core.v01.network.Node;
//import org.matsim.contrib.freight.carrier.*;
//import org.matsim.contrib.freight.carrier.CarrierCapabilities.FleetSize;
//import org.matsim.contrib.freight.carrier.Tour.Leg;
//import org.matsim.core.config.Config;
//import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
//import org.matsim.core.router.util.TravelDisutility;
//import org.matsim.core.router.util.TravelTime;
//import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
//import org.matsim.utils.leastcostpathtree.LeastCostPathTree;
//
//import ovgu.pave.handler.modelHandler.InputHandler;
//import ovgu.pave.model.input.Edge;
//import ovgu.pave.model.input.FirstRequestActivity;
//import ovgu.pave.model.input.Input;
//import ovgu.pave.model.input.InputFactory;
//import ovgu.pave.model.input.Location;
//import ovgu.pave.model.input.Request;
//import ovgu.pave.model.input.RequestActivity;
//import ovgu.pave.model.input.Requests;
//import ovgu.pave.model.input.SecondRequestActivity;
//import ovgu.pave.model.solution.RequestActivityRouteElement;
//import ovgu.pave.model.solution.Route;
//import ovgu.pave.model.solution.RouteElement;
//import ovgu.pave.model.solution.Solution;
//import ovgu.pave.model.solution.SupportRouteElement;
//
//public class MatsimOvguFactory {
//
//	private static final Logger log = Logger.getLogger(MatsimOvguFactory.class);
//	private MatsimOvguMapping maps = new MatsimOvguMapping();
//	private Carrier carrier;
//
//	public MatsimOvguFactory(Carrier carrier) {
//		this.carrier = carrier;
//	}
//
//	public Input createOVGUInput(Network network, Config config) {
//		log.info("prepare OVGU Input");
//		// init Input
//		Input input = InputFactory.eINSTANCE.createInput();
//		// create Requests (from carrierShipments)
//		input.setRequests(createRequests(carrier.getShipments().values(), network));
//		// create Vehicles and VehicleTypes
//		createVehiclesAndTypes(network);
//		input.getLocations().addAll(maps.getLocations());
//		input.getVehicleTypes().addAll(maps.getVehicleTypes());
//		input.getVehicles().addAll(maps.getVehicles());
//		// create Network
//		input.getEdges().addAll(createNetworkEdges(network, config));
//		return input;
//	}
//
//	public Collection<ScheduledTour> createMatsimTours(Solution solution) {
//		Collection<ScheduledTour> tours = new ArrayList<ScheduledTour>();
//		for (Route route : solution.getRoutes()) {
//			double tourDepTime = Double.NaN;
//			Tour.Builder tourBuilder = Tour.Builder.newInstance();
//
//			for (RouteElement re : route.getRouteElements()) {
//				log.debug("handle RouteElement:" + re.toString());
//				re.getServiceBegin(); // Wann er laut Torurnplan an der Location wäre.
//				re.getServiceDuration(); // Dauer, wie lange er sich an der Location aufhält --> Dopplung zu Request ->
//											// Definition sollte in Auftragsplan vorahnden sein, wegen online kommen
//											// ggf. noch zusätzlich Locations ohne Auftrag hinzu.
//				if (re instanceof RequestActivityRouteElement) {
//					// Dies ist ein eigentlicher Request
//					RequestActivity requestActivity = ((RequestActivityRouteElement) re).getRequestActivity();
//					int requestId = requestActivity.getRequest().getId(); // Request-Id
//					Id<CarrierShipment> shipmentId = maps.getMATSimShipmentID(requestActivity.getRequest());
//					log.debug("handle Activity: " + requestActivity.getId() + " ; requestId: " + requestId
//							+ " ; shipmentId: " + shipmentId);
//					if (requestActivity instanceof FirstRequestActivity) {
//						// Pickup
//						log.debug("Found FirstRequestActivity: " + requestActivity.getId() + " at location "
//								+ requestActivity.getLocation().getId() + " : lat "
//								+ requestActivity.getLocation().getLat() + " long: "
//								+ requestActivity.getLocation().getLon());
//						CarrierShipment carrierShipment = CarrierUtils.getShipment(carrier, shipmentId);
//						tourBuilder.addLeg(new Leg());
//						tourBuilder.schedulePickup(carrierShipment);
//					}
//					if (requestActivity instanceof SecondRequestActivity) {
//						// Delivery
//						log.debug("Found SecondRequestActivity: " + requestActivity.getId() + " at location "
//								+ requestActivity.getLocation().getId() + " : lat "
//								+ requestActivity.getLocation().getLat() + " long: "
//								+ requestActivity.getLocation().getLon());
//						CarrierShipment carrierShipment = CarrierUtils.getShipment(carrier, shipmentId);
//						tourBuilder.addLeg(new Leg());
//						tourBuilder.scheduleDelivery(carrierShipment);
//					}
//					// TODO: Note: Not implemented; Wenn request keine Second Activity hat (=null),
//					// dann ist es "Service".
//				}
//
//				// "Start" und "Ziel" -> Depot. Jede Route verfügt über ein (requestActivity
//				// instanceof SupportRouteElement) am Anfang (Start) und am Ende (Ziel) einer
//				// Route/Tour
//				if (re instanceof SupportRouteElement) {
//					if (route.getRouteElements().indexOf(re) == 0) {
//						// Start Element
//						log.debug("Route Start Element begin: " + re.getServiceBegin());
//						tourDepTime = re.getServiceBegin() + re.getServiceDuration();
//						tourBuilder.scheduleStart(maps.getMATSimLinkID(((SupportRouteElement) re).getLocation()));
//					}
//					if (route.getRouteElements().indexOf(re) == route.getRouteElements().size() - 1) {
//						tourBuilder.addLeg(new Leg());
//						tourBuilder.scheduleEnd(maps.getMATSimLinkID(((SupportRouteElement) re).getLocation()));
//						// Ziel Element
//					}
//				}
//			}
//			// build MATSim-Tour
//			org.matsim.contrib.freight.carrier.Tour vehicleTour = tourBuilder.build();
//
//			Id<org.matsim.vehicles.Vehicle> usedVehicleID = maps.getMATSimVehicleID(route.getVehicle());
//			CarrierVehicle carrierVehicle = CarrierUtils.getCarrierVehicle(carrier, usedVehicleID);
//
//			ScheduledTour sTour = ScheduledTour.newInstance(vehicleTour, carrierVehicle, tourDepTime);
//			// assert route.getDepartureTime() == sTour.getDeparture() : "departureTime of
//			// both route and scheduledTour must be equal";
//			assert sTour.getTour().getStartLinkId() == sTour.getTour().getEndLinkId(); // TODO: Vielleicht für andere
//																						// Cases raus.
//			tours.add(sTour);
//		} // route/tour
//		return tours;
//	}
//
//	/*
//	 * CarrierShipments to Requests
//	 */
//	private Requests createRequests(Collection<CarrierShipment> shipments, Network network) {
//		log.info("prepare carrierShipments/requests");
//		Requests requests = InputFactory.eINSTANCE.createRequests();
//		for (CarrierShipment carrierShipment : shipments) {
//			Location firstActivityLocation = maps.getOVGULocation(carrierShipment.getFrom(), network);
//			Location secondActivityLocation = maps.getOVGULocation(carrierShipment.getTo(), network);
//			Request request = maps.getOVGURequest(carrierShipment.getId(), firstActivityLocation,
//					secondActivityLocation, carrierShipment.getSize());
//
//			request.setRequestTime(0); // da "offline" kommen Anfragen alle zur Sekunde 0 an ;) // sollte aber irgendwo
//										// auch alleine so gesetzt werden... kmt/aug19
//
//			// From
//			// OVGU hat Millisekunden TODO: Testen ob es auch alles in Sekunden geht.);
//			request.getFirstActivity()
//					.setEarliestArrival((long) carrierShipment.getPickupTimeWindow().getStart() * 1000);
//			request.getFirstActivity().setLatestArrival((long) carrierShipment.getPickupTimeWindow().getEnd() * 1000);
//			request.getFirstActivity().setServiceDuration((long) carrierShipment.getPickupServiceTime() * 1000);
//
//			// To
//			// OVGU hat Millisekunden TODO: Testen ob es auch alles in Sekunden geht.);
//			request.getSecondActivity()
//					.setEarliestArrival((long) carrierShipment.getDeliveryTimeWindow().getStart() * 1000);
//			request.getSecondActivity()
//					.setLatestArrival((long) carrierShipment.getDeliveryTimeWindow().getEnd() * 1000);
//			request.getSecondActivity().setServiceDuration((long) carrierShipment.getDeliveryServiceTime() * 1000);
//
//			requests.getNew().add(request);
//
//		}
//		return requests;
//	}
//
//	/*
//	 * create Vehicles and VehicleTypes
//	 */
//	private void createVehiclesAndTypes(Network network) {
//
//		log.info("prepare vehicles / vehicle types");
//		// TODO Fahrzeuge, Fahrzeugtypen übergeben/erstellen
//		if (carrier.getCarrierCapabilities().getFleetSize() == FleetSize.INFINITE) {
//			log.fatal("Not implemented", new RuntimeException()); // Derzeit auch bei OVGU nicht drin.
//		} else if (carrier.getCarrierCapabilities().getFleetSize() == FleetSize.FINITE) {
//			for (CarrierVehicle cVehicle : carrier.getCarrierCapabilities().getCarrierVehicles().values()) {
//
//				Location depot = maps.getOVGULocation(cVehicle.getLocation(), network);
//
//				final double vehicleCapacity = cVehicle.getType().getCapacity().getOther();
//				final int vehicleCapacityInt = (int) vehicleCapacity;
//				if (vehicleCapacity - vehicleCapacityInt > 0) {
//					log.warn("vehicle capacity truncated to int: before=" + vehicleCapacity + "; after=" + vehicleCapacityInt);
//					// yyyyyy this implies that we would have fewer problems if we set vehicle capacity in kg instead of in tons in our data model.  kai, aug'19
//				}
//
//				// create VehicleType and Vehicle in maps (load into input later)
//				ovgu.pave.model.input.VehicleType vehicleType = maps.createOrGetOVGUVehicleType(cVehicle.getVehicleTypeId(),vehicleCapacityInt);
//				maps.getOVGUVehicle(cVehicle.getId(), vehicleType, depot, depot);
//
//			}
//		} else {
//			log.fatal("Missing FleetSize defintion", new RuntimeException());
//		}
//	}
//
//	/*
//	 * create Network-Edges
//	 */
//	private List<Edge> createNetworkEdges(Network network, Config config) {
//		log.info("prepare network");
//		TravelTime tt = new FreeSpeedTravelTime();
//		TravelDisutility tc = new FreespeedTravelTimeAndDisutility(config.planCalcScore());
//		LeastCostPathTree tree = new LeastCostPathTree(tt, tc);
//
//		List<Edge> edges = new ArrayList<Edge>();
//		for (Location from : maps.getLocations()) {
//			Id<Link> fromLinkId = maps.getMATSimLinkID(from);
//			Node fromNode = network.getLinks().get(fromLinkId).getToNode();
//			double starttime = 8.0 * 3600; // Rechne mit 08:00 Fahrzeiten.
//			tree.calculate(network, fromNode, starttime);
//			Map<Id<Node>, LeastCostPathTree.NodeData> result = tree.getTree();
//
//			for (Location to : maps.getLocations()) {
//				Id<Link> toLinkId = maps.getMATSimLinkID(to);
//				Node toNode = network.getLinks().get(toLinkId).getToNode();
//				log.debug("asking for Traveltimes from: " + fromNode + " to: " + toNode);
//				LeastCostPathTree.NodeData abc = result.get(toNode.getId());
//
//				long travelTime = (long) (abc.getTime() - starttime);
//
//				log.debug("travelTime: " + travelTime);
//				// add value to ovgu data
//				Edge edge = InputHandler.createEdge(from, to);
//				edge.setDuration(travelTime * 1000); // OVGU rechnet in ms
//				edges.add(edge);
//			}
//		}
//		return edges;
//
//		// TODO: wird dieser Code verwendet/benötigt? rk/240919
//
//		// TODO: Haben derzeit nur ein beliebeigen FZGTyp und ein beliebiges Fahrzeug!!!
//		// kmt/aug19
////		CarrierVehicleType vehTypeOne = (CarrierVehicleType) carrier.getCarrierCapabilities().getVehicleTypes()
////				.toArray()[0];
////		CarrierVehicle vehOne = (CarrierVehicle) carrier.getCarrierCapabilities().getCarrierVehicles().toArray()[0];
////		org.matsim.vehicles.Vehicle vehicleOne = new VehicleImpl(vehOne.getVehicleId(), vehOne.getVehicleType());
////		log.debug("maxVelocityOf vehicleOne: " + vehicleOne.getType().getMaximumVelocity());
//
//		// double costPerMeter =
//		// vehTypeOne.getVehicleCostInformation().getPerDistanceUnit();
//		// double costPerSecond =
//		// vehTypeOne.getVehicleCostInformation().getPerTimeUnit();
//
//		// FreespeedTravelTimeAndDisutility ttCostCalculator = new
//		// FreespeedTravelTimeAndDisutility(costPerMeter, costPerSecond, costPerSecond);
//		// // TODO: Welchen nehmen wir hier eigentlich -> in freight schauen kmt/aug19
//		// DijkstraFactory dijkstraFactory = new DijkstraFactory();
//		// LeastCostPathCalculator costCalculator =
//		// dijkstraFactory.createPathCalculator(network, ttCostCalculator ,
//		// ttCostCalculator); //Abfrage erstmal für um 8
//
//		// TODO: Das nachfolgende funktioniert. Ist aber derzeit nur a) die Freespeed
//		// TravelTime und b)Fzg-Typ/Eigenschaften UNABHÄNGIG
//		// calculate travel time
////		List<Id<Link>> listOfLinkIds = new ArrayList<>();
////		for (Id<Link> linkId : OVGULocationIdToMatsimLinkId.values()) {
////			listOfLinkIds.add(linkId);
////		}
////		Collections.sort(listOfLinkIds);
//	}
//}
