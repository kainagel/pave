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

import org.matsim.api.core.v01.IdSet;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.dvrp.optimizer.Request;
import org.matsim.contrib.dvrp.schedule.Task;
import org.matsim.contrib.freight.FreightConfigGroup;
import org.matsim.contrib.freight.carrier.Carrier;
import org.matsim.contrib.freight.carrier.Carriers;
import org.matsim.contrib.freight.carrier.Tour;
import org.matsim.contrib.freight.utils.FreightUtils;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.mobsim.framework.events.MobsimAfterSimStepEvent;
import org.matsim.core.mobsim.framework.events.MobsimInitializedEvent;
import org.matsim.drtBlockings.events.DrtBlockingRequestRejectedEvent;
import org.matsim.drtBlockings.events.DrtBlockingRequestRejectedEventHandler;
import org.matsim.drtBlockings.tasks.FreightPickupTask;
import org.matsim.drtBlockings.tasks.FreightServiceTask;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ExecutionException;

class ReplanningBlockingRequestEngine implements BlockingRequestEngine, DrtBlockingRequestRejectedEventHandler {
    //When do we want to replan our rejected tours?
    Double tourPlanningTime = 60.0 * 60 * 14;

    private Scenario scenario;
    private BlockingOptimizer optimizer;
    private BlockingRequestCreator blockingRequestCreator;
    private final PriorityQueue<DrtBlockingRequest> requestsToSubmit = new PriorityQueue<>(Comparator.comparing(Request::getSubmissionTime));
    private IdSet<Request> rejectedRequests = new IdSet<>(Request.class);
    private Set<DrtBlockingRequest> submittedRequests;

    ReplanningBlockingRequestEngine(Scenario scenario, BlockingOptimizer optimizer, BlockingRequestCreator blockingRequestCreator) {
        this.scenario = scenario;
        this.optimizer = optimizer;
        this.blockingRequestCreator = blockingRequestCreator;
    }

    @Override
    public void notifyMobsimInitialized(MobsimInitializedEvent event) {
        this.requestsToSubmit.clear();
        this.requestsToSubmit.addAll(blockingRequestCreator.createBlockingRequests(FreightUtils.getCarriers(scenario)));
    }

    @Override
    public void notifyMobsimAfterSimStep(MobsimAfterSimStepEvent e) {
        if(e.getSimulationTime() == tourPlanningTime){
            replan();
        }
        while (isReadyForSubmission(requestsToSubmit.peek(), e.getSimulationTime())) {
            DrtBlockingRequest request = requestsToSubmit.poll();
            optimizer.blockingRequestSubmitted(request);
            this.submittedRequests.add(request);
        }
    }

    private void replan() {
        //delete everything that is yet to submit;
        Carriers carriers = FreightUtils.getCarriers(this.scenario);
        initCarriers(carriers);
        try {
            FreightUtils.runJsprit(scenario);
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        this.notifyMobsimInitialized(null);
        this.rejectedRequests.clear();
    }

    private void initCarriers(Carriers carriers) {
        for (Carrier carrier : carriers.getCarriers().values()) {
            carrier.getPlans().clear();
            carrier.setSelectedPlan(null); //not sure if this is necessary..
        }
        for (DrtBlockingRequest submittedRequest : this.submittedRequests) {
            if(! this.rejectedRequests.contains(submittedRequest.getId())){
                Carrier carrier =  carriers.getCarriers().get(submittedRequest.getCarrierId());
                for (Task task : submittedRequest.getTasks()) {
                    if (task instanceof FreightServiceTask){
                        carrier.getServices().remove(((FreightServiceTask) task).getCarrierService());
                    } else if (task instanceof FreightPickupTask){
                        carrier.getShipments().remove( ((FreightPickupTask) task).getShipment());
                    }
                }
            }
        }
    }

    private boolean isReadyForSubmission(DrtBlockingRequest request, double currentTime) {
        return request != null && request.getSubmissionTime() <= currentTime;
    }

    @Override
    public void handleEvent(DrtBlockingRequestRejectedEvent event) {
        this.rejectedRequests.add(event.getRequestId());
    }
}
