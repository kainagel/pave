package org.matsim.drtBlockings.run;

/**
 *   Run class for the DRT-Blocking base case in Berlin, where DRT and Freight-traffic is handled separately
 *   Differences to Policy Cases:
 *       1) Because of the fact that Freight traffic is handled separately from DRT we dont need to include the DrtBlockingModule
 *       2) We also dont need to set CarrierUtils.setCarrierMode(carrier, drtCfg.getMode());
 *       3) We do need some more carrier modules
 */

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import com.google.inject.Singleton;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpModule;
import org.matsim.contrib.dvrp.run.DvrpQSimComponents;
import org.matsim.contrib.freight.FreightConfigGroup;
import org.matsim.contrib.freight.carrier.*;
import org.matsim.contrib.freight.controler.CarrierModule;
import org.matsim.contrib.freight.controler.CarrierPlanStrategyManagerFactory;
import org.matsim.contrib.freight.controler.CarrierScoringFunctionFactory;
import org.matsim.contrib.freight.usecases.analysis.CarrierScoreStats;
import org.matsim.contrib.freight.usecases.analysis.LegHistogram;
import org.matsim.contrib.freight.usecases.chessboard.CarrierScoringFunctionFactoryImpl;
import org.matsim.contrib.freight.utils.FreightUtils;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.MatsimServices;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.replanning.GenericPlanStrategyImpl;
import org.matsim.core.replanning.GenericStrategyManager;
import org.matsim.core.replanning.selectors.KeepSelected;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;
import org.matsim.core.scoring.functions.SubpopulationScoringParameters;
import org.matsim.drtBlockings.DrtBlockingModule;
import org.matsim.drtBlockings.analysis.BaseCaseTourStatsAnalysis;
import org.matsim.pt.config.TransitConfigGroup;
import org.matsim.run.RunBerlinScenario;
import org.matsim.run.drt.RunDrtOpenBerlinScenario;

import java.io.File;
import java.util.concurrent.ExecutionException;

public class RunBaseCaseInBerlin {

    /**
     * @param args should contain the following arguments in the specified order:
     *             1) path to config
     *             2) path to carrier plans
     *             3) path to carrier vehicle types
     *             4) boolean value that determines whether tour planning should be performed before the mobsim. True = tour planning gets performed
     *             5) path to network
     *             6) path to output directory
     *             7) path to networkChangeEvents
     *             8) path to plans
     *             9) path to drt vehicles file
     */

    //GENERAL INPUT
    //dir for 1 carrier only
//    private static final String INPUT_DIR = "C:/Users/simon/tubCloud/Shared/MA-Meinhardt/InputDRT/Lichtenberg Nord_Carrier/";
    //dir for all Berlin carriers
    private static final String INPUT_DIR = "C:/Users/simon/tubCloud/Shared/MA-Meinhardt/InputDRT/Berlin_Carriers/";
    private static final String INPUT_CONFIG = INPUT_DIR + "p2-23.output_config.xml";
    private static final String INPUT_NETWORK_CHANGE_EVENTS = INPUT_DIR + "p2-23.networkChangeEvents.xml.gz";
    //DIE FOLGENDEN PLÄNE FÜR SERIOUS RUNS VERWENDEN!
    private static final String INPUT_DRT_PLANS = INPUT_DIR + "p2-23.output_plans_drtUsersOnly_selectedPlans_noRoutes.xml.gz";
//    private static final String INPUT_DRT_PLANS = INPUT_DIR + "p2-23.output_plans_200Persons.xml.gz";
    private static final String INPUT_NETWORK = INPUT_DIR + "p2-23.output_network.xml.gz";
    private static final String INPUT_DRT_VEHICLES = INPUT_DIR + "p2-23.drt__vehicles.xml.gz";

    //CARRIER INPUT
    private static final String CARRIERS_PLANS_PLANNED = INPUT_DIR + "carriers_4hTimeWindows_openBerlinNet_8-24_BaseCase_PLANNED.xml";
//    private static final String CARRIERS_PLANS_PLANNED = INPUT_DIR + "carriers_4hTimeWindows_openBerlinNet_LichtenbergNord_8-24_PLANNED_oneTour.xml";
    private static final String CARRIER_VEHICLE_TYPES = INPUT_DIR + "carrier_vehicleTypes_wTimeCost.xml";
    private static final boolean RUN_TOURPLANNING = false;

    private static final String OUTPUT_DIR = "./output/berlin-v5.5-10pct/base_cases/" + CARRIERS_PLANS_PLANNED.replace(INPUT_DIR, "");
    private static final String TRANSIT_FILE = "berlin-v5.5-transit-schedule_empty.xml";

    public static void main(String[] args) {
        String configPath;
        String carrierPlans;
        String carrierVehTypes;
        boolean performTourplanning;
        String inputNetwork;
        String outputPath;
        String networkChangeEvents;
        String inputPlans;
        String inputVehicles;


        if(args.length > 0){
            configPath = args[0];
            carrierPlans = args[1];
            carrierVehTypes = args[2];
            performTourplanning = Boolean.valueOf(args[3]);
            inputNetwork = args[4];
            outputPath = args[5];
            networkChangeEvents = args[6];
            inputPlans = args[7];
            inputVehicles = args[8];
            System.out.println("Executing run script with the following arguments: " + args);
        } else {
            configPath = INPUT_CONFIG;
            carrierPlans = CARRIERS_PLANS_PLANNED;
            carrierVehTypes = CARRIER_VEHICLE_TYPES;
            performTourplanning = RUN_TOURPLANNING;
            inputNetwork = INPUT_NETWORK;
            outputPath = OUTPUT_DIR;
            networkChangeEvents = INPUT_NETWORK_CHANGE_EVENTS;
            inputPlans = INPUT_DRT_PLANS;
            inputVehicles = INPUT_DRT_VEHICLES;
        }

        Config config = prepareConfig(configPath, carrierPlans, carrierVehTypes, inputNetwork, outputPath,
                networkChangeEvents, inputPlans);

        Scenario scenario = prepareScenario(config, performTourplanning);

        Controler controler = prepareControler(scenario, inputVehicles);

        //might need to customize this method, for now it stays as it is in PFAV
        prepareFreightOutputDataAndStats(scenario, controler.getEvents(), controler, FreightUtils.getCarriers(scenario));

        BaseCaseTourStatsAnalysis tourAnalysis = new BaseCaseTourStatsAnalysis(scenario.getNetwork(), FreightUtils.getCarriers(scenario));

        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                addControlerListenerBinding().toInstance(tourAnalysis);
                addEventHandlerBinding().toInstance(tourAnalysis);
            }
        });

        controler.run();
    }

    public static Config prepareConfig(String configPath, String carrierPlans, String carrierVehTypes, String inputNetwork,
                                       String outputPath, String networkChangeEvents, String inputPlans) {
        //get inputConfig
        Config config = RunDrtOpenBerlinScenario.prepareConfig(new String[]{configPath});

        //General settings
        config.controler().setLastIteration(2);
        config.controler().setRunId(config.controler().getRunId() + "DRTBlockingBaseCase");
        config.controler().setOutputDirectory(outputPath);
        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        config.network().setInputFile(inputNetwork);
        config.network().setChangeEventsInputFile(networkChangeEvents);
        config.network().setTimeVariantNetwork(true);
        config.plans().setInputFile(inputPlans);
        config.qsim().setFlowCapFactor(10.);

//        QSimConfigGroup qSimCfg = ConfigUtils.addOrGetModule(config, QSimConfigGroup.class);
//        qSimCfg.setNumberOfThreads(4);

        //Freight settings
        FreightConfigGroup freightConfig = ConfigUtils.addOrGetModule(config, FreightConfigGroup.class);
        freightConfig.setCarriersFile(carrierPlans);
        freightConfig.setCarriersVehicleTypesFile(carrierVehTypes);
        freightConfig.setTimeWindowHandling(FreightConfigGroup.TimeWindowHandling.enforceBeginnings);

        SwissRailRaptorConfigGroup srrConfig = ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class);
        srrConfig.setUseIntermodalAccessEgress(false);

        return config;
    }

    public static Scenario prepareScenario(Config config, boolean performTourplanning) {

//        DrtConfigGroup drtCfg = DrtConfigGroup.getSingleModeDrtConfig(config);
        FreightConfigGroup freightCfg = ConfigUtils.addOrGetModule(config, FreightConfigGroup.class);

        Scenario scenario = RunDrtOpenBerlinScenario.prepareScenario(config);
        FreightUtils.loadCarriersAccordingToFreightConfig(scenario);

        //Run Tourplanning if the carriers plans were not already planned before running the sim
        if(performTourplanning){
            try {
                FreightUtils.getCarriers(scenario).getCarriers().values().forEach(carrier -> {
                    CarrierUtils.setJspritIterations(carrier, 50);
                });
                FreightUtils.runJsprit(scenario);
                new File(config.controler().getOutputDirectory()).mkdirs();
                new CarrierPlanXmlWriterV2(FreightUtils.getCarriers(scenario)).write(config.controler().getOutputDirectory() + "carriers_planned.xml");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
//        configureTransitForTest(config);
        return scenario;
    }

    public static Controler prepareControler(Scenario scenario, String inputVehicles) {

        Controler controler = RunBerlinScenario.prepareControler(scenario);
        configureDRTOnly(scenario, controler, inputVehicles);
//        CarrierVehicleTypes types = FreightUtils.getCarrierVehicleTypes(scenario);

        //this was copied from PFAV RunNormalFreightInBerlin class, not sure if its necessary
        controler.addOverridingModule(new CarrierModule());
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                bind(CarrierPlanStrategyManagerFactory.class).toInstance(new DummyCarrierPlansStrategyManagerFactory());
                bind(CarrierScoringFunctionFactory.class).to(CarrierScoringFunctionFactoryImpl.class);
                bind(ScoringParametersForPerson.class).to(SubpopulationScoringParameters.class).in(Singleton.class);
            }
        });

        return controler;
    }

    private static void configureDRTOnly(Scenario scenario, Controler controler, String inputVehicles) {

        DrtConfigGroup drtCfg = DrtConfigGroup.getSingleModeDrtConfig(scenario.getConfig());
        drtCfg.setVehiclesFile(inputVehicles);

        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                install(new DvrpModule());
                //this is needed to get the sim running although we dont want to use DrtBlocking
                // however it does not automatically imply that the drt fleet is used for freight!
                install(new DrtBlockingModule(drtCfg));
            }
        });
        controler.configureQSimComponents(DvrpQSimComponents.activateAllModes(MultiModeDrtConfigGroup.get(controler.getConfig())));
    }

    private static void configureTransitForTest(Config config) {

        TransitConfigGroup transitCfg = ConfigUtils.addOrGetModule(config, TransitConfigGroup.class);
        transitCfg.setUseTransit(false);
        transitCfg.setTransitScheduleFile(TRANSIT_FILE);
    }

    private static void prepareFreightOutputDataAndStats(Scenario scenario, EventsManager eventsManager,
                                                         MatsimServices controler, final Carriers carriers) {
        //freight only histogram
        //15 min bins = 900s; Isnt this too detailed?
        final LegHistogram freightOnly = new LegHistogram(900);
        freightOnly.setPopulation(scenario.getPopulation());
        freightOnly.setInclPop(false);

        //everything besides freight histogram
        final LegHistogram withoutFreight = new LegHistogram(900);
        withoutFreight.setPopulation(scenario.getPopulation());

        CarrierScoreStats carrierScoreStats = new CarrierScoreStats(carriers, "output/carrier_scores", true);

        eventsManager.addHandler(freightOnly);
        eventsManager.addHandler(withoutFreight);
        controler.addControlerListener(carrierScoreStats);
        controler.addControlerListener((IterationEndsListener)event -> {
            String dir = event.getServices().getControlerIO().getOutputPath() + "/";

            //write stats
            freightOnly.writeGraphic(dir + "legHistogram_freight.png");
            freightOnly.reset(event.getIteration());

            withoutFreight.writeGraphic(dir + "legHistogram_withoutFreight.png");
            withoutFreight.reset(event.getIteration());
        });
    }

    private static class DummyCarrierPlansStrategyManagerFactory implements CarrierPlanStrategyManagerFactory {


        @Override
        public GenericStrategyManager<CarrierPlan, Carrier> createStrategyManager() {

            final GenericStrategyManager<CarrierPlan, Carrier> strategyManager = new GenericStrategyManager<>();

            GenericPlanStrategyImpl<CarrierPlan, Carrier> strategy = new GenericPlanStrategyImpl<>(new KeepSelected<CarrierPlan, Carrier>());

            strategyManager.addStrategy(strategy, null, 0.01);
            return strategyManager;
        }
    }

}
