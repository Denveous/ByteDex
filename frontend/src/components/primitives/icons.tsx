import type { CSSProperties, ReactNode } from "react";

export const Icon = ({
  name,
  size = 16,
  style,
}: {
  name: string;
  size?: number;
  style?: CSSProperties;
}) => {
  const paths: Record<string, ReactNode> = {
    cube: <path d="M8 1.5L14 5l-6 3.5L2 5z M2 5v6l6 3.5M14 5v6L8 14.5" />,
    inbox: (
      <path d="M2.5 9.5h3l1 1.5h3l1-1.5h3M2.5 9.5l1.5-6h8l1.5 6v4a1 1 0 0 1-1 1h-10a1 1 0 0 1-1-1z" />
    ),
    search: (
      <>
        <circle cx="7" cy="7" r="4.5" />
        <path d="M10.5 10.5L14 14" />
      </>
    ),
    eye: (
      <>
        <path d="M1.5 8C3 5 5.2 3.5 8 3.5s5 1.5 6.5 4.5c-1.5 3-3.7 4.5-6.5 4.5S3 11 1.5 8z" />
        <circle cx="8" cy="8" r="2" />
      </>
    ),
    layers: (
      <>
        <path d="M8 1.5L14.5 5L8 8.5L1.5 5z" />
        <path d="M1.5 8.5L8 12L14.5 8.5" />
        <path d="M1.5 12L8 15.5L14.5 12" />
      </>
    ),
    user: (
      <>
        <circle cx="8" cy="6" r="2.5" />
        <path d="M3 14c0-2.8 2.2-5 5-5s5 2.2 5 5" />
      </>
    ),
    upload: (
      <>
        <path d="M8 11V3" />
        <path d="M4.5 6.5L8 3l3.5 3.5" />
        <path d="M2 13.5h12" />
      </>
    ),
    plus: <path d="M8 3v10M3 8h10" />,
    chevron: <path d="M6 4l4 4-4 4" />,
    chevronDown: <path d="M4 6l4 4 4-4" />,
    dot: <circle cx="8" cy="8" r="2" />,
    gear: (
      <>
        <circle cx="8" cy="8" r="2" />
        <path d="M8 1v2M8 13v2M3 8H1M15 8h-2M3.5 3.5l1.4 1.4M11.1 11.1l1.4 1.4M3.5 12.5l1.4-1.4M11.1 4.9l1.4-1.4" />
      </>
    ),
    bookmark: <path d="M4 2h8v12l-4-3-4 3z" />,
    arrowRight: <path d="M3 8h10M9 4l4 4-4 4" />,
    download: (
      <>
        <path d="M8 3v8" />
        <path d="M4.5 7.5L8 11l3.5-3.5" />
        <path d="M2 13.5h12" />
      </>
    ),
    copy: (
      <>
        <rect x="5" y="5" width="8.5" height="8.5" rx="1.5" />
        <path d="M3 10.5V3.5A1 1 0 0 1 4 2.5h7" />
      </>
    ),
    code: (
      <>
        <path d="M5 5L2 8l3 3" />
        <path d="M11 5l3 3-3 3" />
        <path d="M9.5 3.5L6.5 12.5" />
      </>
    ),
    filter: <path d="M2 3h12l-4.5 6v4l-3 1.5v-5.5z" />,
    git: (
      <>
        <circle cx="4" cy="4" r="1.5" />
        <circle cx="4" cy="12" r="1.5" />
        <circle cx="12" cy="8" r="1.5" />
        <path d="M4 5.5v5M5.5 4h2A2.5 2.5 0 0 1 10 6.5V8" />
      </>
    ),
    diff: (
      <>
        <path d="M5 2v12M5 5.5L2 8l3 2.5" />
        <path d="M11 14V2M11 10.5L14 8l-3-2.5" />
      </>
    ),
    activity: <path d="M1.5 8h3l2-5 3 10 2-5h3" />,
    chart: (
      <>
        <path d="M2 13.5h12" />
        <rect x="3" y="9" width="2" height="4" />
        <rect x="7" y="6" width="2" height="7" />
        <rect x="11" y="3" width="2" height="10" />
      </>
    ),
    sparkles: (
      <>
        <path d="M5 2l1 2.5L8.5 5.5 6 6.5 5 9 4 6.5 1.5 5.5 4 4.5z" />
        <path d="M11 8l.6 1.5L13 10l-1.4.5-.6 1.5-.6-1.5L9 10l1.4-.5z" />
      </>
    ),
    flame: (
      <path d="M8 14.5c-3 0-5-2-5-4.5 0-2 1-3 2.5-4 0 2 1 2.5 2 1.5C8.5 6 8 4 9 2c1 2 4 3.5 4 6.5 0 3-2 6-5 6z" />
    ),
    eyedrop: (
      <>
        <path d="M9 7l3 3M3 13l4-1 4-4 1-1c.6-.6.6-1.5 0-2l-.5-.5c-.6-.6-1.5-.6-2 0l-1 1-4 4z" />
      </>
    ),
  };
  return (
    <svg
      className="bd-icon"
      width={size}
      height={size}
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      style={style}
    >
      {paths[name]}
    </svg>
  );
};
