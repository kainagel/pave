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

package org.matsim.pfav.analysis;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.*;
import org.matsim.api.core.v01.events.handler.*;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.freight.carrier.*;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.vehicles.Vehicle;
import org.matsim.pfav.privateAV.PFAVEventsReader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BaseCaseFreightTourStatsListener implements ActivityEndEventHandler, ActivityStartEventHandler,
        VehicleEntersTrafficEventHandler, VehicleLeavesTrafficEventHandler, LinkEnterEventHandler, IterationEndsListener {

    private Map<Id<Person>, Double> departureTimes = new HashMap<>();

    private Map<Id<Person>, Id<Vehicle>> driverToVehicle = new HashMap<>();
    private Map<Id<Vehicle>, Id<Person>> vehicleToDriver = new HashMap<>();

    private Map<Id<Vehicle>, Double> lastLegLength = new HashMap<>();

    private List<FreightTourData> allTours = new ArrayList<>();

    private Map<Id<Person>, FreightTourData> currentTours = new HashMap<>();

    private Network network;

    private Map<Id<Person>, ScheduledTour> driverToScheduledTour = null;
    private Map<Id<Person>, Double> serviceActStartTimes = new HashMap<>();

    public BaseCaseFreightTourStatsListener(Network network) {
        this.network = network;
    }

    public BaseCaseFreightTourStatsListener(Network network, Carriers carriers) {
        this.network = network;
        this.driverToScheduledTour = new HashMap<Id<Person>, ScheduledTour>();
        for (Carrier c : carriers.getCarriers().values()) {
            int nextId = 0;
            for (ScheduledTour tour : c.getSelectedPlan().getScheduledTours()) {
                Id<Person> agentID = Id.createPersonId("freight_" + c.getId() + "_veh_" + tour.getVehicle().getId() + "_" + nextId);// this assumes that CarrierAgentTracker is used and is based on CarrierAgent.createDriverId)
                nextId++;
                this.driverToScheduledTour.put(agentID, tour);
            }
        }
    }

    public static void main(String[] args) {
        String dir = "C:/Users/Work/tubCloud/MasterArbeit/Runs/serious/bCs_gzBln_11k_Truck/";
        String input = dir + "berlin-v5.3-1pct.output_events.xml.gz";
        String carriersFile = dir + "output_carriers.xml";
        final Carriers carriers = new Carriers();
        new CarrierPlanXmlReader(carriers).readFile(carriersFile);

        EventsManager manager = EventsUtils.createEventsManager();
        Network network = NetworkUtils.createNetwork();
        new MatsimNetworkReader(network).readFile(dir + "berlin-v5.3-1pct.output_network.xml.gz");

        BaseCaseFreightTourStatsListener handler = new BaseCaseFreightTourStatsListener(network, carriers);
        manager.addHandler(handler);
        MatsimEventsReader reader = PFAVEventsReader.create(manager);
        String output = dir + "/ITERS/it.0/FreightTourStats_it0.csv";

        reader.readFile(input);
        handler.writeStats(output);
    }


    @Override
    public void handleEvent(VehicleEntersTrafficEvent event) {
        if (this.departureTimes.containsKey(event.getPersonId())) {
            this.lastLegLength.put(event.getVehicleId(), 0.);
            this.driverToVehicle.put(event.getPersonId(), event.getVehicleId());
            this.vehicleToDriver.put(event.getVehicleId(), event.getPersonId());
        }
    }


    @Override
    public void handleEvent(LinkEnterEvent event) {
        FreightTourData data = this.currentTours.get(this.vehicleToDriver.get(event.getVehicleId()));
        if (data != null) {
            data.distance += network.getLinks().get(event.getLinkId()).getLength();
            Double length = this.lastLegLength.get(event.getVehicleId());
            this.lastLegLength.put(event.getVehicleId(), length + network.getLinks().get(event.getLinkId()).getLength());
        }
    }

    @Override
    public void handleEvent(VehicleLeavesTrafficEvent event) {
        if (this.currentTours.containsKey(event.getPersonId())) {
            this.currentTours.get(event.getPersonId()).emptyDistance = this.lastLegLength.remove(event.getVehicleId());
        }
    }

    @Override
    public void handleEvent(ActivityEndEvent event) {
        if (event.getActType().equals("start")) {
            if (this.departureTimes.containsKey(event.getPersonId()))
                throw new IllegalStateException("two carrier agent departures in a row of agent " + event.getPersonId());
            this.departureTimes.put(event.getPersonId(), event.getTime());

            FreightTourData data = new FreightTourData(event.getLinkId().toString(), event.getTime());
            this.currentTours.put(event.getPersonId(), data);
        } else if (event.getActType().equals("service")) {
            this.currentTours.get(event.getPersonId()).totalServiceTime += event.getTime() - this.serviceActStartTimes.remove(event.getPersonId());
        }
    }

    /**
     * Notifies all observers of the Controler that a iteration is finished
     *
     * @param event
     */
    @Override
    public void notifyIterationEnds(IterationEndsEvent event) {
        String dir = event.getServices().getConfig().controler().getOutputDirectory() + "/ITERS/it." + event.getIteration() + "/";
        writeStats(dir + "FreightTourStats_it" + event.getIteration() + ".csv");
    }


    @Override
    public void handleEvent(ActivityStartEvent event) {
        if (event.getActType().equals("end")) {
            event.getPersonId();
            if (this.departureTimes.containsKey(event.getPersonId())) {
                FreightTourData data = this.currentTours.remove(event.getPersonId());
                data.actualTourDuration = event.getTime() - departureTimes.remove(event.getPersonId());

                this.allTours.add(data);
            }
        } else if (event.getActType().equals("service")) {
            FreightTourData data = currentTours.get(event.getPersonId());
            if (this.driverToScheduledTour != null) {
                ScheduledTour carrierTour = this.driverToScheduledTour.get(event.getPersonId());

                Tour.ServiceActivity serviceAct = data.getCurrentServiceAct(carrierTour);
                double delay = event.getTime() - serviceAct.getTimeWindow().getEnd();
                double waitTimeAtService = serviceAct.getTimeWindow().getStart() - event.getTime();
                if (waitTimeAtService > 0 && delay > 0)
                    throw new IllegalStateException("driver " + event.getPersonId() + " both is too early AND to late at service " + serviceAct.getService());
                if (waitTimeAtService < 0) waitTimeAtService = 0;
                data.totalWaitTimeAtServices += waitTimeAtService;
                if (data.plannedTourDuration == null) {
                    data.waitTimeAtFirstService = waitTimeAtService;
                    data.deriveInformationFromScheduledTour(carrierTour);
                    data.driverId = event.getPersonId().toString();
                }
                this.serviceActStartTimes.put(event.getPersonId(), event.getTime());
                if (delay > 0) data.increaseDelayAtServices(delay);
            }
            this.lastLegLength.put(this.driverToVehicle.get(event.getPersonId()), 0.);
        }
    }

    /**
     * Gives the event handler the possibility to clean up its internal state.
     * Within a Controler-Simulation, this is called before the mobsim starts.
     *
     * @param iteration the up-coming iteration from which up-coming events will be from.
     */
    @Override
    public void reset(int iteration) {
        this.allTours = new ArrayList<>();
    }

    void writeStats(String file) {
        BufferedWriter writer = IOUtils.getBufferedWriter(file);
        try {
            int i = 1;
            writer.write("index;departureTime;totalServiceTime;waitTimeAtFirstService;totalWaitTimeAtServices;" +
                    "travelledDistance;emptyDistance;" +
                    "plannedTourDuration;actualTourDuration;serviceCount;totalDelay;depotLink;driverId");
            writer.newLine();

            for (FreightTourData data : this.allTours) {
                writer.write("" + i + ";" + data.departureTime + ";" + data.totalServiceTime + ";" + data.waitTimeAtFirstService + ";" + data.totalWaitTimeAtServices
                        + ";" + data.distance + ";" + data.emptyDistance
                        + ";" + data.plannedTourDuration + ";" + data.actualTourDuration + ";" + data.serviceCount
                        + ";" + data.accumulatedDelayAtServices + ";" + data.depot + ";" + data.driverId);
                writer.newLine();
                i++;
            }

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class FreightTourData {

        private final String depot;
        private final double departureTime;
        double totalServiceTime = 0;
        double waitTimeAtFirstService = 0;
        double totalWaitTimeAtServices = 0;

        private Double plannedTourDuration = null;
        private int serviceCount;
        private int serviceIdx = -1;

        private double distance = 0.;
        private double actualTourDuration = 0.;
        private double emptyDistance = 0.;
        private double accumulatedDelayAtServices = 0;

        private String driverId = "";

        private FreightTourData(String depot, double departureTime) {
            this.depot = depot;
            this.departureTime = departureTime;
        }

        void deriveInformationFromScheduledTour(ScheduledTour tour) {
            //for the times set at the end activity, see comments above. we need this workaround here
            int size = tour.getTour().getTourElements().size();
            double tEnde = ((Tour.Leg) tour.getTour().getTourElements().get(size - 1)).getExpectedDepartureTime()
                    + ((Tour.Leg) tour.getTour().getTourElements().get(size - 1)).getExpectedTransportTime();

            this.plannedTourDuration = tEnde - ((Tour.Leg) tour.getTour().getTourElements().get(0)).getExpectedDepartureTime();
            this.serviceCount = (size - 1) / 2;
        }


        private void increaseDelayAtServices(double amount) {
            this.accumulatedDelayAtServices += amount;
        }


        Tour.ServiceActivity getCurrentServiceAct(ScheduledTour carrierTour) {
            // we only increase serviceIdx once per service, so we need to account for the TourLegs in the ScheduledTour
            this.serviceIdx += 2;
            return (Tour.ServiceActivity) carrierTour.getTour().getTourElements().get(serviceIdx);
        }
    }

}
