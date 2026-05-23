import type { CSSProperties, ReactNode } from "react";
import { Icon } from "./icons";

export const Btn = ({
  children,
  primary,
  ghost,
  sm,
  lg,
  block,
  leading,
  trailing,
  style,
}: {
  children?: ReactNode;
  primary?: boolean;
  ghost?: boolean;
  sm?: boolean;
  lg?: boolean;
  block?: boolean;
  leading?: ReactNode;
  trailing?: ReactNode;
  style?: CSSProperties;
}) => {
  const cls = ["bd-btn"];
  if (primary) cls.push("primary");
  if (ghost) cls.push("ghost");
  if (sm) cls.push("sm");
  if (lg) cls.push("lg");
  if (block) cls.push("block");
  return (
    <span className={cls.join(" ")} style={style}>
      {leading}
      {children}
      {trailing}
    </span>
  );
};

export const Chip = ({
  children,
  active,
  kind,
  solid,
  outline,
  sq,
  sm,
  removable,
  dot,
  leading,
  style,
}: {
  children?: ReactNode;
  active?: boolean;
  kind?: string;
  solid?: boolean;
  outline?: boolean;
  sq?: boolean;
  sm?: boolean;
  removable?: boolean;
  dot?: boolean;
  leading?: ReactNode;
  style?: CSSProperties;
}) => {
  const cls = ["bd-chip"];
  if (active) cls.push("active");
  if (kind) cls.push(kind);
  if (solid) cls.push("solid");
  if (outline) cls.push("outline");
  if (sq) cls.push("sq");
  if (sm) cls.push("sm");
  if (removable) cls.push("removable");
  if (dot) cls.push("dot");
  return (
    <span className={cls.join(" ")} style={style}>
      {leading}
      {children}
    </span>
  );
};

export const Dir = ({ dir }: { dir: string }) => (
  <span className={`bd-dir ${dir.toLowerCase()}`}>{dir}</span>
);

export const Input = ({
  icon,
  placeholder,
  value,
  mono,
  lg,
  trailing,
  style,
  width,
}: {
  icon?: string;
  placeholder?: string;
  value?: string;
  mono?: boolean;
  lg?: boolean;
  trailing?: ReactNode;
  style?: CSSProperties;
  width?: number | string;
}) => (
  <span
    className={`bd-input ${mono ? "mono" : ""} ${lg ? "lg" : ""}`}
    style={{ width, ...style }}
  >
    {icon && <Icon name={icon} size={14} />}
    <span
      style={{
        flex: 1,
        color: value ? "var(--fg-primary)" : "var(--fg-muted)",
        overflow: "hidden",
        textOverflow: "ellipsis",
        whiteSpace: "nowrap",
      }}
    >
      {value || placeholder}
    </span>
    {trailing}
  </span>
);

export const SearchInput = ({
  placeholder = "Search...",
  width = 280,
  value,
}: {
  placeholder?: string;
  width?: number;
  value?: string;
}) => (
  <Input
    icon="search"
    placeholder={placeholder}
    value={value}
    width={width}
    trailing={<span className="bd-kbd">⌘K</span>}
  />
);

export const Kbd = ({ children }: { children?: ReactNode }) => (
  <span className="bd-kbd">{children}</span>
);

export const Avatar = ({
  initials = "F",
  size,
  lg,
  xl,
  hue = 0,
}: {
  initials?: string;
  size?: number;
  lg?: boolean;
  xl?: boolean;
  hue?: number;
}) => {
  const cls = ["bd-avatar"];
  if (lg) cls.push("lg");
  if (xl) cls.push("xl");
  const bg = `linear-gradient(135deg, hsl(${hue}, 60%, 78%) 0%, hsl(${
    hue + 40
  }, 60%, 82%) 100%)`;
  return (
    <span
      className={cls.join(" ")}
      style={{ width: size, height: size, background: bg }}
    >
      {initials}
    </span>
  );
};

export const Hex = ({
  children,
  style,
}: {
  children?: ReactNode;
  style?: CSSProperties;
}) => (
  <span className="bd-mono" style={{ fontSize: 12.5, ...style }}>
    {children}
  </span>
);
