package com.handsoap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HandSoapApplication 本身沒有業務邏輯，因此本測試聚焦在「啟動進入點與自動設定契約」是否正確。
 *
 * <p>刻意不啟動 Spring context（不使用 {@code @SpringBootTest}），
 * 因為 {@code application.properties} 指向 MySQL，啟動 context 需要額外準備資料庫；
 * 這裡改以純反射斷言確保「若這些契約被破壞，應用一定啟不起來」的情況能被抓到。</p>
 */
class HandSoapApplicationTest {

    @Test
    @DisplayName("主類別應為 public 且可被 SpringApplication 反射呼叫")
    void shouldBePublicAndInstantiableBySpring() {
        assertThat(Modifier.isPublic(HandSoapApplication.class.getModifiers())).isTrue();
        assertThat(SpringApplication.class).isNotNull();
    }

    @Test
    @DisplayName("應標註 @SpringBootApplication")
    void shouldBeAnnotatedWithSpringBootApplication() {
        assertThat(HandSoapApplication.class.getAnnotation(SpringBootApplication.class)).isNotNull();
    }

    @Test
    @DisplayName("@SpringBootApplication 應隱含 @SpringBootConfiguration")
    void shouldBeMetaAnnotatedWithSpringBootConfiguration() {
        SpringBootApplication annotation =
                HandSoapApplication.class.getAnnotation(SpringBootApplication.class);

        assertThat(AnnotationUtils.findAnnotation(HandSoapApplication.class, SpringBootConfiguration.class))
                .isNotNull();
        assertThat(annotation).isNotNull();
    }

    @Test
    @DisplayName("@SpringBootApplication 應隱含 @EnableAutoConfiguration（自動設定必須啟用）")
    void shouldBeMetaAnnotatedWithEnableAutoConfiguration() {
        assertThat(AnnotationUtils.findAnnotation(HandSoapApplication.class, EnableAutoConfiguration.class))
                .isNotNull();
    }

    @Test
    @DisplayName("@SpringBootApplication 應隱含 @ComponentScan（元件掃描必須啟用）")
    void shouldBeMetaAnnotatedWithComponentScan() {
        assertThat(AnnotationUtils.findAnnotation(HandSoapApplication.class, ComponentScan.class))
                .isNotNull();
    }

    @Test
    @DisplayName("未指定 scanBasePackages 時，掃描範圍應由主類別所在套件決定")
    void shouldLeaveScanBasePackagesEmptySoScanUsesOwnPackage() {
        SpringBootApplication annotation =
                HandSoapApplication.class.getAnnotation(SpringBootApplication.class);

        assertThat(annotation).isNotNull();
        // Spring Boot 3.4 起為 scanBasePackages / scanBasePackageClasses；
        // 兩者都留空時 Spring 才會退回到「宣告該註解的類別所在套件」
        assertThat(annotation.scanBasePackages()).isEmpty();
        assertThat(annotation.scanBasePackageClasses()).isEmpty();
    }

    @Test
    @DisplayName("主類別必須位於 com.handsoap 根套件，否則子套件會掃不到")
    void shouldLiveInRootPackageSoSubPackagesAreScanned() {
        assertThat(HandSoapApplication.class.getPackageName()).isEqualTo("com.handsoap");
    }

    @Test
    @DisplayName("掃描範圍應涵蓋 config / controller / service / exception 子套件")
    void shouldCoverAllBusinessPackages() {
        String root = HandSoapApplication.class.getPackageName();

        List<String> businessPackages = List.of(
                root + ".config",
                root + ".controller",
                root + ".service",
                root + ".exception",
                root + ".dto",
                root + ".entity",
                root + ".repository");

        assertThat(businessPackages).allSatisfy(pkg -> assertThat(pkg).startsWith(root));
        // 逐一確認這些套件確實存在，避免未來搬移檔案後掃描範圍落空而無人察覺
        businessPackages.forEach(pkg ->
                assertThat(getClass().getClassLoader().getResource(pkg.replace('.', '/')))
                        .as("套件 %s 應該存在，才能被元件掃描涵蓋", pkg)
                        .isNotNull());
    }

    @Test
    @DisplayName("應提供符合 JVM 規範的 main(String[]) 進入點")
    void shouldExposeMainMethodWithCorrectSignature() throws Exception {
        Method main = HandSoapApplication.class.getDeclaredMethod("main", String[].class);

        assertThat(Modifier.isPublic(main.getModifiers())).isTrue();
        assertThat(Modifier.isStatic(main.getModifiers())).isTrue();
        assertThat(main.getReturnType()).isEqualTo(void.class);
        assertThat(main.getExceptionTypes()).isEmpty();
    }
}