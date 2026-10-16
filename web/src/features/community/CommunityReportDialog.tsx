import { reportComment, reportPost } from '../../api/community';
import { ReportDialog } from '../../components/ReportDialog';
import { REPORT_REASON_OPTIONS } from './communityLabels';

export type CommunityReportTarget = { type: 'post' | 'comment'; id: number };

type CommunityReportDialogProps = {
  target: CommunityReportTarget;
  onClose: () => void;
};

// 글이나 댓글을 신고한다.
export function CommunityReportDialog({ target, onClose }: CommunityReportDialogProps) {
  return (
    <ReportDialog
      targetLabel={target.type === 'post' ? '글' : '댓글'}
      reasonOptions={REPORT_REASON_OPTIONS}
      alreadyReportedCode="COMMUNITY_ALREADY_REPORTED"
      submit={(request) =>
        target.type === 'post' ? reportPost(target.id, request) : reportComment(target.id, request)
      }
      onClose={onClose}
    />
  );
}
