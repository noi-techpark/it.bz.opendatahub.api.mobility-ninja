package it.bz.idm.bdp.ninja.quota;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.jsoniter.output.JsonStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import it.bz.idm.bdp.ninja.DataFetcher;
import it.bz.idm.bdp.ninja.utils.SecurityUtils;
import it.bz.idm.bdp.ninja.utils.conditionals.ConditionalMap;

import static it.bz.idm.bdp.ninja.quota.PricingPlan.Policy;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

	@Value("${ninja.quota.guest:10}")
    private Long quotaGuest;

	@Value("${ninja.quota.referer:20}")
    private Long quotaReferer;

	@Value("${ninja.quota.basic:50}")
    private Long quotaBasic;

	@Value("${ninja.quota.advanced:100}")
    private Long quotaAdvanced;

	@Value("${ninja.quota.premium:200}")
    private Long quotaPremium;

	@Value("${ninja.quota.url}")
    private String quotaUrl;

	@Autowired
    private DataFetcher dataFetcher;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

		List<String> roles = SecurityUtils.getRolesFromAuthentication(SecurityUtils.RoleType.QUOTA);
		String referer = request.getHeader("referer");
		String ip = request.getLocalAddr();
		String path = request.getRequestURI();
		String user = SecurityUtils.getSubjectFromAuthentication();

		Map<PricingPlan.Policy, Long> quotaMap = new EnumMap<>(PricingPlan.Policy.class);
		quotaMap.put(Policy.ANONYMOUS, quotaGuest);
		quotaMap.put(Policy.REFERER, quotaReferer);
		quotaMap.put(Policy.AUTHENTICATED_BASIC, quotaBasic);
		quotaMap.put(Policy.AUTHENTICATED_ADVANCED, quotaAdvanced);
		quotaMap.put(Policy.AUTHENTICATED_PREMIUM, quotaPremium);
		quotaMap.put(Policy.NO_RESTRICTION, Long.valueOf(-1));

		PricingPlan plan = PricingPlan.resolvePlan(roles, user, referer, quotaMap);
		response.addHeader("X-Rate-Limit-Policy", plan.toString());
		if (plan.is(Policy.NO_RESTRICTION)) {
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
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		JsonStream.setIndentionStep(2);
		response.getWriter().write(
			JsonStream.serialize(
				ConditionalMap
					.init()
					.put("message", "You have exhausted your API Request Quota")
					.put("policy", plan.toString())
					.put("hint", quotaUrl)
					.get()
			)
		);

		return false;
    }
}
