# PC 에이전트 Google OAuth 로그인 설계

## 배경 및 문제 정의

이번 세션에서 백엔드(`study-tracker`), 웹(`study-tracker-web`), Chrome Extension(`study-tracker-extension`)의 로그인을 이메일/비밀번호에서 Google OAuth로 전환했다. 그 과정에서 `/api/auth/login`(이메일/비밀번호 로그인) 엔드포인트를 완전히 삭제했는데, PC 에이전트(`study-tracker-agent`, Python)의 `agent.py`는 이 삭제된 엔드포인트를 여전히 호출하고 있어 더 이상 로그인(=device_token 발급)이 불가능한 상태다.

PC 에이전트는 이 프로젝트의 세 번째 데이터 수집 클라이언트로, PC에서 포커스 앱을 감지해 앱 사용 시간을 백엔드로 전송하는 역할을 한다. device_token은 만료가 없고 OS 키링(Windows Credential Manager)에 저장되므로, 로그인은 기기당 최초 1회만 필요하다 — 이 설계는 그 "최초 로그인"이 내부적으로 어떻게 동작하는지만 바꾸며, 언제 로그인이 필요한지(최초 1회)나 로그인 이후의 흐름(세션 동기화, 로그 전송 등)은 바꾸지 않는다.

## 목표

- `agent.py login`이 Google OAuth로 로그인해서 device_token을 발급받을 수 있게 한다.
- 웹/Extension이 이미 쓰고 있는 백엔드의 검증 경로 중 더 견고한 쪽(ID 토큰 검증)을 재사용해서, 새로운 검증 로직을 백엔드에 추가하지 않는다.

## 비목표

- `agent.py run()`(포커스 앱 감지, 세션 동기화, 로그 전송) 로직은 전혀 건드리지 않는다.
- `config.py`의 키링 저장/조회 로직은 그대로 재사용한다.
- 모바일(iOS/Android) 클라이언트는 이 설계 범위 밖이다.

## 설계

### 1. Google Cloud Console — 데스크톱 앱 클라이언트 신규 생성

기존에 만든 "웹 애플리케이션"/"Chrome 앱" 타입 클라이언트와 별도로, "데스크톱 앱" 타입 OAuth 2.0 클라이언트를 하나 더 만든다. 데스크톱 앱 타입은 로컬 루프백(loopback) 리다이렉트 방식을 기본 지원해서, 특정 포트를 미리 등록해둘 필요가 없다.

데스크톱 앱 타입은 client secret도 함께 발급된다 — OAuth 표준상 Authorization Code 교환 단계에 필요하기 때문이다(Google 문서에서도 이 secret은 배포된 앱 안에 들어가므로 완전한 기밀로 취급하지 않는다고 명시하지만, 어쨌든 코드 상에는 필요하다).

### 2. PC 에이전트 — 로그인 흐름 교체

`requirements.txt`에 `google-auth-oauthlib`를 추가한다(이 패키지가 필요한 하위 의존성 `google-auth`, `requests-oauthlib`, `oauthlib`를 함께 끌어온다).

`agent.py`의 `login()` 함수를 다음 흐름으로 교체한다:

1. 환경변수 `GOOGLE_OAUTH_CLIENT_ID`, `GOOGLE_OAUTH_CLIENT_SECRET`을 읽는다. 둘 중 하나라도 없으면 명확한 한국어 안내 메시지를 출력하고 종료한다(기존 에러 메시지 스타일 유지).
2. `google_auth_oauthlib.flow.InstalledAppFlow.from_client_config(...)`으로 플로우를 만들고, `openid email profile` 스코프를 요청한다. client config는 별도 JSON 파일 없이 코드 내에서 딕셔너리로 구성한다(client_id/secret은 이미 환경변수로만 다루므로 파일로 따로 관리할 필요가 없다).
3. `flow.run_local_server(port=0)`을 호출한다 — 로컬 임시 포트에 서버를 띄우고 기본 브라우저를 자동으로 열어 Google 로그인 화면으로 이동시킨다. 사용자가 로그인/동의를 완료하면 로컬 서버가 리다이렉트를 받아 자동으로 흐름을 마친다(사용자가 코드를 복사/붙여넣기 할 필요 없음).
4. 발급받은 credentials에서 ID 토큰을 꺼낸다.
5. 기존 흐름과 동일하게 이어간다: ID 토큰을 `POST /api/auth/google`로 보내 우리 서비스 액세스 토큰을 받고, 그 액세스 토큰으로 `POST /api/auth/device`를 호출해 device_token을 발급받아 `config.save_token()`/`config.save_device_id()`로 저장한다.

사용자가 브라우저에서 로그인을 취소하거나 흐름이 실패하면 `run_local_server()`가 예외를 던진다 — 이를 잡아서 기존 다른 실패 케이스(로그인 실패, 기기 등록 실패)와 같은 스타일의 에러 메시지를 출력한다.

`login()`의 시그니처는 더 이상 이메일/비밀번호가 필요 없으므로 `login(device_name: str)`로 바뀐다. CLI 사용법(`python agent.py login <device_name>`)도 이에 맞게 갱신한다.

### 3. 백엔드 — audience 리스트 확장

`GoogleAuthConfig`의 `GoogleIdTokenVerifier` 빈은 현재 `google.oauth.client-id`(웹용) 하나만 audience로 허용하고 있다. 여기에 PC 에이전트의 client-id도 추가로 허용하도록 확장한다:

- 새 설정값 `google.oauth.pc-agent-client-id` 추가 (`application.yaml`에 `${GOOGLE_OAUTH_PC_AGENT_CLIENT_ID}`로, `application-local.yaml.example`에 예시 값으로)
- `GoogleAuthConfig.googleIdTokenVerifier(...)`가 두 client-id를 모두 받아 `setAudience(List.of(webClientId, pcAgentClientId))`로 구성

기존 `google.oauth.client-id`, `google.oauth.extension-client-id` 설정값은 이름과 용도가 그대로 유지된다 — `extension-client-id`는 액세스 토큰(tokeninfo) 검증 경로에서만 쓰이므로 이번 변경과 무관하다.

`/api/auth/google` 엔드포인트, `AuthController`, `AuthService`는 코드 변경이 필요 없다 — 이미 "유효한 ID 토큰이면 사용자를 찾거나 만든다"는 범용 로직이라, audience 리스트만 늘어나면 PC 에이전트가 보낸 토큰도 그대로 통과한다.

### 4. 테스트 방향

- 백엔드: `GoogleAuthConfig`가 여러 audience를 정확히 설정하는지 확인하는 가벼운 단위 테스트를 추가한다(예: 빌더가 구성한 `GoogleIdTokenVerifier`의 audience 목록에 두 client-id가 모두 포함되는지 확인 — 실제 Google 서명 검증까지는 필요 없음).
- PC 에이전트: 이 저장소에는 기존 테스트 인프라가 없다. `login()` 내부에서 OAuth 흐름과 무관하게 분리 가능한 순수 로직(예: 환경변수 누락 시 처리, 백엔드 응답 처리)이 있다면 최소한의 테스트를 추가하고, `run_local_server()` 자체(실제 브라우저/네트워크 필요)는 테스트 대상에서 제외한다.

## 재사용 vs 신규

| 재사용 | 신규 |
|---|---|
| `/api/auth/google` 엔드포인트, `AuthService.loginWithGoogleIdToken` | `google-auth-oauthlib` 의존성 (PC 에이전트) |
| `POST /api/auth/device` 흐름 (device_token 발급) | Google Cloud "데스크톱 앱" OAuth 클라이언트 |
| `config.py`의 키링 저장/조회 | `google.oauth.pc-agent-client-id` 설정값 |
| | `GoogleAuthConfig`의 다중 audience 지원 |

## 열린 질문

- 이 설계는 이번 세션에서 다루지 않음: iOS/Android 클라이언트가 추가될 때도 같은 audience 리스트 확장 패턴을 재사용할 수 있을 것으로 보이나, 그 시점에 다시 확인이 필요하다.
