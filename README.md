# CommuteFlow

서울·경기의 버스 정류소와 지하철역을 출근/퇴근 프로필로 나눠 추적하는 Android 앱입니다. Kotlin, Jetpack Compose, Material 3로 작성했습니다.

## 주요 기능

- 출근·퇴근 프로필별 교통편 최대 3개 저장
- 서울·경기 버스 정류소 검색 후 표시할 버스 노선 복수 선택
- 지하철역 검색 후 노선과 상행·내선/하행·외선 방향 선택
- 프로필별 다크/라이트 테마(기본값: 출근 다크, 퇴근 라이트)
- 기준 시각 이전에는 출근, 같거나 이후에는 퇴근 탭으로 시작(기본 12:00)
- 교통편 순서 변경 및 삭제
- 설정과 선택 항목을 앱 재실행 후에도 유지
- 버스 번호·남은 정거장·가장 빠른 첫 도착만 간결하게 표시
- 정류소의 모든 버스 노선을 선택 목록에 표시하고, 홈에는 사용자가 선택한 노선만 표시
- 앱이 화면에 보일 때 30초마다 자동 갱신

선택한 버스는 현재 홈 추적 목록을 의미합니다. Android 백그라운드 알림은 알림 시점/조건이 정해진 뒤 별도 기능으로 추가할 수 있습니다.

## API 설정

프로젝트 루트의 `local.properties`에 필요한 값을 추가합니다. 키는 모두 발급 화면의 Decoding 키를 사용합니다.

```properties
PUBLIC_DATA_SERVICE_KEY=공공데이터포털_통합_키
SEOUL_TRANSIT_PROXY_URL=https://배포한-worker.workers.dev
```

### 경기 버스

공공데이터포털에서 아래 두 서비스를 활용 신청합니다.

- [경기도 버스정류소 조회](https://www.data.go.kr/data/15080666/openapi.do)
- [경기도 버스도착정보 조회](https://www.data.go.kr/data/15080346/openapi.do)

두 서비스가 같은 공공데이터포털 키에 승인되어 있어야 검색과 도착 조회가 모두 동작합니다.

### 서울 버스

서울 버스는 서울시 공식 API가 평문 HTTP만 제공되므로 앱에서 직접 호출하지 않고 지하철과 같은 HTTPS Worker를 사용합니다.

- [서울특별시 정류소정보조회 서비스](https://www.data.go.kr/data/15000303/openapi.do)

이 서비스에 `PUBLIC_DATA_SERVICE_KEY`가 활용 승인되어 있어야 합니다. 같은 키를 Cloudflare Worker의 `PUBLIC_DATA_SERVICE_KEY` secret에도 등록합니다. Worker는 정류소명 검색, 경유노선, 실시간 도착정보를 서울시 공식 API에서 중계합니다.

### 지하철

서울시 지하철 원본 API는 현재 HTTP만 제공하므로 앱이 키를 평문으로 보내지 않도록 HTTPS Worker 예제를 함께 제공합니다.

1. 서울 열린데이터광장에서 역 검색용 일반 인증키와 실시간 지하철 인증키를 준비합니다.
2. 프로젝트 루트의 `seoul-transit-proxy-worker.js`와 `wrangler.toml`을 Cloudflare Workers에 배포합니다.
3. Worker secret `SEOUL_OPEN_DATA_KEY`, `SEOUL_SUBWAY_KEY`, `PUBLIC_DATA_SERVICE_KEY`를 등록합니다.
4. 배포된 HTTPS 주소를 `SEOUL_TRANSIT_PROXY_URL`에 넣습니다.

```bash
npx wrangler secret put SEOUL_OPEN_DATA_KEY
npx wrangler secret put SEOUL_SUBWAY_KEY
npx wrangler secret put PUBLIC_DATA_SERVICE_KEY
npx wrangler deploy
```

Worker는 역 검색을 1시간, 실시간 도착을 20초 캐시합니다. 서울시 공식 안내상 서울 밖 일부 수도권 구간은 실시간 도착정보가 제공되지 않을 수 있습니다.

## 빌드

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

키나 Worker 주소가 없어도 빌드는 성공하며, 해당 제공자를 검색하거나 조회할 때 필요한 설정명을 화면에 표시합니다. application ID와 Kotlin 패키지는 `com.jcheol.commuteflow`, 앱 표시명과 Gradle 프로젝트명은 `CommuteFlow`로 통일했습니다. 패키지명이 변경되어 이전 설치본과는 별도 앱으로 설치되며 저장 설정은 자동 이전되지 않습니다.

> `PUBLIC_DATA_SERVICE_KEY`는 경기 버스 호출을 위해 APK에도 포함됩니다. 스토어 배포본에서는 경기 API도 서버 프록시로 옮기고 호출 제한·키 회전을 적용하는 구성을 권장합니다.
