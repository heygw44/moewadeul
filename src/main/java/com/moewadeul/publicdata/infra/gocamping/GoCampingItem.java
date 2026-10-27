package com.moewadeul.publicdata.infra.gocamping;

// 고캠핑은 모든 값을 문자열로 주고, 값이 없으면 빈 문자열, 필드가 없으면 null이다. mapX는 경도, mapY는 위도다.
// syncStatus로 관찰한 값은 A(추가), U(수정), D(삭제)이고, lctCl은 "산,숲,계곡"처럼 쉼표로 여러 값을 준다.
public record GoCampingItem(
        String contentId,
        String facltNm,
        String addr1,
        String addr2,
        String mapX,
        String mapY,
        String induty,
        String tel,
        String homepage,
        String toiletCo,
        String swrmCo,
        String wtrplCo,
        String brazierCl,
        String sbrsCl,
        String sbrsEtc,
        String posblFcltyCl,
        String posblFcltyEtc,
        String animalCmgCl,
        String syncStatus,
        String manageSttus,
        String hvofBgnde,
        String hvofEnddle,
        String modifiedtime,
        String lctCl) {}
