package com.moewadeul.common.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvent;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.elements.GivenClassesConjunction;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ArchitectureRules {

    static final String COMMON = "common";

    static final List<String> MODULES = List.of(
            "member",
            "trust",
            "spot",
            "publicdata",
            "weather",
            "review",
            "basecamp",
            "program",
            "notification",
            "outbox",
            "community",
            "trip",
            "memory",
            "aisearch",
            "admin",
            "idempotency",
            COMMON);

    static final Map<String, Set<String>> ALLOWED_DEPENDENCIES = Map.of(
            "admin", Set.of("trust", "program", "spot", "community", "memory"),
            "community", Set.of("spot", "idempotency"),
            "basecamp", Set.of("trust", "spot", "idempotency"),
            "program", Set.of("trust", "idempotency"),
            "review", Set.of("spot"),
            "trip", Set.of("spot", "idempotency"),
            "memory", Set.of("spot", "idempotency"),
            "spot", Set.of("weather"),
            "publicdata", Set.of("spot"),
            "trust", Set.of("member"));

    private static final String APPLIER_SUFFIX = "Applier";

    private static final String APPLICATION = "application";
    private static final String API = "api";
    private static final String DOMAIN = "domain";
    private static final String INFRA = "infra";
    private static final Set<String> LAYERS = Set.of(API, APPLICATION, DOMAIN, INFRA);

    private static final String SCHEDULED = "org.springframework.scheduling.annotation.Scheduled";
    private static final String SCHEDULES = "org.springframework.scheduling.annotation.Schedules";
    private static final String SCHEDULED_JOB_RUNNER = "com.moewadeul.common.scheduling.ScheduledJobRunner";
    private static final Set<String> JOB_RUNNER_ENTRY_METHODS = Set.of("run", "runCountingFailures");

    private static final String SERVICE_SUFFIX = "Service";
    private static final String OUTSIDE_TRANSACTION = "com.moewadeul.common.transaction.OutsideTransaction";
    private static final String OUTSIDE_TRANSACTION_GUARD = "require";

    private static final String SPRING_DATA_REPOSITORY = "org.springframework.data.repository.Repository";
    private static final String JPA_REPOSITORY_SUFFIX = "JpaRepository";
    private static final String DOMAIN_PORT_SUFFIX = "Repository";
    private static final List<String> EXTERNAL_ADAPTER_PACKAGES = List.of("storage", "mail");
    private static final List<String> PERSISTENCE_NAME_SUFFIXES = List.of("Repository", "Mapper");
    private static final List<String> TRANSACTIONAL_TYPES =
            List.of("org.springframework.transaction.annotation.Transactional", "jakarta.transaction.Transactional");

    private ArchitectureRules() {}

    public static ArchRule moduleDependencyRule(String rootPackage) {
        Scope scope = new Scope(rootPackage);
        return scope.moduleClasses()
                .should(registeredModule(scope))
                .andShould(noDependency(scope, "follow the allowed module dependency directions", (source, dep) -> {
                    Optional<Location> target = scope.locate(dep.getTargetClass());
                    return target.filter(t -> !isAllowedDirection(source.module(), t.module()))
                            .map(t -> "모듈 " + source.module() + " 은(는) 모듈 " + t.module() + " 에 의존할 수 없다");
                }))
                .as("모듈은 허용된 방향으로만 의존한다")
                .allowEmptyShould(true);
    }

    public static ArchRule crossModuleAccessRule(String rootPackage) {
        Scope scope = new Scope(rootPackage);
        return scope.moduleClasses()
                .should(noDependency(
                        scope,
                        "access other modules only through application",
                        (source, dep) -> scope.locate(dep.getTargetClass())
                                .filter(t -> !t.module().equals(source.module()))
                                .filter(t -> !COMMON.equals(t.module()))
                                .filter(t -> !APPLICATION.equals(t.layer()))
                                .map(t -> "모듈 " + t.module() + " 은(는) application 패키지로만 접근할 수 있다(대상 계층: "
                                        + (t.layer().isEmpty() ? "없음" : t.layer()) + ")")))
                .as("다른 모듈은 application 패키지로만 접근한다")
                .allowEmptyShould(true);
    }

    public static ArchRule domainIndependenceRule(String rootPackage) {
        Scope scope = new Scope(rootPackage);
        return scope.moduleClasses()
                .should(noDependency(scope, "keep domain independent of api, application and infra", (source, dep) -> {
                    if (!DOMAIN.equals(source.layer())) {
                        return Optional.empty();
                    }
                    return scope.locate(dep.getTargetClass())
                            .filter(t -> t.module().equals(source.module()) || COMMON.equals(t.module()))
                            .filter(t ->
                                    API.equals(t.layer()) || APPLICATION.equals(t.layer()) || INFRA.equals(t.layer()))
                            .map(t -> "domain 은(는) " + t.module() + " 의 " + t.layer() + " 에 의존할 수 없다");
                }))
                .as("domain 패키지는 api·application·infra 패키지에 의존하지 않는다")
                .allowEmptyShould(true);
    }

    public static ArchRule apiPersistenceRule(String rootPackage) {
        Scope scope = new Scope(rootPackage);
        return scope.moduleClasses()
                .should(noDependency(scope, "keep api away from persistence", (source, dep) -> {
                    if (!API.equals(source.layer())) {
                        return Optional.empty();
                    }
                    return persistenceReason(scope, dep.getTargetClass());
                }))
                .as("api 패키지는 영속 계층을 직접 쓰지 않는다")
                .allowEmptyShould(true);
    }

    public static ArchRule applicationJpaRepositoryRule(String rootPackage) {
        Scope scope = new Scope(rootPackage);
        return scope.moduleClasses()
                .should(noDependency(scope, "keep application away from JpaRepository", (source, dep) -> {
                    if (!APPLICATION.equals(source.layer())) {
                        return Optional.empty();
                    }
                    JavaClass target = dep.getTargetClass();
                    if (target.getSimpleName().endsWith(JPA_REPOSITORY_SUFFIX)
                            || target.isAssignableTo(SPRING_DATA_REPOSITORY)) {
                        return Optional.of("application 은(는) *JpaRepository 를 직접 쓸 수 없다(domain 저장소 포트를 거친다)");
                    }
                    return Optional.empty();
                }))
                .as("application 패키지는 *JpaRepository를 직접 쓰지 않는다")
                .allowEmptyShould(true);
    }

    public static ArchRule domainPortRule(String rootPackage) {
        Scope scope = new Scope(rootPackage);
        return scope.moduleClasses()
                .should(domainPortsAreRepositories(scope))
                .as("domain 패키지에는 저장소가 아닌 외부 포트를 두지 않는다")
                .allowEmptyShould(true);
    }

    public static ArchRule apiExternalAdapterRule(String rootPackage) {
        Scope scope = new Scope(rootPackage);
        return scope.moduleClasses()
                .should(noDependency(scope, "keep api away from external adapters", (source, dep) -> {
                    if (!API.equals(source.layer())) {
                        return Optional.empty();
                    }
                    String packageName = dep.getTargetClass().getPackageName();
                    return EXTERNAL_ADAPTER_PACKAGES.stream()
                            .map(name -> rootPackage + "." + COMMON + "." + name)
                            .filter(prefix -> packageName.equals(prefix) || packageName.startsWith(prefix + "."))
                            .findFirst()
                            .map(prefix -> "api 은(는) " + prefix + " 패키지를 쓸 수 없다(application 결과로 받는다)");
                }))
                .as("api 패키지는 common.storage·common.mail 을 직접 쓰지 않는다")
                .allowEmptyShould(true);
    }

    public static ArchRule transactionalPlacementRule(String rootPackage) {
        Scope scope = new Scope(rootPackage);
        return scope.moduleClasses()
                .should(transactionalOnlyInApplication(scope))
                .as("@Transactional 은 application 패키지에만 둔다")
                .allowEmptyShould(true);
    }

    public static ArchRule applierVisibilityRule(String rootPackage) {
        Scope scope = new Scope(rootPackage);
        return scope.moduleClasses()
                .should(applierNotPublic())
                .as("*Applier 는 package-private 이다")
                .allowEmptyShould(true);
    }

    public static ArchRule applierCallerRule(String rootPackage) {
        Scope scope = new Scope(rootPackage);
        return scope.moduleClasses()
                .should(appliersCalledOnlyFromGuardedServices())
                .as("*Applier 는 OutsideTransaction 가드를 둔 같은 패키지의 *Service 만 부른다")
                .allowEmptyShould(true);
    }

    public static ArchRule scheduledJobsRunThroughRunnerRule(String rootPackage) {
        Scope scope = new Scope(rootPackage);
        return scope.moduleClasses()
                .should(scheduledMethodsUseJobRunner())
                .as("@Scheduled 메서드는 ScheduledJobRunner 를 거쳐 실패 수를 센다")
                .allowEmptyShould(true);
    }

    private static boolean isAllowedDirection(String source, String target) {
        if (source.equals(target) || COMMON.equals(target)) {
            return true;
        }
        return ALLOWED_DEPENDENCIES.getOrDefault(source, Set.of()).contains(target);
    }

    private static Optional<String> persistenceReason(Scope scope, JavaClass target) {
        Optional<Location> location = scope.locate(target);
        if (location.isPresent() && INFRA.equals(location.get().layer())) {
            return Optional.of("api 은(는) infra 패키지의 클래스를 쓸 수 없다");
        }
        if (location.isPresent() && hasPersistenceName(target)) {
            return Optional.of("api 은(는) Repository·Mapper 클래스를 쓸 수 없다");
        }
        if (target.isAssignableTo(SPRING_DATA_REPOSITORY)) {
            return Optional.of("api 은(는) Spring Data 리포지토리를 쓸 수 없다");
        }
        return Optional.empty();
    }

    private static boolean hasPersistenceName(JavaClass target) {
        return PERSISTENCE_NAME_SUFFIXES.stream()
                .anyMatch(suffix -> target.getSimpleName().endsWith(suffix));
    }

    private static ArchCondition<JavaClass> registeredModule(Scope scope) {
        return new ArchCondition<>("belong to a registered module") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                scope.locate(item)
                        .filter(location -> !MODULES.contains(location.module()))
                        .ifPresent(location -> events.add(SimpleConditionEvent.violated(
                                item,
                                "등록되지 않은 모듈 " + location.module() + " 의 클래스 " + item.getName()
                                        + " (ArchitectureRules.MODULES 에 등록해야 한다)")));
            }
        };
    }

    private static ArchCondition<JavaClass> noDependency(Scope scope, String description, DependencyJudge judge) {
        return new ArchCondition<>(description) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                Optional<Location> source = scope.locate(item);
                if (source.isEmpty()) {
                    return;
                }
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    judge.violation(source.get(), dependency)
                            .ifPresent(reason -> events.add(SimpleConditionEvent.violated(
                                    dependency, reason + ": " + dependency.getDescription())));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> domainPortsAreRepositories(Scope scope) {
        return new ArchCondition<>("name every domain interface implemented by an adapter *Repository") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                boolean domainInterface = item.isInterface()
                        && scope.locate(item).map(l -> DOMAIN.equals(l.layer())).orElse(false);
                if (!domainInterface || item.getSimpleName().endsWith(DOMAIN_PORT_SUFFIX)) {
                    return;
                }
                item.getAllSubclasses().stream()
                        .filter(sub ->
                                scope.locate(sub).map(Location::isAdapter).orElse(false))
                        .findFirst()
                        .ifPresent(sub -> events.add(SimpleConditionEvent.violated(
                                item,
                                "domain 인터페이스 " + item.getName() + " 을(를) 어댑터 " + sub.getName()
                                        + " 이(가) 구현한다. 저장소가 아닌 포트는 application 에 두고,"
                                        + " 여러 모듈이 쓰면 common.<패키지>.application 에 둔다")));
            }
        };
    }

    private static ArchCondition<JavaClass> scheduledMethodsUseJobRunner() {
        return new ArchCondition<>("call ScheduledJobRunner from every @Scheduled method") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (JavaMethod method : item.getMethods()) {
                    boolean scheduled = method.isAnnotatedWith(SCHEDULED) || method.isAnnotatedWith(SCHEDULES);
                    boolean viaRunner = method.getMethodCallsFromSelf().stream()
                            .anyMatch(call -> call.getTargetOwner().getName().equals(SCHEDULED_JOB_RUNNER)
                                    && JOB_RUNNER_ENTRY_METHODS.contains(call.getName()));
                    if (scheduled && !viaRunner) {
                        events.add(SimpleConditionEvent.violated(
                                method,
                                "@Scheduled 메서드가 ScheduledJobRunner 를 거치지 않아 실패가 moewadeul.scheduled.job.failures 에 잡히지 않는다: "
                                        + method.getFullName()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> applierNotPublic() {
        return new ArchCondition<>("not be public when named *Applier") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                boolean applier = item.getSimpleName().endsWith(APPLIER_SUFFIX);
                if (applier && item.getModifiers().contains(JavaModifier.PUBLIC)) {
                    events.add(SimpleConditionEvent.violated(
                            item, "public Applier 는 서비스의 트랜잭션 밖 가드를 우회하게 한다: " + item.getName()));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> appliersCalledOnlyFromGuardedServices() {
        return new ArchCondition<>("be called only from a guarded *Service when named *Applier") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (!item.getSimpleName().endsWith(APPLIER_SUFFIX)) {
                    return;
                }
                for (JavaAccess<?> access : item.getAccessesToSelf()) {
                    JavaClass owner = access.getOriginOwner();
                    if (owner.equals(item) || isGuardedPairService(item, owner)) {
                        continue;
                    }
                    events.add(SimpleConditionEvent.violated(
                            access, "*Applier 는 트랜잭션 밖을 확인하는 같은 패키지의 *Service 에서만 부른다: " + access.getDescription()));
                }
            }
        };
    }

    private static boolean isGuardedPairService(JavaClass applier, JavaClass owner) {
        return owner.getPackageName().equals(applier.getPackageName())
                && owner.getSimpleName().endsWith(SERVICE_SUFFIX)
                && callsOutsideTransactionGuard(owner);
    }

    private static boolean callsOutsideTransactionGuard(JavaClass owner) {
        return owner.getMethodCallsFromSelf().stream()
                .anyMatch(call -> call.getTargetOwner().getName().equals(OUTSIDE_TRANSACTION)
                        && call.getName().equals(OUTSIDE_TRANSACTION_GUARD));
    }

    private static ArchCondition<JavaClass> transactionalOnlyInApplication(Scope scope) {
        return new ArchCondition<>("use @Transactional only in application") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                boolean inApplication =
                        scope.locate(item).map(Location::isApplication).orElse(true);
                if (inApplication) {
                    return;
                }
                if (isTransactional(item)) {
                    events.add(violation(item, item.getName()));
                }
                item.getCodeUnits().stream()
                        .filter(ArchitectureRules::isTransactional)
                        .forEach(unit -> events.add(violation(item, unit.getFullName())));
            }

            private ConditionEvent violation(JavaClass item, String where) {
                return SimpleConditionEvent.violated(
                        item, "application 패키지 밖에서 @Transactional 을 쓴다: " + where + " (" + item.getName() + ")");
            }
        };
    }

    private static boolean isTransactional(CanBeAnnotated element) {
        return TRANSACTIONAL_TYPES.stream()
                .anyMatch(type -> element.isAnnotatedWith(type) || element.isMetaAnnotatedWith(type));
    }

    @FunctionalInterface
    private interface DependencyJudge {

        Optional<String> violation(Location source, Dependency dependency);
    }

    private record Location(String module, String layer) {

        boolean isApplication() {
            return APPLICATION.equals(layer);
        }

        boolean isAdapter() {
            return INFRA.equals(layer) || (COMMON.equals(module) && layer.isEmpty());
        }
    }

    private record Scope(String rootPackage) {

        Optional<Location> locate(JavaClass javaClass) {
            String prefix = rootPackage + ".";
            String packageName = javaClass.getPackageName();
            if (!packageName.startsWith(prefix)) {
                return Optional.empty();
            }
            String[] segments = packageName.substring(prefix.length()).split("\\.");
            String module = segments[0];
            if (!COMMON.equals(module)) {
                return Optional.of(new Location(module, segments.length > 1 ? segments[1] : ""));
            }
            boolean layered = segments.length > 2 && LAYERS.contains(segments[2]);
            return Optional.of(new Location(module, layered ? segments[2] : ""));
        }

        GivenClassesConjunction moduleClasses() {
            return classes()
                    .that(DescribedPredicate.describe(
                            "are located in a module under " + rootPackage,
                            javaClass -> locate(javaClass).isPresent()));
        }
    }
}
