package com.moewadeul.idempotency.application;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.error.UniqueViolations;
import com.moewadeul.common.transaction.OutsideTransaction;
import com.moewadeul.idempotency.domain.IdempotencyRecord;
import com.moewadeul.idempotency.domain.IdempotencyRecordId;
import com.moewadeul.idempotency.domain.IdempotencyRecordRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
public class IdempotencyExecutor {

    private static final String PRIMARY_KEY = "PRIMARY";
    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final IdempotencyRecordRepository recordRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public IdempotencyExecutor(
            IdempotencyRecordRepository recordRepository,
            JsonMapper jsonMapper,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.recordRepository = recordRepository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public <T> T execute(
            IdempotentRequest request, HttpStatus successStatus, Class<T> responseType, Supplier<T> action) {
        return execute(request, successStatus, responseType, () -> {}, action);
    }

    public <T> T execute(
            IdempotentRequest request,
            HttpStatus successStatus,
            Class<T> responseType,
            Runnable precheckOutsideTransaction,
            Supplier<T> action) {
        OutsideTransaction.require("멱등성 기록은 작업과 따로 커밋해야 한다.");
        String key = validateKey(request.key());
        String requestHash = hash(request);

        IdempotencyRecord record = IdempotencyRecord.claim(request.memberId(), key, requestHash, Instant.now(clock));
        if (!tryClaim(record)) {
            Optional<T> replayed = replayExisting(request.memberId(), key, requestHash, responseType);
            if (replayed.isPresent()) {
                return replayed.get();
            }
            record = IdempotencyRecord.claim(request.memberId(), key, requestHash, Instant.now(clock));
            if (!tryClaim(record)) {
                throw new BusinessException(CommonErrorCode.IDEMPOTENCY_IN_PROGRESS);
            }
        }

        return runAndComplete(record, successStatus, request.memberId(), precheckOutsideTransaction, action);
    }

    private String validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(CommonErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "멱등성 키는 영문, 숫자, '-', '_'로 이루어진 1~64자여야 해요.");
        }
        return key;
    }

    private String hash(IdempotentRequest request) {
        String source = request.operation() + "\n" + payloadJson(request.payload());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    private String payloadJson(Object payload) {
        if (payload == null) {
            return "";
        }
        try {
            return jsonMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(
                    "요청 본문을 JSON으로 바꿀 수 없습니다. 본문 타입=" + payload.getClass().getName(), e);
        }
    }

    private boolean tryClaim(IdempotencyRecord record) {
        try {
            recordRepository.saveAndFlush(record);
            return true;
        } catch (DataIntegrityViolationException e) {
            if (UniqueViolations.violates(e, PRIMARY_KEY)) {
                return false;
            }
            throw e;
        }
    }

    private <T> Optional<T> replayExisting(long memberId, String key, String requestHash, Class<T> responseType) {
        Optional<IdempotencyRecord> existing = recordRepository.findById(new IdempotencyRecordId(memberId, key));
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        IdempotencyRecord found = existing.get();
        if (!found.matches(requestHash)) {
            throw new BusinessException(CommonErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        if (!found.isCompleted()) {
            throw new BusinessException(CommonErrorCode.IDEMPOTENCY_IN_PROGRESS);
        }
        try {
            return Optional.of(jsonMapper.readValue(found.getResponseBody(), responseType));
        } catch (JacksonException e) {
            throw new IllegalStateException("저장된 멱등성 응답을 읽을 수 없습니다. 응답 타입=" + responseType.getName(), e);
        }
    }

    private <T> T runAndComplete(
            IdempotencyRecord record,
            HttpStatus successStatus,
            long memberId,
            Runnable precheckOutsideTransaction,
            Supplier<T> action) {
        try {
            precheckOutsideTransaction.run();
            return transactionTemplate.execute(status -> {
                T result = action.get();
                recordCompletion(record, successStatus, result, memberId);
                return result;
            });
        } catch (RuntimeException e) {
            releaseClaim(record, e);
            throw e;
        }
    }

    private void releaseClaim(IdempotencyRecord record, RuntimeException original) {
        try {
            recordRepository.deleteById(record.getId());
        } catch (RuntimeException deleteFailure) {
            original.addSuppressed(deleteFailure);
        }
    }

    private <T> void recordCompletion(IdempotencyRecord record, HttpStatus successStatus, T result, long memberId) {
        try {
            String body = jsonMapper.writeValueAsString(result);
            recordRepository.save(record.complete(successStatus.value(), body));
        } catch (RuntimeException e) {
            log.error(
                    "idempotency completion record failed memberId={} errorType={}",
                    memberId,
                    e.getClass().getName());
            throw e;
        }
    }
}
