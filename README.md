# GPS Helper Pro v7

대리운전용 GPS 안정화 보조 앱입니다.

## v7 주요 기능

- 듀얼 위치 품질 판단: GPS/네트워크 위치 품질을 기준으로 최신 좌표 관리
- AI GPS 이상 감지: 좌표 튐, 순간이동, 속도 급변, 장시간 미갱신 감지
- 실시간 GPS 정확도 그래프
- SOS 현재 좌표 공유
- 초절전 모드: 정차 중 점검 부담 완화
- 대리운전 강화 모드
- GPS OFF/권한/절전 설정 자동 안내
- 운행 리포트: 이동거리, 평균 정확도, 끊김 횟수, 이상 감지 횟수
- Google Drive 등 공유 앱을 통한 백업/복원
- 백그라운드 Foreground Service + 상태바 알림

## GitHub Actions

저장소에 업로드하면 `Build APK and AAB` 워크플로에서 APK/AAB를 생성합니다.
설치용은 `apk/debug/app-debug.apk`를 사용하세요.
`app-release-unsigned.apk`는 미서명 릴리스 APK입니다.
