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

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.drt.optimizer.DefaultDrtOptimizer;
import org.matsim.contrib.drt.schedule.DrtDriveTask;
import org.matsim.contrib.drt.schedule.DrtStayTask;
import org.matsim.contrib.drt.scheduler.DrtScheduleInquiry;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.fleet.Fleet;
import org.matsim.contrib.dvrp.optimizer.Request;
import org.matsim.contrib.dvrp.path.VrpPathWithTravelData;
import org.matsim.contrib.dvrp.path.VrpPaths;
import org.matsim.contrib.dvrp.router.TimeAsTravelDisutility;
import org.matsim.contrib.dvrp.schedule.Schedule;
import org.matsim.contrib.dvrp.schedule.Task;
import org.matsim.contrib.dvrp.schedule.Tasks;
import org.matsim.contrib.freight.carrier.CarrierVehicle;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.mobsim.framework.MobsimTimer;
import org.matsim.core.mobsim.framework.events.MobsimBeforeSimStepEvent;
import org.matsim.core.router.FastAStarEuclideanFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.misc.OptionalTime;
import org.matsim.drtBlockings.events.DrtBlockingEndedEvent;
import org.matsim.drtBlockings.events.DrtBlockingRequestRejectedEvent;
import org.matsim.drtBlockings.events.DrtBlockingRequestScheduledEvent;
import org.matsim.drtBlockings.events.DrtBlockingRequestSubmittedEvent;
import org.matsim.drtBlockings.tasks.FreightRetoolTask;

import java.util.*;
import java.util.stream.Collectors;

class AdaptiveBlockingOptimizer implements BlockingOptimizer {

    private static final Logger log = Logger.getLogger(BlockingOptimizer.class);


    private final DefaultDrtOptimizer optimizer;
    private final Fleet fleet;

    private final DrtScheduleInquiry scheduleInquiry;

    private final DrtBlockingManager blockingManager;
    private DrtBlockingRequestDispatcher dispatcher;

    private final EventsManager eventsManager;

    private final LeastCostPathCalculator router;
    private final MobsimTimer timer;
    private final TravelTime travelTime;
    Random rnd;

    private PriorityQueue<DrtBlockingRequest> blockingRequests;

    private final double minIdleVehicleRatio;
    private final Config config;

    AdaptiveBlockingOptimizer(DefaultDrtOptimizer optimizer, Fleet fleet, DrtScheduleInquiry scheduleInquiry, DrtBlockingManager blockingManager,
                              DrtBlockingRequestDispatcher dispatcher, TravelTime travelTime, EventsManager eventsManager, Network modalNetwork,
                              MobsimTimer timer, Config config, double minIdleVehicleRatio) {
        this.optimizer = optimizer;
        this.fleet = fleet;
        this.scheduleInquiry = scheduleInquiry;
        this.blockingManager = blockingManager;
        this.dispatcher = dispatcher;
        this.eventsManager = eventsManager;

        this.timer = timer;
        this.travelTime = travelTime;
        this.rnd = MatsimRandom.getLocalInstance();
        this.router = new FastAStarEuclideanFactory().createPathCalculator(modalNetwork, new TimeAsTravelDisutility(travelTime),
                travelTime);

        this.blockingRequests = new PriorityQueue<>(Comparator.comparing((DrtBlockingRequest::getSubmissionTime)).reversed());
        this.minIdleVehicleRatio = minIdleVehicleRatio;
        this.config = config;
    }


//    @Override
    public void blockingRequestSubmitted(DrtBlockingRequest drtBlockingRequest) {
        this.blockingRequests.add(drtBlockingRequest);
        log.info("blocking request " + drtBlockingRequest.getId() + " starting at " + drtBlockingRequest.getStartTime()
                + " was submitted");
        eventsManager.processEvent(new DrtBlockingRequestSubmittedEvent(timer.getTimeOfDay(), drtBlockingRequest.getMode(),
                drtBlockingRequest.getId(), drtBlockingRequest.getStartLink().getId(), drtBlockingRequest.getEndLink().getId(), drtBlockingRequest.getPlannedBlockingDuration()));
    }

    @Override
    public void requestSubmitted(Request request) {
        optimizer.requestSubmitted(request);
    }

    @Override
    public void nextTask(DvrpVehicle vehicle) {

        optimizer.nextTask(vehicle);

        //if we do not check for the start time, it might be that the vehicle is idle before the start of the blocking tasks (for instance at iteration start)
        //is this comment still valid? tschlenther dec 23, '20
//        if(this.blockedVehicles.containsKey(vehicle) && this.timer.getTimeOfDay() > this.blockedVehicles.get(vehicle).getStartTime()){

        if(this.blockingManager.isVehicleBlocked(vehicle)){
            updateBlocking(vehicle);
        }

    }

    private void updateBlocking(DvrpVehicle vehicle) {
        if(scheduleInquiry.isIdle(vehicle)){ //TODO actually we could unblock the vehicle already when the last retooling has begun. What happens if we call eventsManager.processEvent(futureTime) ?
            //if the blocking request has started and the vehicle is idle then we can unblock the vehicle..
            this.blockingManager.unblockVehicle(vehicle);
            log.info("Blocking of vehicle " + vehicle.getId() + " has ended at " + timer.getTimeOfDay());
            this.eventsManager.processEvent(
                    new DrtBlockingEndedEvent(timer.getTimeOfDay(), vehicle.getId(), Tasks.getEndLink(vehicle.getSchedule().getCurrentTask()).getId()));
        } else {
//            TODO: possibly interrupt the blocking request, e.g. if some threshold is exceeded (later than some final time, duration exceeded, ...)
//            OptionalTime estimatedBlockingEnd = retrieveEndTimeOfBlocking(vehicle.getSchedule());
        }
    }

    @Override
    public void notifyMobsimBeforeSimStep(MobsimBeforeSimStepEvent e) {
        optimizer.notifyMobsimBeforeSimStep(e);

        List<? extends DvrpVehicle> idleVehicles = fleet.getVehicles()
                .values()
                .stream()
                .filter(scheduleInquiry::isIdle)
                .collect(Collectors.toList());
        Iterator<DrtBlockingRequest> blockingRequestsIterator = this.blockingRequests.iterator();

        while(blockingRequestsIterator.hasNext()) {
            DrtBlockingRequest drtBlockingRequest = blockingRequestsIterator.next();

            //there should be a more elegant way to determine the latest time to schedule, for now it will be the closing time of our depots

            //some buffer to avoid breaks through tour delays
            double schedulingBuffer = 15 * 60.;

            //This would be a rather hard deadline to just set the latest time to schedule to a fix value
//            double latestBlockingSchedulingTime = 22 * 3600.;
            //It seems more elegant to do it this way, so the latest time to schedule always depends on the blockingDuration
            double latestBlockingSchedulingTime = config.qsim().getEndTime().seconds() - (drtBlockingRequest.getPlannedBlockingDuration() + schedulingBuffer);

            //TODO what if the 1st tour of the queue is very long and can never be assigned?
            // => replanning of tours (incorporate org.matsim.drtBlockings.ReplanningBlockingRequestEngine). tschlenther, dec '20
            // the way that works now, we would actually NEED to reject those requests.
//            if(timer.getTimeOfDay() >= latestBlockingSchedulingTime){
            if(timer.getTimeOfDay() > drtBlockingRequest.getStartTime()){
                //Do we still need the latestBlockingSchedulingTIme?? Because with our vehicles for each time window, its not
                //possible to exceed the global service time
                //instead of using this we should use timer.getTimeOfDay() > drtBlockingRequest.getBlockingStart.
                rejectBlockingRequest(drtBlockingRequest);
                blockingRequestsIterator.remove();
            } else {
                double idleVehiclesNo = idleVehicles.size();
                double allVehiclesNo = fleet.getVehicles().size();
                double currentIdleVehicleRatio = idleVehiclesNo / allVehiclesNo;

                if (currentIdleVehicleRatio > this.minIdleVehicleRatio) {
                    //TODO does current blocking request fit (based on historic fleet occupancy data?)

                    if (!idleVehicles.isEmpty()) {
                        DvrpVehicle vehicle;

                        List<DvrpVehicle> availableVehicles = idleVehicles.stream()
                                .filter(v -> v.getServiceEndTime() > timer.getTimeOfDay() + drtBlockingRequest.getPlannedBlockingDuration()) //blocking should be expected to fit into vehicle service time
                                .filter(v -> !this.blockingManager.isVehicleBlocked(v))  //if the idle vehicle is blocked (in the future), do not assign it
                                .collect(Collectors.toList());
                        vehicle = this.dispatcher.findDispatchForBlockingRequest(Collections.unmodifiableList(availableVehicles), drtBlockingRequest);

                        if (vehicle == null) {
                            continue;
                        }

                        log.info("blocking vehicle " + vehicle.getId() + " for time period start=" + timer.getTimeOfDay()
                                + " end=" + (timer.getTimeOfDay() + drtBlockingRequest.getPlannedBlockingDuration()));
                        idleVehicles.remove(vehicle);
                        scheduleTasksForBlockedVehicle(drtBlockingRequest, vehicle);
                        if(! this.blockingManager.blockVehicle(vehicle,drtBlockingRequest)) throw new RuntimeException("could not block vehicle=" + vehicle + ". should not happen... ");
                        blockingRequestsIterator.remove();
                        eventsManager.processEvent(new DrtBlockingRequestScheduledEvent(timer.getTimeOfDay(),
                                drtBlockingRequest.getId(), drtBlockingRequest.getCarrierId(),
                                Id.create(drtBlockingRequest.getCarrierId(), CarrierVehicle.class), vehicle.getId()));
                        }
                } else {
                    return;
                }
            }
        }
    }

    private void rejectBlockingRequest(DrtBlockingRequest drtBlockingRequest) {
        //If a BlockingRequest could not be assigned to a vehicle in time, we need to reject it
        log.warn("drt blocking request " + drtBlockingRequest + " could not be assigned in time. It is rejected.");
        eventsManager.processEvent(new DrtBlockingRequestRejectedEvent(timer.getTimeOfDay(), drtBlockingRequest.getId(),
                drtBlockingRequest.getCarrierId(), drtBlockingRequest.getSubmissionTime()));
    }

    private void scheduleTasksForBlockedVehicle(DrtBlockingRequest drtBlockingRequest, DvrpVehicle vehicle) {
            Schedule schedule = vehicle.getSchedule();
            DrtStayTask stayTask = (DrtStayTask)schedule.getCurrentTask();
            if (stayTask.getTaskIdx() != schedule.getTaskCount() - 1) {
                throw new IllegalStateException("The current STAY task is not last. Not possible without prebooking");
            }
            stayTask.setEndTime(timer.getTimeOfDay()); // finish STAY

            VrpPathWithTravelData pathToReservationStart = VrpPaths.calcAndCreatePath(stayTask.getLink(), Tasks.getBeginLink(drtBlockingRequest.getTasks().get(0)), stayTask.getEndTime(), router,
                    travelTime);

            Task previousTask = new DrtDriveTask(pathToReservationStart, DrtDriveTask.TYPE);
            schedule.addTask(previousTask);

            for (Task task : drtBlockingRequest.getTasks()) {
                double duration = task.getEndTime() - task.getBeginTime();
                task.setBeginTime(previousTask.getEndTime());
                task.setEndTime(previousTask.getEndTime() + duration);
                schedule.addTask(task);
                previousTask = task;
            }
            schedule.addTask(new DrtStayTask(previousTask.getEndTime(), vehicle.getServiceEndTime(), Tasks.getEndLink(previousTask)));
//            System.out.println(schedule.getTasks());
    }

    private OptionalTime retrieveEndTimeOfBlocking(Schedule schedule) {
        OptionalTime updatedBlockingEndTime = OptionalTime.undefined();
        for(int i = schedule.getTaskCount() - 1; i >= schedule.getCurrentTask().getTaskIdx(); i--){
            if(schedule.getTasks().get(i) instanceof FreightRetoolTask){
                //end times just got updated by ScheduleTimingUpdater which is called from the delegate optimizer
                return OptionalTime.defined(schedule.getTasks().get(i).getEndTime());
            }
        }
        return updatedBlockingEndTime;
    }

}
