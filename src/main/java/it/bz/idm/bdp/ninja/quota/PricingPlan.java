package it.bz.idm.bdp.ninja.quota;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
    private long quotaGuest;
    private long quotaReferer;
    private long quotaUser;

	public PricingPlan(Policy policy, long quotaGuest, long quotaReferer, long quotaUser) {
		this.policy = policy;
		this.quotaGuest = quotaGuest;
		this.quotaReferer = quotaReferer;
		this.quotaUser = quotaUser;
	}

	public static PricingPlan resolvePlan(List<String> roles, String user, String referer, long quotaGuest, long quotaReferer, long quotaUser) {
		if (roles == null) {
			throw new IllegalArgumentException(
				"PricingPlan cannot be resolved. Invalid argument: Roles missing!"
			);
		}

		// Validation of roles and users have already been done outside, so no need to check against
		// an authentication server anymore...
		if (roles.contains(SecurityUtils.ROLE_ADMIN)) {
			return new PricingPlan(Policy.NO_RESTRICTION, quotaGuest, quotaReferer, quotaUser);
		}

		if (roles.size() == 1 && roles.contains(SecurityUtils.ROLE_GUEST)) {
			if (referer != null && !referer.isEmpty()) {
				return new PricingPlan(Policy.KNOWN_REFERER, quotaGuest, quotaReferer, quotaUser);
			}
			return new PricingPlan(Policy.GUEST, quotaGuest, quotaReferer, quotaUser);
		}

		if (user != null && !user.isEmpty()) {
			return new PricingPlan(Policy.PRIVILEGED_USER, quotaGuest, quotaReferer, quotaUser);
		}

		throw new IllegalArgumentException(
			"Cannot resolve the pricing plan. Too many arguments missing!"
		);
	}

	public Bandwidth getBandwidth() {
		switch(policy) {
			case NO_RESTRICTION:
				throw new IllegalArgumentException(
					"Princing plan NO_RESTRICTION cannot have a bandwidth constraint."
				);
			case PRIVILEGED_USER:
				return Bandwidth.classic(
					quotaUser,
					Refill.intervally(
						quotaUser,
						Duration.ofSeconds(1)
					)
				);
			case KNOWN_REFERER:
				return Bandwidth.classic(
					quotaReferer,
					Refill.intervally(
						quotaReferer,
						Duration.ofSeconds(1)
					)
				);
			default:
			case GUEST:
				return Bandwidth.classic(
					quotaGuest,
					Refill.intervally(
						quotaGuest,
						Duration.ofSeconds(1)
					)
				);
		}
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
