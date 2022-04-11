package it.bz.idm.bdp.ninja.utils;

import java.util.ArrayList;
import java.util.List;

import org.keycloak.adapters.springsecurity.account.SimpleKeycloakAccount;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {

	public static final String ROLE_PREFIX = "BDP_";
	public static final String ROLE_ADMIN = "ADMIN";
	public static final String ROLE_GUEST = "GUEST";

	private SecurityUtils() {
		// This is just an utility class
	}

	public static List<String> getRolesFromAuthentication() {
		return getRolesFromAuthentication(SecurityContextHolder.getContext().getAuthentication());
	}

	public static List<String> getRolesFromAuthentication(Authentication auth) {
		List<String> result = new ArrayList<>();
		if (auth instanceof KeycloakAuthenticationToken) {
			SimpleKeycloakAccount user = (SimpleKeycloakAccount) auth.getDetails();
			for (String role : user.getRoles()) {
				if (role.startsWith(ROLE_PREFIX)) {
					String cleanName = role.replaceFirst(ROLE_PREFIX, "");
					if (cleanName.equals(ROLE_ADMIN)) {
						result.clear();
						result.add(ROLE_ADMIN);
						return result;
					} else {
						result.add(cleanName);
					}
				}
			}
		}

		if (result.isEmpty() || !result.contains(ROLE_GUEST)) {
			result.add(ROLE_GUEST);
		}
		return result;
	}

	public static String getSubjectFromAuthentication() {
		return getSubjectFromAuthentication(SecurityContextHolder.getContext().getAuthentication());
	}

	public static String getSubjectFromAuthentication(Authentication auth) {
		if (auth instanceof KeycloakAuthenticationToken) {
			SimpleKeycloakAccount user = (SimpleKeycloakAccount) auth.getDetails();
			return user.getPrincipal().getName();
		}

		return "[NOSUBJECT]";
	}
}
