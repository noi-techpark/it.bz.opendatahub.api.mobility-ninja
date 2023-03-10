package it.bz.idm.bdp.ninja.quota;

/**
 * This Exception has a special handler in
 * {@link it.bz.idm.bdp.ninja.config.ErrorResponseConfig}
 * 
 */
public class QuotaLimitException extends RuntimeException {
    public final String message;
    public final String policy;
    public final String hint;

    public QuotaLimitException(String message, String policy, String hint) {
        this.message = message;
        this.policy = policy;
        this.hint = hint;
    }
}
