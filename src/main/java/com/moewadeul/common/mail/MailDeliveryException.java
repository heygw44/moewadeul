package com.moewadeul.common.mail;

public class MailDeliveryException extends RuntimeException {

    private final boolean transientFailure;

    public MailDeliveryException(String message) {
        this(message, null, false);
    }

    public MailDeliveryException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public MailDeliveryException(String message, Throwable cause, boolean transientFailure) {
        super(message, cause);
        this.transientFailure = transientFailure;
    }

    public boolean isTransientFailure() {
        return transientFailure;
    }

    public static boolean isTransient(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof MailDeliveryException delivery && delivery.isTransientFailure()) {
                return true;
            }
        }
        return false;
    }
}
