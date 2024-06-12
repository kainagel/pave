package org.matsim.drtBlockings.scenarioCreation;

import com.opencsv.CSVWriter;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.dvrp.fleet.*;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.network.NetworkUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FleetSizeReducer {

    private String inputVehiclesFile;
    private String outputFileBase;
    private Network network;
    private Map<Integer, List<DvrpVehicleSpecification>> fleetSizeToFleet = new HashMap<>();

    public FleetSizeReducer(Network network, String inputVehiclesFile, String outputFileBase) {
        this.network = network;
        this.inputVehiclesFile = inputVehiclesFile;
        this.outputFileBase = outputFileBase;
    }


    public static void main(String[] args) {

        //input--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
        String inputVehiclesFile = "C:/Users/Simon/Documents/vsp-papers/Using_On-Demand-Vehicle-Fleets_for_Person_and_Freight_Transport/JTTM_re-publish/InputDRT/Berlin_Carriers/p2-23.drt__vehicles.xml.gz";
        String outputFileBase = inputVehiclesFile.split("\\.")[0] + "." + inputVehiclesFile.split("\\.")[1];
        String inputNetworkFile = "C:/Users/Simon/Documents/vsp-papers/Using_On-Demand-Vehicle-Fleets_for_Person_and_Freight_Transport/JTTM_re-publish/InputDRT/Berlin_Carriers/p2-23.output_network.xml.gz";
        List<Integer> fleetSizesToBeCreated = Arrays.asList(6400, 4800, 3200, 5000, 3750, 2500);
        //-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

        Network network = NetworkUtils.readNetwork(inputNetworkFile);

        FleetSizeReducer fleetSizeReducer = new FleetSizeReducer(network, inputVehiclesFile, outputFileBase);

        FleetSpecification fleet = new FleetSpecificationImpl();
        FleetReader fleetReader = new FleetReader(fleet);
        fleetReader.readFile(inputVehiclesFile);

        List<DvrpVehicleSpecification> vehicles = fleet.getVehicleSpecifications().values().stream().toList();

        fleetSizeReducer.createReducedFleets(vehicles, fleetSizesToBeCreated);

        for(Integer fleetSize : fleetSizesToBeCreated) {
            fleetSizeReducer.writeVehXml(fleetSize, outputFileBase);
            fleetSizeReducer.writeVehStartPositionsCSV(fleetSize, outputFileBase);
        }
        //if we want to have our original fleet as a csv as well
//        fleetSizeReducer.writeVehStartPositionsCSV(vehicles.size(), outputFileBase);
    }

    private void writeVehXml(Integer fleetSize, String outputFileBase) {
        new FleetWriter(this.fleetSizeToFleet.get(fleetSize).stream()).write(outputFileBase + "_" + fleetSize + "vehicles" + ".xml");
    }

    //copied and adapted from matsim-berlin: org.matsim.prepare.drt.DrtVehicleCreator
    private void writeVehStartPositionsCSV(Integer fleetSize, String fileNameBase) {
        List<DvrpVehicleSpecification> vehicles = this.fleetSizeToFleet.get(fleetSize);

        Map<Id<Link>, Long> linkId2NrVeh = vehicles.stream().
                map(veh -> veh.getStartLinkId()).
                collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        try {
            CSVWriter writer = new CSVWriter(Files.newBufferedWriter(Paths.get(fileNameBase + "_" + fleetSize + "vehicles" + "_startPositions.csv")), ';', '"', '"', "\n");
            writer.writeNext(new String[]{"link", "x", "y", "drtVehicles"}, false);
            linkId2NrVeh.forEach( (linkId, numberVeh) -> {
                Coord coord = network.getLinks().get(linkId).getCoord();
                double x = coord.getX();
                double y = coord.getY();
                writer.writeNext(new String[]{linkId.toString(), "" + x, "" + y, "" + numberVeh}, false);
            });

            writer.close();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    private void createReducedFleets(List<DvrpVehicleSpecification> originalVehicles, List<Integer> fleetSizesToBeCreated) {

        Random random = MatsimRandom.getRandom();
        for(Integer fleetSize : fleetSizesToBeCreated) {

            List<DvrpVehicleSpecification> reducedFleet = new ArrayList<>();
            reducedFleet.addAll(originalVehicles);

            int i = reducedFleet.size();

            while(i > fleetSize) {
                reducedFleet.remove(random.nextInt(reducedFleet.size() - 1));
                i = reducedFleet.size();
            }

            this.fleetSizeToFleet.put(fleetSize, reducedFleet);
            reducedFleet = originalVehicles;
        }
    }



}
