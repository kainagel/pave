package org.matsim.drtBlockings.run;

/**
*   Run class for the DRT-Blocking policy cases in Berlin, where DRT and Freight-traffic is handled by one fleet
*   Differences to Base Case:
*       1) Because of the fact that Freight traffic is handled by the same fleet as DRT we need to include the DrtBlockingModule
 *      2) Drt needs to be set as Carrier mode as well: CarrierUtils.setCarrierMode(carrier, drtCfg.getMode());
*       3) We do not need some of the carrier modules
 */

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import com.google.inject.Singleton;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpModule;
import org.matsim.contrib.dvrp.run.DvrpQSimComponents;
import org.matsim.contrib.freight.FreightConfigGroup;
import org.matsim.contrib.freight.carrier.CarrierPlanXmlWriterV2;
import org.matsim.contrib.freight.carrier.CarrierUtils;
import org.matsim.contrib.freight.carrier.Carriers;
import org.matsim.contrib.freight.usecases.analysis.CarrierScoreStats;
import org.matsim.contrib.freight.usecases.analysis.LegHistogram;
import org.matsim.contrib.freight.utils.FreightUtils;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.MatsimServices;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;
import org.matsim.core.scoring.functions.SubpopulationScoringParameters;
import org.matsim.drtBlockings.DrtBlockingModule;
import org.matsim.drtBlockings.analysis.BasicTourStatsAnalysis;
import org.matsim.drtBlockings.analysis.NeverStartedToursAnalysis;
import org.matsim.drtBlockings.analysis.ServicesConductedAnalysis;
import org.matsim.drtBlockings.analysis.ToursFromCarriersFileAnalysis;
import org.matsim.run.RunBerlinScenario;
import org.matsim.run.drt.RunDrtOpenBerlinScenario;

import java.io.File;
import java.util.concurrent.ExecutionException;

public class RunPolicyCaseInBerlin {

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
     *             10) wished value for minIdleVehicleRatio. Standard is 0.5
     */

    //General input
    //dir for 1 carrier only
//    private static final String INPUT_DIR = "C:/Users/simon/tubCloud/Shared/MA-Meinhardt/InputDRT/Lichtenberg Nord_Carrier/";
    //dir for all Berlin carriers
    private static final String INPUT_DIR = "C:/Users/Simon/Documents/vsp-papers/Using_On-Demand-Vehicle-Fleets_for_Person_and_Freight_Transport/JTTM_re-publish/InputDRT/Berlin_Carriers/";
    private static final String INPUT_CONFIG = INPUT_DIR + "p2-23.output_config.xml";
    private static final String INPUT_NETWORK_CHANGE_EVENTS = INPUT_DIR + "p2-23.networkChangeEvents.xml.gz";
//    private static final String INPUT_DRT_PLANS = INPUT_DIR + "p2-23.output_plans_drtUsersOnly_selectedPlans_noRoutes.xml.gz";
    private static final String INPUT_DRT_PLANS = INPUT_DIR + "p2-23.output_plans_200Persons.xml.gz";
    private static final String INPUT_NETWORK = INPUT_DIR + "p2-23.output_network.xml.gz";
    private static final String INPUT_DRT_VEHICLES = INPUT_DIR + "p2-23.drt__vehicles.xml.gz";

    //Carrier input
//    private static final String CARRIERS_PLANS_PLANNED = INPUT_DIR + "carriers_4hTimeWindows_openBerlinNet_8-24_PLANNED.xml";
//    private static final String CARRIERS_PLANS_PLANNED = INPUT_DIR + "carriers_4hTimeWindows_openBerlinNet_fullDay_PLANNED.xml";
//    private static final String CARRIERS_PLANS_PLANNED = INPUT_DIR + "carriers_2hTimeWindows_openBerlinNet_8-24_PLANNED.xml";
    private static final String CARRIERS_PLANS_PLANNED = INPUT_DIR + "carriers_2hTimeWindows_openBerlinNet_fullDay_PLANNED.xml";
//    private static final String CARRIERS_PLANS_PLANNED = INPUT_DIR + "carriers_4hTimeWindows_openBerlinNet_LichtenbergNord_8-24_PLANNED_oneTour.xml";
    private static final String CARRIER_VEHICLE_TYPES = INPUT_DIR + "carrier_vehicleTypes_woTimeCost.xml";
    private static final boolean RUN_TOURPLANNING = false;
    private static final double MIN_IDLE_VEHICLE_RATIO = 0.5;

    private static final String OUTPUT_DIR = "./output/berlin-v5.5-10pct/policy_cases/" + CARRIERS_PLANS_PLANNED.replace(INPUT_DIR, "")
            + "_minIdleVehicleRatio" + MIN_IDLE_VEHICLE_RATIO;

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
        double minIdleVehicleRatio;


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
            minIdleVehicleRatio = Double.parseDouble(args[9]);
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
            minIdleVehicleRatio = MIN_IDLE_VEHICLE_RATIO;
        }

        Config config = prepareConfig(configPath, carrierPlans, carrierVehTypes, inputNetwork, outputPath,
                networkChangeEvents, inputPlans);

        Scenario scenario = prepareScenario(config, performTourplanning);

        Controler controler = prepareControler(scenario, inputVehicles, minIdleVehicleRatio);

        //might need to customize this method, for now it stays as it is in PFAV
        //not sure if its needed here because we already got our carriers analyzed with the following analysis
        prepareFreightOutputDataAndStats(scenario, controler.getEvents(), controler, FreightUtils.getCarriers(scenario));

        //not sure which analysis are needed. We may need to add some analysis classes
        BasicTourStatsAnalysis basicBlockingAnalysis = new BasicTourStatsAnalysis(scenario.getNetwork());
        NeverStartedToursAnalysis blockingRejectionAnalysis = new NeverStartedToursAnalysis();
        ServicesConductedAnalysis servicesConductedAnalysis = new ServicesConductedAnalysis(FreightUtils.getCarriers(scenario));
        ToursFromCarriersFileAnalysis toursFromCarriersFileAnalysis = new ToursFromCarriersFileAnalysis(scenario.getNetwork(), FreightUtils.getCarriers(scenario));

        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                addControlerListenerBinding().toInstance(basicBlockingAnalysis);
                addEventHandlerBinding().toInstance(basicBlockingAnalysis);

                addControlerListenerBinding().toInstance(blockingRejectionAnalysis);
                addEventHandlerBinding().toInstance(blockingRejectionAnalysis);

                addControlerListenerBinding().toInstance(servicesConductedAnalysis);
                addEventHandlerBinding().toInstance(servicesConductedAnalysis);

                //addControlerListenerBinding().toInstance(toursFromCarriersFileAnalysis);
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
        config.controler().setRunId(config.controler().getRunId() + "DRTBlockingPolicyCase");
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

        DrtConfigGroup drtCfg = DrtConfigGroup.getSingleModeDrtConfig(config);
        FreightConfigGroup freightCfg = ConfigUtils.addOrGetModule(config, FreightConfigGroup.class);

        Scenario scenario = RunDrtOpenBerlinScenario.prepareScenario(config);
        FreightUtils.loadCarriersAccordingToFreightConfig(scenario);

        //This lets our engine know that we want to handle freight traffic by the drt fleet
        try {
            FreightUtils.getCarriers(scenario).getCarriers().values().forEach(carrier -> {
                CarrierUtils.setCarrierMode(carrier, drtCfg.getMode());
            });

        } catch (NullPointerException e) {
            e.printStackTrace();
        }


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
        return scenario;
    }

    public static Controler prepareControler(Scenario scenario, String inputVehicles, double minIdleVehicleRatio) {

        Controler controler = RunBerlinScenario.prepareControler(scenario);
        configureDRTIncludingDRTBlocking(scenario, controler, inputVehicles, minIdleVehicleRatio);

        //this was copied from PFAV RunNormalFreightInBerlin class, not sure if its necessary
//        controler.addOverridingModule(new CarrierModule());
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
//                bind(CarrierPlanStrategyManagerFactory.class).toInstance(() -> null);
//                bind(CarrierScoringFunctionFactory.class).to(CarrierScoringFunctionFactoryImpl.class);
                bind(ScoringParametersForPerson.class).to(SubpopulationScoringParameters.class).in(Singleton.class);
            }
        });

        return controler;
    }

    private static void configureDRTIncludingDRTBlocking(Scenario scenario, Controler controler, String inputVehicles, double minIdleVehicleRatio) {

        //this line is only needed if DrtBlocking is wished to be activated (=only in the policy cases)
        DrtConfigGroup drtCfg = DrtConfigGroup.getSingleModeDrtConfig(scenario.getConfig());
        drtCfg.setVehiclesFile(inputVehicles);

        //Here the main difference between base case and policy cases is set:
        //Base case does not need to add DrtBlockingModule
        //Policy cases do need the module
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                install(new DvrpModule());
                install(new DrtBlockingModule(drtCfg, minIdleVehicleRatio));
            }
        });
        controler.configureQSimComponents(DvrpQSimComponents.activateAllModes(MultiModeDrtConfigGroup.get(controler.getConfig())));
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
        controler.addControlerListener((IterationEndsListener) event -> {
            String dir = event.getServices().getControlerIO().getOutputPath() + "/";

            //write stats
            freightOnly.writeGraphic(dir + "legHistogram_freight.png");
            freightOnly.reset(event.getIteration());

            withoutFreight.writeGraphic(dir + "legHistogram_withoutFreight.png");
            withoutFreight.reset(event.getIteration());
        });
    }

}