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

package org.matsim.drt;

import com.google.inject.Singleton;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.drt.analysis.PassengerRequestRejectionListener;

public class DrtBlockingModule extends AbstractModule {

    DrtConfigGroup drtConfigGroup;

    public DrtBlockingModule(DrtConfigGroup drtConfigGroup) {
        this.drtConfigGroup = drtConfigGroup;
    }

    @Override
    public void install() {

        installQSimModule( new DrtBlockingQSimModule(drtConfigGroup));

        install(new AbstractModule() {
            @Override
            public void install() {
                addEventHandlerBinding().to(PassengerRequestRejectionListener.class).in(Singleton.class);
                addControlerListenerBinding().to(PassengerRequestRejectionListener.class).in(Singleton.class);
            }
        });
    }
}
