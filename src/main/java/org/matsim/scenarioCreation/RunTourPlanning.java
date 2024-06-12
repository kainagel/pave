package org.matsim.scenarioCreation;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.freight.FreightConfigGroup;
import org.matsim.contrib.freight.carrier.*;
import org.matsim.contrib.freight.utils.FreightUtils;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.util.TravelTimeUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pfav.privateAV.FreightTourPlanning;
import org.matsim.pfav.privateAV.PFAVUtils;

import java.util.concurrent.ExecutionException;

class RunTourPlanning {

    public static void main(String[] args) {

        String inputDir = "C:/Users/simon/tubCloud/Shared/MA-Meinhardt/InputDRT/CarrierCreationInput/readyForTourPlanning/";
        String carriersInputName = "carriers_4hTimeWindows_openBerlinNet_LichtenbergNord_8-24.xml";
        String carriersOutput = inputDir + "PLANNED/" + carriersInputName;
        String carrierVehtypes = "C:/Users/simon/tubCloud/Shared/MA-Meinhardt/InputDRT/CarrierCreationInput/carrier_vehicleTypes.xml";
        String networkFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.5-10pct/input/berlin-v5.5-network.xml.gz\n";
        String changeEventsFile = "C:/Users/simon/tubCloud/Shared/MA-Meinhardt/10pct/p2-23/p2-23.networkChangeEvents.xml.gz";

        if(args.length != 0){
            inputDir = args[0];
            carriersInputName = args[1];
            carriersOutput = args[2];
            carrierVehtypes = args[3];
            networkFile = args[4];
            changeEventsFile = args[5];
        }

        Config config = ConfigUtils.createConfig(new FreightConfigGroup());
        config.network().setInputFile(networkFile);
        config.network().setTimeVariantNetwork(true);
        config.network().setChangeEventsInputFile(changeEventsFile);

        FreightConfigGroup freightConfig = ConfigUtils.addOrGetModule(config, FreightConfigGroup.class);
        freightConfig.setCarriersFile(inputDir + carriersInputName);
        freightConfig.setTimeWindowHandling(FreightConfigGroup.TimeWindowHandling.enforceBeginnings);
        freightConfig.setCarriersVehicleTypesFile(inputDir + carrierVehtypes);
        freightConfig.setTravelTimeSliceWidth(1800);

//        Carriers carriers = new Carriers();
//        new CarrierPlanXmlReader(carriers).readFile(inputDir + carriersInput);

//        CarrierVehicleTypes vehicleTypes = new CarrierVehicleTypes();
//        new CarrierVehicleTypeReader(vehicleTypes).readFile(inputDir + carrierVehtypes);
//
//        new CarrierVehicleTypeLoader(carriers).loadVehicleTypes(vehicleTypes);

//        Network network = NetworkUtils.createNetwork();
//        new MatsimNetworkReader(network).readFile(networkFile);

        Scenario scenario = ScenarioUtils.loadScenario(config);
        FreightUtils.loadCarriersAccordingToFreightConfig(scenario);

        Carriers carriers = FreightUtils.getCarriers(scenario);
        carriers.getCarriers().values().forEach(carrier -> CarrierUtils.setJspritIterations(carrier, 50));

        try {
            FreightUtils.runJsprit(scenario);
        } catch (Exception e) {
            e.printStackTrace();
        }
        new CarrierPlanXmlWriterV2(carriers).write(carriersOutput);
    }
}
