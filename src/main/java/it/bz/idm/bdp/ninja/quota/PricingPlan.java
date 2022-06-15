package it.bz.idm.bdp.ninja.quota;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Refill;
import it.bz.idm.bdp.ninja.utils.SecurityUtils;

public class PricingPlan {

	public enum Policy {
		ANONYMOUS("Anonymous"),
		REFERER("Referer"),
		AUTHENTICATED_BASIC("Authenticated Basic"),
		AUTHENTICATED_ADVANCED("Authenticated Advanced"),
		AUTHENTICATED_PREMIUM("Authenticated Premium"),
		NO_RESTRICTION("No Restriction");

		private String name;

		Policy(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

	private Policy policy;
	private Map<Policy, Long> quotaMap;

	public PricingPlan(Policy policy, Map<Policy, Long> quotaMap) {
		this.policy = policy;
		this.quotaMap = new EnumMap<>(quotaMap);
	}

	public static PricingPlan resolvePlan(
		List<String> roles,
		String user,
		String referer,
		Map<Policy, Long> quotaMap
	) {
		if (roles == null) {
			throw new IllegalArgumentException(
				"PricingPlan cannot be resolved. Invalid argument: Roles missing!"
			);
		}

		// Validation of roles and users have already been done outside, so no need to check against
		// an authentication server anymore...
		if (roles.contains(SecurityUtils.ROLE_QUOTA_ADMIN)) {
			return new PricingPlan(Policy.NO_RESTRICTION, quotaMap);
		}

		if (user != null && !user.isEmpty()) {
			if (roles.contains(SecurityUtils.ROLE_QUOTA_PREMIUM))
				return new PricingPlan(Policy.AUTHENTICATED_PREMIUM, quotaMap);
			if (roles.contains(SecurityUtils.ROLE_QUOTA_ADVANCED))
				return new PricingPlan(Policy.AUTHENTICATED_ADVANCED, quotaMap);
			return new PricingPlan(Policy.AUTHENTICATED_BASIC, quotaMap);
		}

		if (roles.size() == 1 && roles.contains(SecurityUtils.ROLE_QUOTA_GUEST)) {
			if (referer != null && !referer.isEmpty()) {
				return new PricingPlan(Policy.REFERER, quotaMap);
			}
			return new PricingPlan(Policy.ANONYMOUS, quotaMap);
		}

		throw new IllegalArgumentException(
			"Cannot resolve the pricing plan. Too many arguments missing!"
		);
	}

	public Bandwidth getBandwidth() {
		long quota = quotaMap.getOrDefault(policy, Long.valueOf(-1));
		if (quota < 0) {
			throw new IllegalArgumentException(
				String.format(
					"Princing plan '%s' does not have a bandwidth constraint or does not exist at all.",
					policy
				)
			);
		}
		return Bandwidth.classic(
			quota,
			Refill.intervally(
				quota,
				Duration.ofSeconds(1)
			)
		);
	}

    public boolean is(Policy policy) {
        return policy == this.policy;
    }

	public Policy getPolicy() {
		return policy;
	}

	@Override
	public String toString() {
		return policy.getName();
	}

}
