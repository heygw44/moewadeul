import { useEffect, useId, useRef } from 'react';
import type { ReactNode } from 'react';
import { Icon } from './icons';

type DialogProps = {
  title: string;
  onClose: () => void;
  children: ReactNode;
};

export function Dialog({ title, onClose, children }: DialogProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    dialog.showModal();
    return () => {
      dialog.close();
      previous?.focus();
    };
  }, []);

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      className="m-auto w-[calc(100%-2rem)] max-w-md rounded-sheet bg-surface p-0 text-ink shadow-raise backdrop:bg-ink/40"
    >
      <div className="flex items-center justify-between gap-2 pt-3 pr-3 pl-6">
        <h2 id={titleId} className="text-xl font-bold text-ink">
          {title}
        </h2>
        <button
          type="button"
          aria-label="닫기"
          onClick={onClose}
          className="flex size-11 shrink-0 items-center justify-center rounded-control text-ink hover:bg-fill"
        >
          <Icon name="close" size={24} />
        </button>
      </div>
      <div className="max-h-[70dvh] overflow-y-auto px-6 pt-2 pb-6">{children}</div>
    </dialog>
  );
}
