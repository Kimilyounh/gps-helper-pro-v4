# GPS Init Ultimate Pro v2.0 APK

안드로이드가 허용하는 범위 안에서 GPS를 새로 요청하고, GNSS 위성 상태와 정확도를 확인해 내비게이션 운행 중 위치 수신을 안정화하는 보조 앱입니다.

## 포함 기능
- GPS BOOST: 고정밀 GPS 재수신
- 1회 재측정: 오래된 위치 캐시 대신 최신 위치 요청
- GPS 품질 점수 0~100 표시
- 위도, 경도, 정확도, 속도, 고도, 측정 시간 표시
- GNSS 위성 상태 표시
  - 보이는 위성 수
  - 실제 Fix에 사용 중인 위성 수
  - 평균 신호 세기 dB-Hz
  - GPS / GLONASS / Galileo / BeiDou / QZSS 구분
- 자동 복구
  - 정확도가 30m 이상이면 재측정 요청
  - GPS 상태 변화 표시
- 주행 모드
  - 위치 갱신 간격을 빠르게 변경
- 백그라운드 GPS 유지 서비스
- 지도 앱으로 현재 위치 열기
- 위치 설정 바로가기
- 최근 GPS 로그 표시 및 저장

## 제한 사항
- 일반 앱은 GPS 칩 자체를 강제로 리셋하거나 시스템 A-GPS 데이터를 직접 삭제할 수 없습니다.
- 이 앱은 위치 재요청, GNSS 상태 확인, 백그라운드 유지, 정확도 기반 재측정으로 GPS 수신을 보조합니다.

## GitHub Actions APK 빌드
1. ZIP 압축 해제
2. GitHub 새 저장소 생성
3. 압축 해제한 전체 파일 업로드
4. GitHub 저장소의 Actions 탭 이동
5. `Build GPS Init Ultimate Pro v2 APK` 선택
6. `Run workflow` 실행
7. 완료 후 Artifacts에서 `gps-init-ultimate-pro-v2-apk` 다운로드
8. 압축을 풀면 `app-debug.apk`가 있습니다.

## 권장 사용법
1. 휴대폰 위치 켜기
2. 앱 설치 후 위치 권한 허용
3. 실외 또는 창가에서 `GPS BOOST 시작`
4. 정확도 5~20m 이내로 내려가면 내비게이션 실행
5. 장시간 운행 시 `백그라운드 유지 ON` 사용

## 콜마노 연동 안내
콜마노 앱의 정확한 패키지명을 알면 버튼에서 자동 실행 기능을 추가할 수 있습니다.
예: `com.example.navigation` 형식의 패키지명 필요
