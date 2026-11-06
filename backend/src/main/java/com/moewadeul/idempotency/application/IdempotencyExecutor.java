package com.moewadeul.idempotency.application;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.error.UniqueViolations;
import com.moewadeul.common.transaction.OutsideTransaction;
import com.moewadeul.idempotency.domain.IdempotencyRecord;
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
import org.springframework.transaction.TransactionDefinition;
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
    private final TransactionTemplate readCommittedTemplate;

    public IdempotencyExecutor(
            IdempotencyRecordRepository recordRepository,
            JsonMapper jsonMapper,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.recordRepository = recordRepository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.readCommittedTemplate = new TransactionTemplate(transactionManager);
        this.readCommittedTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
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
        return claimAndRun(
                request, successStatus, responseType, precheckOutsideTransaction, transactionTemplate, action);
    }

    public <T> T executeReadCommitted(
            IdempotentRequest request, HttpStatus successStatus, Class<T> responseType, Supplier<T> action) {
        return claimAndRun(request, successStatus, responseType, () -> {}, readCommittedTemplate, action);
    }

    private <T> T claimAndRun(
            IdempotentRequest request,
            HttpStatus successStatus,
            Class<T> responseType,
            Runnable precheckOutsideTransaction,
            TransactionTemplate template,
            Supplier<T> action) {
        OutsideTransaction.require("멱등성 기록은 작업과 따로 커밋해야 한다.");
        String key = validateKey(request.key());
        String requestHash = hash(request);

        IdempotencyRecord record = IdempotencyRecord.claim(request.memberId(), key, requestHash, Instant.now(clock));
        if (!tryClaim(record)) {
            Optional<IdempotencyRecord> existing = recordRepository.findById(record.getId());
            if (existing.isEmpty()) {
                record = IdempotencyRecord.claim(request.memberId(), key, requestHash, Instant.now(clock));
                if (!tryClaim(record)) {
                    throw new BusinessException(CommonErrorCode.IDEMPOTENCY_IN_PROGRESS);
                }
            } else {
                IdempotencyRecord found = existing.get();
                if (!found.matches(requestHash)) {
                    throw new BusinessException(CommonErrorCode.IDEMPOTENCY_KEY_REUSED);
                }
                if (found.isCompleted()) {
                    return replay(found, responseType);
                }
                record = takeOver(found);
            }
        }
        return runAndComplete(record, successStatus, precheckOutsideTransaction, template, action);
    }

    private IdempotencyRecord takeOver(IdempotencyRecord found) {
        Instant now = Instant.now(clock);
        if (!found.isAbandonedAt(now)) {
            throw new BusinessException(CommonErrorCode.IDEMPOTENCY_IN_PROGRESS);
        }
        Instant abandonedClaim = found.getCreatedAt();
        IdempotencyRecord takenOver = found.takenOverAt(now);
        Integer updated = transactionTemplate.execute(status -> recordRepository.takeOverClaim(
                takenOver.getMemberId(), takenOver.getIdempotencyKey(), abandonedClaim, takenOver.getCreatedAt()));
        if (updated == null || updated == 0) {
            throw new BusinessException(CommonErrorCode.IDEMPOTENCY_IN_PROGRESS);
        }
        log.warn("idempotency claim taken over memberId={} abandonedClaim={}", takenOver.getMemberId(), abandonedClaim);
        return takenOver;
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

    private <T> T replay(IdempotencyRecord completed, Class<T> responseType) {
        try {
            return jsonMapper.readValue(completed.getResponseBody(), responseType);
        } catch (JacksonException e) {
            throw new IllegalStateException("저장된 멱등성 응답을 읽을 수 없습니다. 응답 타입=" + responseType.getName(), e);
        }
    }

    private <T> T runAndComplete(
            IdempotencyRecord record,
            HttpStatus successStatus,
            Runnable precheckOutsideTransaction,
            TransactionTemplate template,
            Supplier<T> action) {
        try {
            precheckOutsideTransaction.run();
            return template.execute(status -> {
                T result = action.get();
                recordCompletion(record, successStatus, result);
                return result;
            });
        } catch (RuntimeException e) {
            releaseClaim(record, e);
            throw e;
        }
    }

    private void releaseClaim(IdempotencyRecord record, RuntimeException original) {
        try {
            transactionTemplate.execute(status -> recordRepository.releaseClaim(
                    record.getMemberId(), record.getIdempotencyKey(), record.getCreatedAt()));
        } catch (RuntimeException deleteFailure) {
            original.addSuppressed(deleteFailure);
        }
    }

    // 다른 요청이 선점을 넘겨받았으면 이 요청의 작업을 트랜잭션째 버린다. 그래서 느리게 살아 있던 요청이 있어도 작업은 한 번만 커밋된다.
    // 행이 없으면 넘겨받은 요청이 실패해 선점을 지운 경우이고, 커밋된 작업이 없으므로 이 요청이 완료해도 된다.
    private <T> void recordCompletion(IdempotencyRecord record, HttpStatus successStatus, T result) {
        try {
            recordRepository.findByIdForUpdate(record.getId()).ifPresent(current -> {
                if (!current.isSameClaim(record)) {
                    throw new BusinessException(CommonErrorCode.IDEMPOTENCY_IN_PROGRESS);
                }
            });
            String body = jsonMapper.writeValueAsString(result);
            recordRepository.save(record.complete(successStatus.value(), body));
        } catch (RuntimeException e) {
            log.error(
                    "idempotency completion record failed memberId={} errorType={}",
                    record.getMemberId(),
                    e.getClass().getName());
            throw e;
        }
    }
}
