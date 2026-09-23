import { Separator } from "react-resizable-panels";

const separatorStyle = {
  width: "16px",
  background: "transparent",
  cursor: "col-resize",
  position: "relative",
  flexShrink: 0,
} as const;

const linjeStyle = {
  position: "absolute",
  top: 0,
  bottom: 0,
  left: "50%",
  transform: "translateX(-50%)",
  width: "1px",
  background: "var(--ax-border-divider)",
} as const;

const grepStyle = {
  position: "absolute",
  top: "50%",
  left: "50%",
  transform: "translate(-50%, -50%)",
  width: "4px",
  height: "32px",
  borderRadius: "2px",
  background: "var(--ax-border-strong)",
  opacity: 0.5,
} as const;

export default function KolonneSeparator() {
  return (
    <Separator style={separatorStyle}>
      <div style={linjeStyle} />
      <div style={grepStyle} />
    </Separator>
  );
}
