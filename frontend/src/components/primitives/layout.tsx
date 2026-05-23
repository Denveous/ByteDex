import type { CSSProperties, HTMLAttributes, ReactNode } from "react";

type Div = HTMLAttributes<HTMLDivElement>;

export const Row = ({
  children,
  style,
  gap = 8,
  ...rest
}: { children?: ReactNode; style?: CSSProperties; gap?: number } & Div) => (
  <div style={{ display: "flex", alignItems: "center", gap, ...style }} {...rest}>
    {children}
  </div>
);

export const Col = ({
  children,
  style,
  gap = 8,
  ...rest
}: { children?: ReactNode; style?: CSSProperties; gap?: number } & Div) => (
  <div
    style={{ display: "flex", flexDirection: "column", gap, ...style }}
    {...rest}
  >
    {children}
  </div>
);

export const Screen = ({
  children,
  style,
}: {
  children?: ReactNode;
  style?: CSSProperties;
}) => (
  <div className="bd-screen" style={style}>
    {children}
  </div>
);

export const Card = ({
  children,
  style,
  hover = true,
  padding = 16,
  ...rest
}: {
  children?: ReactNode;
  style?: CSSProperties;
  hover?: boolean;
  padding?: number;
} & Div) => (
  <div
    className={`bd-card ${hover ? "bd-card-hover" : ""}`}
    style={{ padding, ...style }}
    {...rest}
  >
    {children}
  </div>
);
