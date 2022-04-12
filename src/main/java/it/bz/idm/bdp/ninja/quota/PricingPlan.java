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
		GUEST,
		KNOWN_REFERER,
		PRIVILEGED_USER,
		NO_RESTRICTION
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
		Map<PricingPlan.Policy, Long> quotaMap
	) {
		if (roles == null) {
			throw new IllegalArgumentException(
				"PricingPlan cannot be resolved. Invalid argument: Roles missing!"
			);
		}

		// Validation of roles and users have already been done outside, so no need to check against
		// an authentication server anymore...
		if (roles.contains(SecurityUtils.ROLE_ADMIN)) {
			return new PricingPlan(Policy.NO_RESTRICTION, quotaMap);
		}

		if (roles.size() == 1 && roles.contains(SecurityUtils.ROLE_GUEST)) {
			if (referer != null && !referer.isEmpty()) {
				return new PricingPlan(Policy.KNOWN_REFERER, quotaMap);
			}
			return new PricingPlan(Policy.GUEST, quotaMap);
		}

		if (user != null && !user.isEmpty()) {
			return new PricingPlan(Policy.PRIVILEGED_USER, quotaMap);
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
		return policy.toString();
	}

}
