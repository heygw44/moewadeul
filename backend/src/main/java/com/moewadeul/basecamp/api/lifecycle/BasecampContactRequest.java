package com.moewadeul.basecamp.api.lifecycle;

import com.moewadeul.basecamp.domain.Basecamp;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record BasecampContactRequest(
        @Schema(description = "오픈채팅 링크 같은 연락 수단. https URL만 받는다.", example = "https://open.kakao.com/o/example")
        @NotBlank(message = "연락 수단을 적어 주세요.")
        @Size(max = Basecamp.CONTACT_INFO_MAX_LENGTH, message = "연락 수단은 255자 이하여야 해요.")
        @URL(protocol = "https", message = "https://로 시작하는 주소를 적어 주세요.")
        String contactInfo) {}
