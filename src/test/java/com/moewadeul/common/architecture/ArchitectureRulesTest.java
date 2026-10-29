package com.moewadeul.common.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTest {

    private static final String FIXTURE_PACKAGE = "com.moewadeul.common.architecture.fixture";
    private static final String COMPLIANT = "compliant";

    private static JavaClasses fixtureClasses;

    private enum Rule {
        MODULE_DEPENDENCY(ArchitectureRules::moduleDependencyRule),
        CROSS_MODULE_ACCESS(ArchitectureRules::crossModuleAccessRule),
        DOMAIN_INDEPENDENCE(ArchitectureRules::domainIndependenceRule),
        API_PERSISTENCE(ArchitectureRules::apiPersistenceRule),
        TRANSACTIONAL_PLACEMENT(ArchitectureRules::transactionalPlacementRule),
        APPLIER_VISIBILITY(ArchitectureRules::applierVisibilityRule),
        SCHEDULED_JOB_RUNNER(ArchitectureRules::scheduledJobsRunThroughRunnerRule);

        private final Function<String, ArchRule> factory;

        Rule(Function<String, ArchRule> factory) {
            this.factory = factory;
        }

        void check(String subRoot) {
            factory.apply(FIXTURE_PACKAGE + "." + subRoot).check(fixtureClasses);
        }
    }

    @BeforeAll
    static void importFixtureClasses() {
        fixtureClasses = new ClassFileImporter().importPackages(FIXTURE_PACKAGE);
    }

    @Test
    @DisplayName(
            "[ADR-005] 허용된 모듈 방향, application 경유, 계층 분리, application의 @Transactional, package-private Applier는 모든 규칙을 통과한다")
    void compliantStructurePassesAllRules() {
        for (Rule rule : Rule.values()) {
            assertThatCode(() -> rule.check(COMPLIANT)).as(rule.name()).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("[ADR-005] 허용 목록에 없는 모듈 방향의 의존은 R1 위반이다")
    void moduleDependencyRuleRejectsForbiddenDirection() {
        assertOnlyRuleFails(
                Rule.MODULE_DEPENDENCY,
                "violation.r1.direction",
                "member.application.MemberService",
                "basecamp.application.CampService");
    }

    @Test
    @DisplayName("[ADR-005] common이 도메인 모듈에 의존하면 R1 위반이다")
    void moduleDependencyRuleRejectsCommonDependingOnModule() {
        assertOnlyRuleFails(
                Rule.MODULE_DEPENDENCY,
                "violation.r1.common",
                "common.support.Util",
                "member.application.MemberService");
    }

    @Test
    @DisplayName("[ADR-005] 등록되지 않은 모듈의 클래스는 R1 위반이다")
    void moduleDependencyRuleRejectsUnregisteredModule() {
        assertOnlyRuleFails(Rule.MODULE_DEPENDENCY, "violation.r1.unregistered", "billing.application.InvoiceService");
    }

    @Test
    @DisplayName("[ADR-005] 다른 모듈의 domain 클래스를 쓰면 R2 위반이다")
    void crossModuleAccessRuleRejectsOtherModuleDomain() {
        assertOnlyRuleFails(
                Rule.CROSS_MODULE_ACCESS,
                "violation.r2.internal",
                "review.application.ReviewService",
                "spot.domain.Spot");
    }

    @Test
    @DisplayName("[ADR-005] domain이 같은 모듈의 api·application·infra에 의존하면 R3 위반이다")
    void domainIndependenceRuleRejectsOutwardDependency() {
        assertOnlyRuleFails(
                Rule.DOMAIN_INDEPENDENCE,
                "violation.r3.outward",
                "member.domain.Member",
                "member.infra.MemberPersistence",
                "member.domain.Address",
                "member.api.MemberController",
                "member.domain.Profile",
                "member.application.MemberService");
    }

    @Test
    @DisplayName("[ADR-005] api가 infra 패키지를 쓰면 R4 위반이다")
    void apiPersistenceRuleRejectsInfraDependency() {
        assertOnlyRuleFails(
                Rule.API_PERSISTENCE,
                "violation.r4.infra",
                "member.api.MemberController",
                "member.infra.MemberPersistence");
    }

    @Test
    @DisplayName("[ADR-005] api가 Repository 이름이나 Spring Data 리포지토리를 쓰면 R4 위반이다")
    void apiPersistenceRuleRejectsRepositoryDependency() {
        assertOnlyRuleFails(
                Rule.API_PERSISTENCE,
                "violation.r4.repository",
                "member.api.MemberController",
                "member.domain.MemberRepository",
                "member.api.ProfileController",
                "member.domain.ProfileStore");
    }

    @Test
    @DisplayName("[ADR-005] application 밖의 클래스·메서드·메타 애너테이션 @Transactional은 R5 위반이다")
    void transactionalPlacementRuleRejectsTransactionalOutsideApplication() {
        assertOnlyRuleFails(
                Rule.TRANSACTIONAL_PLACEMENT,
                "violation.r5.misplaced",
                "member.domain.Member",
                "member.infra.MemberPersistence.save",
                "member.api.MemberController");
    }

    @Test
    @DisplayName("[ADR-005] public *Applier 는 R6 위반이다")
    void applierVisibilityRuleRejectsPublicApplier() {
        assertOnlyRuleFails(Rule.APPLIER_VISIBILITY, "violation.r6.exposed", "member.application.MemberApplier");
    }

    @Test
    @DisplayName("[ADR-012] ScheduledJobRunner 를 거치지 않는 @Scheduled 메서드는 R7 위반이다")
    void scheduledJobRuleRejectsSchedulerBypassingRunner() {
        assertOnlyRuleFails(
                Rule.SCHEDULED_JOB_RUNNER, "violation.r7.bypass", "member.application.MemberCleanupScheduler.cleanUp");
    }

    private static void assertOnlyRuleFails(Rule failing, String subRoot, String... expectedFixtures) {
        for (Rule rule : Rule.values()) {
            if (rule != failing) {
                assertThatCode(() -> rule.check(subRoot)).as(rule.name()).doesNotThrowAnyException();
            }
        }
        var thrown = assertThatThrownBy(() -> failing.check(subRoot)).isInstanceOf(AssertionError.class);
        for (String fixture : expectedFixtures) {
            thrown.hasMessageContaining(FIXTURE_PACKAGE + "." + subRoot + "." + fixture);
        }
    }
}
