import { Separator } from "react-resizable-panels";

const separatorStyle = {
  width: "1px",
  background: "var(--ax-border-neutral-subtle)",
  cursor: "col-resize",
  flexShrink: 0,
} as const;

export default function KolonneSeparator() {
  return <Separator style={separatorStyle} />;
}
