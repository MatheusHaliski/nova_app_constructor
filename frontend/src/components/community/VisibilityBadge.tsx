import { NovaIcon } from "@/components/icons/NovaIcon";
import { VISIBILITY, type Visibility } from "@/lib/community-types";

export function VisibilityBadge({ visibility }: { visibility: Visibility }) {
  const v = VISIBILITY[visibility];
  const open = visibility !== "PRIVATE";
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-semibold ${open ? "bg-primary/10 text-primary" : "bg-surface-2 text-muted"}`}>
      <NovaIcon name={v.icon} size={12} /> {v.label}
    </span>
  );
}
