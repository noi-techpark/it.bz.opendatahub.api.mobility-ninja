package it.bz.idm.bdp.ninja.quota;

import java.security.Policy;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import it.bz.idm.bdp.ninja.DataFetcher;
import it.bz.idm.bdp.ninja.utils.SecurityUtils;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

	@Value("${ninja.quota.guest:20}")
    private Long quotaGuest;

	@Value("${ninja.quota.referer:100}")
    private Long quotaReferer;

	@Value("${ninja.quota.user:200}")
    private Long quotaUser;

	@Autowired
    private DataFetcher dataFetcher;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

		List<String> roles = SecurityUtils.getRolesFromAuthentication();
		String referer = request.getHeader("referer");
		String ip = request.getLocalAddr();
		String path = request.getRequestURI();
		String user = SecurityUtils.getSubjectFromAuthentication();

		PricingPlan plan = PricingPlan.resolvePlan(roles, user, referer, quotaGuest, quotaReferer, quotaUser);
		response.addHeader("X-Rate-Limit-Policy", plan.toString());
		if (plan.is(PricingPlan.Policy.NO_RESTRICTION)) {
			return true;
		}

        Bucket tokenBucket = dataFetcher.resolveBucket(plan, user, referer, ip, path);
        ConsumptionProbe probe = tokenBucket.tryConsumeAndReturnRemaining(1);

		long waitForRefill = probe.getNanosToWaitForRefill() / 1_000_000_000;
		response.addHeader("X-Rate-Limit-Reset", String.valueOf(waitForRefill));
		response.addHeader("X-Rate-Limit-Limit", String.valueOf(plan.getBandwidth().getCapacity()));

		if (probe.isConsumed()) {
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
			return true;
        }

		response.addHeader("X-Rate-Limit-Remaining", "0");
		throw new ResponseStatusException(
			HttpStatus.TOO_MANY_REQUESTS,
			"You have exhausted your API Request Quota"
		);
    }
}
