package com.moewadeul.member.application.signup;

import com.moewadeul.common.audit.AdminAuditAction;
import com.moewadeul.common.audit.AdminAuditRecorder;
import com.moewadeul.common.audit.AdminAuditTargetType;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.UniqueViolations;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.signup.AdminDisposableEmailDomain;
import com.moewadeul.member.domain.signup.AdminDisposableEmailDomainRepository;
import com.moewadeul.member.domain.signup.DisposableEmailDomainRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminDisposableEmailDomainService {

    private static final String DOMAIN_UNIQUE_CONSTRAINT = "uk_admin_disposable_email_domain_domain";

    private final AdminDisposableEmailDomainRepository adminDomainRepository;
    private final DisposableEmailDomainRepository publicDomainRepository;
    private final AdminAuditRecorder auditRecorder;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<AdminDisposableEmailDomain> list() {
        return adminDomainRepository.findAllByOrderByIdDesc();
    }

    @Transactional
    public AdminDisposableEmailDomain add(long adminId, String domain) {
        AdminDisposableEmailDomain added = AdminDisposableEmailDomain.add(domain, Instant.now(clock));
        if (publicDomainRepository.existsByDomainIn(List.of(added.getDomain()))
                || adminDomainRepository.existsByDomain(added.getDomain())) {
            throw new BusinessException(MemberErrorCode.MEMBER_DISPOSABLE_DOMAIN_ALREADY_BLOCKED);
        }
        AdminDisposableEmailDomain saved = saveOrTranslate(added);
        record(adminId, AdminAuditAction.DISPOSABLE_DOMAIN_ADD, saved);
        return saved;
    }

    @Transactional
    public void remove(long adminId, long id) {
        AdminDisposableEmailDomain domain = adminDomainRepository.findById(id).orElseThrow(BusinessException::notFound);
        adminDomainRepository.delete(domain);
        record(adminId, AdminAuditAction.DISPOSABLE_DOMAIN_DELETE, domain);
    }

    private AdminDisposableEmailDomain saveOrTranslate(AdminDisposableEmailDomain domain) {
        try {
            return adminDomainRepository.save(domain);
        } catch (DataIntegrityViolationException e) {
            if (UniqueViolations.violates(e, DOMAIN_UNIQUE_CONSTRAINT)) {
                throw new BusinessException(MemberErrorCode.MEMBER_DISPOSABLE_DOMAIN_ALREADY_BLOCKED);
            }
            throw e;
        }
    }

    private void record(long adminId, AdminAuditAction action, AdminDisposableEmailDomain domain) {
        auditRecorder.record(
                adminId,
                action,
                AdminAuditTargetType.DISPOSABLE_EMAIL_DOMAIN,
                domain.getId(),
                new DomainAuditDetail(domain.getDomain()));
    }

    private record DomainAuditDetail(String domain) {}
}
