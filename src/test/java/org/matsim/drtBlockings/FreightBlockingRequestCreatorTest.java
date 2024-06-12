package org.matsim.drtBlockings;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.optimizer.Request;
import org.matsim.contrib.freight.FreightConfigGroup;
import org.matsim.contrib.freight.carrier.*;
import org.matsim.contrib.freight.utils.FreightUtils;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.drtBlockings.tasks.FreightRetoolTask;
import org.matsim.testcases.MatsimTestUtils;
import org.matsim.vehicles.VehicleType;

import java.util.Map;
import java.util.stream.Collectors;


public class FreightBlockingRequestCreatorTest {

    Scenario scenario;

    @Before
    public void setUp() throws Exception {
        Config config = ConfigUtils.loadConfig("chessboard/chessboard_drtBlocking_config.xml");
        MultiModeDrtConfigGroup multiModeDrtConfig = ConfigUtils.addOrGetModule(config, MultiModeDrtConfigGroup.class);
        multiModeDrtConfig.addParameterSet(new DrtConfigGroup());
        scenario = ScenarioUtils.loadScenario(config);

        CarrierVehicleTypes vTypes = FreightUtils.getCarrierVehicleTypes(scenario);
        Carriers carrierContainer = FreightUtils.getOrCreateCarriers(scenario);
        Id<VehicleType> vehicleTypeId = Id.create("standardVehicle", VehicleType.class);

        VehicleType vehicleType = CarrierVehicleType.Builder.newInstance(vehicleTypeId)
                .setCapacity(1)
                .setCostPerDistanceUnit(1)
                .setCostPerTimeUnit(1)
                .setFixCost(1)
                .build();
        vTypes.getVehicleTypes().put(vehicleTypeId,vehicleType);

        Carrier carrier = CarrierUtils.createCarrier(Id.create("testCarrierUno", Carrier.class));
        setupCarrier(carrier, vehicleType);
        CarrierUtils.setCarrierMode(carrier, TransportMode.drt);
        carrierContainer.addCarrier(carrier);

        carrier = CarrierUtils.createCarrier(Id.create("testCarrierDos", Carrier.class));
        setupCarrier(carrier, vehicleType);
        CarrierUtils.setCarrierMode(carrier, TransportMode.car);
        carrierContainer.addCarrier(carrier);

        new CarrierVehicleTypeLoader(carrierContainer).loadVehicleTypes(vTypes);

        FreightUtils.runJsprit(scenario);
    }

    private void setupCarrier(Carrier carrier, VehicleType vehicleType) {
        CarrierVehicle lateVehicle = CarrierVehicle.Builder.newInstance(Id.createVehicleId("lateVehicle"), Id.createLinkId(2))
                .setEarliestStart(9*3600)
                .setLatestEnd(24*3600)
                .setType(vehicleType)
                .build();
        CarrierUtils.addCarrierVehicle(carrier, lateVehicle);
        CarrierVehicle earlyVehicle = CarrierVehicle.Builder.newInstance(Id.createVehicleId("earlyVehicle"), Id.createLinkId(2))
                .setEarliestStart(6*3600)
                .setLatestEnd(24*3600)
                .setType(vehicleType)
                .build();
        CarrierUtils.addCarrierVehicle(carrier, earlyVehicle);
        CarrierService service1 = CarrierService.Builder.newInstance(Id.create(1, CarrierService.class), Id.createLinkId(3))
                .setCapacityDemand(1)
                .setServiceStartTimeWindow(TimeWindow.newInstance(10*3600,12*3600))
                .setServiceDuration(5*60)
                .build();
        CarrierUtils.addService(carrier, service1);
        CarrierService service2 = CarrierService.Builder.newInstance(Id.create(2, CarrierService.class), Id.createLinkId(3))
                .setCapacityDemand(1)
                .setServiceStartTimeWindow(TimeWindow.newInstance(6*3600,8*3600))
                .setServiceDuration(5*60)
                .build();
        CarrierUtils.addService(carrier, service2);

        CarrierUtils.setJspritIterations(carrier, 30);
    }

    @Test
    public void createRequestsForIteration() {

        Map<Id<Request>, DrtBlockingRequest> requests = new FreightBlockingRequestCreator(scenario.getConfig(), scenario.getNetwork(), new FreeSpeedTravelTime())
                .createBlockingRequests(FreightUtils.getCarriers(scenario))
                .stream()
                .collect(Collectors.toMap(request -> request.getId(), request -> request));

        Assert.assertEquals("the number of requests should be 2. The carrier not using drt should not initiate a blocking request" ,  2 , requests.size());

        {
            DrtBlockingRequest request = requests.get(Id.create("testCarrierUno_earlyVehicle_1", Request.class)); //this will be the request with a service time window starting at 6 a.m.
            Assert.assertNotNull(request);

            double submissionTime = 6 * 3600 - FreightBlockingRequestCreator.SUBMISSION_LOOK_AHEAD;

            Assert.assertEquals(submissionTime, request.getSubmissionTime(), MatsimTestUtils.EPSILON);
            Assert.assertEquals("the depot is on link 2. this is where the start link of the blocking request should point to.", Id.createLinkId(2), request.getStartLink().getId());

//            the vehicle needs to travel 1 link on the way to the depot, 1 complete link on the way to the service and 7 complete links on the way back to the depot (each link takes 100 seconds)
//            plus an additional second for each start links.
//            plus retool duration and service duration (5 minutes)
            double duration =  FreightRetoolTask.RETOOL_DURATION * 2 + 5*60 + 8 * 100 + 1 + 1;
            Assert.assertEquals(duration, request.getPlannedBlockingDuration(), MatsimTestUtils.EPSILON);

//            drive to depot, retool, drive to service, service, drive to depot
            Assert.assertEquals(5 ,request.getTasks().size());
            Assert.assertEquals(6*3600, request.getTasks().get(0).getBeginTime(), MatsimTestUtils.EPSILON);
        }
        {
            DrtBlockingRequest request = requests.get(Id.create("testCarrierUno_lateVehicle_1", Request.class)); //this will be the request with a service time window starting at 10 a.m.
            Assert.assertNotNull(request);

//      service time window starts at 10 a.m., travel time from depot to service = 100s which is multiplied by a bufferFactor of 1.25 (this is done because jsprit calculates with waiting at the first service which we want to avoid)
            double submissionTime = 10*3600 - FreightBlockingRequestCreator.SUBMISSION_LOOK_AHEAD - FreightRetoolTask.RETOOL_DURATION - 125;

            //            the vehicle needs to travel 1 link on the way to the depot, 1 complete link on the way to the service and 7 complete links on the way back to the depot (each link takes 100 seconds)
//            plus an additional second for each start links.
//            plus retool duration and service duration (5 minutes)
            double duration =  FreightRetoolTask.RETOOL_DURATION * 2 + 5*60 + 8 * 100 + 1 + 1;
            Assert.assertEquals(duration, request.getPlannedBlockingDuration(), MatsimTestUtils.EPSILON);

            Assert.assertEquals(submissionTime, request.getSubmissionTime(), MatsimTestUtils.EPSILON);
            Assert.assertEquals(Id.createLinkId(2), request.getStartLink().getId());

//            drive to depot, retool, drive to service, service, drive to depot
            Assert.assertEquals(5 ,request.getTasks().size());


            //jsprit calculates with vehicle departure = 9.00 a.m. and a travel time of 100s to the service
            //but the services' earliest start is 10 a.m.
            //so we set the tour start to 10 a.m. - 100s  * bufferfactor - RETOOL-Duration

            Assert.assertEquals(10*3600 - 100 * 1.25 - FreightRetoolTask.RETOOL_DURATION, request.getTasks().get(0).getBeginTime(), MatsimTestUtils.EPSILON);
        }
    }
}