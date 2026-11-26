package dev.rootline.kyc;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 守住 Clean Architecture 的依賴方向：
 * domain ← application ← (iface, infra)，iface 與 infra 互不依賴。
 */
@AnalyzeClasses(packages = "dev.rootline.kyc", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainIsPureJava = noClasses()
            .that().resideInAPackage("..application.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "org.hibernate..", "tools.jackson..",
                    "com.fasterxml..", "..application.port..", "..application.service..",
                    "..infra..", "..iface..")
            .because("domain 層必須是純 Java，不依賴框架或外層");

    @ArchTest
    static final ArchRule applicationDoesNotKnowAdapters = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("..infra..", "..iface..", "org.springframework.jdbc..")
            .because("application 層只透過 port 與外界溝通");

    @ArchTest
    static final ArchRule ifaceDoesNotTouchInfra = noClasses()
            .that().resideInAPackage("..iface..")
            .should().dependOnClassesThat().resideInAPackage("..infra..")
            .because("介面層只呼叫 application service");

    @ArchTest
    static final ArchRule infraDoesNotTouchIface = noClasses()
            .that().resideInAPackage("..infra..")
            .should().dependOnClassesThat().resideInAPackage("..iface..");
}
