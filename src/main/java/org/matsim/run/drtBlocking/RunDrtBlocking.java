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

package org.matsim.run.drtBlocking;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.drt.run.*;
import org.matsim.contrib.dvrp.run.DvrpModule;
import org.matsim.contrib.dvrp.run.DvrpQSimComponents;
import org.matsim.contrib.freight.FreightConfigGroup;
import org.matsim.contrib.freight.carrier.CarrierPlanXmlWriterV2;
import org.matsim.contrib.freight.carrier.CarrierUtils;
import org.matsim.contrib.freight.utils.FreightUtils;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.drtBlockings.DrtBlockingModule;
import org.matsim.run.RunBerlinScenario;
import org.matsim.run.drt.RunDrtOpenBerlinScenario;

import java.io.File;
import java.util.concurrent.ExecutionException;

public class RunDrtBlocking {

	/**
	 *
	 *
	 * @param args should contain the following arguments in the specified order:
	 *             1) path to config
	 *             2) path to carrier plans
	 *             3) path to carrier vehicle types
	 *             4) boolean value that determines whether tour planning should be performed before the mobsim. True = tour planning gets performed
	 */
	public static void main(String[] args) {

		if(args.length != 4){
			throw new IllegalArgumentException("wrong number of program arguments. expected 4, but was " + args.length);
		}

		String configPath = args[0];
		String carrierPlans = args[1];
		String carrierVehTypes = args[2];
		boolean performTourplanning = Boolean.valueOf(args[3]);

		Config config = prepareConfig(configPath, carrierPlans, carrierVehTypes);

		Scenario scenario = prepareScenario(config, performTourplanning);

		Controler controler = prepareControler(scenario);

		controler.run();
	}

	public static Config prepareConfig(String configPath, String carrierPlans, String carrierVehTypes){
		Config config = RunDrtOpenBerlinScenario.prepareConfig(new String[]{configPath});
//		config.qsim().setSimStarttimeInterpretation(QSimConfigGroup.StarttimeInterpretation.onlyUseStarttime);
		config.controler().setLastIteration(0);

//		this is not set by RunBerlinScenario, but vsp consistency checker needs it...
//		config.planCalcScore().setFractionOfIterationsToStartScoreMSA(0.8);

//		config.controler().setOutputDirectory("output/" + config.controler().getRunId() + "/");

		SwissRailRaptorConfigGroup srrConfig = ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class);
		//no intermodal access egress for the time being here!
		srrConfig.setUseIntermodalAccessEgress(false);

		FreightConfigGroup freightCfg = ConfigUtils.addOrGetModule(config, FreightConfigGroup.class);
		freightCfg.setCarriersFile(carrierPlans);
		freightCfg.setCarriersVehicleTypesFile(carrierVehTypes);
		return config;
	}

	public static Scenario prepareScenario(Config config, boolean performTourplanning) {

		DrtConfigGroup drtCfg = DrtConfigGroup.getSingleModeDrtConfig(config);
		FreightConfigGroup freightCfg = ConfigUtils.addOrGetModule(config, FreightConfigGroup.class);

		Scenario scenario = RunDrtOpenBerlinScenario.prepareScenario(config);
		FreightUtils.loadCarriersAccordingToFreightConfig(scenario);

		if(performTourplanning){
			try {
				FreightUtils.getCarriers(scenario).getCarriers().values().forEach(carrier -> {
					CarrierUtils.setCarrierMode(carrier, drtCfg.getMode());
					CarrierUtils.setJspritIterations(carrier, 20);
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

	public static Controler prepareControler(Scenario scenario){
		Controler controler = RunBerlinScenario.prepareControler( scenario ) ;

		configureDRT(scenario, controler);

//		controler.addOverridingModule(new AbstractModule() {
//			@Override
//			public void install() {
//				// use a main mode identifier which knows how to handle intermodal trips generated by the used sbb pt raptor router
//				// the SwissRailRaptor already binds its IntermodalAwareRouterModeIdentifier, however drt obviuosly replaces it
//				// with its own implementation
//				// So we need our own main mode indentifier which replaces both :-(
//				bind(MainModeIdentifier.class).to(OpenBerlinIntermodalPtDrtRouterModeIdentifier.class);
//				bind(AnalysisMainModeIdentifier.class).to(OpenBerlinIntermodalPtDrtRouterAnalysisModeIdentifier.class);
//			}
//		});

//		controler.addOverridingModule(new IntermodalTripFareCompensatorsModule());
//		controler.addOverridingModule(new PtIntermodalRoutingModesModule());

		return controler;
	}

	private static void configureDRT(Scenario scenario, Controler controler) {

//		MultiModeDrtConfigGroup multiModeDrtCfg = MultiModeDrtConfigGroup.get(scenario.getConfig());
		DrtConfigGroup drtCfg = DrtConfigGroup.getSingleModeDrtConfig(scenario.getConfig());

		// at the moment, we only configure our 1 drt mode!
		// if you want to use several drt modes AND drt blocking, take care that DrtBlockingModeModule does the QSimBindings for it's mode, so do not use MultiModeDrtModule!
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				install(new DvrpModule());
				install(new DrtBlockingModule(drtCfg));
			}
		});
		controler.configureQSimComponents(DvrpQSimComponents.activateAllModes(MultiModeDrtConfigGroup.get(controler.getConfig())));
	}


}
