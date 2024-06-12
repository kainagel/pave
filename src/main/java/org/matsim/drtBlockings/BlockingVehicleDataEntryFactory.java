package org.matsim.drtBlockings;

import com.google.inject.Provider;
import org.matsim.contrib.drt.optimizer.VehicleDataEntryFactoryImpl;
import org.matsim.contrib.drt.optimizer.VehicleEntry;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.schedule.DrtTaskType;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.schedule.Schedule;
import org.matsim.core.config.Config;

import javax.inject.Inject;

 class BlockingVehicleDataEntryFactory implements VehicleEntry.EntryFactory {

    private final VehicleDataEntryFactoryImpl delegate;
    DrtBlockingManager blockingManager;

    BlockingVehicleDataEntryFactory(DrtConfigGroup drtCfg, DrtBlockingManager blockingManager) {
        this.delegate = new VehicleDataEntryFactoryImpl(drtCfg);
        this.blockingManager = blockingManager;
    }

    @Override
    public VehicleEntry create(DvrpVehicle vehicle, double currentTime) {
        if(! blockingManager.isVehicleBlocked(vehicle)){
            //can not touch vehicles out of order
            if(vehicle.getSchedule().getStatus() == Schedule.ScheduleStatus.STARTED){
                //do some consistency checks
                if(! (vehicle.getSchedule().getCurrentTask().getTaskType() instanceof DrtTaskType)){
                    throw new IllegalStateException("vehicle " + vehicle + " is not blocked but still is performing a task that has no DrtTaskType." +
                            "\n Task=" + vehicle.getSchedule().getCurrentTask());
                }
                {//TODO this is only here as long as blockings are not supported to start in the future
                    for(int i = vehicle.getSchedule().getCurrentTask().getTaskIdx(); i < vehicle.getSchedule().getTaskCount(); i++){
                        if(! (vehicle.getSchedule().getTasks().get(i).getTaskType() instanceof DrtTaskType) ){
                            throw new IllegalStateException("vehicle " + vehicle + " \n is not blocked but still has tasks planned that are of other type than DrtTaskType.");
                        }
                    }
                }
            }
            return delegate.create(vehicle, currentTime);
        }
        return null;
    }

//    static class ReservationVehicleDataEntryFactoryProvider implements Provider<BlockingVehicleDataEntryFactory> {
//        @Inject
//        private Config config;
//
//        @Inject
//        DrtBlockingManager blockingManager;
//
//        @Override
//        public BlockingVehicleDataEntryFactory get() {
//            return new BlockingVehicleDataEntryFactory(DrtConfigGroup.getSingleModeDrtConfig(config), blockingManager);
//        }
//    }
}


