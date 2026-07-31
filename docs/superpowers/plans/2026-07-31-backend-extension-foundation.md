# 백엔드 + Chrome Extension 기반 작업 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Google OAuth 로그인으로 통일하고, 크로스 디바이스 세션 동기화(push 브로드캐스트)의 백엔드 기반을 구축하고, Chrome Extension의 로그인 풀림 버그를 고친다. 이 작업이 끝나면 이후의 iOS PWA / Android 네이티브 앱 계획이 여기 위에 얹힐 수 있다.

**Architecture:** Spring Boot 백엔드에 Google ID 토큰(웹) / Google 액세스 토큰(Extension) 두 경로를 모두 받아 동일한 내부 로직(`GoogleIdentityResolver` → find-or-create `User` → 기존 자체 JWT 발급)으로 처리한다. 세션 시작/종료/일시정지/재개 시 `SessionEventBroadcaster`가 같은 사용자의 등록된 기기 전체(호출한 기기 포함, 클라이언트가 멱등하게 무시하는 것을 전제)에 FCM push를 보낸다. Firebase 설정이 없는 동안에는 no-op 구현으로 자동 대체되어 앱이 깨지지 않는다.

**Tech Stack:** Spring Boot 3.5.15 / Java 21, MySQL 8.0, `com.google.api-client:google-api-client` (ID 토큰 검증), `com.google.firebase:firebase-admin` (push 발송), React 18.3.1 + TypeScript (Google Identity Services), Chrome Extension MV3 + Jest.

## Global Constraints

- 인증은 Google OAuth로 통일한다 — 이메일/비밀번호 `register`/`login`은 완전히 제거한다 (하위 호환 유지 안 함).
- `device_token`은 만료 없음 유지 (기존 `JwtProvider.generateDeviceToken` 그대로 재사용).
- 세션 이벤트 브로드캐스트는 "호출한 기기를 제외"하지 않는다 — 모든 등록 기기에 보내고, 각 클라이언트가 이미 알고 있는 세션이면 무시하는 방식으로 멱등하게 처리한다 (이 플랜에는 클라이언트 쪽 멱등 처리는 포함하지 않음 — 각 클라이언트 플랜에서 처리).
- Firebase(push) 설정이 없는 개발 환경에서도 앱 구동이 실패하면 안 된다 (`@ConditionalOnProperty`로 optional하게 구성).
- 로컬 DB는 `schema.sql` (`CREATE TABLE IF NOT EXISTS`)로 관리되며 `ddl-auto: none`이다 — 컬럼 추가/삭제 시 기존 로컬 DB를 재생성해야 한다 (테스트용 데이터라 무방).
- 새로 추가하는 백엔드 코드는 Mockito 기반 순수 단위 테스트로 검증한다 (이 프로젝트에는 아직 `@DataJpaTest`/`@WebMvcTest` 관례가 없으므로 새로 만들지 않는다).
- 신규 npm/서드파티 의존성은 최소화한다: 웹은 Google Identity Services를 `<script>` 태그로 로드해 npm 의존성을 추가하지 않는다.

---

## File Structure

**백엔드 (`study-tracker/study-tracker`)**

- Modify: `src/main/resources/schema.sql`, `application.yaml`, `application-local.yaml.example`, `build.gradle`
- Modify: `domain/user/entity/User.java`, `domain/user/repository/UserRepository.java`
- Modify: `domain/device/entity/Device.java`, `domain/device/repository/DeviceRepository.java`
- Create: `domain/user/service/GoogleIdentity.java`, `GoogleIdentityResolver.java`, `GoogleIdentityResolverImpl.java`
- Create: `global/config/GoogleAuthConfig.java`, `global/config/FirebaseConfig.java`
- Create: `global/push/PushMessageSender.java`, `FirebaseCloudMessagingSender.java`, `NoopPushMessageSender.java`
- Create: `domain/session/service/SessionEventType.java`, `SessionEventBroadcaster.java`, `FcmSessionEventBroadcaster.java`
- Modify: `domain/user/service/AuthService.java`, `api/auth/AuthController.java`, `api/auth/AuthRequest.java`, `api/auth/AuthResponse.java`, `global/security/SecurityConfig.java`
- Modify: `domain/session/service/SessionService.java`

**Chrome Extension (`study-tracker-extension`)**

- Create: `package.json`, `jest.config.js`, `test/chromeMock.js`, `background.test.js`
- Modify: `background.js`, `manifest.json`, `popup/popup.html`, `popup/popup.js`

**웹 프론트엔드 (`study-tracker-web`)**

- Modify: `public/index.html`, `src/api/auth.ts`, `src/hooks/useAuth.ts`, `src/pages/LoginPage.tsx`
- Create: `src/types/google-identity.d.ts`

---

## Phase 0 — Chrome Extension 버그 수정 (독립적, 먼저 처리)

### Task 1: MV3 서비스워커 상태 손실 버그 수정

**배경:** `background.js`가 `deviceToken`/`sessionId`/`deviceId`를 모듈 최상단 `let` 변수에 캐싱하고, `chrome.runtime.onInstalled`/`onStartup` 시점에만 `chrome.storage.local`에서 다시 채워 넣고 있다. MV3 서비스워커는 유휴 상태가 되면 브라우저가 아무 때나 종료했다가 이벤트가 발생하면 재시작하는데, 이 재시작은 `onInstalled`/`onStartup`을 발생시키지 않는다. 따라서 재시작 후 메모리상의 `deviceToken`은 `null`로 리셋되고, `chrome.storage.local`에는 로그인 정보가 멀쩡히 남아있는데도 팝업은 "로그인이 필요해요"로 보여준다. 이게 테스트 중 반복적으로 "로그인이 풀리는" 것처럼 보인 원인이다. 추가로 `sendLogs` 알람 생성과 `onAlarm` 리스너 등록이 파일에 중복으로 존재한다.

**Files:**
- Create: `study-tracker-extension/package.json`
- Create: `study-tracker-extension/jest.config.js`
- Create: `study-tracker-extension/test/chromeMock.js`
- Test: `study-tracker-extension/background.test.js`
- Modify: `study-tracker-extension/background.js`

**Interfaces:**
- Produces: `background.js`가 CommonJS 환경(`typeof module !== "undefined"`)에서 `module.exports = { extractDomain, getConfig }`를 노출 (테스트 전용, 브라우저에서는 무시됨)

- [ ] **Step 1: 테스트 인프라 + 실패하는 테스트 작성**

`study-tracker-extension/package.json` 생성:

```json
{
  "name": "study-tracker-extension",
  "version": "1.0.0",
  "private": true,
  "scripts": {
    "test": "jest"
  },
  "devDependencies": {
    "jest": "^29.7.0"
  }
}
```

`study-tracker-extension/jest.config.js` 생성:

```js
module.exports = {
    testEnvironment: "node",
    setupFiles: ["./test/chromeMock.js"],
};
```

`study-tracker-extension/test/chromeMock.js` 생성:

```js
const storageData = {};
const listeners = {
    onInstalled: [],
    onStartup: [],
    onMessage: [],
    onAlarm: [],
};

global.chrome = {
    storage: {
        local: {
            get: (keys) =>
                Promise.resolve(
                    keys.reduce((acc, key) => {
                        acc[key] = storageData[key];
                        return acc;
                    }, {})
                ),
            set: (values) => {
                Object.assign(storageData, values);
                return Promise.resolve();
            },
            clear: () => {
                Object.keys(storageData).forEach((key) => delete storageData[key]);
                return Promise.resolve();
            },
        },
    },
    runtime: {
        onInstalled: { addListener: (fn) => listeners.onInstalled.push(fn) },
        onStartup: { addListener: (fn) => listeners.onStartup.push(fn) },
        onMessage: { addListener: (fn) => listeners.onMessage.push(fn) },
    },
    alarms: {
        create: () => {},
        onAlarm: { addListener: (fn) => listeners.onAlarm.push(fn) },
    },
    tabs: {
        onActivated: { addListener: () => {} },
        onUpdated: { addListener: () => {} },
        query: () => Promise.resolve([]),
    },
    windows: {
        onFocusChanged: { addListener: () => {} },
        WINDOW_ID_NONE: -1,
    },
};

global.fetch = jest.fn(() =>
    Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({ sessionId: null }) })
);

global.__chromeTestHelpers = {
    storageData,
    listeners,
    reset() {
        Object.keys(storageData).forEach((key) => delete storageData[key]);
        Object.keys(listeners).forEach((key) => (listeners[key].length = 0));
        global.fetch = jest.fn(() =>
            Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({ sessionId: null }) })
        );
    },
};
```

`study-tracker-extension/background.test.js` 생성:

```js
beforeEach(() => {
    jest.resetModules();
    global.__chromeTestHelpers.reset();
});

function getMessageListener() {
    return global.__chromeTestHelpers.listeners.onMessage[0];
}

test("서비스워커가 재시작돼도(모듈 재로드) storage에 저장된 deviceToken을 인식한다", async () => {
    await chrome.storage.local.set({ deviceToken: "device-token-123", sessionId: null, deviceId: 7 });

    require("./background.js");
    const onMessage = getMessageListener();

    const status = await new Promise((resolve) => {
        onMessage({ type: "GET_STATUS" }, {}, resolve);
    });

    expect(status.isConnected).toBe(true);
});

test("GET_STATUS는 storage에 deviceToken이 없으면 연결 안 됨으로 응답한다", async () => {
    require("./background.js");
    const onMessage = getMessageListener();

    const status = await new Promise((resolve) => {
        onMessage({ type: "GET_STATUS" }, {}, resolve);
    });

    expect(status.isConnected).toBe(false);
});

test("sendLogs 알람 리스너는 하나만 등록된다 (중복 리스너 없음)", () => {
    require("./background.js");

    expect(global.__chromeTestHelpers.listeners.onAlarm).toHaveLength(1);
});
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd study-tracker-extension && npm install && npm test`
Expected: 첫 번째 테스트 FAIL (`isConnected`가 `false`로 나옴 — storage엔 있지만 메모리 캐시가 `null`이라서), 세 번째 테스트도 FAIL (`onAlarm` 리스너가 2개 등록됨)

- [ ] **Step 3: background.js 수정 — storage를 항상 직접 조회하도록 변경, 중복 등록 제거**

`study-tracker-extension/background.js`를 다음으로 전체 교체:

```js
// background.js
const SERVER_URL = "http://localhost:8080";

// 탭 추적용 상태 (서비스워커 재시작 시 사라져도 무방 — 다음 탭 전환 때 다시 계산됨)
let currentTab = null;
let tabStartTime = null;
let browserLogs = [];

// ── 설정 조회 ──────────────────────────────────────
// MV3 서비스워커는 유휴 상태에서 언제든 종료됐다가 재시작될 수 있고,
// 이때 onInstalled/onStartup은 발생하지 않는다. 그래서 deviceToken 등을
// 모듈 변수에 캐싱하지 않고, 필요할 때마다 매번 storage에서 읽는다.
async function getConfig() {
    const data = await chrome.storage.local.get(["deviceToken", "sessionId", "deviceId"]);
    return {
        deviceToken: data.deviceToken || null,
        sessionId: data.sessionId || null,
        deviceId: data.deviceId || null,
    };
}

// ── 탭 추적 ──────────────────────────────────────

chrome.tabs.onActivated.addListener(async (activeInfo) => {
    const tab = await chrome.tabs.get(activeInfo.tabId);
    handleTabChange(tab);
});

chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
    if (changeInfo.status === "complete" && tab.active) {
        handleTabChange(tab);
    }
});

chrome.windows.onFocusChanged.addListener(async (windowId) => {
    if (windowId === chrome.windows.WINDOW_ID_NONE) {
        saveCurrentTabLog();
        currentTab = null;
        tabStartTime = null;
        return;
    }

    try {
        const tabs = await chrome.tabs.query({ active: true, windowId });
        if (tabs.length > 0) {
            handleTabChange(tabs[0]);
        }
    } catch (e) {
        console.error("[오류]", e);
    }
});

function handleTabChange(tab) {
    saveCurrentTabLog();

    if (!tab.url || tab.url.startsWith("chrome://")) {
        currentTab = null;
        tabStartTime = null;
        return;
    }

    currentTab = tab;
    tabStartTime = new Date();
}

function saveCurrentTabLog() {
    if (!currentTab || !tabStartTime) return;

    const durationSec = Math.floor((new Date() - tabStartTime) / 1000);
    if (durationSec < 3) return;

    const domain = extractDomain(currentTab.url);
    if (!domain) return;

    browserLogs.push({
        domain: domain,
        pageTitle: currentTab.title || "",
        startedAt: tabStartTime.toISOString().slice(0, 19),
        durationSec: durationSec
    });

    console.log(`[로그] ${domain} | ${durationSec}초 | 배치: ${browserLogs.length}개`);
}

// ── 알람 (1분마다 로그 전송 + 세션 동기화) ─────────────────

chrome.alarms.create("sendLogs", { periodInMinutes: 1 });
chrome.alarms.create("syncSession", { periodInMinutes: 1 });

chrome.alarms.onAlarm.addListener((alarm) => {
    if (alarm.name === "sendLogs") {
        sendBrowserLogs();
    }
    if (alarm.name === "syncSession") {
        syncActiveSession();
    }
});

async function syncActiveSession() {
    const config = await getConfig();
    if (!config.deviceToken) return;

    try {
        const response = await fetch(`${SERVER_URL}/api/sessions/active`, {
            headers: { "Authorization": `Bearer ${config.deviceToken}` }
        });
        if (response.ok) {
            const data = await response.json();
            if (config.sessionId !== data.sessionId) {
                await chrome.storage.local.set({ sessionId: data.sessionId });
                console.log("[세션 동기화]", data.sessionId);
            }
        } else if (response.status === 400 || response.status === 404) {
            if (config.sessionId) {
                await chrome.storage.local.set({ sessionId: null });
                console.log("[세션 동기화] 활성 세션 없음");
            }
        }
    } catch (e) {
        console.error("[오류] 세션 동기화 실패", e);
    }
}

// ── 도메인 추출 ──────────────────────────────────

function extractDomain(url) {
    try {
        const parsed = new URL(url);
        let domain = parsed.hostname;
        if (domain.startsWith("www.")) {
            domain = domain.slice(4);
        }
        return domain;
    } catch {
        return null;
    }
}

// ── 서버 전송 ─────────────────────────────────────

async function sendBrowserLogs() {
    const config = await getConfig();
    if (!browserLogs.length || !config.deviceToken || !config.sessionId || !config.deviceId) {
        return;
    }

    saveCurrentTabLog();

    const logsToSend = [...browserLogs];
    browserLogs = [];

    try {
        const response = await fetch(`${SERVER_URL}/api/browser-logs`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Authorization": `Bearer ${config.deviceToken}`
            },
            body: JSON.stringify({
                sessionId: parseInt(config.sessionId),
                deviceId: parseInt(config.deviceId),
                logs: logsToSend
            })
        });

        if (response.ok) {
            console.log(`[전송] ${logsToSend.length}개 브라우저 로그 전송 완료`);
        } else {
            console.error("[오류] 전송 실패:", response.status);
            browserLogs = [...logsToSend, ...browserLogs];
        }
    } catch (e) {
        console.error("[오류] 서버 연결 실패:", e);
        browserLogs = [...logsToSend, ...browserLogs];
    }
}

// ── 팝업으로부터 메시지 수신 ──────────────────────

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message.type === "GET_STATUS") {
        getConfig().then((config) => {
            sendResponse({
                isConnected: !!config.deviceToken,
                sessionId: config.sessionId,
                logCount: browserLogs.length
            });
        });
        return true;
    }

    if (message.type === "SAVE_CONFIG") {
        chrome.storage.local.set({
            deviceToken: message.deviceToken,
            sessionId: message.sessionId,
            deviceId: message.deviceId
        }).then(() => sendResponse({ success: true }));
        return true;
    }

    if (message.type === "SEND_NOW") {
        sendBrowserLogs().then(() => sendResponse({ success: true }));
        return true;
    }

    return true;
});

if (typeof module !== "undefined") {
    module.exports = { extractDomain, getConfig };
}
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `cd study-tracker-extension && npm test`
Expected: 3개 테스트 모두 PASS

- [ ] **Step 5: 커밋**

```bash
cd study-tracker-extension
git add package.json jest.config.js test/chromeMock.js background.test.js background.js
git commit -m "fix: MV3 서비스워커 재시작 시 로그인 상태 손실 버그 수정"
```

---

## Phase 1 — 백엔드 Google OAuth 전환

### Task 2: User 엔티티 Google OAuth로 전환 (스키마 + 엔티티 + 레포지토리)

**Files:**
- Modify: `src/main/resources/schema.sql`
- Modify: `domain/user/entity/User.java`
- Modify: `domain/user/repository/UserRepository.java`

**Interfaces:**
- Produces: `User.getGoogleId()`, `UserRepository.findByGoogleId(String googleId)`, `UserRepository.existsByGoogleId(String googleId)`

- [ ] **Step 1: schema.sql의 users 테이블 수정**

`src/main/resources/schema.sql`의 1번 테이블 정의를 다음으로 교체:

```sql
-- 1. 사용자
CREATE TABLE IF NOT EXISTS users (
     id                  BIGINT          NOT NULL AUTO_INCREMENT,
     email               VARCHAR(255)    NOT NULL,
    name                VARCHAR(100)    NOT NULL,
    google_id           VARCHAR(255)    NOT NULL,
    day_change_hour     TINYINT         NOT NULL DEFAULT 5,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    UNIQUE KEY uk_users_google_id (google_id)
    );
```

(`password_hash` 컬럼 제거, `google_id` 컬럼 추가)

- [ ] **Step 2: User 엔티티 수정**

`domain/user/entity/User.java`:

```java
package com.wonbin.study_tracker.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "Users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "google_id", nullable = false, unique = true, length = 255)
    private String googleId;

    @Column(name = "day_change_hour", nullable = false)
    private int dayChangeHour = 5;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

}
```

- [ ] **Step 3: UserRepository 수정**

`domain/user/repository/UserRepository.java`에 다음 메서드 추가 (기존 `findByEmail`/`existsByEmail`은 유지 — 표시용 조회에 계속 쓰임):

```java
Optional<User> findByGoogleId(String googleId);
boolean existsByGoogleId(String googleId);
```

- [ ] **Step 4: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL (이 태스크는 스키마/엔티티 변경이라 단위 테스트 대상이 없음 — 실제 동작은 Task 4의 AuthService 테스트에서 검증됨)

- [ ] **Step 5: 로컬 DB 재생성 (수동, 테스트 데이터라 무방)**

Run:
```sql
DROP DATABASE study_tracker;
CREATE DATABASE study_tracker;
```
그 후 앱을 재기동하면 `schema.sql`이 새 스키마로 테이블을 다시 만든다.

- [ ] **Step 6: 커밋**

```bash
git add src/main/resources/schema.sql src/main/java/com/wonbin/study_tracker/domain/user/entity/User.java src/main/java/com/wonbin/study_tracker/domain/user/repository/UserRepository.java
git commit -m "refactor: User 엔티티를 비밀번호 기반에서 Google OAuth 기반으로 전환"
```

---

### Task 3: GoogleIdentityResolver — ID 토큰(웹) / 액세스 토큰(Extension) 검증

**Files:**
- Create: `domain/user/service/GoogleIdentity.java`
- Create: `domain/user/service/GoogleIdentityResolver.java`
- Create: `domain/user/service/GoogleIdentityResolverImpl.java`
- Create: `global/config/GoogleAuthConfig.java`
- Test: `src/test/java/com/wonbin/study_tracker/domain/user/service/GoogleIdentityResolverImplTest.java`
- Modify: `build.gradle`, `application.yaml`, `application-local.yaml.example`

**Interfaces:**
- Produces: `GoogleIdentity(String googleId, String email, String name)`, `GoogleIdentityResolver.resolveFromIdToken(String)`, `GoogleIdentityResolver.resolveFromAccessToken(String)`
- Consumes (Task 4에서): 위 두 메서드가 반환하는 `GoogleIdentity`

- [ ] **Step 1: build.gradle에 의존성 추가**

`build.gradle`의 `dependencies` 블록에 추가:

```groovy
implementation 'com.google.api-client:google-api-client:2.9.0'
implementation 'com.google.http-client:google-http-client-gson:2.1.0'
```

- [ ] **Step 2: application.yaml / application-local.yaml.example에 설정 키 추가**

`application.yaml`에 추가:

```yaml
google:
  oauth:
    client-id: ${GOOGLE_OAUTH_CLIENT_ID}
```

`application-local.yaml.example`에 추가:

```yaml
google:
  oauth:
    client-id: your_google_oauth_web_client_id.apps.googleusercontent.com
```

(Google Cloud Console에서 "OAuth 2.0 클라이언트 ID"를 웹 애플리케이션 유형으로 만들어서 나온 클라이언트 ID를 여기 넣는다. 이 단계는 코드가 아니라 외부 설정이므로 개발자가 직접 진행해야 한다.)

- [ ] **Step 3: 실패하는 단위 테스트 작성 (GoogleIdentityResolverImpl)**

`src/test/java/com/wonbin/study_tracker/domain/user/service/GoogleIdentityResolverImplTest.java` 생성:

```java
package com.wonbin.study_tracker.domain.user.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleIdentityResolverImplTest {

    @Mock
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @Mock
    private RestClient googleRestClient;

    @Test
    void ID_토큰이_유효하면_GoogleIdentity를_반환한다() throws Exception {
        GoogleIdToken token = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("google-sub-123");
        payload.setEmail("test@example.com");
        payload.set("name", "테스트유저");

        when(token.getPayload()).thenReturn(payload);
        when(googleIdTokenVerifier.verify("valid-id-token")).thenReturn(token);

        GoogleIdentityResolverImpl resolver =
                new GoogleIdentityResolverImpl(googleIdTokenVerifier, googleRestClient);

        GoogleIdentity identity = resolver.resolveFromIdToken("valid-id-token");

        assertThat(identity.googleId()).isEqualTo("google-sub-123");
        assertThat(identity.email()).isEqualTo("test@example.com");
        assertThat(identity.name()).isEqualTo("테스트유저");
    }

    @Test
    void ID_토큰이_유효하지_않으면_예외를_던진다() throws Exception {
        when(googleIdTokenVerifier.verify("invalid-token")).thenReturn(null);

        GoogleIdentityResolverImpl resolver =
                new GoogleIdentityResolverImpl(googleIdTokenVerifier, googleRestClient);

        assertThatThrownBy(() -> resolver.resolveFromIdToken("invalid-token"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

`build.gradle`의 테스트 의존성에 AssertJ가 필요하면 `spring-boot-starter-test`에 이미 포함되어 있으므로 추가 설정은 필요 없다.

- [ ] **Step 4: 테스트 실행해서 실패 확인 (컴파일 실패)**

Run: `./gradlew test --tests "*.GoogleIdentityResolverImplTest"`
Expected: FAIL — `GoogleIdentity`, `GoogleIdentityResolverImpl` 클래스가 아직 없어서 컴파일 에러

- [ ] **Step 5: GoogleIdentity, GoogleIdentityResolver, GoogleIdentityResolverImpl, GoogleAuthConfig 구현**

`domain/user/service/GoogleIdentity.java`:

```java
package com.wonbin.study_tracker.domain.user.service;

public record GoogleIdentity(String googleId, String email, String name) {
}
```

`domain/user/service/GoogleIdentityResolver.java`:

```java
package com.wonbin.study_tracker.domain.user.service;

public interface GoogleIdentityResolver {
    GoogleIdentity resolveFromIdToken(String idToken);
    GoogleIdentity resolveFromAccessToken(String accessToken);
}
```

`domain/user/service/GoogleIdentityResolverImpl.java`:

```java
package com.wonbin.study_tracker.domain.user.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GoogleIdentityResolverImpl implements GoogleIdentityResolver {

    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final RestClient googleRestClient;

    @Override
    public GoogleIdentity resolveFromIdToken(String idToken) {
        GoogleIdToken token;
        try {
            token = googleIdTokenVerifier.verify(idToken);
        } catch (Exception e) {
            throw new IllegalArgumentException("유효하지 않은 Google ID 토큰입니다.", e);
        }

        if (token == null) {
            throw new IllegalArgumentException("유효하지 않은 Google ID 토큰입니다.");
        }

        GoogleIdToken.Payload payload = token.getPayload();
        return new GoogleIdentity(
                payload.getSubject(),
                payload.getEmail(),
                (String) payload.get("name")
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public GoogleIdentity resolveFromAccessToken(String accessToken) {
        Map<String, Object> userInfo = googleRestClient.get()
                .uri("https://www.googleapis.com/oauth2/v3/userinfo")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(Map.class);

        if (userInfo == null || userInfo.get("sub") == null) {
            throw new IllegalArgumentException("유효하지 않은 Google 액세스 토큰입니다.");
        }

        return new GoogleIdentity(
                (String) userInfo.get("sub"),
                (String) userInfo.get("email"),
                (String) userInfo.get("name")
        );
    }
}
```

`global/config/GoogleAuthConfig.java`:

```java
package com.wonbin.study_tracker.global.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.Collections;

@Configuration
public class GoogleAuthConfig {

    @Bean
    public GoogleIdTokenVerifier googleIdTokenVerifier(
            @Value("${google.oauth.client-id}") String clientId) {
        return new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    @Bean
    public RestClient googleRestClient() {
        return RestClient.create();
    }
}
```

- [ ] **Step 6: 테스트 실행해서 통과 확인**

Run: `./gradlew test --tests "*.GoogleIdentityResolverImplTest"`
Expected: PASS (2개 테스트)

- [ ] **Step 7: 커밋**

```bash
git add build.gradle src/main/resources/application.yaml src/main/resources/application-local.yaml.example src/main/java/com/wonbin/study_tracker/domain/user/service/GoogleIdentity.java src/main/java/com/wonbin/study_tracker/domain/user/service/GoogleIdentityResolver.java src/main/java/com/wonbin/study_tracker/domain/user/service/GoogleIdentityResolverImpl.java src/main/java/com/wonbin/study_tracker/global/config/GoogleAuthConfig.java src/test/java/com/wonbin/study_tracker/domain/user/service/GoogleIdentityResolverImplTest.java
git commit -m "feat: Google ID 토큰/액세스 토큰 검증 컴포넌트 추가"
```

---

### Task 4: AuthService — Google 로그인 흐름 (기존 register/login 제거)

**Files:**
- Modify: `domain/user/service/AuthService.java`
- Modify: `api/auth/AuthRequest.java`
- Test: `src/test/java/com/wonbin/study_tracker/domain/user/service/AuthServiceGoogleTest.java`

**Interfaces:**
- Consumes: `GoogleIdentityResolver.resolveFromIdToken/resolveFromAccessToken` (Task 3), `UserRepository.findByGoogleId/existsByGoogleId` (Task 2)
- Produces: `AuthService.loginWithGoogleIdToken(String idToken)`, `AuthService.loginWithGoogleAccessToken(String accessToken)` — 둘 다 `AuthResponse.Token` 반환

- [ ] **Step 1: AuthRequest에서 Register/Login 제거, GoogleLogin 추가**

`api/auth/AuthRequest.java` 전체 교체:

```java
package com.wonbin.study_tracker.api.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

public class AuthRequest {

    @Getter
    public static class GoogleIdTokenLogin {
        @NotBlank
        private String idToken;
    }

    @Getter
    public static class GoogleAccessTokenLogin {
        @NotBlank
        private String accessToken;
    }

    @Getter
    public static class DeviceRegister {
        @NotBlank
        private String deviceName;

        @NotBlank
        private String deviceType;
    }

    @Getter
    public static class PushTokenUpdate {
        @NotBlank
        private String deviceId;

        @NotBlank
        private String pushToken;
    }
}
```

(주의: `PushTokenUpdate`는 Task 8에서 실제로 쓰이지만, `AuthRequest` 파일을 여러 태스크에서 나눠 편집하면 diff가 꼬이기 쉬워서 이번에 한 번에 정리한다. `deviceId`는 요청 바디에서 문자열로 받고 서비스단에서 `Long.parseLong` 한다.)

- [ ] **Step 2: 실패하는 단위 테스트 작성 (AuthService)**

`src/test/java/com/wonbin/study_tracker/domain/user/service/AuthServiceGoogleTest.java` 생성:

```java
package com.wonbin.study_tracker.domain.user.service;

import com.wonbin.study_tracker.api.auth.AuthResponse;
import com.wonbin.study_tracker.domain.device.repository.DeviceRepository;
import com.wonbin.study_tracker.domain.user.entity.User;
import com.wonbin.study_tracker.domain.user.repository.UserRepository;
import com.wonbin.study_tracker.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceGoogleTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private GoogleIdentityResolver googleIdentityResolver;

    @InjectMocks
    private AuthService authService;

    @Test
    void 처음_로그인하는_구글_사용자는_새로_생성된다() {
        when(googleIdentityResolver.resolveFromIdToken("id-token"))
                .thenReturn(new GoogleIdentity("google-sub-1", "new@example.com", "새유저"));
        when(userRepository.findByGoogleId("google-sub-1")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            return User.builder()
                    .id(1L)
                    .email(u.getEmail())
                    .name(u.getName())
                    .googleId(u.getGoogleId())
                    .dayChangeHour(5)
                    .build();
        });
        when(jwtProvider.generateAccessToken(1L, "new@example.com")).thenReturn("access-token");
        when(jwtProvider.generateRefreshToken(1L)).thenReturn("refresh-token");

        AuthResponse.Token result = authService.loginWithGoogleIdToken("id-token");

        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getUserId()).isEqualTo(1L);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void 이미_있는_구글_사용자는_재생성하지_않는다() {
        User existing = User.builder()
                .id(5L)
                .email("existing@example.com")
                .name("기존유저")
                .googleId("google-sub-2")
                .dayChangeHour(5)
                .build();

        when(googleIdentityResolver.resolveFromIdToken("id-token"))
                .thenReturn(new GoogleIdentity("google-sub-2", "existing@example.com", "기존유저"));
        when(userRepository.findByGoogleId("google-sub-2")).thenReturn(Optional.of(existing));
        when(jwtProvider.generateAccessToken(5L, "existing@example.com")).thenReturn("access-token");
        when(jwtProvider.generateRefreshToken(5L)).thenReturn("refresh-token");

        authService.loginWithGoogleIdToken("id-token");

        verify(userRepository, never()).save(any(User.class));
    }
}
```

- [ ] **Step 3: 테스트 실행해서 실패 확인 (컴파일 실패)**

Run: `./gradlew test --tests "*.AuthServiceGoogleTest"`
Expected: FAIL — `AuthService`에 `loginWithGoogleIdToken` 메서드가 없어서 컴파일 에러

- [ ] **Step 4: AuthService 구현 (register/login 제거, Google 로그인 추가)**

`domain/user/service/AuthService.java` 전체 교체:

```java
package com.wonbin.study_tracker.domain.user.service;

import com.wonbin.study_tracker.api.auth.AuthRequest;
import com.wonbin.study_tracker.api.auth.AuthResponse;
import com.wonbin.study_tracker.domain.device.entity.Device;
import com.wonbin.study_tracker.domain.device.repository.DeviceRepository;
import com.wonbin.study_tracker.domain.user.entity.User;
import com.wonbin.study_tracker.domain.user.repository.UserRepository;
import com.wonbin.study_tracker.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final JwtProvider jwtProvider;
    private final GoogleIdentityResolver googleIdentityResolver;

    // 웹: Google ID 토큰으로 로그인
    @Transactional
    public AuthResponse.Token loginWithGoogleIdToken(String idToken) {
        GoogleIdentity identity = googleIdentityResolver.resolveFromIdToken(idToken);
        return issueTokensForGoogleUser(identity);
    }

    // Chrome Extension: Google 액세스 토큰으로 로그인
    @Transactional
    public AuthResponse.Token loginWithGoogleAccessToken(String accessToken) {
        GoogleIdentity identity = googleIdentityResolver.resolveFromAccessToken(accessToken);
        return issueTokensForGoogleUser(identity);
    }

    private AuthResponse.Token issueTokensForGoogleUser(GoogleIdentity identity) {
        User user = userRepository.findByGoogleId(identity.googleId())
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .email(identity.email())
                                .name(identity.name() != null ? identity.name() : identity.email())
                                .googleId(identity.googleId())
                                .dayChangeHour(5)
                                .build()
                ));

        return AuthResponse.Token.builder()
                .accessToken(jwtProvider.generateAccessToken(user.getId(), user.getEmail()))
                .refreshToken(jwtProvider.generateRefreshToken(user.getId()))
                .userId(user.getId())
                .name(user.getName())
                .build();
    }

    // device Token 발급
    @Transactional
    public AuthResponse.DeviceToken registerDevice(Long userId, AuthRequest.DeviceRegister request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        String deviceToken = jwtProvider.generateDeviceToken(userId, request.getDeviceName());

        Device device = Device.builder()
                .user(user)
                .deviceName(request.getDeviceName())
                .deviceType(request.getDeviceType())
                .deviceToken(deviceToken)
                .build();

        deviceRepository.save(device);

        return AuthResponse.DeviceToken.builder()
                .deviceToken(deviceToken)
                .deviceId(device.getId())
                .build();
    }
}
```

- [ ] **Step 5: 테스트 실행해서 통과 확인**

Run: `./gradlew test --tests "*.AuthServiceGoogleTest"`
Expected: PASS (2개 테스트)

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/wonbin/study_tracker/domain/user/service/AuthService.java src/main/java/com/wonbin/study_tracker/api/auth/AuthRequest.java src/test/java/com/wonbin/study_tracker/domain/user/service/AuthServiceGoogleTest.java
git commit -m "feat: AuthService를 Google OAuth 로그인으로 전환, 이메일/비밀번호 흐름 제거"
```

---

### Task 5: AuthController 엔드포인트 교체 + SecurityConfig 갱신

**Files:**
- Modify: `api/auth/AuthController.java`
- Modify: `global/security/SecurityConfig.java`

**Interfaces:**
- Consumes: `AuthService.loginWithGoogleIdToken/loginWithGoogleAccessToken` (Task 4)
- Produces: `POST /api/auth/google`, `POST /api/auth/google/token` (인증 없이 접근 가능)

- [ ] **Step 1: AuthController 수정**

`api/auth/AuthController.java` 전체 교체:

```java
package com.wonbin.study_tracker.api.auth;

import com.wonbin.study_tracker.domain.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 웹: Google Identity Services에서 받은 ID 토큰으로 로그인
    @PostMapping("/google")
    public ResponseEntity<AuthResponse.Token> loginWithGoogleIdToken(
            @Valid @RequestBody AuthRequest.GoogleIdTokenLogin request) {
        return ResponseEntity.ok(authService.loginWithGoogleIdToken(request.getIdToken()));
    }

    // Chrome Extension: chrome.identity.getAuthToken()으로 받은 액세스 토큰으로 로그인
    @PostMapping("/google/token")
    public ResponseEntity<AuthResponse.Token> loginWithGoogleAccessToken(
            @Valid @RequestBody AuthRequest.GoogleAccessTokenLogin request) {
        return ResponseEntity.ok(authService.loginWithGoogleAccessToken(request.getAccessToken()));
    }

    @PostMapping("/device")
    public ResponseEntity<AuthResponse.DeviceToken> registerDevice(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AuthRequest.DeviceRegister register) {
        return ResponseEntity.ok(authService.registerDevice(userId, register));
    }

}
```

- [ ] **Step 2: SecurityConfig의 permitAll 목록 갱신**

`global/security/SecurityConfig.java`의 `authorizeHttpRequests` 블록을 다음으로 교체:

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers(
                "/api/auth/google",
                "/api/auth/google/token"
        ).permitAll()
        .anyRequest().authenticated()
)
```

(`passwordEncoder()` Bean은 더 이상 아무도 쓰지 않으므로 함께 제거한다. `BCryptPasswordEncoder`/`PasswordEncoder` import도 제거.)

- [ ] **Step 3: 전체 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL (컨트롤러/시큐리티 설정은 배선 변경이라 별도 단위 테스트 없이, 이미 있는 `AuthServiceGoogleTest`가 핵심 로직을 커버한다)

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/wonbin/study_tracker/api/auth/AuthController.java src/main/java/com/wonbin/study_tracker/global/security/SecurityConfig.java
git commit -m "feat: 인증 엔드포인트를 Google OAuth 경로로 교체"
```

---

## Phase 2 — 클라이언트 Google OAuth 적용

### Task 6: 웹 프론트엔드 — Google Identity Services 적용

**Files:**
- Modify: `study-tracker-web/public/index.html`
- Create: `study-tracker-web/src/types/google-identity.d.ts`
- Modify: `study-tracker-web/src/api/auth.ts`
- Modify: `study-tracker-web/src/hooks/useAuth.ts`
- Modify: `study-tracker-web/src/pages/LoginPage.tsx`
- Test: `study-tracker-web/src/hooks/useAuth.test.ts`

**Interfaces:**
- Consumes: 백엔드 `POST /api/auth/google` (Task 5)
- Produces: `useAuth().handleGoogleLogin(idToken: string): Promise<boolean>`

- [ ] **Step 1: index.html에 Google Identity Services 스크립트 추가**

`public/index.html`의 `</head>` 바로 앞에 추가:

```html
<script src="https://accounts.google.com/gsi/client" async defer></script>
```

- [ ] **Step 2: window.google 타입 선언 추가**

`src/types/google-identity.d.ts` 생성:

```ts
export {};

declare global {
    interface Window {
        google?: {
            accounts: {
                id: {
                    initialize: (config: {
                        client_id: string;
                        callback: (response: { credential: string }) => void;
                    }) => void;
                    renderButton: (parent: HTMLElement, options: { theme?: string; size?: string }) => void;
                };
            };
        };
    }
}
```

- [ ] **Step 3: 실패하는 테스트 작성 (useAuth)**

`src/hooks/useAuth.test.ts` 생성:

```ts
import { renderHook, act } from '@testing-library/react';
import { useAuth } from './useAuth';
import * as authApi from '../api/auth';

jest.mock('../api/auth');

describe('useAuth.handleGoogleLogin', () => {
    beforeEach(() => {
        localStorage.clear();
        jest.clearAllMocks();
    });

    it('로그인 성공 시 토큰을 저장하고 true를 반환한다', async () => {
        (authApi.loginWithGoogle as jest.Mock).mockResolvedValue({
            accessToken: 'access-token',
            refreshToken: 'refresh-token',
            userId: 1,
            name: '테스트유저',
        });

        const { result } = renderHook(() => useAuth());

        let success = false;
        await act(async () => {
            success = await result.current.handleGoogleLogin('google-id-token');
        });

        expect(success).toBe(true);
        expect(localStorage.getItem('accessToken')).toBe('access-token');
        expect(localStorage.getItem('userName')).toBe('테스트유저');
    });

    it('로그인 실패 시 false를 반환하고 에러 메시지를 설정한다', async () => {
        (authApi.loginWithGoogle as jest.Mock).mockRejectedValue({
            response: { data: { message: 'Google 로그인 실패' } },
        });

        const { result } = renderHook(() => useAuth());

        let success = true;
        await act(async () => {
            success = await result.current.handleGoogleLogin('bad-token');
        });

        expect(success).toBe(false);
        expect(result.current.error).toBe('Google 로그인 실패');
    });
});
```

- [ ] **Step 4: 테스트 실행해서 실패 확인**

Run: `cd study-tracker-web && npm test -- --watchAll=false useAuth.test.ts`
Expected: FAIL — `authApi.loginWithGoogle`, `result.current.handleGoogleLogin`이 존재하지 않음

- [ ] **Step 5: api/auth.ts, useAuth.ts 수정**

`src/api/auth.ts` 전체 교체:

```ts
// src/api/auth.ts
import client from './client';
import { TokenResponse, DeviceTokenResponse } from '../types';

export const loginWithGoogle = async (idToken: string): Promise<TokenResponse> => {
    const response = await client.post('/api/auth/google', { idToken });
    return response.data;
};

export const registerDevice = async (
    deviceName: string, deviceType: string
): Promise<DeviceTokenResponse> => {
    const response = await client.post('/api/auth/device', { deviceName, deviceType });
    return response.data;
};
```

`src/hooks/useAuth.ts` 전체 교체:

```ts
// src/hooks/useAuth.ts
import { useState } from 'react';
import { loginWithGoogle } from '../api/auth';
import { TokenResponse } from '../types';

export const useAuth = () => {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const isLoggedIn = (): boolean => {
        return !!localStorage.getItem('accessToken');
    };

    const handleGoogleLogin = async (idToken: string): Promise<boolean> => {
        setLoading(true);
        setError(null);
        try {
            const data: TokenResponse = await loginWithGoogle(idToken);
            localStorage.setItem('accessToken', data.accessToken);
            localStorage.setItem('refreshToken', data.refreshToken);
            localStorage.setItem('userId', String(data.userId));
            localStorage.setItem('userName', data.name);
            return true;
        } catch (e: any) {
            setError(e.response?.data?.message || 'Google 로그인에 실패했습니다.');
            return false;
        } finally {
            setLoading(false);
        }
    };

    const handleLogout = () => {
        localStorage.clear();
        window.location.href = '/login';
    };

    return { isLoggedIn, handleGoogleLogin, handleLogout, loading, error };
};
```

- [ ] **Step 6: 테스트 실행해서 통과 확인**

Run: `cd study-tracker-web && npm test -- --watchAll=false useAuth.test.ts`
Expected: PASS (2개 테스트)

- [ ] **Step 7: LoginPage를 Google 버튼으로 교체**

`src/pages/LoginPage.tsx` 전체 교체:

```tsx
// src/pages/LoginPage.tsx
import { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { color } from '../theme';
import { BookOpen } from 'lucide-react';

const GOOGLE_CLIENT_ID = process.env.REACT_APP_GOOGLE_CLIENT_ID as string;

const LoginPage = () => {
    const { handleGoogleLogin, error } = useAuth();
    const navigate = useNavigate();
    const buttonRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!window.google || !buttonRef.current) return;

        window.google.accounts.id.initialize({
            client_id: GOOGLE_CLIENT_ID,
            callback: async (response) => {
                const success = await handleGoogleLogin(response.credential);
                if (success) navigate('/');
            },
        });
        window.google.accounts.id.renderButton(buttonRef.current, { theme: 'outline', size: 'large' });
    }, [handleGoogleLogin, navigate]);

    return (
        <div className="login-shell" style={styles.shell}>
            <div className="login-brand" style={styles.brandPanel}>
                <div style={styles.brandInner}>
                    <div style={styles.brandMark}>
                        <BookOpen size={22} strokeWidth={1.75} color={color.onAccent} />
                        <span style={styles.brandMarkText}>Study Tracker</span>
                    </div>
                    <p style={styles.brandHeadline}>
                        오늘 무엇에<br />집중했는지 기록하세요.
                    </p>
                    <p style={styles.brandSub}>
                        PC와 브라우저 사용 기록을 자동으로 모아 순공 시간과 딴짓을 정리해드려요.
                    </p>
                </div>
            </div>

            <div style={styles.formPanel}>
                <div style={styles.card}>
                    <p style={styles.eyebrowMobile}>
                        <BookOpen size={18} strokeWidth={1.75} color={color.accent} />
                        Study Tracker
                    </p>
                    <h1 style={styles.title}>다시 오셨네요</h1>
                    <p style={styles.subtitle}>Google 계정으로 로그인해주세요.</p>

                    <div ref={buttonRef} />
                    {error && <p style={styles.error}>{error}</p>}
                </div>
            </div>
        </div>
    );
};

const styles: { [key: string]: React.CSSProperties } = {
    shell: { background: color.page },
    brandPanel: {
        flex: '0 0 42%', background: color.accent, color: color.onAccent,
        alignItems: 'center', justifyContent: 'center', padding: '48px',
    },
    brandInner: { maxWidth: '360px' },
    brandMark: { display: 'flex', alignItems: 'center', gap: '9px', marginBottom: '40px' },
    brandMarkText: { fontSize: '16px', fontWeight: 700, letterSpacing: '-0.01em' },
    brandHeadline: {
        fontSize: '30px', fontWeight: 700, lineHeight: 1.35, letterSpacing: '-0.01em',
        margin: '0 0 16px', color: color.onAccent,
    },
    brandSub: { fontSize: '14px', lineHeight: 1.6, opacity: 0.82, margin: 0 },

    formPanel: {
        flex: 1, minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
        padding: '24px',
    },
    card: { width: '360px', maxWidth: '100%' },
    eyebrowMobile: {
        display: 'flex', alignItems: 'center', gap: '7px',
        fontSize: '13px', fontWeight: 700, color: color.accent, margin: '0 0 20px',
    },
    title: { margin: '0 0 6px', fontSize: '24px', fontWeight: 700, color: color.ink, letterSpacing: '-0.01em' },
    subtitle: { margin: '0 0 24px', color: color.inkSecondary, fontSize: '14px' },
    error: { color: color.distract, fontSize: '13px', marginTop: '12px' },
};

export default LoginPage;
```

`REACT_APP_GOOGLE_CLIENT_ID`는 CRA 환경변수 관례에 따라 `.env.local`(gitignore 대상)에 `REACT_APP_GOOGLE_CLIENT_ID=your_google_oauth_web_client_id.apps.googleusercontent.com`로 설정한다. Task 3에서 백엔드에 설정한 것과 같은 Google Cloud OAuth 클라이언트 ID(웹 애플리케이션 유형)를 쓴다.

- [ ] **Step 8: 프론트엔드 전체 테스트 및 빌드 확인**

Run: `cd study-tracker-web && npm test -- --watchAll=false && npm run build`
Expected: 테스트 PASS, 빌드 성공

- [ ] **Step 9: 커밋**

```bash
cd study-tracker-web
git add public/index.html src/types/google-identity.d.ts src/api/auth.ts src/hooks/useAuth.ts src/hooks/useAuth.test.ts src/pages/LoginPage.tsx
git commit -m "feat: 로그인을 이메일/비밀번호에서 Google OAuth로 전환"
```

---

### Task 7: Chrome Extension — Google OAuth 적용

**Files:**
- Modify: `manifest.json`
- Modify: `popup/popup.html`
- Modify: `popup/popup.js`
- Test: `popup/popup.test.js` (신규)

**Interfaces:**
- Consumes: 백엔드 `POST /api/auth/google/token` (Task 5)
- Consumes: `chrome.identity.getAuthToken` (Chrome 제공 API)

**배경:** `chrome.identity.getAuthToken()`은 Chrome 브라우저에 로그인된 Google 계정을 이용해 액세스 토큰을 발급받는 표준 MV3 방식이다. 이 토큰을 백엔드의 `/api/auth/google/token`에 보내면, 백엔드가 Google의 userinfo 엔드포인트로 검증하고 우리 서비스의 JWT를 발급한다.

- [ ] **Step 1: manifest.json에 identity 권한 + oauth2 설정 추가**

`manifest.json`을 다음으로 교체:

```json
{
  "manifest_version": 3,
  "name": "Study Tracker",
  "version": "1.0.0",
  "description": "브라우저 사용 시간을 측정해서 순공 시간을 계산합니다",

  "permissions": [
    "tabs",
    "storage",
    "alarms",
    "idle",
    "identity"
  ],

  "host_permissions": [
    "http://localhost:8080/*"
  ],

  "oauth2": {
    "client_id": "your_google_oauth_chrome_extension_client_id.apps.googleusercontent.com",
    "scopes": ["openid", "email", "profile"]
  },

  "background": {
    "service_worker": "background.js"
  },

  "content_scripts": [
    {
      "matches": ["<all_urls>"],
      "js": ["content.js"]
    }
  ],

  "action": {
    "default_popup": "popup/popup.html",
    "default_title": "Study Tracker"
  }
}
```

`oauth2.client_id`는 Google Cloud Console에서 "Chrome 앱" 유형으로 새로 만든 OAuth 클라이언트 ID로 교체해야 한다 (Task 3의 웹용 클라이언트 ID와는 별도). 이 값은 확장 프로그램의 실제 ID를 등록해야 발급되므로, 로컬에서 `chrome://extensions`의 "압축해제된 확장 프로그램으로 로드"로 먼저 확장 프로그램을 로드해 ID를 확인한 뒤 Google Cloud Console에 등록하는 수동 절차가 필요하다.

- [ ] **Step 2: popup.html에서 이메일/비밀번호 폼 제거, Google 버튼으로 교체**

`popup/popup.html`의 `#login-form` 블록을 다음으로 교체:

```html
  <div id="login-form">
    <div id="error" class="error"></div>
    <button class="btn-primary" id="googleLoginBtn">Google 계정으로 로그인</button>
  </div>
```

(`#email`, `#password` input과 기존 `#loginBtn` 제거)

- [ ] **Step 3: 실패하는 테스트 작성 (popup.js)**

`popup/popup.test.js` 생성:

```js
/**
 * @jest-environment jsdom
 */

beforeEach(() => {
    jest.resetModules();
    document.body.innerHTML = `
        <div id="status" class="status"></div>
        <div id="login-form">
            <div id="error"></div>
            <button id="googleLoginBtn"></button>
        </div>
        <div id="connected-info" style="display:none">
            <button id="logoutBtn"></button>
        </div>
        <div id="info"></div>
    `;

    global.chrome = {
        identity: {
            getAuthToken: jest.fn(),
        },
        runtime: {
            sendMessage: jest.fn().mockResolvedValue({ success: true }),
            lastError: null,
        },
        storage: {
            local: { clear: jest.fn().mockResolvedValue(undefined) },
        },
    };

    global.fetch = jest.fn();
});

test("구글 로그인 버튼 클릭 시 chrome.identity.getAuthToken으로 받은 토큰을 백엔드로 전송한다", async () => {
    chrome.identity.getAuthToken.mockImplementation((options, callback) => {
        callback("fake-google-access-token");
    });

    global.fetch
        .mockResolvedValueOnce({
            ok: true,
            json: () => Promise.resolve({ accessToken: "app-access-token" }),
        })
        .mockResolvedValueOnce({
            ok: true,
            json: () => Promise.resolve({ deviceToken: "device-token", deviceId: 1 }),
        });

    require("./popup.js");
    document.dispatchEvent(new Event("DOMContentLoaded"));

    document.getElementById("googleLoginBtn").click();
    await new Promise(process.nextTick);
    await new Promise(process.nextTick);

    expect(global.fetch).toHaveBeenNthCalledWith(
        1,
        expect.stringContaining("/api/auth/google/token"),
        expect.objectContaining({
            method: "POST",
            body: JSON.stringify({ accessToken: "fake-google-access-token" }),
        })
    );
});
```

- [ ] **Step 4: 테스트 실행 환경 준비 및 실패 확인**

`package.json`의 `devDependencies`에 `jest-environment-jsdom` 추가:

```json
"jest-environment-jsdom": "^29.7.0"
```

Run: `cd study-tracker-extension && npm install && npm test -- popup.test.js`
Expected: FAIL — `popup.js`가 아직 `chrome.identity.getAuthToken`을 쓰지 않음 (기존 이메일/비밀번호 코드라 `googleLoginBtn` 리스너 자체가 없음)

- [ ] **Step 5: popup.js를 Google OAuth 흐름으로 교체**

`popup/popup.js` 전체 교체:

```js
const SERVER_URL = "http://localhost:8080";

document.addEventListener("DOMContentLoaded", async () => {
    await refreshStatus();

    document.getElementById("googleLoginBtn").addEventListener("click", handleGoogleLogin);

    const logoutBtn = document.getElementById("logoutBtn");
    if (logoutBtn) {
        logoutBtn.addEventListener("click", handleLogout);
    }

    document.getElementById("sendBtn")?.addEventListener("click", async () => {
        const result = await chrome.runtime.sendMessage({ type: "SEND_NOW" });
        if (result.success) {
            document.getElementById("info").textContent = "전송 완료!";
        }
    });
});

async function refreshStatus() {
    const status = await chrome.runtime.sendMessage({ type: "GET_STATUS" });

    const statusEl = document.getElementById("status");
    const loginForm = document.getElementById("login-form");
    const connectedInfo = document.getElementById("connected-info");
    const infoEl = document.getElementById("info");

    if (status.isConnected) {
        statusEl.className = "status connected";
        statusEl.textContent = "서버 연결됨";
        loginForm.style.display = "none";
        connectedInfo.style.display = "block";
        infoEl.textContent = `세션 ID: ${status.sessionId || "없음"} | 미전송 로그: ${status.logCount}개`;
    } else {
        statusEl.className = "status disconnected";
        statusEl.textContent = "연결 안 됨 — 로그인이 필요해요";
        loginForm.style.display = "block";
        connectedInfo.style.display = "none";
    }
}

function getGoogleAccessToken() {
    return new Promise((resolve, reject) => {
        chrome.identity.getAuthToken({ interactive: true }, (token) => {
            if (chrome.runtime.lastError || !token) {
                reject(chrome.runtime.lastError || new Error("토큰을 받지 못했습니다."));
                return;
            }
            resolve(token);
        });
    });
}

async function handleGoogleLogin() {
    const errorEl = document.getElementById("error");
    errorEl.textContent = "";

    try {
        // 1. Chrome이 관리하는 Google 계정으로 액세스 토큰 획득
        const googleAccessToken = await getGoogleAccessToken();

        // 2. 백엔드에 액세스 토큰 전달 → 우리 서비스 JWT 발급
        const loginRes = await fetch(`${SERVER_URL}/api/auth/google/token`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ accessToken: googleAccessToken })
        });

        if (!loginRes.ok) {
            errorEl.textContent = "Google 로그인 실패.";
            return;
        }

        const loginData = await loginRes.json();
        const accessToken = loginData.accessToken;

        // 3. device_token 발급
        const deviceRes = await fetch(`${SERVER_URL}/api/auth/device`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Authorization": `Bearer ${accessToken}`
            },
            body: JSON.stringify({
                deviceName: "Chrome Extension",
                deviceType: "PC"
            })
        });

        if (!deviceRes.ok) {
            errorEl.textContent = "기기 등록 실패.";
            return;
        }

        const deviceData = await deviceRes.json();

        // 4. background.js에 저장 요청
        await chrome.runtime.sendMessage({
            type: "SAVE_CONFIG",
            deviceToken: deviceData.deviceToken,
            deviceId: deviceData.deviceId,
            sessionId: null
        });

        await refreshStatus();
    } catch (e) {
        errorEl.textContent = "Google 로그인에 실패했어요.";
        console.error(e);
    }
}

async function handleLogout() {
    await chrome.storage.local.clear();
    await chrome.runtime.sendMessage({
        type: "SAVE_CONFIG",
        deviceToken: null,
        deviceId: null,
        sessionId: null
    });
    await refreshStatus();
}

if (typeof module !== "undefined") {
    module.exports = { handleGoogleLogin, handleLogout, refreshStatus };
}
```

- [ ] **Step 6: 테스트 실행해서 통과 확인**

Run: `cd study-tracker-extension && npm test -- popup.test.js`
Expected: PASS

- [ ] **Step 7: 커밋**

```bash
cd study-tracker-extension
git add manifest.json popup/popup.html popup/popup.js popup/popup.test.js package.json
git commit -m "feat: Extension 로그인을 이메일/비밀번호에서 Google OAuth로 전환"
```

---

## Phase 3 — Push 토큰 등록 & 크로스 디바이스 세션 브로드캐스트

### Task 8: Device push_token 등록 API

**Files:**
- Modify: `src/main/resources/schema.sql`
- Modify: `domain/device/entity/Device.java`
- Modify: `domain/device/repository/DeviceRepository.java`
- Modify: `domain/user/service/AuthService.java`
- Modify: `api/auth/AuthController.java`
- Test: `src/test/java/com/wonbin/study_tracker/domain/user/service/AuthServicePushTokenTest.java`

**Interfaces:**
- Produces: `Device.getPushToken()`, `Device.updatePushToken(String)`, `DeviceRepository.findByUserIdAndPushTokenIsNotNull(Long)`, `AuthService.registerPushToken(Long userId, AuthRequest.PushTokenUpdate)`, `PATCH /api/auth/device/push-token`

- [ ] **Step 1: schema.sql의 devices 테이블에 push_token 컬럼 추가**

`src/main/resources/schema.sql`의 2번 테이블 정의를 다음으로 교체:

```sql
-- 2. 기기
CREATE TABLE IF NOT EXISTS devices (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_id         BIGINT          NOT NULL,
    device_name     VARCHAR(100)    NOT NULL,
    device_type     VARCHAR(10)     NOT NULL COMMENT 'PC / IOS / IPAD / ANDROID',
    device_token    VARCHAR(255)    NOT NULL,
    push_token      VARCHAR(500),
    last_seen       DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_devices_token (device_token),
    FOREIGN KEY (user_id) REFERENCES users (id)
    );
```

- [ ] **Step 2: Device 엔티티에 pushToken 추가**

`domain/device/entity/Device.java`:

```java
package com.wonbin.study_tracker.domain.device.entity;

import com.wonbin.study_tracker.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "devices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_name", nullable = false, length = 100)
    private String deviceName;

    @Column(name = "device_type", nullable = false, length = 10)
    private String deviceType;

    @Column(name = "device_token", nullable = false, unique = true, length = 255)
    private String deviceToken;

    @Column(name = "push_token", length = 500)
    private String pushToken;

    @Column(name = "last_seen")
    private LocalDateTime lastSeen;

    public void updateLastSeen() {
        this.lastSeen = LocalDateTime.now();
    }

    public void updatePushToken(String pushToken) {
        this.pushToken = pushToken;
    }
}
```

- [ ] **Step 3: DeviceRepository에 조회 메서드 추가**

`domain/device/repository/DeviceRepository.java`:

```java
package com.wonbin.study_tracker.domain.device.repository;

import com.wonbin.study_tracker.domain.device.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    Optional<Device> findByDeviceToken(String deviceToken);
    boolean existsByDeviceToken(String deviceToken);
    List<Device> findByUserIdAndPushTokenIsNotNull(Long userId);
}
```

- [ ] **Step 4: 실패하는 단위 테스트 작성 (AuthService.registerPushToken)**

`src/test/java/com/wonbin/study_tracker/domain/user/service/AuthServicePushTokenTest.java` 생성:

```java
package com.wonbin.study_tracker.domain.user.service;

import com.wonbin.study_tracker.api.auth.AuthRequest;
import com.wonbin.study_tracker.domain.device.entity.Device;
import com.wonbin.study_tracker.domain.device.repository.DeviceRepository;
import com.wonbin.study_tracker.domain.user.entity.User;
import com.wonbin.study_tracker.domain.user.repository.UserRepository;
import com.wonbin.study_tracker.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServicePushTokenTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private GoogleIdentityResolver googleIdentityResolver;

    @InjectMocks
    private AuthService authService;

    @Test
    void 본인_소유_기기에는_push_토큰을_저장한다() throws Exception {
        User owner = User.builder().id(1L).email("a@a.com").name("A").googleId("g1").dayChangeHour(5).build();
        Device device = buildDevice(10L, owner);

        when(deviceRepository.findById(10L)).thenReturn(Optional.of(device));

        AuthRequest.PushTokenUpdate request = new AuthRequest.PushTokenUpdate();
        setField(request, "deviceId", "10");
        setField(request, "pushToken", "fcm-token-123");

        authService.registerPushToken(1L, request);

        assertThat(device.getPushToken()).isEqualTo("fcm-token-123");
    }

    @Test
    void 다른_사용자의_기기에는_등록할_수_없다() throws Exception {
        User owner = User.builder().id(1L).email("a@a.com").name("A").googleId("g1").dayChangeHour(5).build();
        Device device = buildDevice(10L, owner);

        when(deviceRepository.findById(10L)).thenReturn(Optional.of(device));

        AuthRequest.PushTokenUpdate request = new AuthRequest.PushTokenUpdate();
        setField(request, "deviceId", "10");
        setField(request, "pushToken", "fcm-token-123");

        assertThatThrownBy(() -> authService.registerPushToken(999L, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Device buildDevice(Long id, User owner) throws Exception {
        Device device = Device.builder()
                .user(owner)
                .deviceName("테스트 기기")
                .deviceType("ANDROID")
                .deviceToken("device-token")
                .build();
        setField(device, "id", id);
        return device;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
```

- [ ] **Step 5: 테스트 실행해서 실패 확인 (컴파일 실패)**

Run: `./gradlew test --tests "*.AuthServicePushTokenTest"`
Expected: FAIL — `AuthService.registerPushToken`이 없어서 컴파일 에러

- [ ] **Step 6: AuthService에 registerPushToken 추가**

`domain/user/service/AuthService.java`에 다음 메서드 추가 (기존 메서드들 아래):

```java
    // 기기 push 토큰 등록/갱신
    @Transactional
    public void registerPushToken(Long userId, AuthRequest.PushTokenUpdate request) {
        Device device = deviceRepository.findById(Long.parseLong(request.getDeviceId()))
                .orElseThrow(() -> new IllegalArgumentException("기기를 찾을 수 없습니다."));

        if (!device.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }

        device.updatePushToken(request.getPushToken());
    }
```

- [ ] **Step 7: AuthController에 엔드포인트 추가**

`api/auth/AuthController.java`에 다음 메서드 추가:

```java
    @PatchMapping("/device/push-token")
    public ResponseEntity<Void> registerPushToken(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AuthRequest.PushTokenUpdate request) {
        authService.registerPushToken(userId, request);
        return ResponseEntity.noContent().build();
    }
```

- [ ] **Step 8: 테스트 실행해서 통과 확인**

Run: `./gradlew test --tests "*.AuthServicePushTokenTest"`
Expected: PASS (2개 테스트)

- [ ] **Step 9: 로컬 DB 재생성 + 커밋**

```sql
DROP DATABASE study_tracker;
CREATE DATABASE study_tracker;
```

```bash
git add src/main/resources/schema.sql src/main/java/com/wonbin/study_tracker/domain/device/entity/Device.java src/main/java/com/wonbin/study_tracker/domain/device/repository/DeviceRepository.java src/main/java/com/wonbin/study_tracker/domain/user/service/AuthService.java src/main/java/com/wonbin/study_tracker/api/auth/AuthController.java src/test/java/com/wonbin/study_tracker/domain/user/service/AuthServicePushTokenTest.java
git commit -m "feat: 기기별 push 토큰 등록 API 추가"
```

---

### Task 9: SessionEventBroadcaster — FCM 발송 + no-op 폴백

**Files:**
- Create: `global/push/PushMessageSender.java`
- Create: `global/push/FirebaseCloudMessagingSender.java`
- Create: `global/push/NoopPushMessageSender.java`
- Create: `global/config/FirebaseConfig.java`
- Create: `domain/session/service/SessionEventType.java`
- Create: `domain/session/service/SessionEventBroadcaster.java`
- Create: `domain/session/service/FcmSessionEventBroadcaster.java`
- Test: `src/test/java/com/wonbin/study_tracker/domain/session/service/FcmSessionEventBroadcasterTest.java`
- Modify: `build.gradle`, `application-local.yaml.example`

**Interfaces:**
- Produces: `SessionEventBroadcaster.broadcast(Long userId, SessionEventType eventType, Long sessionId)` (Task 10에서 SessionService가 호출)
- Consumes: `DeviceRepository.findByUserIdAndPushTokenIsNotNull` (Task 8)

- [ ] **Step 1: build.gradle에 firebase-admin 추가**

`build.gradle`의 `dependencies`에 추가:

```groovy
implementation 'com.google.firebase:firebase-admin:9.10.0'
```

- [ ] **Step 2: application-local.yaml.example에 선택적 설정 예시 추가 (주석 처리)**

`application-local.yaml.example` 맨 아래에 추가:

```yaml
# Firebase push 알림을 실제로 보내려면 아래 주석을 풀고 서비스 계정 키 경로를 지정한다.
# 설정하지 않으면 NoopPushMessageSender가 자동으로 대신 동작한다.
# firebase:
#   credentials-path: /absolute/path/to/firebase-service-account.json
```

(`application.yaml`에는 `firebase.credentials-path`를 추가하지 않는다 — 값이 존재하면 `@ConditionalOnProperty`가 항상 참이 되어 빈 문자열로 Firebase 초기화를 시도하다 실패하기 때문. 완전히 없어야 폴백이 정상 동작한다.)

- [ ] **Step 3: 실패하는 단위 테스트 작성 (FcmSessionEventBroadcaster)**

`src/test/java/com/wonbin/study_tracker/domain/session/service/FcmSessionEventBroadcasterTest.java` 생성:

```java
package com.wonbin.study_tracker.domain.session.service;

import com.wonbin.study_tracker.domain.device.entity.Device;
import com.wonbin.study_tracker.domain.device.repository.DeviceRepository;
import com.wonbin.study_tracker.domain.user.entity.User;
import com.wonbin.study_tracker.global.push.PushMessageSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FcmSessionEventBroadcasterTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private PushMessageSender pushMessageSender;

    @InjectMocks
    private FcmSessionEventBroadcaster broadcaster;

    @Test
    void push_토큰이_있는_모든_기기에_전송한다() throws Exception {
        User user = User.builder().id(1L).email("a@a.com").name("A").googleId("g1").dayChangeHour(5).build();
        Device device1 = buildDeviceWithPushToken(user, "token-1");
        Device device2 = buildDeviceWithPushToken(user, "token-2");

        when(deviceRepository.findByUserIdAndPushTokenIsNotNull(1L))
                .thenReturn(List.of(device1, device2));

        broadcaster.broadcast(1L, SessionEventType.STARTED, 100L);

        verify(pushMessageSender).send(eq("token-1"), any(Map.class));
        verify(pushMessageSender).send(eq("token-2"), any(Map.class));
    }

    @Test
    void 한_기기_전송이_실패해도_나머지_기기_전송은_계속된다() throws Exception {
        User user = User.builder().id(1L).email("a@a.com").name("A").googleId("g1").dayChangeHour(5).build();
        Device device1 = buildDeviceWithPushToken(user, "token-fail");
        Device device2 = buildDeviceWithPushToken(user, "token-ok");

        when(deviceRepository.findByUserIdAndPushTokenIsNotNull(1L))
                .thenReturn(List.of(device1, device2));
        doThrow(new RuntimeException("전송 실패")).when(pushMessageSender).send(eq("token-fail"), any(Map.class));

        broadcaster.broadcast(1L, SessionEventType.ENDED, 100L);

        verify(pushMessageSender).send(eq("token-ok"), any(Map.class));
    }

    private Device buildDeviceWithPushToken(User user, String pushToken) throws Exception {
        Device device = Device.builder()
                .user(user)
                .deviceName("테스트 기기")
                .deviceType("ANDROID")
                .deviceToken("device-token-" + pushToken)
                .build();
        device.updatePushToken(pushToken);
        return device;
    }
}
```

- [ ] **Step 4: 테스트 실행해서 실패 확인 (컴파일 실패)**

Run: `./gradlew test --tests "*.FcmSessionEventBroadcasterTest"`
Expected: FAIL — `SessionEventType`, `SessionEventBroadcaster`, `FcmSessionEventBroadcaster`, `PushMessageSender`가 아직 없어서 컴파일 에러

- [ ] **Step 5: 필요한 클래스 구현**

`domain/session/service/SessionEventType.java`:

```java
package com.wonbin.study_tracker.domain.session.service;

public enum SessionEventType {
    STARTED, ENDED, PAUSED, RESUMED
}
```

`domain/session/service/SessionEventBroadcaster.java`:

```java
package com.wonbin.study_tracker.domain.session.service;

public interface SessionEventBroadcaster {
    void broadcast(Long userId, SessionEventType eventType, Long sessionId);
}
```

`global/push/PushMessageSender.java`:

```java
package com.wonbin.study_tracker.global.push;

import java.util.Map;

public interface PushMessageSender {
    void send(String token, Map<String, String> data);
}
```

`domain/session/service/FcmSessionEventBroadcaster.java`:

```java
package com.wonbin.study_tracker.domain.session.service;

import com.wonbin.study_tracker.domain.device.entity.Device;
import com.wonbin.study_tracker.domain.device.repository.DeviceRepository;
import com.wonbin.study_tracker.global.push.PushMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FcmSessionEventBroadcaster implements SessionEventBroadcaster {

    private final DeviceRepository deviceRepository;
    private final PushMessageSender pushMessageSender;

    @Override
    public void broadcast(Long userId, SessionEventType eventType, Long sessionId) {
        List<Device> devices = deviceRepository.findByUserIdAndPushTokenIsNotNull(userId);

        Map<String, String> data = Map.of(
                "eventType", eventType.name(),
                "sessionId", String.valueOf(sessionId)
        );

        for (Device device : devices) {
            try {
                pushMessageSender.send(device.getPushToken(), data);
            } catch (RuntimeException e) {
                log.warn("push 전송 실패: deviceId={}, error={}", device.getId(), e.getMessage());
            }
        }
    }
}
```

`global/push/FirebaseCloudMessagingSender.java`:

```java
package com.wonbin.study_tracker.global.push;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(FirebaseMessaging.class)
public class FirebaseCloudMessagingSender implements PushMessageSender {

    private final FirebaseMessaging firebaseMessaging;

    @Override
    public void send(String token, Map<String, String> data) {
        Message message = Message.builder()
                .setToken(token)
                .putAllData(data)
                .build();
        try {
            firebaseMessaging.send(message);
        } catch (FirebaseMessagingException e) {
            log.warn("FCM 전송 실패: token={}, error={}", token, e.getMessage());
        }
    }
}
```

`global/push/NoopPushMessageSender.java`:

```java
package com.wonbin.study_tracker.global.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@ConditionalOnMissingBean(PushMessageSender.class)
public class NoopPushMessageSender implements PushMessageSender {

    @Override
    public void send(String token, Map<String, String> data) {
        log.info("[Push 비활성화] token={}, data={} (Firebase 설정 없음)", token, data);
    }
}
```

`global/config/FirebaseConfig.java`:

```java
package com.wonbin.study_tracker.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

@Configuration
public class FirebaseConfig {

    @Bean
    @ConditionalOnProperty(prefix = "firebase", name = "credentials-path")
    public FirebaseMessaging firebaseMessaging(
            @Value("${firebase.credentials-path}") String credentialsPath) throws IOException {

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(new FileInputStream(credentialsPath)))
                .build();

        FirebaseApp app = FirebaseApp.getApps().isEmpty()
                ? FirebaseApp.initializeApp(options)
                : FirebaseApp.getInstance();

        return FirebaseMessaging.getInstance(app);
    }
}
```

- [ ] **Step 6: 테스트 실행해서 통과 확인**

Run: `./gradlew test --tests "*.FcmSessionEventBroadcasterTest"`
Expected: PASS (2개 테스트)

- [ ] **Step 7: 전체 빌드로 Noop 폴백이 기본 동작하는지 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL — `firebase.credentials-path`가 설정되지 않았으므로 `FirebaseMessaging` 빈이 생성되지 않고, `NoopPushMessageSender`가 유일한 `PushMessageSender` 빈으로 등록되어 컨텍스트가 정상 기동한다.

- [ ] **Step 8: 커밋**

```bash
git add build.gradle src/main/resources/application-local.yaml.example src/main/java/com/wonbin/study_tracker/global/push src/main/java/com/wonbin/study_tracker/global/config/FirebaseConfig.java src/main/java/com/wonbin/study_tracker/domain/session/service/SessionEventType.java src/main/java/com/wonbin/study_tracker/domain/session/service/SessionEventBroadcaster.java src/main/java/com/wonbin/study_tracker/domain/session/service/FcmSessionEventBroadcaster.java src/test/java/com/wonbin/study_tracker/domain/session/service/FcmSessionEventBroadcasterTest.java
git commit -m "feat: 세션 이벤트를 등록된 기기에 push로 브로드캐스트하는 컴포넌트 추가"
```

---

### Task 10: SessionService에 브로드캐스트 연결

**Files:**
- Modify: `domain/session/service/SessionService.java`
- Test: `src/test/java/com/wonbin/study_tracker/domain/session/service/SessionServiceBroadcastTest.java`

**Interfaces:**
- Consumes: `SessionEventBroadcaster.broadcast` (Task 9)

- [ ] **Step 1: 실패하는 단위 테스트 작성**

`src/test/java/com/wonbin/study_tracker/domain/session/service/SessionServiceBroadcastTest.java` 생성:

```java
package com.wonbin.study_tracker.domain.session.service;

import com.wonbin.study_tracker.domain.classification.service.ClassificationService;
import com.wonbin.study_tracker.domain.log.repository.ActivityLogRepository;
import com.wonbin.study_tracker.domain.log.repository.BrowserLogRepository;
import com.wonbin.study_tracker.domain.session.dto.SessionRequest;
import com.wonbin.study_tracker.domain.session.entity.StudySession;
import com.wonbin.study_tracker.domain.session.repository.SessionLogNoteRepository;
import com.wonbin.study_tracker.domain.session.repository.StudySessionRepository;
import com.wonbin.study_tracker.domain.user.entity.User;
import com.wonbin.study_tracker.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceBroadcastTest {

    @Mock private StudySessionRepository sessionRepository;
    @Mock private UserRepository userRepository;
    @Mock private ActivityLogRepository activityLogRepository;
    @Mock private BrowserLogRepository browserLogRepository;
    @Mock private SessionLogNoteRepository sessionLogNoteRepository;
    @Mock private ClassificationService classificationService;
    @Mock private SessionEventBroadcaster sessionEventBroadcaster;

    @InjectMocks
    private SessionService sessionService;

    @Test
    void 세션_시작_시_STARTED_이벤트를_브로드캐스트한다() {
        User user = User.builder().id(1L).email("a@a.com").name("A").googleId("g1").dayChangeHour(5).build();

        when(sessionRepository.findByUserIdAndEndedAtIsNull(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sessionRepository.save(any(StudySession.class))).thenAnswer(invocation -> {
            StudySession s = invocation.getArgument(0);
            setId(s, 200L);
            return s;
        });

        SessionRequest.Start request = new SessionRequest.Start();
        setField(request, "studyType", "ONLINE");
        setField(request, "targetSec", 3600);

        sessionService.start(1L, request);

        verify(sessionEventBroadcaster).broadcast(eq(1L), eq(SessionEventType.STARTED), eq(200L));
    }

    @Test
    void 세션_종료_시_ENDED_이벤트를_브로드캐스트한다() throws Exception {
        User user = User.builder().id(1L).email("a@a.com").name("A").googleId("g1").dayChangeHour(5).build();
        StudySession session = StudySession.builder()
                .user(user)
                .studyType("ONLINE")
                .startedAt(LocalDateTime.now().minusHours(1))
                .isAutoEnded(false)
                .totalSec(0).studySec(0).distractSec(0).pauseSec(0)
                .build();
        setId(session, 200L);

        when(sessionRepository.findById(200L)).thenReturn(Optional.of(session));

        sessionService.end(1L, 200L, false);

        verify(sessionEventBroadcaster).broadcast(eq(1L), eq(SessionEventType.ENDED), eq(200L));
    }

    private void setId(StudySession session, Long id) throws Exception {
        setField(session, "id", id);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `./gradlew test --tests "*.SessionServiceBroadcastTest"`
Expected: FAIL — `SessionService` 생성자에 `SessionEventBroadcaster` 파라미터가 없어서 컴파일 에러, 또는 `sessionEventBroadcaster`가 한 번도 호출되지 않아 검증 실패

- [ ] **Step 3: SessionService에 브로드캐스터 연결**

`domain/session/service/SessionService.java`에서 필드 선언부와 `start`/`end`/`pause`/`resume` 메서드를 수정:

```java
    private final StudySessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final ActivityLogRepository activityLogRepository;
    private final BrowserLogRepository browserLogRepository;
    private final SessionLogNoteRepository sessionLogNoteRepository;
    private final ClassificationService classificationService;
    private final SessionEventBroadcaster sessionEventBroadcaster;

    // 세션 시작
    @Transactional
    public SessionResponse.Detail start(Long userId, SessionRequest.Start request) {
        sessionRepository.findByUserIdAndEndedAtIsNull(userId)
                .ifPresent(s -> {
                    throw new IllegalArgumentException("이미 진행중인 세션이 있습니다.");
                });

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        StudySession session = StudySession.builder()
                .user(user)
                .studyType(request.getStudyType())
                .startedAt(LocalDateTime.now())
                .targetSec((request.getTargetSec()))
                .isAutoEnded(false)
                .totalSec(0)
                .studySec(0)
                .distractSec(0)
                .pauseSec(0)
                .build();

        sessionRepository.save(session);
        sessionEventBroadcaster.broadcast(userId, SessionEventType.STARTED, session.getId());
        return SessionResponse.Detail.from(session);
    }

    // 세션 종료
    @Transactional
    public SessionResponse.Detail end(Long userId, Long sessionId, boolean isAutoEnded) {
        StudySession session = getSessionByUser(userId, sessionId);

        validateSessionNotEnded(session);

        session.end(isAutoEnded);
        sessionEventBroadcaster.broadcast(userId, SessionEventType.ENDED, sessionId);

        return SessionResponse.Detail.from(session);
    }

    // 일시정지
    @Transactional
    public SessionResponse.Detail pause(Long userId, Long sessionId) {
        StudySession session = getSessionByUser(userId, sessionId);

        validateSessionNotEnded(session);
        sessionEventBroadcaster.broadcast(userId, SessionEventType.PAUSED, sessionId);

        return SessionResponse.Detail.from(session);
    }

    // 재개 (일시정지 시간 누적)
    @Transactional
    public SessionResponse.Detail resume(Long userId, Long sessionId, int pauseSec) {
        StudySession session = getSessionByUser(userId, sessionId);

        validateSessionNotEnded(session);

        session.addPauseSec(pauseSec);
        sessionEventBroadcaster.broadcast(userId, SessionEventType.RESUMED, sessionId);
        return SessionResponse.Detail.from(session);
    }
```

(파일의 나머지 메서드 — `extend`, `getSession`, `getNotes`, `getActiveSessionOrEmpty`, `getSessionByUser`, `validateSessionNotEnded`, `getLogSummary`, `finalizeSession` — 는 변경하지 않는다.)

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `./gradlew test --tests "*.SessionServiceBroadcastTest"`
Expected: PASS (2개 테스트)

- [ ] **Step 5: 전체 백엔드 테스트 스위트 실행**

Run: `./gradlew test`
Expected: 모든 테스트 PASS (`AuthServiceGoogleTest`, `AuthServicePushTokenTest`, `GoogleIdentityResolverImplTest`, `FcmSessionEventBroadcasterTest`, `SessionServiceBroadcastTest`, 기존 `StudyTrackerApplicationTests` 포함)

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/wonbin/study_tracker/domain/session/service/SessionService.java src/test/java/com/wonbin/study_tracker/domain/session/service/SessionServiceBroadcastTest.java
git commit -m "feat: 세션 시작/종료/일시정지/재개 시 크로스 디바이스 push 브로드캐스트 연결"
```

---

## 완료 후 상태

- Google OAuth가 웹/Extension 양쪽에서 동작하고, 이메일/비밀번호 경로는 완전히 제거됨
- Chrome Extension의 MV3 상태 손실 버그가 고쳐져서 로그인이 임의로 풀리지 않음
- 기기별 push 토큰을 등록할 수 있고, 세션 시작/종료/일시정지/재개가 등록된 모든 기기로 브로드캐스트됨 (Firebase 미설정 시 no-op으로 안전하게 폴백)
- 이 위에 **iOS PWA 플랜**과 **Android 네이티브 앱 플랜**을 별도로 얹을 수 있음 (둘 다 Google 로그인 + push 등록 + `/api/sessions/*` 재사용)
