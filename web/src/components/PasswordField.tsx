import { useState } from 'react';
import type { ComponentProps } from 'react';
import { Icon } from './icons';
import { TextField } from './TextField';

type PasswordFieldProps = Omit<ComponentProps<typeof TextField>, 'type' | 'trailing'>;

export function PasswordField(props: PasswordFieldProps) {
  const [visible, setVisible] = useState(false);

  return (
    <TextField
      {...props}
      type={visible ? 'text' : 'password'}
      trailing={
        <button
          type="button"
          aria-label="비밀번호 보기"
          aria-pressed={visible}
          onClick={() => setVisible((current) => !current)}
          className="flex h-12 w-12 items-center justify-center rounded-field text-ink-muted hover:text-ink"
        >
          <Icon name={visible ? 'eyeOff' : 'eye'} />
        </button>
      }
    />
  );
}
