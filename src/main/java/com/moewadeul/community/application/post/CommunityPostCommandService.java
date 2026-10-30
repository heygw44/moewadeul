package com.moewadeul.community.application.post;

import com.moewadeul.common.error.InvalidFieldException;
import com.moewadeul.common.image.application.ImageAttachments;
import com.moewadeul.common.patch.PatchField;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommunityPostCommandService {

    private final CommunityPostWriter communityPostWriter;
    private final CommunityImageAttachments communityImageAttachments;

    public void precheckWrite(long memberId, CommunityPostWriteCommand command) {
        communityImageAttachments.precheck(memberId, null, command.imageIds());
    }

    public long write(long memberId, CommunityPostWriteCommand command) {
        return communityPostWriter.write(memberId, command);
    }

    public CommunityPostItem revise(long memberId, long postId, CommunityPostReviseCommand command) {
        PatchField<List<Long>> imageIds = command.imageIds();
        if (imageIds.present()) {
            communityPostWriter.requireOwnActivePost(memberId, postId);
            if (imageIds.value() == null) {
                throw new InvalidFieldException(
                        ImageAttachments.FIELD, "imageIds는 비울 수 없어요. 이미지를 모두 떼려면 빈 배열을 보내 주세요.");
            }
            communityImageAttachments.precheck(memberId, postId, imageIds.value());
        }
        return communityPostWriter.revise(memberId, postId, command);
    }

    public void delete(long memberId, long postId) {
        communityPostWriter.delete(memberId, postId);
    }
}
