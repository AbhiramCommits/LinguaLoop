interface MasteryRingProps {
  label: string;
  percent: number;
  size?: number;
}

export function MasteryRing({ label, percent, size = 96 }: MasteryRingProps) {
  const stroke = 8;
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const clamped = Math.max(0, Math.min(100, Math.round(percent)));
  const offset = circumference * (1 - clamped / 100);

  return (
    <div
      className="mastery-ring"
      role="progressbar"
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={clamped}
      aria-label={`${label}: ${clamped}% mastered`}
    >
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden="true" focusable="false">
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="#dde3e9"
          strokeWidth={stroke}
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="#123b63"
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
        />
      </svg>
      <span className="mastery-ring-label">
        {clamped}
        <span className="mastery-ring-percent">%</span>
      </span>
    </div>
  );
}
