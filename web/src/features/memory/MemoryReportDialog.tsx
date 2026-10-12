import { reportMemory } from '../../api/memories';
import { ReportDialog } from '../../components/ReportDialog';
import { REPORT_REASON_OPTIONS } from '../community/communityLabels';

type MemoryReportDialogProps = {
  memoryId: number;
  onClose: () => void;
};

// 남의 공개 발자국을 신고한다. 사유는 커뮤니티 신고와 같다.
export function MemoryReportDialog({ memoryId, onClose }: MemoryReportDialogProps) {
  return (
    <ReportDialog
      targetLabel="발자국"
      reasonOptions={REPORT_REASON_OPTIONS}
      alreadyReportedCode="MEMORY_ALREADY_REPORTED"
      submit={(request) => reportMemory(memoryId, request)}
      onClose={onClose}
    />
  );
}
