# ADR-007: Mermaid-in-Markdown instead of draw.io files

**Status:** Accepted · 2026-07-18 · *Supersedes the starter repo's `architecture/*.drawio` placeholders*

## Context
The starter repo carried five empty `.drawio` files. Diagram files that live outside the docs they illustrate rot: they're not diffable, not reviewable in PRs, and require a separate tool to edit.

## Decision
All diagrams are Mermaid code blocks inside the Markdown documents they belong to. GitHub renders them natively.

## Alternatives
- **draw.io** — richer visuals, but binary-ish XML diffs and guaranteed drift.
- **PlantUML** — comparable; Mermaid wins on zero-setup GitHub rendering.
- **Structurizr/C4 tooling** — powerful model-based docs; overkill for this repo's size.

## Consequences
+ Diagrams versioned, reviewed, and updated in the same commit as the text.
− Mermaid's layout control is limited; complex diagrams get split rather than styled.
