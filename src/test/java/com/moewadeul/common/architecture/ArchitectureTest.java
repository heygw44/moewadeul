package com.moewadeul.common.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.beans.Introspector;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    @DisplayName("[ADR-009] application 패키지는 *JpaRepository를 직접 쓰지 않는다")
    void applicationDoesNotUseJpaRepositoryDirectly() {
        ArchitectureRules.applicationJpaRepositoryRule(ROOT_PACKAGE).check(productionClasses);
    }

    @Test
    @DisplayName("[ADR-009] domain 패키지에는 저장소가 아닌 외부 포트를 두지 않는다")
    void domainHoldsOnlyRepositoryPorts() {
        ArchitectureRules.domainPortRule(ROOT_PACKAGE).check(productionClasses);
    }

    @Test
    @DisplayName("[ADR-005] api 패키지는 common.storage·common.mail 을 직접 쓰지 않는다")
    void apiDoesNotUseExternalAdaptersDirectly() {
        ArchitectureRules.apiExternalAdapterRule(ROOT_PACKAGE).check(productionClasses);
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

    @Test
    @DisplayName("[ADR-012] ScheduledTaskRegistrar 로 작업을 등록하는 클래스는 ScheduledJobRunner를 거친다")
    void registrarTasksRunThroughJobRunner() {
        ArchitectureRules.scheduledTaskRegistrarUsesRunnerRule(ROOT_PACKAGE).check(productionClasses);
    }

    @Test
    @DisplayName("[ADR-005] 운영 코드는 ClockConfig 밖에서 시스템 시계를 직접 읽지 않는다")
    void productionCodeDoesNotReadSystemClock() {
        ArchitectureRules.noImplicitSystemClockRule(ROOT_PACKAGE).check(productionClasses);
    }

    // 조건부 빈은 테스트 프로필에서 꺼져 있어 컨텍스트가 이름 충돌을 보지 못한다. 그래서 조건과 상관없이 이름만 비교한다.
    @Test
    @DisplayName("스캔되는 빈은 조건과 상관없이 빈 이름이 겹치지 않는다")
    void scannedBeanNamesAreUnique() {
        Map<String, List<String>> classesByBeanName = productionClasses.stream()
                .filter(ArchitectureTest::isScannedBean)
                .collect(Collectors.groupingBy(
                        ArchitectureTest::beanName, Collectors.mapping(JavaClass::getName, Collectors.toList())));

        assertThat(classesByBeanName)
                .allSatisfy((name, classes) ->
                        assertThat(classes).as("빈 이름 %s", name).hasSize(1));
    }

    private static boolean isScannedBean(JavaClass javaClass) {
        if (javaClass.isInterface()) {
            return javaClass.isAnnotatedWith("org.apache.ibatis.annotations.Mapper")
                    || javaClass.isAssignableTo("org.springframework.data.repository.Repository");
        }
        return !javaClass.isAnnotation() && javaClass.isMetaAnnotatedWith("org.springframework.stereotype.Component");
    }

    private static String beanName(JavaClass javaClass) {
        return javaClass.getAnnotations().stream()
                .filter(annotation ->
                        annotation.getRawType().isMetaAnnotatedWith("org.springframework.stereotype.Component")
                                || annotation.getRawType().getName().equals("org.springframework.stereotype.Component"))
                .map(ArchitectureTest::explicitName)
                .filter(name -> !name.isEmpty())
                .findFirst()
                .orElseGet(() -> Introspector.decapitalize(javaClass.getSimpleName()));
    }

    private static String explicitName(JavaAnnotation<?> annotation) {
        return annotation
                .get("value")
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .orElse("");
    }
}
