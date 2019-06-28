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
package privateAV;

import org.matsim.contrib.dvrp.path.VrpPathWithTravelData;
import org.matsim.contrib.dvrp.schedule.DriveTaskImpl;
import org.matsim.contrib.taxi.schedule.TaxiTask;

/**
 * @author tschlenther
 *
 */
class PFAVServiceDriveTask extends DriveTaskImpl implements TaxiTask {

    PFAVServiceDriveTask(VrpPathWithTravelData path) {
		super(path);
	}

	@Override
	public TaxiTaskType getTaxiTaskType() {
		return TaxiTaskType.OCCUPIED_DRIVE;
	} 

	
	@Override
	protected String commonToString() {
		return "[" + getTaxiTaskType().name() + "_SERVICE]" + super.commonToString();
	}

}
