//package org.matsim.run.ovgu;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//
//import org.matsim.api.core.v01.Id;
//import org.matsim.api.core.v01.network.Link;
//import org.matsim.api.core.v01.network.Network;
//import org.matsim.contrib.freight.carrier.CarrierShipment;
//
//import ovgu.pave.handler.modelHandler.InputHandler;
//import ovgu.pave.model.input.Location;
//import ovgu.pave.model.input.Request;
//import ovgu.pave.model.input.Vehicle;
//import ovgu.pave.model.input.VehicleType;
//
//public class MatsimOvguMapping {
//
//	public void clear() {
//		matsimToOVGUVehicleType = new HashMap<Id<org.matsim.vehicles.VehicleType>, Integer>();
//		OVGUtoMatsimVehicleType = new HashMap<Integer, Id<org.matsim.vehicles.VehicleType>>();
//		matsimToOVGUVehicle = new HashMap<Id<org.matsim.vehicles.Vehicle>, Integer>();
//		OVGUtoMatsimVehicle = new HashMap<Integer, Id<org.matsim.vehicles.Vehicle>>();
//		matsimLinkIdToOVGULocationID = new HashMap<Id<Link>, Integer>();
//		OVGULocationIdToMatsimLinkId = new HashMap<Integer, Id<Link>>();
//		matsimShipmentIdToOVGURequestID = new HashMap<Id<CarrierShipment>, Integer>();
//		OVGURequestIdToMatsimShipmentId = new HashMap<Integer, Id<CarrierShipment>>();
//	}
//
//	/*
//	 * Map VehicleTypes
//	 */
//	private HashMap<Id<org.matsim.vehicles.VehicleType>, Integer> matsimToOVGUVehicleType = new HashMap<Id<org.matsim.vehicles.VehicleType>, Integer>();
//	private HashMap<Integer, Id<org.matsim.vehicles.VehicleType>> OVGUtoMatsimVehicleType = new HashMap<Integer, Id<org.matsim.vehicles.VehicleType>>();
//	private List<VehicleType> vehicleTypes = new ArrayList<VehicleType>();
//
//	public VehicleType createOrGetOVGUVehicleType(Id<org.matsim.vehicles.VehicleType> vehicleTypeID, int capacity) {
//		return vehicleTypes.get(createOrGetOVGUVehicleTypeID(vehicleTypeID, capacity));
//	}
//
//	/**
//	 * Get the (OVGU) vehicleTypeId from the matsim VehicleType.
//	 * If it does not exist at this moment it will be created in advance.
//	 * @param vehicleTypeID
//	 * @param capacity
//	 * @return
//	 */
//	private Integer createOrGetOVGUVehicleTypeID(Id<org.matsim.vehicles.VehicleType> vehicleTypeID, int capacity) {
//		if (!matsimToOVGUVehicleType.containsKey(vehicleTypeID))
//			createOVGUVehicleType(vehicleTypeID, capacity);
//
//		return matsimToOVGUVehicleType.get(vehicleTypeID);
//	}
//
//	/**
//	 * Create an OVGU Vehicle Type based on the MATSim Vehicle type ID and its capacity and register it to
//	 * maps which translates both vehicleTypes to each other.
//	 * @param vehicleTypeID
//	 * @param capacity
//	 */
//	private void createOVGUVehicleType(Id<org.matsim.vehicles.VehicleType> vehicleTypeID, int capacity) {
//		Integer ovguVehTypeId = matsimToOVGUVehicleType.size();
//		matsimToOVGUVehicleType.put(vehicleTypeID, ovguVehTypeId);
//		OVGUtoMatsimVehicleType.put(ovguVehTypeId, vehicleTypeID);
//		vehicleTypes.add(InputHandler.createVehicleType(ovguVehTypeId, capacity));
//	}
//
//	public Id<org.matsim.vehicles.VehicleType> getMATSimVehicleTypeID(VehicleType vehicleType) {
//		return OVGUtoMatsimVehicleType.get(vehicleType.getId());
//	}
//
//	public List<VehicleType> getVehicleTypes() {
//		return vehicleTypes;
//	}
//
//	/*
//	 * Map Vehicles
//	 */
//
//	private HashMap<Id<org.matsim.vehicles.Vehicle>, Integer> matsimToOVGUVehicle = new HashMap<Id<org.matsim.vehicles.Vehicle>, Integer>();
//	private HashMap<Integer, Id<org.matsim.vehicles.Vehicle>> OVGUtoMatsimVehicle = new HashMap<Integer, Id<org.matsim.vehicles.Vehicle>>();
//	private List<Vehicle> vehicles = new ArrayList<Vehicle>();
//
//	public Vehicle getOVGUVehicle(Id<org.matsim.vehicles.Vehicle> vehicleID, VehicleType vehicleType,
//			Location startLocation, Location endLocation) {
//		return vehicles.get(getOVGUVehicleID(vehicleID, vehicleType, startLocation, endLocation));
//	}
//
//	private Integer getOVGUVehicleID(Id<org.matsim.vehicles.Vehicle> vehicleID, VehicleType vehicleType,
//			Location startLocation, Location endLocation) {
//		if (!matsimToOVGUVehicle.containsKey(vehicleID))
//			createOVGUVehicle(vehicleID, vehicleType, startLocation, endLocation);
//
//		return matsimToOVGUVehicle.get(vehicleID);
//	}
//
//	private void createOVGUVehicle(Id<org.matsim.vehicles.Vehicle> vehicleID, VehicleType vehicleType,
//			Location startLocation, Location endLocation) {
//		Integer ovguVehId = matsimToOVGUVehicle.size();
//		matsimToOVGUVehicle.put(vehicleID, ovguVehId);
//		OVGUtoMatsimVehicle.put(ovguVehId, vehicleID);
//		vehicles.add(InputHandler.createVehicle(ovguVehId, vehicleType, startLocation, endLocation));
//	}
//
//	public Id<org.matsim.vehicles.Vehicle> getMATSimVehicleID(Vehicle vehicle) {
//		return OVGUtoMatsimVehicle.get(vehicle.getId());
//	}
//
//	public List<Vehicle> getVehicles() {
//		return vehicles;
//	}
//
//	/*
//	 * Map LinkIDs and Locations
//	 */
//	private HashMap<Id<Link>, Integer> matsimLinkIdToOVGULocationID = new HashMap<Id<Link>, Integer>();
//	private HashMap<Integer, Id<Link>> OVGULocationIdToMatsimLinkId = new HashMap<Integer, Id<Link>>();
//	private List<Location> locations = new ArrayList<Location>();
//
//	public Location getOVGULocation(Id<Link> link, Network network) {
//		return locations.get(getOVGULocationID(link, network));
//	}
//
//	private Integer getOVGULocationID(Id<Link> link, Network network) {
//		if (!matsimLinkIdToOVGULocationID.containsKey(link))
//			createOVGULocation(link, network);
//
//		return matsimLinkIdToOVGULocationID.get(link);
//	}
//
//	private void createOVGULocation(Id<Link> link, Network network) {
//		Integer ovguLocId = matsimLinkIdToOVGULocationID.size();
//		matsimLinkIdToOVGULocationID.put(link, ovguLocId);
//		OVGULocationIdToMatsimLinkId.put(ovguLocId, link);
//		locations.add(InputHandler.createLocation(ovguLocId, network.getLinks().get(link).getCoord().getX(),
//				network.getLinks().get(link).getCoord().getY()));
//	}
//
//	public Id<Link> getMATSimLinkID(Location location) {
//		return OVGULocationIdToMatsimLinkId.get(location.getId());
//	}
//
//	public List<Location> getLocations() {
//		return locations;
//	}
//
//	/*
//	 * Map Shipment and Request
//	 */
//	private HashMap<Id<CarrierShipment>, Integer> matsimShipmentIdToOVGURequestID = new HashMap<Id<CarrierShipment>, Integer>();
//	private HashMap<Integer, Id<CarrierShipment>> OVGURequestIdToMatsimShipmentId = new HashMap<Integer, Id<CarrierShipment>>();
//	private List<Request> requests = new ArrayList<Request>();
//
//	public Request getOVGURequest(Id<CarrierShipment> shipment, Location firstActivityLocation,
//			Location secondActivityLocation, int shipmentSize) {
//		return requests.get(getOVGURequestID(shipment, firstActivityLocation, secondActivityLocation, shipmentSize));
//	}
//
//	private Integer getOVGURequestID(Id<CarrierShipment> shipment, Location firstActivityLocation,
//			Location secondActivityLocation, int shipmentSize) {
//		if (!matsimShipmentIdToOVGURequestID.containsKey(shipment))
//			createOVGURequest(shipment, firstActivityLocation, secondActivityLocation, shipmentSize);
//
//		return matsimShipmentIdToOVGURequestID.get(shipment);
//	}
//
//	private void createOVGURequest(Id<CarrierShipment> shipment, Location firstActivityLocation,
//			Location secondActivityLocation, int shipmentSize) {
//		Integer ovguReqId = matsimShipmentIdToOVGURequestID.size();
//		matsimShipmentIdToOVGURequestID.put(shipment, ovguReqId);
//		OVGURequestIdToMatsimShipmentId.put(ovguReqId, shipment);
//		requests.add(
//				InputHandler.createRequest(ovguReqId, firstActivityLocation, secondActivityLocation, shipmentSize));
//	}
//
//	public Id<CarrierShipment> getMATSimShipmentID(Request request) {
//		return OVGURequestIdToMatsimShipmentId.get(request.getId());
//	}
//
//	public List<Request> getRequests() {
//		return requests;
//	}
//}
