package com.moewadeul.common.mail;

/**
 * 메일을 보내지 못했을 때 던진다.
 *
 * <p>호출하는 쪽(예: 아웃박스 발행기)이 예외 메시지를 DB에 기록하므로, 메시지에는 수신자 주소나 본문을 넣지 않는다.
 * 원인 예외(cause)는 SMTP 서버의 응답 문장을 담고 있어 수신자 주소가 들어 있을 수 있다.
 * 그래서 원인은 이 예외에 연결만 해 두고, 로그에 원인의 메시지를 찍지 않는다.
 *
 * <p>메일 서버에 연결하지 못했거나 4xx 응답을 받은 실패는 {@link #isTransientFailure()}가 true다. 서버가 돌아오면 같은 메일이
 * 나갈 수 있으므로, 호출하는 쪽은 이 실패를 재시도 한도에 세지 않고 계속 다시 보낸다.
 */
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

    /**
     * 처리기가 이 예외를 다른 예외로 감싸 던져도 알아볼 수 있게, 원인 사슬을 따라가며 일시적인 메일 실패가 있는지 본다.
     *
     * @return 원인 사슬에 {@link #isTransientFailure()}가 true인 이 예외가 있으면 true
     */
    public static boolean isTransient(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof MailDeliveryException delivery && delivery.isTransientFailure()) {
                return true;
            }
        }
        return false;
    }
}
