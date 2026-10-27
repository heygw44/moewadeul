package com.moewadeul.common.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
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
            COMMON);

    static final Map<String, Set<String>> ALLOWED_DEPENDENCIES = Map.of(
            "admin", Set.of("trust", "program", "spot", "member", "community", "memory"),
            "community", Set.of("spot"),
            "basecamp", Set.of("trust", "spot"),
            "program", Set.of("trust"),
            "review", Set.of("spot"),
            "trip", Set.of("spot"),
            "memory", Set.of("spot"),
            "spot", Set.of("weather"),
            "publicdata", Set.of("spot"),
            "trust", Set.of("member"));

    private static final String APPLICATION = "application";
    private static final String API = "api";
    private static final String DOMAIN = "domain";
    private static final String INFRA = "infra";

    private static final String SPRING_DATA_REPOSITORY = "org.springframework.data.repository.Repository";
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
                            .filter(t -> t.module().equals(source.module()))
                            .filter(t ->
                                    API.equals(t.layer()) || APPLICATION.equals(t.layer()) || INFRA.equals(t.layer()))
                            .map(t -> "domain 은(는) 같은 모듈의 " + t.layer() + " 에 의존할 수 없다");
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

    public static ArchRule transactionalPlacementRule(String rootPackage) {
        Scope scope = new Scope(rootPackage);
        return scope.moduleClasses()
                .should(transactionalOnlyInApplication(scope))
                .as("@Transactional 은 application 패키지에만 둔다")
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
            return !COMMON.equals(module) && APPLICATION.equals(layer);
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
            boolean layered = !COMMON.equals(module) && segments.length > 1;
            return Optional.of(new Location(module, layered ? segments[1] : ""));
        }

        GivenClassesConjunction moduleClasses() {
            return classes()
                    .that(DescribedPredicate.describe(
                            "are located in a module under " + rootPackage,
                            javaClass -> locate(javaClass).isPresent()));
        }
    }
}
