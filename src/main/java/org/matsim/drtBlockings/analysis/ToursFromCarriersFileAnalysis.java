package org.matsim.drtBlockings.analysis;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.freight.FreightConfigGroup;
import org.matsim.contrib.freight.carrier.*;
import org.matsim.contrib.freight.utils.FreightUtils;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.vehicles.Vehicle;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;

public class ToursFromCarriersFileAnalysis implements IterationEndsListener {

    private Carriers carriers;
    private Network network;

    private List<CarrierTourData> tours = new ArrayList<>();

    public ToursFromCarriersFileAnalysis(Network network, Carriers carriers) {
        this.network = network;
        this.carriers = carriers;
    }


    public static void main(String[] args) {

        String dir = "C:/Users/simon/Documents/UNI/MA/Projects/paveFork/output/berlin-v5.5-10pct/policy_cases/";
        String inputConfig = dir + "p2-23.output_config.xml";
        String inputNetwork = dir + "p2-23DRTBlockingPolicyCase.output_network.xml.gz";
        String inputPlans = dir + "p2-23.output_plans_200Persons.xml.gz";
        String inputCarriers = dir + "carriers_4hTimeWindows_openBerlinNet_8-24.xml";
        String inputVehicleTypes = dir + "carrier_vehicleTypes.xml";
        String outputFile = dir + "ToursFromCarriersAnalysis.csv";
        Config config = ConfigUtils.loadConfig(inputConfig);

        config.network().setInputFile(inputNetwork);

        config.plans().setInputFile(inputPlans);
        FreightConfigGroup freightCfg = ConfigUtils.addOrGetModule(config, FreightConfigGroup.class);
        freightCfg.setCarriersFile(inputCarriers);
        freightCfg.setCarriersVehicleTypesFile(inputVehicleTypes);

        Scenario scenario = ScenarioUtils.loadScenario(config);
        FreightUtils.loadCarriersAccordingToFreightConfig(scenario);

        Network network = scenario.getNetwork();

        Carriers carriers = FreightUtils.getCarriers(scenario);
        ToursFromCarriersFileAnalysis analysis = new ToursFromCarriersFileAnalysis(network, carriers);
        analysis.analyzeCarrierServices(carriers, network);
        analysis.writeStats(outputFile);
        System.out.println("Writing of carrier service stats to " + outputFile + " was successful!");
    }

    public void analyzeCarrierServices(Carriers carriers, Network network) {
        for(Carrier carrier : carriers.getCarriers().values()) {
            for(ScheduledTour tour : carrier.getSelectedPlan().getScheduledTours()) {

                Id<Vehicle> id = tour.getVehicle().getId();
                double start = tour.getTour().getStart().getTimeWindow().getStart();

                double distance = 0;
                int serviceCount = 0;
                double duration = 0;
                List<Id<CarrierService>> services = new ArrayList<>();
                for(Tour.TourElement e : tour.getTour().getTourElements()) {
                    if(e instanceof Tour.Leg) {


                        for( Id<Link> linkId : ((NetworkRoute) ((Tour.Leg) e).getRoute()).getLinkIds()) {
                            distance = distance + network.getLinks().get(linkId).getLength();
                        }

                        duration = duration + ((Tour.Leg) e).getExpectedTransportTime();
                    } else if(e instanceof Tour.ServiceActivity) {
                        serviceCount = serviceCount + 1;
                        services.add(((Tour.ServiceActivity) e).getService().getId());
                        duration = duration + ((Tour.ServiceActivity) e).getDuration();
//                    } else if(e instanceof Tour.Delivery) {
//                        serviceCount = serviceCount + 1;
//                        services.add(e.ge)
//                        duration = duration + ((Tour.Delivery) e).getDuration();
                    } else {
                        System.out.println("There should be no other TourElements than leg or service");
                    }
                }

                CarrierTourData data = new CarrierTourData(createTourId(id, services.get(0)), start);

                data.duration = duration;
                data.distance = distance;
                data.carrierId = carrier.getId();
                data.serviceCount = serviceCount;
                data.services = services;

                tours.add(data);
            }
        }
    }

    private Id<Vehicle> createTourId(Id<Vehicle> id, Id<CarrierService> firstServiceId) {

        Id<Vehicle> tourId = Id.create(id + "_" + firstServiceId, Vehicle.class);

        return tourId;
    }

    public void writeStats(String file) {
        BufferedWriter statsWriter = IOUtils.getBufferedWriter(file);
        try {
            System.out.println("WRITING TOUR STATS FROM CARRIERS FILE!");
            int i =1;
            statsWriter.write("no;tourId;totalDistance [m];departureTime [s];arrivalTime [s]; tourDuration [s];" +
                    "carrierId;numberOfServices;services");
            statsWriter.newLine();

//            PriorityQueue<CarrierTourData> sortedTours = new PriorityQueue<>(Comparator.comparing(CarrierTourData::getTourId));

            for(CarrierTourData data  : this.tours) {
                statsWriter.write(i + ";" + data.carrierId + data.tourId + ";" + data.distance + ";" + data.start + ";" +
                        (data.start + data.duration) + ";" +data.duration + ";" + data.carrierId + ";" +
                        data.serviceCount + ";" + data.services);
                statsWriter.newLine();
                i++;
            }
            statsWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void notifyIterationEnds(IterationEndsEvent event) {
        ToursFromCarriersFileAnalysis analysis = new ToursFromCarriersFileAnalysis(network, carriers);
        analysis.analyzeCarrierServices(carriers, network);

        String dir = event.getServices().getConfig().controler().getOutputDirectory();
        String outputFile = dir + "/ToursFromCarriersAnalysis.csv";
        writeStats(outputFile);
    }

    private class CarrierTourData {
        private Id<Vehicle> tourId;
        private double start;
        private double duration;
        private double distance;
        private Id<Carrier> carrierId;
        private int serviceCount;
        private List<Id<CarrierService>> services;

        private CarrierTourData(Id<Vehicle> tourId, double start) {
            this.tourId = tourId;
            this.start = start;

        }
    }
}
