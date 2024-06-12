package org.matsim.pfav.privateAV;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.fleet.Fleet;
import org.matsim.contrib.dvrp.path.VrpPathWithTravelData;
import org.matsim.contrib.dvrp.path.VrpPaths;
import org.matsim.contrib.dvrp.schedule.DriveTask;
import org.matsim.contrib.dvrp.schedule.Schedule;
import org.matsim.contrib.dvrp.schedule.Schedule.ScheduleStatus;
import org.matsim.contrib.dvrp.schedule.Schedules;
import org.matsim.contrib.dvrp.schedule.StayTask;
import org.matsim.contrib.dvrp.schedule.Task;
import org.matsim.contrib.dvrp.schedule.Tasks;
import org.matsim.contrib.dvrp.tracker.TaskTrackers;
import org.matsim.contrib.dvrp.trafficmonitoring.DvrpTravelTimeModule;
import org.matsim.contrib.taxi.passenger.TaxiRequest;
import org.matsim.contrib.taxi.passenger.TaxiRequest.TaxiRequestStatus;
import org.matsim.contrib.taxi.run.TaxiConfigGroup;
import org.matsim.contrib.taxi.schedule.*;
import org.matsim.contrib.taxi.scheduler.TaxiScheduleInquiry;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.mobsim.framework.MobsimTimer;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelTime;

import com.google.inject.name.Named;

final class PFAVScheduler {

	private static final Logger log = Logger.getLogger(PFAVScheduler.class);

	private final TaxiScheduleInquiry taxiScheduleInquiry;
	private final LeastCostPathCalculator router;
	private final Network network;
	private final FreightAVConfigGroup pfavConfigGroup;

	private final TaxiConfigGroup taxiCfg;
	private final TravelTime travelTime;
	private final MobsimTimer timer;
	private final EventsManager eventsManager;
	private final FreightTourManagerListBased freightManager;
	private final HashSet<DvrpVehicle> vehiclesOnFreightTour = new HashSet<>();
	private final Map<Id<DvrpVehicle>, Double> requestedVehicles = new HashMap<>();

	/**
	 *
	 */
	PFAVScheduler(TaxiConfigGroup taxiCfg, Fleet fleet, Network network, MobsimTimer timer,
			@Named(DvrpTravelTimeModule.DVRP_ESTIMATED) TravelTime travelTime, LeastCostPathCalculator router,
			EventsManager eventsManager, FreightTourManagerListBased tourManager,
			FreightAVConfigGroup pfavConfigGroup) {
		this.freightManager = tourManager;
		this.eventsManager = eventsManager;
		this.taxiCfg = taxiCfg;
		this.router = router;
		this.travelTime = travelTime;
		this.timer = timer;
		this.network = network;
		this.pfavConfigGroup = pfavConfigGroup;
		taxiScheduleInquiry = new TaxiScheduleInquiry(taxiCfg, timer);

		//init fleet
		for (DvrpVehicle veh : fleet.getVehicles().values()) {
			veh.getSchedule()
					.addTask(new TaxiStayTask(veh.getServiceBeginTime(), veh.getServiceEndTime(), veh.getStartLink()));
		}
	}

	void updateBeforeNextTask(DvrpVehicle vehicle) {

		Schedule schedule = vehicle.getSchedule();
		// Assumption: there is no delay as long as the schedule has not been started (PLANNED)
		if (schedule.getStatus() != ScheduleStatus.STARTED) {
			return;
		}

		updateTimeline(vehicle);
		Task currentTask = schedule.getCurrentTask();

		switch (TaxiTaskBaseType.getBaseTypeOrElseThrow(currentTask)) {
			case PICKUP:
				if (!taxiCfg.isDestinationKnown()) {
					appendResultingTasksAfterPickup(vehicle);
				}
				if (!requestedVehicles.containsKey(vehicle.getId())) {
					throw new RuntimeException(
							"vehicle performed a passenger pick up but was not on the requested vehicles list..");
				}
				requestedVehicles.remove(vehicle.getId());
				//remove the must return time from vehicle specification
				PFAVehicle.MustReturnLinkTimePair returnLog = ((PFAVehicle) vehicle).getMustReturnToOwnerLinkTimePairs().remove();
//				log.warn("removed must return log= "
//						+ returnLog
//						+ " from vehicle= "
//						+ vehicle.getId());
				break;
			case DROPOFF:
				if (currentTask instanceof TaxiDropoffTask) {
					if (vehicle instanceof PFAVehicle) {
						requestFreightTour(vehicle);
					} else {
						//in future, there could be usecases where we have both, DvrpVehicles (supertype) and PFAVehicles, so we would not throw an exception here and just keep going
						throw new RuntimeException("currently, all DvrpVehicles should be of type PFAVehicle");
					}
				} else if (currentTask instanceof PFAVRetoolTask
						&& Schedules.getNextTask(schedule) instanceof TaxiEmptyDriveTask //we are at the end of a freight tour (otherwise next task would be instanceof PFAVServiceDriveTask
						&& pfavConfigGroup.isAllowMultipleToursInaRow()
						&& !requestedVehicles.containsKey(vehicle.getId())) {    //owner has not submitted a request yet
					requestFreightTour(vehicle);
				}
				break;

			case STAY: {
				break;
			}
			case EMPTY_DRIVE:
                if (isPFAVReturningToDepot(vehicle)
						&& pfavConfigGroup.isAllowMultipleToursInaRow()
						&& !requestedVehicles.containsKey(vehicle.getId())) {
					requestFreightTour(vehicle);
                } else if (isPFAVReturningToOwner(vehicle)) {
					//we are at the end of a freight tour
					endFreightTour(vehicle);
					log.warn("vehicle "
							+ vehicle.getId()
							+ " returns to it's owner's location at time="
							+ timer.getTimeOfDay());
				}

				break;
			case OCCUPIED_DRIVE:
				//TODO test this enforcement of customer time windows...
				if(Schedules.getNextTask(schedule) instanceof PFAVServiceTask){
					PFAVServiceTask serviceTask = (PFAVServiceTask) Schedules.getNextTask(schedule);
					double earliestStart = serviceTask.getCarrierService().getServiceStartTimeWindow().getStart();
					double latestStart = serviceTask.getCarrierService().getServiceStartTimeWindow().getEnd();
					if(earliestStart > timer.getTimeOfDay()){
						//vehicle is too early at delivery location. we need to lengthen the serviceTask. timeLine will be updated when the stayTask ends
						double newServiceEndTime = serviceTask.getCarrierService().getServiceDuration() + earliestStart;
						serviceTask.setEndTime(newServiceEndTime);
						schedule.getTasks().get(serviceTask.getTaskIdx() + 1).setBeginTime(newServiceEndTime);
					} else if(latestStart < timer.getTimeOfDay()){
						log.warn("Vehicle " + vehicle.getId() + " is about to start a service for which the latest start time expired at " + latestStart + ". time of day = " + timer.getTimeOfDay());
					}
				}
				break;
			default: {
				throw new IllegalStateException();
			}
		}
	}

    private boolean isPFAVReturningToDepot(DvrpVehicle vehicle) {
        Schedule schedule = vehicle.getSchedule();
        return (schedule.getCurrentTask() instanceof TaxiEmptyDriveTask &&
                Schedules.getNextTask(schedule) instanceof PFAVRetoolTask &&
                Schedules.getPreviousTask(schedule) instanceof PFAVServiceTask);
    }

    private boolean isPFAVReturningToOwner(DvrpVehicle vehicle) {
        Schedule schedule = vehicle.getSchedule();
        return (schedule.getCurrentTask() instanceof TaxiEmptyDriveTask
                && Schedules.getPreviousTask(schedule) instanceof PFAVRetoolTask
                && !(Schedules.getNextTask(schedule) instanceof PFAVRetoolTask)); //no transfer drive to another depot
    }

	private void requestFreightTour(DvrpVehicle vehicle) {
		if (timer.getTimeOfDay() > pfavConfigGroup.getFreightTourLatestStart()) {
//			log.info("No freight tour is requested for vehicle "
//					+ vehicle.getId()
//					+ " because global freight time window ended already at "
//					+ pfavConfigGroup.getFreightTourLatestStart());
			return;
		}
		if(timer.getTimeOfDay() > ((PFAVehicle) vehicle).getMustReturnToOwnerLinkTimePairs().peek().getTime()){
			log.warn("the must return time for vehicle " + vehicle.getId() + " is in the past. Thus, it will not request a freight tour. This means the owner agent is in delay....");
			return;
		}

//		log.info("Vehicle "
//				+ vehicle.getId()
//				+ " requests a freight tour at "
//				+ timer.getTimeOfDay()
//				+ " on link "
//				+ (Tasks.getEndLink(vehicle.getSchedule().getCurrentTask())).getId());
		FreightTourDataPlanned tourData;

		Task currentTask = vehicle.getSchedule().getCurrentTask();
		if (currentTask instanceof TaxiDropoffTask) {
			tourData = freightManager.vehicleRequestedFreightTour((PFAVehicle) vehicle, router);
		} else {
			Link depotLink = Tasks.getEndLink(currentTask);
			if (currentTask instanceof TaxiEmptyDriveTask) {
				tourData = freightManager.vehicleRequestedFreightTourAtDepot((PFAVehicle) vehicle, depotLink, router);
			} else if (currentTask instanceof PFAVRetoolTask) {
				tourData = freightManager.vehicleRequestedFreightTourExcludingDepot((PFAVehicle) vehicle, depotLink, router);
			} else {
				throw new IllegalStateException();
			}
			if (tourData != null) {
				endFreightTour(vehicle);
			}
		}
		if (tourData != null) {
//			log.info("vehicle " + vehicle.getId() + " requested a freight tour and received one by the manager");
			scheduleFreightTour(vehicle, tourData);
		} else {
			Link requestLink = Tasks.getEndLink(vehicle.getSchedule().getCurrentTask());
			eventsManager.processEvent(new EventFreightTourRequestRejected((PFAVehicle) vehicle, requestLink.getId(),
					timer.getTimeOfDay()));
//			log.info("request is rejected");
		}
	}

	private void endFreightTour(DvrpVehicle vehicle) {
		if (!this.vehiclesOnFreightTour.contains(vehicle))
			throw new IllegalStateException("freight tour of vehicle "
					+ vehicle.getId()
					+ "ends and scheduler did not even mark it as being on freight tour");
		eventsManager.processEvent(new EventFreightTourCompleted(vehicle.getId(), timer.getTimeOfDay()));
		this.vehiclesOnFreightTour.remove(vehicle);
	}

	private void scheduleFreightTour(DvrpVehicle vehicle, FreightTourDataPlanned tourData) {
		cleanScheduleBeforeInsertingFreightTour(vehicle);
		Schedule schedule = vehicle.getSchedule();
		DriveTask accessDrive = tourData.getAccessDriveTask();
        if (accessDrive == null) throw new IllegalStateException("no access drive found for tour " + tourData);
        schedule.addTask(accessDrive);

        //actually insert the tasks into the schedule. shift start and end times respectively
		FreightTourDataDispatched dispatchData;
		insertFreightTourInSchedule(vehicle, tourData);

		double returnDistance = scheduleReturnToOwnerAndGetDistance((PFAVehicle) vehicle, schedule, (StayTask) Schedules.getLastTask(schedule));
		double distanceToDepot = VrpPaths.calcDistance(tourData.getAccessDriveTask().getPath());
		dispatchData = buildDispatchData(vehicle, tourData, distanceToDepot, returnDistance);
		//append stay task
		appendStayTask(vehicle);
		//mark this vehicle's state as "on freight tour"
		this.vehiclesOnFreightTour.add(vehicle);
		log.info("vehicle " + vehicle.getId() + " got assigned to a freight schedule");
		eventsManager.processEvent(new EventFreightTourScheduled(dispatchData));
	}

	private FreightTourDataDispatched buildDispatchData(DvrpVehicle vehicle, FreightTourDataPlanned tourData, double distanceToDepot, double distanceBackToOwner) {
		double totalDistance = 0.;  //for analysis
		double emptyMeters = 0.;    //for analysis

		List<Task> remainingDriveTasks = vehicle.getSchedule().getTasks().stream()
				.filter(t -> t.getStatus().equals(Task.TaskStatus.PLANNED))
				.filter(t -> t instanceof DriveTask)
				.collect(Collectors.toList());

		for (Task task : remainingDriveTasks) {
			double distance = VrpPaths.calcDistance(((DriveTask) task).getPath());
			if (task instanceof TaxiEmptyDriveTask) emptyMeters += distance;
			totalDistance += distance;
		}

		return FreightTourDataDispatched.newBuilder()
				.vehicleId(vehicle.getId())
				.mustReturnLog(((PFAVehicle) vehicle).getMustReturnToOwnerLinkTimePairs().peek())
				.dispatchTime(timer.getTimeOfDay())
                .requestLink(Tasks.getEndLink(vehicle.getSchedule().getCurrentTask()).getId())
				.tourData(tourData)
				.distanceToDepot(distanceToDepot)
				.distanceBackToOwner(distanceBackToOwner)
				.plannedEmptyMeters(emptyMeters)
				.plannedTourLength(totalDistance)
				.build();
	}

	private void insertFreightTourInSchedule(DvrpVehicle vehicle, FreightTourDataPlanned tourData) {
		List<Task> tourActivities = tourData.getTourTasks();
		Task previousTask = tourData.getAccessDriveTask();
		for (Task currentTask : tourActivities) {
			if (pfavConfigGroup.isReRouteTours() && currentTask instanceof DriveTask) {
				DriveTask originalDriveTask = (DriveTask) currentTask;
				VrpPathWithTravelData path = VrpPaths.calcAndCreatePath(originalDriveTask.getPath().getFromLink(), originalDriveTask.getPath().getToLink(),
						previousTask.getEndTime(), this.router, this.travelTime);
				if (originalDriveTask instanceof TaxiEmptyDriveTask) {
					currentTask = new TaxiEmptyDriveTask(path, (TaxiTaskType) originalDriveTask.getTaskType());
				} else if (originalDriveTask instanceof PFAVServiceDriveTask) {
					currentTask = new PFAVServiceDriveTask(path);
				} else {
					throw new IllegalStateException();
				}
			} else {
				setAttributesForTask(vehicle, currentTask, previousTask);
			}
			previousTask = currentTask;
			vehicle.getSchedule().addTask(currentTask);
		}
	}

	private double scheduleReturnToOwnerAndGetDistance(PFAVehicle vehicle, Schedule schedule, StayTask previousTask) {
		Link returnLink = getReturnLink(vehicle);
		VrpPathWithTravelData pathBackToOwner = VrpPaths.calcAndCreatePath(previousTask.getLink(), returnLink,
				previousTask.getEndTime(), router, travelTime);
		TaxiEmptyDriveTask returnDriveTask = new TaxiEmptyDriveTask(pathBackToOwner, PFAVTaskTypes.EGRESS_FROM_DEPOT);
		schedule.addTask(returnDriveTask);
		return VrpPaths.calcDistance(pathBackToOwner);
	}

	private void setAttributesForTask(DvrpVehicle vehicle, Task currentTask, Task previousTask) {
		double duration = currentTask.getEndTime() - currentTask.getBeginTime();
		currentTask.setBeginTime(previousTask.getEndTime());
		currentTask.setEndTime(previousTask.getEndTime() + duration);
		if (currentTask instanceof PFAVRetoolTask) {
			((PFAVRetoolTask)currentTask).setVehicle(vehicle.getId());
		}
	}

	private Link getReturnLink(PFAVehicle vehicle) {
		Id<Link> ownerLink = vehicle.getMustReturnToOwnerLinkTimePairs().peek().getLinkId();
		if (ownerLink == null) {
			ownerLink = PFAVUtils.getLastPassengerDropOff(vehicle.getSchedule()).getLink().getId();
		}
		return network.getLinks().get(ownerLink);
	}

	private void cleanScheduleBeforeInsertingFreightTour(DvrpVehicle vehicle) {
		/*TODO: calling same method several times feels dirty - extract method !?
		 *we cannot use delegate.removeAwaitingRequests() because this method inserts another stay task
		 *
		 *vehicle requested freight tour after having performed another one
		 *we have to remove the following awaiting tasks in the schedule
		 */
		Schedule schedule = vehicle.getSchedule();
		Task currentTask = schedule.getCurrentTask();

		if (currentTask instanceof TaxiDropoffTask) {
			//vehicle requested freight tour after passenger dropoff
			//remove the stay task at the owner's activity location
			schedule.removeLastTask();
		} else if (currentTask instanceof TaxiEmptyDriveTask) {
			schedule.removeLastTask();    //retool task at depot
			schedule.removeLastTask();    //empty drive to owner's location
			schedule.removeLastTask();    //stay at owner's location
		} else if (currentTask instanceof PFAVRetoolTask) {
			schedule.removeLastTask();    //empty drive to owner's location
			schedule.removeLastTask();    //stay at owner's location
		} else {
			throw new IllegalStateException(
					"if a freight tour shall be inserted the current task has to be DROPOFF, PFAVRetool or EMPTY_DRIVE. That is not the case for vehicle "
							+ vehicle.getId()
							+ " at time "
							+ timer.getTimeOfDay());
		}
	}

	void scheduleRequest(DvrpVehicle vehicle, TaxiRequest request) {
		if (request.getStatus() != TaxiRequestStatus.UNPLANNED) {
			throw new IllegalStateException();
		}

		Schedule schedule = vehicle.getSchedule();
		Task lastTask = Schedules.getLastTask(schedule);

		if (!TaxiTaskBaseType.STAY.isBaseTypeOf(lastTask)) {
			throw new IllegalStateException();
		} else {

			double departureTime = Math.max(lastTask.getBeginTime(), timer.getTimeOfDay());

			VrpPathWithTravelData path = VrpPaths.calcAndCreatePath(Schedules.getLastLinkInSchedule(vehicle),
					request.getFromLink(), departureTime, router, travelTime);

			scheduleDrive(schedule, (TaxiStayTask)lastTask, path);

			double pickupEndTime = Math.max(path.getArrivalTime(), request.getEarliestStartTime())
					+ taxiCfg.getPickupDuration();
			schedule.addTask(new TaxiPickupTask(path.getArrivalTime(), pickupEndTime, request));

			if (taxiCfg.isDestinationKnown()) {
				appendResultingTasksAfterPickup(vehicle);

			}
		}

		requestedVehicles.put(vehicle.getId(), request.getSubmissionTime());
	}

	private void appendResultingTasksAfterPickup(DvrpVehicle vehicle) {
		appendOccupiedDriveAndDropoff(vehicle.getSchedule());
		appendStayTask(vehicle);
	}

	private void appendOccupiedDriveAndDropoff(Schedule schedule) {
		TaxiPickupTask pickupStayTask = (TaxiPickupTask)Schedules.getLastTask(schedule);

		// add DELIVERY after SERVE
		TaxiRequest req = (pickupStayTask).getRequest();
		Link reqFromLink = req.getFromLink();
		Link reqToLink = req.getToLink();
		double t3 = pickupStayTask.getEndTime();
		VrpPathWithTravelData path = VrpPaths.calcAndCreatePath(reqFromLink, reqToLink, t3, router, travelTime);
		schedule.addTask(new TaxiOccupiedDriveTask(path, req));

		double t4 = path.getArrivalTime();
		double t5 = t4 + taxiCfg.getDropoffDuration();
		schedule.addTask(new TaxiDropoffTask(t4, t5, req));
	}

	private void appendStayTask(DvrpVehicle vehicle) {
		Schedule schedule = vehicle.getSchedule();
		double tBegin = schedule.getEndTime();
		double tEnd = Math.max(tBegin, vehicle.getServiceEndTime());// even 0-second WAIT
		Link link = Schedules.getLastLinkInSchedule(vehicle);
		schedule.addTask(new TaxiStayTask(tBegin, tEnd, link));
	}

	boolean isCurrentlyOnOrWillPerformPerformFreightTour(DvrpVehicle veh) {
		return this.vehiclesOnFreightTour.contains(veh);
	}

	/**
	 * currently not implemented. is here for use in future
	 */
	void cancelFreightTour(DvrpVehicle veh) {
		// TODO: cancel the freight tour and make vehicle return to depot. insert STAY task at the depot
		//		- first check if freight tour is started in the first place! otherwise throw exception!
		throw new RuntimeException("currently not implemented");
	}

	//---------------------------------------------------- DELEGATE METHODS --------------

	//	@TODO: sort out methods / clean the code

	//-------------------------------COPIES

	//	cannot be delegated cause is set to protected in TaxiScheduler
	private void scheduleDrive(Schedule schedule, TaxiStayTask lastTask, VrpPathWithTravelData vrpPath) {
		switch (lastTask.getStatus()) {
			case PLANNED:
				if (lastTask.getBeginTime() == vrpPath.getDepartureTime()) { // waiting for 0 seconds!!!
					schedule.removeLastTask();// remove WaitTask
				} else {
					// actually this WAIT task will not be performed
					lastTask.setEndTime(vrpPath.getDepartureTime());// shortening the WAIT task
				}
				break;

			case STARTED:
				lastTask.setEndTime(vrpPath.getDepartureTime());// shortening the WAIT task
				break;

			case PERFORMED:
			default:
				throw new IllegalStateException();
		}

		if (vrpPath.getLinkCount() > 1) {
			schedule.addTask(new TaxiEmptyDriveTask(vrpPath, TaxiEmptyDriveTask.TYPE));
		}
	}

	//	cannot be delegated because we need different new end time calculation than in TaxiScheduler.calcNewEndTime()
	//	maybe we should use inheritance in order to get rid of all these redundant lines....
	void updateTimeline(DvrpVehicle vehicle) {
		Schedule schedule = vehicle.getSchedule();
		if (schedule.getStatus() != ScheduleStatus.STARTED) {
			return;
		}

		double predictedEndTime = TaskTrackers.predictEndTime(schedule.getCurrentTask(), timer.getTimeOfDay());
		updateTimelineImpl(vehicle, predictedEndTime);
	}

	//this also is (almost) the same as TaxiScheduler.updateTimeLineImpl() except that we have a (slightly) different implementation of calcNewEndTime()
	private void updateTimelineImpl(DvrpVehicle vehicle, double newEndTime) {
		Schedule schedule = vehicle.getSchedule();
		Task currentTask = schedule.getCurrentTask();
		if (currentTask.getEndTime() == newEndTime) {
			return;
		}

		currentTask.setEndTime(newEndTime);

		List<? extends Task> tasks = schedule.getTasks();
		int startIdx = currentTask.getTaskIdx() + 1;
		double newBeginTime = newEndTime;

		for (int i = startIdx; i < tasks.size(); i++) {
			Task task = tasks.get(i);
			double calcEndTime = calcNewEndTime(vehicle, task, newBeginTime);

			if (calcEndTime == REMOVE_STAY_TASK) {
				schedule.removeTask(task);
				i--;
			} else if (calcEndTime < newBeginTime) {// 0 s is fine (e.g. last 'wait')
				throw new IllegalStateException();
			} else {
				task.setBeginTime(newBeginTime);
				task.setEndTime(calcEndTime);
				newBeginTime = calcEndTime;
			}
		}
	}

	private final static double REMOVE_STAY_TASK = Double.NEGATIVE_INFINITY;

	private double calcNewEndTime(DvrpVehicle vehicle, Task task, double newBeginTime) {
		switch (TaxiTaskBaseType.getBaseTypeOrElseThrow(task)) {
			case STAY: {
				if (Schedules.getLastTask(vehicle.getSchedule()).equals(task)) {// last task
					// even if endTime=beginTime, do not remove this task!!! A taxi schedule should end with WAIT
					return Math.max(newBeginTime, vehicle.getServiceEndTime());
				} else {
					// if this is not the last task then some other task (e.g. DRIVE or PICKUP)
					// must have been added at time submissionTime <= t
					double oldEndTime = task.getEndTime();
					if (oldEndTime <= newBeginTime) {// may happen if the previous task is delayed
						return REMOVE_STAY_TASK;// remove the task
					} else {
						return oldEndTime;
					}
				}
			}

			case EMPTY_DRIVE:
			case OCCUPIED_DRIVE: {
				// cannot be shortened/lengthen, therefore must be moved forward/backward
				VrpPathWithTravelData path = (VrpPathWithTravelData)((DriveTask)task).getPath();
				// TODO one may consider recalculation of SP!!!!
				return newBeginTime + path.getTravelTime();
			}

			case PICKUP: {
				double t0 = ((TaxiPickupTask)task).getRequest().getEarliestStartTime();
				// the actual pickup starts at max(t, t0)
				double duration = task.getEndTime() - task.getBeginTime();
				return Math.max(newBeginTime, t0) + duration;
			}
			case DROPOFF: {
				// cannot be shortened/lengthen, therefore must be moved forward/backward
				double duration = task.getEndTime() - task.getBeginTime();
				return newBeginTime + duration;
			}

			default:
				throw new IllegalStateException();
		}
	}
}
