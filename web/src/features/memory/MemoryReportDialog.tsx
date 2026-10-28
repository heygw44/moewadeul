import { reportMemory } from '../../api/memories';
import { ReportDialog } from '../../components/ReportDialog';
import { REPORT_REASON_OPTIONS } from '../community/communityLabels';

type MemoryReportDialogProps = {
  memoryId: number;
  onClose: () => void;
};

export function MemoryReportDialog({ memoryId, onClose }: MemoryReportDialogProps) {
  return (
    <ReportDialog
      targetLabel="기록"
      reasonOptions={REPORT_REASON_OPTIONS}
      alreadyReportedCode="MEMORY_ALREADY_REPORTED"
      submit={(request) => reportMemory(memoryId, request)}
      onClose={onClose}
    />
  );
}
