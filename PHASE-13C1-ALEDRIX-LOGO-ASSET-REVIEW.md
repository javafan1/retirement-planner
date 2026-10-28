# Phase 13C1 — Aledrix Logo Asset Review

## Approval gate

Stage 13C1 artwork preparation only. These are **raster reconstruction candidates awaiting visual approval**, not an exact original vector master. No branding configuration, application title, startup lifecycle, splash screen, settings, financial code or PDF exporter was changed. Stages 13C2–13C5 have not begun.

Used the built-in image-generation tool under the imagegen skill, then JavaFX scene snapshots to review the actual PNG composited over Light/Dark backgrounds. No CLI/API fallback, new dependency, enlarged source-panel crop or off-white rectangular background was used.

## Source and fidelity

The inspected source is `src/main/resources/images/branding/aledrix-logo-reference.png`, 1536 × 1024. Selected design is **#5, second row, first column**. The bottom icon examples and other numbered designs were not used as substitutes.

Reference SHA-256 before preparation: `805458B37F394CDB0C2343E53C8ECECE6CECD2857A9906F1060256E1B1E8306F`. Verify the same digest at completion. The reference is preserved byte-for-byte.

The isolated mark preserves the recognizable A silhouette, triangular negative space, interrupted upper-right stroke, navy lower-right leg, blue sweep and muted-gold sweep. It contains no ALEDRIX wordmark, tagline, number or panel. Product name/tagline remain separate for later configuration.

The reference symbol is only approximately 190 × 135 pixels. Consequently exact original curves, stroke intersections, edge antialiasing and gradient stops cannot be recovered or certified. The reconstruction has somewhat brighter cyan highlights and visible gradient/facet interpretation compared with the small source; the dark navy leg is intentionally subdued on Dark. These differences need the user's visual decision. There is no SVG/vector fidelity claim and no vector master was fabricated.

An initial candidate had unwanted edge/speckle appearance in direct transparency inspection. A targeted cleanup iteration removed interior artifacts and reduced faceting. The second candidate was inspected directly and through JavaFX against both backgrounds. At actual preview size no visible rectangular fringe, text, clipped arc or foreign colored speckle was observed. This is visual review evidence, not a guarantee about every source-resolution pixel. If closer-than-reconstruction identity is required, obtain the original master or use a designer-guided manual trace from higher-resolution artwork.

## Files

- `src/main/resources/images/branding/aledrix-logo.png` — transparent 1498 × 1050 PNG candidate. SHA-256 `5F94B4338D3B809987ED8254FA8247C4FF98D7BB02A261DF244D2DCBD1983C7C`.
- `src/main/resources/images/branding/aledrix-icon.png` — square padded transparent 1254 × 1254 PNG candidate. SHA-256 `394CC7F9D611F93E9E0BEAF8314ED55BCD60E576A021B885A1ADC19D3BFB5E98`.
- `docs/branding/13c1/aledrix-logo-light.png` — actual JavaFX preview over `#f5f7fa`.
- `docs/branding/13c1/aledrix-logo-dark.png` — actual JavaFX preview over `#1e222a`.
- `docs/branding/13c1/aledrix-icon-light.png` — actual JavaFX icon preview on Light.
- `docs/branding/13c1/aledrix-icon-dark.png` — actual JavaFX icon preview on Dark.
- This report.

The logo resources are not referenced by application code yet. Their presence under Maven resources is preparation only, not startup/title/icon integration. Preview files remain outside production resources.

## Generation specifications and provenance

Built-in tool reference: the original concept sheet. Initial prompt selected only #5 and described its source coordinates, A geometry, blue/navy gradients, tapered blue arc over the muted-gold arc, transparent negative spaces, no wordmark/background/tile/shadow or other designs, symbol width at least 1024px, and modest padding. Output was an isolated high-resolution raster reconstruction.

Cleanup prompt used that candidate plus the original sheet, requested removal of cyan/blue/red/green edge speckles and hole artifacts, smoother original-like gradients instead of extra facets, the same silhouette/occlusion and muted champagne gold, genuine transparency, and no redesign or new elements. The second output is the review logo.

Icon prompt uses only the review logo, requests a 1024 × 1024 square alpha canvas with centered identical-proportion mark at about 80% width, unchanged geometry/colors/negative space, and no tile, background, shadow, border or text. Because it is a separate generated derivative rather than deterministic vector export, inspect its consistency independently; do not assume pixel identity with the splash PNG.

The tool returned 1254 × 1254 rather than the requested 1024 square. The first icon had insufficient padding. A canvas-padding iteration requested at least 15% transparent left/right margin with a mark at most 70% width. That iteration brightened the navy; a final color-reference correction restored darker navy/blue using the review splash logo. Final icon retains some different arc spacing/proportions (notably the upper-right stroke/blue-arc gap) and is not an exact same-pixel padded derivative. **This is an unresolved visual consistency limitation requiring approval, not a certified production match.** Do not integrate the two as interchangeable masters without the user's decision. A designer/original master and deterministic exports are recommended if exact cross-size identity is required.

Final splash prompt (cleanup pass, original sheet and initial reconstruction supplied):

> Use case: precise-object-edit. Input1 is an almost correct reconstruction of Logo #5; input2 is original reference, only #5 secondrowfirstcolumn is relevant. Clean up input1 for production. Remove ALL stray cyan/blue/red/green speckles and edge noise, especially within triangular hole and gaps around arcs, eliminate multicolor fringes. Output genuinely transparent clean negative space and clean antialiased edges. Preserve basic geometry, blue sweeping arc, gold sweeping arc, separated upper-right stroke and navy lower-right leg. Replace added diagonal faceting inside left/right A strokes with smooth blue-to-navy gradients matching original #5; avoid changing silhouette or creating new decorations. Keep gold restrained muted champagne not bright yellow. No text, no panel, no tile, no shadows. Output one isolated large symbol with transparent alpha and modest padding, crisp smooth geometry, not photographic texture. This is careful cleanup of same Logo #5, no redesign.

Final icon prompt (padded icon target and splash color reference supplied):

> Use case: precise-object-edit. Input1 square padded icon is edit target. Input2 rectangular Logo #5 is authoritative COLOR REFERENCE. Change ONLY COLORS inside input1 to exactly match input2: DARK NAVY lower right leg and lower left stroke, darker blue upper right stroke, cyan-to-navy left stroke. Keep blue arc and muted gold same as reference. The current icon is too bright cobalt overall. Match input2 restrained dark navy/teal-blue gradients; no saturated royal/cobalt blue in lower leg. Preserve input1 canvas, mark geometry, negative space, centered scale and generous transparent padding exactly. No new shapes, text, tile, background, white fill, shadow or glow. Output same square transparent icon with reference color treatment.

The full operational prompts and intermediate candidates remain in session tool history/default generated-image storage. Final project-bound deliverables are copied into this workspace; they do not depend on files outside the project at runtime. Generated-image provenance metadata may be embedded by the image tool; no original vector source is asserted from it.

## Verification and boundaries

JavaFX loaded both PNGs and rendered four 760 × 580 snapshots, with a 520-logical-pixel image view, preserving ratio. All four actual previews were opened and inspected. The dark navy components have less contrast on Dark, as expected from the original design; no alternative palette is silently substituted. Native 100–200% DPI, taskbar rendering and small-icon legibility are not certified by these previews.

Both PNGs decode successfully, have a genuine alpha channel and zero-alpha corner pixels. Full logo alpha inspection counted 1,176,346 fully transparent pixels, 393,670 partially transparent pixels and 2,884 fully opaque pixels; alpha >=128 bounds x=84–1435, y=95–974. The generated mark includes partial alpha, not merely a white background. These checks do not certify edge quality at every scale. Original sheet digest at completion exactly matches the recorded original. `git diff --check` passed. Staging index remains empty. Maven tests: not run in this stage (asset preparation and preview harness only; no application/test code changes).

Ignored preparation artifacts: `target/BrandAssetPreview.java`, `target/aledrix-preview.log`, `target/aledrix-icon-preview.log`, `target/aledrix-logo-review-light.png`, `target/aledrix-logo-review-dark.png`, `target/aledrix-icon-review-light.png` and `target/aledrix-icon-review-dark.png`. These are a standalone preview harness, not application/test source modifications. No Maven financial regression suite is required for this isolated asset-only stage; no production integration is claimed verified.

Preserved all pre-existing Phase 13B source/test/CSS modifications and architecture reports. No staging, commit, push, reset, clean or discard operation was performed. Request visual approval of both the reconstructed mark and padded icon before Stage 13C2 or any integration.
