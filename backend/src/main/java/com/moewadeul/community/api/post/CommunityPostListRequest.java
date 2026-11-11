package com.moewadeul.community.api.post;

import com.moewadeul.common.paging.PageSlice;
import com.moewadeul.community.application.post.CommunityPostListQuery;
import com.moewadeul.community.application.post.CommunitySearchType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CommunityPostListRequest(
        Long spotId,

        @Schema(description = "true면 좋아요를 5개 이상 받은 인기글만 준다.")
        Boolean popular,

        @Schema(description = "검색 대상. keyword가 없으면 무시하고, 보내지 않으면 제목과 본문을 찾는다.")
        CommunitySearchType searchType,

        @Schema(description = "검색어. 앞뒤 공백을 지운 뒤 2~50자이고, 없거나 공백뿐이면 검색하지 않는다.")
        @Size(min = 2, max = 50, message = "검색어는 2자 이상 50자 이하여야 해요.")
        String keyword,

        @Schema(description = "직전 응답의 nextCursor. 보내지 않으면 첫 페이지를 주고, 보내면 그 id보다 오래된 글부터 준다.")
        @Positive(message = "커서는 1 이상이어야 해요.")
        Long cursor,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 해요.")
        @Max(value = PageSlice.MAX_SIZE, message = "페이지 크기는 " + PageSlice.MAX_SIZE + " 이하여야 해요.")
        Integer size) {

    public CommunityPostListRequest {
        keyword = keyword == null || keyword.isBlank() ? null : keyword.strip();
    }

    CommunityPostListQuery toQuery() {
        return new CommunityPostListQuery(spotId, Boolean.TRUE.equals(popular), searchType, keyword);
    }

    int sizeOrDefault() {
        return size == null ? PageSlice.DEFAULT_SIZE : size;
    }
}
