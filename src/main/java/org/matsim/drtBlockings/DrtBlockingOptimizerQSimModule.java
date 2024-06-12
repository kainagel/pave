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

package org.matsim.drtBlockings;

import com.google.inject.*;
import com.google.inject.name.Names;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.drt.optimizer.*;
import org.matsim.contrib.drt.optimizer.depot.DepotFinder;
import org.matsim.contrib.drt.optimizer.depot.NearestStartLinkAsDepot;
import org.matsim.contrib.drt.optimizer.insertion.*;
import org.matsim.contrib.drt.optimizer.rebalancing.RebalancingStrategy;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.schedule.DrtTaskFactory;
import org.matsim.contrib.drt.schedule.DrtTaskFactoryImpl;
import org.matsim.contrib.drt.scheduler.DefaultRequestInsertionScheduler;
import org.matsim.contrib.drt.scheduler.DrtScheduleInquiry;
import org.matsim.contrib.drt.scheduler.EmptyVehicleRelocator;
import org.matsim.contrib.drt.scheduler.RequestInsertionScheduler;
import org.matsim.contrib.dvrp.fleet.Fleet;
import org.matsim.contrib.dvrp.optimizer.VrpOptimizer;
import org.matsim.contrib.dvrp.passenger.DefaultPassengerRequestValidator;
import org.matsim.contrib.dvrp.passenger.PassengerHandler;
import org.matsim.contrib.dvrp.passenger.PassengerRequestValidator;
import org.matsim.contrib.dvrp.path.OneToManyPathSearch;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeQSimModule;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.run.ModalProviders;
import org.matsim.contrib.dvrp.schedule.ScheduleTimingUpdater;
import org.matsim.contrib.dvrp.trafficmonitoring.DvrpTravelTimeModule;
import org.matsim.contrib.dvrp.vrpagent.VrpAgentLogic;
import org.matsim.contrib.freight.FreightConfigGroup;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.mobsim.framework.MobsimTimer;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.drtBlockings.tasks.FreightTaskEndTimeCalculator;

import com.google.inject.name.Named;

import java.util.function.Function;

class DrtBlockingOptimizerQSimModule extends AbstractDvrpModeQSimModule {

	private final DrtConfigGroup drtCfg;
    private final double minIdleVehicleRatio;

	DrtBlockingOptimizerQSimModule(DrtConfigGroup drtConfigGroup, double minIdleVehicleRatio) {
		super(drtConfigGroup.getMode());
        this.drtCfg = drtConfigGroup;
        this.minIdleVehicleRatio = minIdleVehicleRatio;
    }

    @Override
    protected void configureQSim() {
        // the "modal" methods are MMs way to bind things not in the normal guice way, but under a "tag" that refers to the corresponding drt mode.
        // That drt mode is communicated in the constructor, and passed on via the "super" command to the super-class.  kai, mar '23

        // addModalComponent(a,b) is (1) bind a to a provider of b, and (2) register a to the QSim.  For the latter, the "qsimComponent" syntax is
        // used, which I generally try to avoid.  Because b is a provider, a lot of stuff is not yet there, and in consequence a lot of arguments need
        // to be used that get stuff later.  (My intuition is that guice should make these kind of things unnecessary, but evidently that did not work
        // here.)  kai, mar'23

        addModalComponent(BlockingOptimizer.class, modalProvider(
                        getter -> new AdaptiveBlockingOptimizer(getter.getModal(DefaultDrtOptimizer.class),
                                        getter.getModal(Fleet.class),
                                        getter.getModal(DrtScheduleInquiry.class),
                                        getter.getModal(DrtBlockingManager.class),
                                        getter.getModal(DrtBlockingRequestDispatcher.class),
                                        getter.getNamed(TravelTime.class, DvrpTravelTimeModule.DVRP_ESTIMATED),
                                        getter.get(EventsManager.class),
                                        getter.getModal(Network.class),
                                        getter.get(MobsimTimer.class),
                                        getter.get(Config.class), minIdleVehicleRatio) {
                        }));

        // In my view, the following would also work.  Personally, I would prefer that, since it is closer to the standard guice syntax.  Somehow, I
        // think that it should also possible to do a modal bind of the AdaptiveBlockingOptimizer, and then it should be able to pull out the material
        // by itself (i.e. try to avoid generating explicit instances of material that is guice-bound).  kai, mar' 23
//        addModalComponent(BlockingOptimizer.class, new ModalProviders.AbstractProvider<AdaptiveBlockingOptimizer>( getMode() ) {
//                            @Inject private Injector injector;
//
//                            @Override public AdaptiveBlockingOptimizer get(){
//                                return new AdaptiveBlockingOptimizer( getModalInstance( DefaultDrtOptimizer.class ),
//                                                getModalInstance( Fleet.class ),
//                                                getModalInstance( DrtScheduleInquiry.class ),
//                                                getModalInstance( DrtBlockingManager.class ),
//                                                getModalInstance( DrtBlockingRequestDispatcher.class ),
//                                                injector.getInstance( Key.get( TravelTime.class, Names.named( DvrpTravelTimeModule.DVRP_ESTIMATED ) ) ),
//                                                injector.getInstance( EventsManager.class ),
//                                                getModalInstance( Network.class ),
//                                                getModalInstance( MobsimTimer.class ),
//                                                injector.getInstance( Config.class ), minIdleVehicleRatio );
//                            }
//        } );

        bindModal(DrtOptimizer.class).to(modalKey(BlockingOptimizer.class));

        bindModal(DefaultDrtOptimizer.class).toProvider(modalProvider(
                getter -> new DefaultDrtOptimizer(drtCfg, getter.getModal(Fleet.class), getter.get(MobsimTimer.class),
                        getter.getModal(DepotFinder.class), getter.getModal(RebalancingStrategy.class),
                        getter.getModal(DrtScheduleInquiry.class), getter.getModal(ScheduleTimingUpdater.class),
                        getter.getModal(EmptyVehicleRelocator.class), getter.getModal(UnplannedRequestInserter.class))))
                .in(Singleton.class);

        bindModal(VehicleEntry.EntryFactory.class).toProvider(modalProvider(
                getter -> new BlockingVehicleDataEntryFactory(drtCfg, getter.getModal(DrtBlockingManager.class))))
                .in(Singleton.class);

        bindModal(DrtBlockingManager.class).toInstance(new SimpleBlockingManager());

        addModalComponent(BlockingRequestEngine.class, new ModalProviders.AbstractProvider<>(drtCfg.getMode()) {
            @Inject
            Scenario scenario;

            @Override
            public BlockingRequestEngine get() {
                return new DefaultBlockingRequestEngine(scenario, (BlockingOptimizer) getModalInstance(DrtOptimizer.class), getModalInstance(BlockingRequestCreator.class));
            }
        });

        bindModal(BlockingRequestCreator.class).toProvider(new ModalProviders.AbstractProvider<>(getMode()) {
            @Inject
            @Named(DvrpTravelTimeModule.DVRP_ESTIMATED)
            private TravelTime travelTime;

            @Override
            public BlockingRequestCreator get() {
                return new FreightBlockingRequestCreator(getConfig(), getModalInstance(Network.class), travelTime);
            }
        });

        bindModal(DrtBlockingRequestDispatcher.class).to(StraightLineKnnBlockingDispatcher.class).in(Singleton.class);

        bindModal(ScheduleTimingUpdater.class).toProvider(modalProvider(
                getter -> new ScheduleTimingUpdater(getter.get(MobsimTimer.class), new FreightTaskEndTimeCalculator(drtCfg, getter.get(FreightConfigGroup.class)))))
                .asEagerSingleton();

		bindModal(VrpAgentLogic.DynActionCreator.class).
				toProvider(modalProvider(getter -> new FreightDrtActionCreator(getter.getModal(PassengerHandler.class),
						getter.get(MobsimTimer.class), getter.get(DvrpConfigGroup.class)))).
                asEagerSingleton();

        configureStandardDrt();
    }

    private void configureStandardDrt(){
        bindModal(DepotFinder.class).toProvider(
                modalProvider(getter -> new NearestStartLinkAsDepot(getter.getModal(Fleet.class)))).asEagerSingleton();

        bindModal(PassengerRequestValidator.class).to(DefaultPassengerRequestValidator.class).asEagerSingleton();

        addModalComponent(QSimScopeForkJoinPoolHolder.class,
                () -> new QSimScopeForkJoinPoolHolder(drtCfg.getNumberOfThreads()));

        bindModal(UnplannedRequestInserter.class).toProvider(modalProvider(
                getter -> new DefaultUnplannedRequestInserter(drtCfg, getter.getModal(Fleet.class),
                        getter.get(MobsimTimer.class), getter.get(EventsManager.class),
                        getter.getModal(RequestInsertionScheduler.class),
                        getter.getModal(VehicleEntry.EntryFactory.class),
                        getter.getModal(new TypeLiteral<DrtInsertionSearch<OneToManyPathSearch.PathData>>() {
                        }), getter.getModal(QSimScopeForkJoinPoolHolder.class).getPool()))).asEagerSingleton();

        install(DrtModeOptimizerQSimModule.getInsertionSearchQSimModule(drtCfg));


        bindModal(CostCalculationStrategy.class).to(
                drtCfg.isRejectRequestIfMaxWaitOrTravelTimeViolated() ?
                        CostCalculationStrategy.RejectSoftConstraintViolations.class :
                        CostCalculationStrategy.DiscourageSoftConstraintViolations.class).asEagerSingleton();

        bindModal(DrtTaskFactory.class).toInstance(new DrtTaskFactoryImpl());

        bindModal(EmptyVehicleRelocator.class).toProvider(
                new ModalProviders.AbstractProvider<>(drtCfg.getMode()) {

                    @Inject
                    @Named(DvrpTravelTimeModule.DVRP_ESTIMATED)
                    private TravelTime travelTime;

                    @Inject
                    private MobsimTimer timer;

                    @Override
                    public EmptyVehicleRelocator get() {
                        Network network = getModalInstance(Network.class);
                        DrtTaskFactory taskFactory = getModalInstance(DrtTaskFactory.class);
                        TravelDisutility travelDisutility = getModalInstance(
                                TravelDisutilityFactory.class).createTravelDisutility(travelTime);
                        return new EmptyVehicleRelocator(network, travelTime, travelDisutility, timer, taskFactory);
                    }
                }).asEagerSingleton();

        bindModal(DrtScheduleInquiry.class).to(DrtScheduleInquiry.class).asEagerSingleton();

        bindModal(RequestInsertionScheduler.class).toProvider(modalProvider(
                        getter -> new DefaultRequestInsertionScheduler(drtCfg, getter.getModal(Fleet.class),
                                getter.get(MobsimTimer.class),
                                getter.getNamed(TravelTime.class, DvrpTravelTimeModule.DVRP_ESTIMATED),
                                getter.getModal(ScheduleTimingUpdater.class), getter.getModal(DrtTaskFactory.class))))
                .asEagerSingleton();

        bindModal(VrpOptimizer.class).to(modalKey(DrtOptimizer.class));
    }

}
