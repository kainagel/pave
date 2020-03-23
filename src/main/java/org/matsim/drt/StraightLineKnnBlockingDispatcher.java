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

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.schedule.Schedules;
import org.matsim.contrib.util.StraightLineKnnFinder;

import java.util.Collection;

public class StraightLineKnnBlockingDispatcher implements DrtBlockingRequestDispatcher {

    @Override
    public DvrpVehicle findDispatchForBlockingRequest(Collection<DvrpVehicle> availableVehicles, DrtBlockingRequest blockingRequest) {
        if(availableVehicles.isEmpty()) return null;
        StraightLineKnnFinder<Link, DvrpVehicle> finder = new StraightLineKnnFinder<>(1, link1 -> link1, vehicle -> Schedules.getLastLinkInSchedule(vehicle));
        return finder.findNearest(blockingRequest.getStartLink(), availableVehicles.stream()).get(0);
    }

}