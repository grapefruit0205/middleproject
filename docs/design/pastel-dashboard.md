# 파스텔 일정 대시보드

2026-09-08 — 사용자가 첨부한 생산성 대시보드 이미지를 디자인 방향으로 적용했다.

- 크림 배경, 라벤더 사이드바, 라운드 카드와 부드러운 입체 그림자, 분홍·민트·버터색 요약 카드를 사용한다.
- 화면의 숫자는 저장된 일정으로 계산한다. 검색·상태별 메뉴·서울 시간 기준 달력 날짜 선택으로 목록을 좁힌다.
- 기존 등록·수정·취소·이력·인증 동작을 유지한다. 모바일은 상단 메뉴와 2열 요약 카드, 1열 콘텐츠로 바뀐다.
- 주간 생산성이나 완료율처럼 실제 데이터가 없는 지표를 만들지 않는다.
- 로고 `remi.`와 토끼는 이 디자인의 표시용 요소이며 계정 이름이나 사용자 프로필을 의미하지 않는다.

## 이미지 리소스

내장 이미지 생성 도구로 제작한 `frontend/public/images/remi-bunny.png`를 사이드바, 로그인 화면, 응원 카드에 사용한다. 외부 이미지 URL에 의존하지 않는다. 원본 생성 파일은 보존했다.

생성 프롬프트:

> Use case: stylized-concept. Asset type: decorative mascot image for a warm pastel Korean personal deadline planner website. A single adorable cream-white bunny made of soft matte 3D polymer clay, round chubby body, long floppy upright ears with pale peach interiors, tiny dark chocolate eyes, soft pink cheeks, a friendly tiny smile. Sitting and holding a lavender-purple heart in front of its belly, little cream feet, soft handmade sculpted texture. Full body centered, fills 82% of image with generous clean padding. Warm soft studio light and subtle ambient occlusion. Background solid very pale warm lavender #efe6f7, with two very small floating butter yellow clay stars near the bunny shoulders. A cohesive premium cute claymorphism aesthetic, not a flat cartoon, no text, no letters, no logo, no watermark, square composition. This is a website illustration asset, not a UI screenshot.

## 확인 범위

프론트엔드 12개 테스트, TypeScript 검사와 production build를 실행했다. 기존 로컬 Apache/Tomcat/PostgreSQL 환경에서 인증 후 검색, 달력 월 이동·날짜 필터·초기화를 확인했다. 실제 CSS 너비 320px와 약 400px에서 문서 가로 넘침이 없었고 이미지가 정상 로딩됐다. API 또는 AWS 구성은 이번 디자인 변경 대상에 포함되지 않는다.
