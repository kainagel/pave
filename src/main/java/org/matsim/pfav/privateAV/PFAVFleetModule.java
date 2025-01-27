package org.matsim.pfav.privateAV;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.dvrp.fleet.Fleet;
import org.matsim.contrib.dvrp.fleet.FleetSpecification;
import org.matsim.contrib.dvrp.fleet.FleetSpecificationImpl;
import org.matsim.contrib.dvrp.fleet.Fleets;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeModule;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeQSimModule;
import org.matsim.contrib.dvrp.run.ModalProviders;
import org.matsim.contrib.dvrp.run.QSimScopeObjectListenerModule;

final class PFAVFleetModule extends AbstractDvrpModeModule {

    private Scenario scenario;

    PFAVFleetModule(String mode, Scenario scenario) {
        super(mode);
        this.scenario = scenario;
    }

    @Override
    public void install() {
        FleetSpecificationImpl fleetSpecification = new FleetSpecificationImpl();
        bindModal(FleetSpecification.class).toProvider(() -> {
            return fleetSpecification;
        }).asEagerSingleton();

        installQSimModule(new AbstractDvrpModeQSimModule(getMode()) {
            @Override
            protected void configureQSim() {
                bindModal(Fleet.class).toProvider(new ModalProviders.AbstractProvider<Fleet>(getMode()) {
                    @Override
                    public Fleet get() {
                        FleetSpecification fleetSpecification = getModalInstance(FleetSpecification.class);
                        Network network = getModalInstance(Network.class);
                        return Fleets.createCustomFleet(fleetSpecification,
								s -> PFAVehicle.createWithLinkProvider(s, network.getLinks().get(s.getStartLinkId())));
                    }
                }).asEagerSingleton();
            }
        });

        bindModal(PFAVFleetStatsCalculator.class).toProvider(modalProvider(getter ->
                new PFAVFleetStatsCalculator(fleetSpecification, scenario, getMode()))).asEagerSingleton();
        addControlerListenerBinding().to(modalKey(PFAVFleetStatsCalculator.class));

        installQSimModule(QSimScopeObjectListenerModule.createSimplifiedModule(getMode(), Fleet.class, PFAVFleetStatsCalculator.class));

    }
}
