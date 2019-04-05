/* *********************************************************************** *
 * project: org.matsim.*												   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
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
package freight.manager;

import freight.tour.PFAVTourData;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.dvrp.schedule.StayTask;
import org.matsim.contrib.freight.carrier.ScheduledTour;
import org.matsim.contrib.freight.carrier.Tour;
import org.matsim.contrib.freight.carrier.Tour.ServiceActivity;
import org.matsim.contrib.freight.carrier.Tour.TourElement;
import privateAV.PFAVUtils;
import privateAV.schedule.PFAVRetoolTask;
import privateAV.schedule.PFAVServiceTask;

import java.util.ArrayList;
import java.util.List;

/**
 * @author tschlenther
 *
 */
public class ConvertFreightTourForDvrp {

	/**
	 * @param freightTour
	 * @param network
	 * @return a list that contains the start task and all service tasks in the ScheduledTour (not the end task!). The legs in the ScheduledTour do not contain useful route information.
	 * Furthermore, we want to route with up to date travel times within the mobsim anyways (as we are looking on dvrp vehicles).
	 * Consequently, we only convert the (service) activities here and construct the paths/legs later.
     * @see ListBasedFreightTourManagerImpl
	 */
    static PFAVTourData convertToPFAVTourData(ScheduledTour freightTour, Network network) {

        //the Start and End activities are not part of ScheduledTour.getTour.getTourElements();
        //otherwise, this method could be shortened by two thirds
        List<StayTask> taskList = new ArrayList<>();

        //we actually get the begin time by deriving it out of the first leg in the tour. in the freight activity itself, the begin time is always set to the 'opening time' or 0
        double tEnd = ((Tour.Leg) freightTour.getTour().getTourElements().get(0)).getExpectedDepartureTime();

        //since i (yet) don't know how to set the duration of the start act in the freight contrib it is actually 0. so we use our retool value derived out of our utils
        double tBegin = tEnd - PFAVUtils.RETOOL_TIME_FOR_PFAVEHICLES;

        Link depotLink = network.getLinks().get(freightTour.getTour().getStart().getLocation());

        Link location = depotLink;
        taskList.add(new PFAVRetoolTask(tBegin, tEnd, location));

        int totalCapacityDemand = 0;

        for (TourElement currentElement : freightTour.getTour().getTourElements()) {
            if (currentElement instanceof ServiceActivity) {
                //currently we need to add PFAVUtils.RETOOL_TIME_FOR_PFAVEHICLES, since we added this already to the start act duration
                ServiceActivity serviceAct = (ServiceActivity) currentElement;

                tBegin = serviceAct.getExpectedArrival() + PFAVUtils.RETOOL_TIME_FOR_PFAVEHICLES;
                tEnd = tBegin + serviceAct.getDuration();
                location = network.getLinks().get(serviceAct.getLocation());
                totalCapacityDemand += serviceAct.getService().getCapacityDemand();
                taskList.add(new PFAVServiceTask(tBegin, tEnd, location, serviceAct.getService()));
            }
        }

        double travelTimeToLastService = taskList.get(taskList.size() - 1).getBeginTime();

        //for the times set at the end activity, see comments above. we need this workaround here
        int size = freightTour.getTour().getTourElements().size();
        tBegin = ((Tour.Leg) freightTour.getTour().getTourElements().get(size - 1)).getExpectedDepartureTime();
//                + ((Tour.Leg) freightTour.getTour().getTourElements().get(size - 1)).getExpectedTransportTime();
        tEnd = tBegin + PFAVUtils.RETOOL_TIME_FOR_PFAVEHICLES;
        location = network.getLinks().get(freightTour.getTour().getEnd().getLocation());
        taskList.add(new PFAVRetoolTask(tBegin, tEnd, location));

        double plannedTourDuration = tEnd - taskList.get(0).getBeginTime();
        return new PFAVTourData(taskList, depotLink, plannedTourDuration, travelTimeToLastService, totalCapacityDemand);
	}

}
