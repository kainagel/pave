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

import com.google.inject.Singleton;
import org.matsim.contrib.drt.analysis.DrtModeAnalysisModule;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.DrtModeModule;
import org.matsim.contrib.drt.run.DrtModeQSimModule;
import org.matsim.core.controler.AbstractModule;
import org.matsim.drtBlockings.analysis.PassengerRequestRejectionListener;

public class DrtBlockingModule extends AbstractModule {

    DrtConfigGroup drtConfigGroup;
    private final double minIdleVehicleRatio;

    //standard constructor which sets minIdleVehicleRatio of the AdaptiveBlockingOptimizer to its standard value -sm0622
    public DrtBlockingModule(DrtConfigGroup drtConfigGroup) {
        this.drtConfigGroup = drtConfigGroup;
        this.minIdleVehicleRatio = 0.5;
    }

    //this additional constructor allows to configure minIdleVehicleRatio of the AdaptiveBlockingOptimizer in the RunClass without
    //having to change all the tests etc, where a DrtBlockingModule is created -sm0622
    public DrtBlockingModule(DrtConfigGroup drtConfigGroup, double minIdleVehicleRatio) {
        this.drtConfigGroup = drtConfigGroup;
        this.minIdleVehicleRatio = minIdleVehicleRatio;
    }

    @Override
    public void install() {
        install( new DrtModeModule(drtConfigGroup)) ;
        install(new DrtModeAnalysisModule(drtConfigGroup));
        installQSimModule( new DrtModeQSimModule(drtConfigGroup, new DrtBlockingOptimizerQSimModule(drtConfigGroup, minIdleVehicleRatio)));

        install(new AbstractModule() {
            @Override
            public void install() {
                addEventHandlerBinding().to(PassengerRequestRejectionListener.class).in(Singleton.class);
                addControlerListenerBinding().to(PassengerRequestRejectionListener.class).in(Singleton.class);
            }
        });
    }
}
