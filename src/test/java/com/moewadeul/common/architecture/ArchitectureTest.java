package com.moewadeul.common.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    private static final String ROOT_PACKAGE = "com.moewadeul";

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(ROOT_PACKAGE);
    }

    @Test
    @DisplayName("[ADR-005] 모듈은 허용된 방향으로만 의존한다")
    void modulesDependOnlyInAllowedDirections() {
        ArchitectureRules.moduleDependencyRule(ROOT_PACKAGE).check(productionClasses);
    }

    @Test
    @DisplayName("[ADR-005] 다른 모듈은 application 패키지로만 접근한다")
    void otherModulesAreAccessedOnlyThroughApplication() {
        ArchitectureRules.crossModuleAccessRule(ROOT_PACKAGE).check(productionClasses);
    }

    @Test
    @DisplayName("[ADR-005] domain 패키지는 api·application·infra 패키지에 의존하지 않는다")
    void domainDoesNotDependOnApiOrInfra() {
        ArchitectureRules.domainIndependenceRule(ROOT_PACKAGE).check(productionClasses);
    }

    @Test
    @DisplayName("[ADR-005] api 패키지는 영속 계층을 직접 쓰지 않는다")
    void apiDoesNotUsePersistenceDirectly() {
        ArchitectureRules.apiPersistenceRule(ROOT_PACKAGE).check(productionClasses);
    }

    @Test
    @DisplayName("[ADR-005] @Transactional 은 application 패키지에만 둔다")
    void transactionalIsOnlyInApplication() {
        ArchitectureRules.transactionalPlacementRule(ROOT_PACKAGE).check(productionClasses);
    }

    @Test
    @DisplayName("[ADR-005] *Applier 는 package-private 이라 짝 서비스를 거치지 않고 부를 수 없다")
    void appliersArePackagePrivate() {
        ArchitectureRules.applierVisibilityRule(ROOT_PACKAGE).check(productionClasses);
    }

    @Test
    @DisplayName("[ADR-005] *Applier 는 OutsideTransaction 가드를 둔 같은 패키지의 *Service 만 부른다")
    void appliersAreCalledOnlyFromGuardedServices() {
        ArchitectureRules.applierCallerRule(ROOT_PACKAGE).check(productionClasses);
    }

    @Test
    @DisplayName("[ADR-012] @Scheduled 메서드는 모두 ScheduledJobRunner를 거친다")
    void scheduledMethodsRunThroughJobRunner() {
        ArchitectureRules.scheduledJobsRunThroughRunnerRule(ROOT_PACKAGE).check(productionClasses);
    }
}
