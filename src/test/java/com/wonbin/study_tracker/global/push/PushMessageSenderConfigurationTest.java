package com.wonbin.study_tracker.global.push;

import com.wonbin.study_tracker.global.config.FirebaseConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class PushMessageSenderConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    FirebaseConfig.class,
                    FirebaseCloudMessagingSender.class,
                    NoopPushMessageSender.class
            );

    @Test
    void firebase_설정이_없으면_Noop_센더만_등록된다() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PushMessageSender.class);
            assertThat(context).hasSingleBean(NoopPushMessageSender.class);
            assertThat(context).doesNotHaveBean(FirebaseCloudMessagingSender.class);
        });
    }

    @Test
    void firebase_설정이_있으면_FCM_센더만_등록된다(@TempDir Path tempDir) throws Exception {
        Path credentials = writeFakeServiceAccountJson(tempDir);

        contextRunner
                .withPropertyValues("firebase.credentials-path=" + credentials)
                .run(context -> {
                    assertThat(context).hasSingleBean(PushMessageSender.class);
                    assertThat(context).hasSingleBean(FirebaseCloudMessagingSender.class);
                    assertThat(context).doesNotHaveBean(NoopPushMessageSender.class);
                });
    }

    /**
     * GoogleCredentials.fromStream 은 서비스 계정 JSON을 로컬에서 파싱만 하므로
     * 런타임에 생성한 가짜 키로도 빈 생성이 가능하다. (실제 키를 저장소에 커밋하지 않기 위함)
     */
    private Path writeFakeServiceAccountJson(Path tempDir) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        String base64Key = Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(keyPair.getPrivate().getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + base64Key + "\n-----END PRIVATE KEY-----\n";
        String jsonEscapedPem = pem.replace("\n", "\\n");

        String json = """
                {
                  "type": "service_account",
                  "project_id": "test-project",
                  "private_key_id": "test-private-key-id",
                  "private_key": "%s",
                  "client_email": "test@test-project.iam.gserviceaccount.com",
                  "client_id": "123456789",
                  "auth_uri": "https://accounts.google.com/o/oauth2/auth",
                  "token_uri": "https://oauth2.googleapis.com/token"
                }
                """.formatted(jsonEscapedPem);

        Path file = tempDir.resolve("fake-service-account.json");
        Files.writeString(file, json);
        return file;
    }
}
