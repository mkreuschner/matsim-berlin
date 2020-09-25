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

package org.matsim.run.ev;

import com.google.common.base.Preconditions;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.router.PlanRouter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.utils.collections.Tuple;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class ActivityWhileChargingFinder {

	private final Set<String> activityTypes;
	private static final double MINIMUM_TIME = 10 * 60;
	private final String evRoutingMode;
	private Logger log = Logger.getLogger(ActivityWhileChargingFinder.class);

	ActivityWhileChargingFinder(String mode, List<String> possibleWhileChargingStartActTypes){
		this.evRoutingMode = mode;
		this.activityTypes = possibleWhileChargingStartActTypes;
	}

	/**
	 * last possible activity that fulfills all criteria is returned
	 *
	 * @param plan
	 * @return null if no suitable activity was found
	 */
	@Nullable
	Activity findActivityWhileCharging (List<PlanElement> planElements){
		List<Activity> activities = TripStructureUtils.getActivities(planElements, TripStructureUtils.StageActivityHandling.ExcludeStageActivities);
		List<Tuple<Leg, Activity>> evLegsWithFollowingActs = activities.stream()
				.filter(activity -> activityTypes.contains(activity.getType()))
				.map(activity -> planElementsBeforeLeg.indexOf(activity))
				.filter(idx -> idx > 0)
				.map(idx -> new Tuple<>((Leg) planElements.get(idx - 1), (Activity) planElements.get(idx)))
				.filter(tuple -> TripStructureUtils.getRoutingMode(tuple.getFirst()).equals(this.evRoutingMode))
				.collect(Collectors.toList());

		if(evLegsWithFollowingActs.isEmpty()) return null;
		evLegsWithFollowingActs.sort(Comparator.comparingDouble(tuple -> tuple.getFirst().getDepartureTime().seconds()));

		for (int i = evLegsWithFollowingActs.size() - 1; i >= 0; i++) {
			Tuple<Leg, Activity> tuple = evLegsWithFollowingActs.get(i);
			double begin = tuple.getFirst().getDepartureTime().seconds() + tuple.getFirst().getTravelTime().seconds();
			double end = getNextLegOfRoutingModeAfterActivity(planElements, tuple.getSecond(), this.evRoutingMode).getDepartureTime().seconds();
			if(end - begin >= MINIMUM_TIME) return tuple.getSecond();
		}
		return null;
	}

	/**
	 *
	 * @param planElements
	 * @param activity
	 * @param routingMode
	 * @return null if no suitable leg was found
	 */
	@Nullable
	Leg getNextLegOfRoutingModeAfterActivity(List<PlanElement> planElements, Activity activity, String routingMode){
		int actIndex = planElements.indexOf(activity);
		if(actIndex < 0){
			log.warn("could not find activity within given list of plan elements");
			return null;
		}
		if(actIndex == planElements.size() - 1) {
			log.warn("the given activity is the last activity in the given list of plan elements");
			return null;
		}
		for (int ii = actIndex + 1; ii < planElements.size(); ii++){
			PlanElement element = planElements.get(ii);
			if(element instanceof Leg && TripStructureUtils.getRoutingMode((Leg) element).equals(routingMode)){
				return (Leg) element;
			}
		}
		return null;
	}

}