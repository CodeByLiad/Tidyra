---
name: Tidyra
description: An editorial-minimal storage station — warm paper, hairline rules, one crimson accent, and every figure set in Playfair.
colors:
  paper: "#F3EDE3"
  paper-night: "#131110"
  ink: "#15110E"
  ink-night: "#F2EDE6"
  muted: "#6B635A"
  muted-night: "#9A9188"
  crimson: "#C41733"
  crimson-night: "#FF5A72"
  rule: "#DED6C8"
  rule-night: "#2A2724"
  olive: "#4C6B4F"
  amber: "#9A6B14"
  surface-container-lowest: "#FAF6EE"
  surface-container-lowest-night: "#0B0A09"
  outline-variant: "#EDE8E0"
  outline-variant-night: "#23201C"
typography:
  wordmark:
    fontFamily: "Playfair Display"
    fontSize: "28sp"
    fontWeight: 700
    letterSpacing: "-0.02em"
  figure:
    fontFamily: "Playfair Display"
    fontSize: "64sp"
    fontWeight: 700
    lineHeight: "64sp"
    letterSpacing: "-0.04em"
  container-label:
    fontFamily: "Playfair Display"
    fontSize: "26sp"
    fontWeight: 700
    letterSpacing: "-0.02em"
  section:
    fontFamily: "Playfair Display"
    fontSize: "24sp"
    fontWeight: 700
    letterSpacing: "-0.02em"
  amount:
    fontFamily: "Playfair Display"
    fontSize: "15sp"
    fontWeight: 700
  stencil:
    fontFamily: "system sans (Material 3 default)"
    fontSize: "11sp"
    fontWeight: 500
    letterSpacing: "0.14em"
  headline-small:
    fontFamily: "Playfair Display"
    fontSize: "24sp"
    fontWeight: 700
  title-large:
    fontFamily: "Playfair Display"
    fontSize: "22sp"
    fontWeight: 700
  body-large:
    fontFamily: "system sans (Material 3 default)"
    fontSize: "16sp"
    fontWeight: 400
  body-medium:
    fontFamily: "system sans (Material 3 default)"
    fontSize: "14sp"
    fontWeight: 400
  body-small:
    fontFamily: "system sans (Material 3 default)"
    fontSize: "12sp"
    fontWeight: 400
rounded:
  half: "4dp"
  pip: "9999dp"
  app-icon: "25% of icon size"
spacing:
  half: "4dp"
  u: "8dp"
  x2: "16dp"
  x3: "24dp"
  x4: "32dp"
components:
  button-primary:
    backgroundColor: "{colors.crimson}"
    textColor: "{colors.paper}"
    typography: "{typography.stencil}"
    rounded: "{rounded.half}"
  button-primary-disabled:
    backgroundColor: "{colors.rule}"
    textColor: "{colors.muted}"
    typography: "{typography.stencil}"
    rounded: "{rounded.half}"
  button-outlined:
    textColor: "{colors.muted}"
    typography: "{typography.stencil}"
    rounded: "{rounded.half}"
    height: "52dp"
  text-button-stencil:
    textColor: "{colors.crimson}"
    typography: "{typography.stencil}"
    padding: "0dp"
  tab:
    textColor: "{colors.muted}"
    typography: "{typography.body-large}"
    padding: "8dp 0dp"
  tab-active:
    textColor: "{colors.crimson}"
    typography: "{typography.body-large}"
    padding: "8dp 0dp"
  category-row:
    textColor: "{colors.ink}"
    typography: "{typography.amount}"
    padding: "8dp 24dp"
    height: "48dp"
  finding-row:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    typography: "{typography.title-large}"
    padding: "16dp 24dp"
  approval-bar:
    backgroundColor: "{colors.surface-container-lowest}"
    textColor: "{colors.ink}"
    typography: "{typography.stencil}"
    padding: "16dp 24dp"
  capacity-rule:
    backgroundColor: "{colors.rule}"
    height: "16dp"
---

# Design System: Tidyra

> The direction contract for this build (`.impeccable/surfaces/digital-jinatra-tidyra-ui-mainactivity-kt-1037687c.md`) carries an OWN-WORLD block describing municipal waste-sorting signage — hi-vis yellow on concrete grey, enamel plates, a fixed container palette. **That block is superseded.** The editorial-minimal world recorded below was pinned by the client from a reference screenshot, and that pin wins. The contract's THESIS, STORY and RAISES survived the change and are enforced in the shipped build; only its material and palette were replaced.

## Overview

**Creative North Star: "The Printed Page of Record"**

Tidyra is set, not laid out. The screen behaves like a page in a well-printed broadsheet: warm paper ground, a high-contrast serif carrying the wordmark and every number, generous margins, and hairline rules doing the structural work that cards, boxes and shadows do in a stock Material app. Nothing is enclosed. Sections are separated by a 1px rule and air, the way a printed page separates them.

The category this app sits in — Android "boosters" — is loud, gauged and rocket-animated, and it exaggerates findings for a living. The answer here is typographic authority instead of urgency: one crimson accent used sparingly, exact figures rather than rounded reassurance, and no illustration, no gauge, no donut chart, no mascot. Dynamic colour is deliberately switched off; a wallpaper-derived scheme would replace crimson with something arbitrary and the page would lose its one voice.

Density is reading density. A row is at least 48dp because a finger needs it, but the vertical rhythm is set by one 8dp module and the type carries the hierarchy. Dark mode is the same page printed on near-black stock: the same rules, the same rhythm, a lighter crimson so the accent still measures.

**Key Characteristics:**
- Warm paper ground, never pure white; near-black ink, never pure black.
- Playfair Display for the wordmark, every heading and every figure.
- One crimson accent, read from the theme, never hardcoded.
- Hairline rules and space instead of cards, borders and elevation.
- Exact sizes, always — `756 MB`, `<1%`, never a rounded `0%`.
- Marks are drawn on a Canvas at one stroke ratio; no glyph ever stands in for an icon.

## Colors

A warm, printed palette: aged paper, near-black ink, one vivid crimson, and two muted status hues borrowed from a proof-reader's pencil.

### Primary
- **Press Crimson** (`{colors.crimson}` light / `{colors.crimson-night}` dark): the single accent. It marks the hero free-space figure, the active tab and its underline, primary buttons, the proposed-removal span inside the capacity rule, the selection ring fill, and the live action word in the masthead (`SCAN` / `RESCAN`). It is measured, not decorative: the light value replaced an earlier `#D6203F` that read 4.36:1 on paper, under the floor for the 11sp tracked caps it sits on. The shipped value measures 5.1:1 and is still the vivid end of crimson, not the muted one.

### Secondary
- **Field Olive** (`{colors.olive}`): the "safe" risk pip on a finding row, and the accent pip on a completed-cleanup notice. Reassurance that does not shout.
- **Caution Amber** (`{colors.amber}`): the "your call" risk pip, and the accent pip on permission notices that are a limitation rather than a blocker.

### Neutral
- **Warm Paper** (`{colors.paper}`) / **Night Stock** (`{colors.paper-night}`): the ground. Background, surface and the low surface container are all the same paper; depth is not made of tone here.
- **Press Ink** (`{colors.ink}`) / **Paper-White Ink** (`{colors.ink-night}`): all primary text, and the used segments of the capacity rule.
- **Pencil Grey** (`{colors.muted}`) / **Night Pencil** (`{colors.muted-night}`): secondary text, stat labels, rank numbers, risk words, the inactive tab, body copy under a title.
- **Hairline** (`{colors.rule}` / `{colors.rule-night}`): every rule, divider and leader dot, and — doubling as the highest surface container — the unused track of the capacity rule and the disabled button fill.
- **Faint Hairline** (`{colors.outline-variant}` / `{colors.outline-variant-night}`): the lighter divider that closes a list row, so row separators read quieter than section rules.
- **Lifted Paper** (`{colors.surface-container-lowest}` / `{colors.surface-container-lowest-night}`): the approval bar's ground — the one surface in the app tonally separated from the page, because it floats over scrolling content.

### Named Rules
**The Theme-Read Accent Rule.** The accent is always read from `MaterialTheme.colorScheme.primary`, never from the `Crimson` constant. Hardcoding it once ran dark mode at the light value and measured roughly 3.2:1 against the night ground. If a composable names a crimson, it is a bug.

**The One Voice Rule.** Crimson is the only accent. Olive and amber are 6dp status pips and never expand into surfaces, text colours or fills. Dynamic/wallpaper colour is off by construction.

**The Measured Colour Rule.** A colour earns its value by contrast measurement against its own ground, at the smallest size it is used on. The tracked 11sp caps are the governing case.

## Typography

**Display Font:** Playfair Display (bundled variable font, OFL; the `Editorial` family, instanced at 400/500/600/700 through `FontVariation`)
**Body Font:** the platform sans (Material 3 default), used for body copy, labels and tab words
**Label voice:** the same sans at 11sp, weight 500, `0.14em` tracking, set in caps

**Character:** A high-contrast transitional serif against a plain sans — a magazine's masthead over its body column. The serif carries authority and every quantity; the sans carries the plain-language sentences and the small tracked caps that act as buttons and tags.

### Hierarchy
- **Figure** (700, 64sp, -0.04em): the one number a screen is about — free space on the station. One per screen, in crimson.
- **Wordmark** (700, 28sp, -0.02em): "Tidyra" in the masthead, marked as a heading for TalkBack.
- **Container Label** (700, 26sp, -0.02em): the statistic in a column of statistics (Apps / App data / Media).
- **Section** (700, 24sp, -0.02em): section headings — "Storage by category", "Suggested cleanups".
- **Headline Small / Title Large** (Playfair, 700): the line under the hero figure; finding, notice and app titles.
- **Amount** (Playfair, 700, 15sp): the value at the end of a row, including its percentage.
- **Body Large / Medium / Small** (sans, 400, 16/14/12sp): tab words, row labels, plain-language detail, footnotes.
- **Stencil** (sans, 500, 11sp, 0.14em, caps): the only sans voice that shouts — button text, text-button actions, risk words, the `SELECTED` field label.

### Named Rules
**The Playfair Figure Rule.** Every quantity in the app — hero figure, stat, row amount, percentage — is set in Playfair through `StationType.amount` or a larger serif role. A number in the sans face is a number that escaped the system.

**The Exact Figure Rule.** Sizes are formatted through `Format` and nowhere else: one decimal at GB scale, none below it, an em dash for unknown, and `<1%` for anything real but under one percent. A cleaner that says "about 0.7 GB" where it means `756 MB` is rounding in its own favour. Every figure also has a spoken form (`Format.spokenSize`) so TalkBack reads "756 megabytes", not letters.

**The Stencil-Is-An-Action Rule.** Tracked 11sp caps mean "this is a control or a status tag" — button labels, `SCAN`, `BACK`, `SAFE`, `SELECTED`. They are never used as a kicker or an eyebrow above a heading.

## Layout

One 8dp module governs the page. `Module` defines `half` (4dp), `u` (8dp), `x2` (16dp), `x3` (24dp), `x4` (32dp), and every gap in the app is one of those. The only values off the module are Material's own interactive minimums — `heightIn(min = 48.dp)` on a tappable row, `minimumInteractiveComponentSize()` around a selection ring — plus a few optical constants that are not spacing at all: the 1dp hairline, the 2dp tab underline, the 6dp status pip, the 20dp rank column, the 16dp capacity-rule height.

The page margin is `x3` (24dp) on every screen, left and right: the masthead wordmark, the tab strip's first and last label, the section headings, the rules and the rows all sit on the same two lines. Sections are stacked in a single `LazyColumn`; there is no grid and no card. Vertical rhythm inside a section is `u` or `x2`; between sections it is a rule plus `x3` above the next heading.

System bars are the system's. The app paints its background under them and puts nothing there. The masthead carries `statusBarsPadding()` for the whole tabbed shell, so **sections under the masthead must not add their own** — doing so double-pads them. A detail screen replaces the masthead, so the detail host carries `statusBarsPadding()` itself. Lists end above the gesture bar via `navigationBarsPadding()` rather than running under it; the approval bar carries its own.

### Named Rules
**The 8dp Module Rule.** Every gap is a multiple of 8dp, with 4dp as the half-step. The only exceptions are Material's interactive minimums. Nothing sits off-grid "just this once".

**The One Margin Rule.** 24dp, both sides, every screen. A section that indents differently from the masthead breaks the page.

**The Status Bar Ownership Rule.** Exactly one composable in any stack owns `statusBarsPadding()` — the masthead in the tabbed shell, the detail host on a detail screen. Never both.

## Elevation & Depth

**There are no shadows in Tidyra.** Not one `shadow`, `elevation` or `tonalElevation` is set anywhere in the UI. Depth is typographic and linear: a 1px rule, a change of type size, and space. The single tonal move in the whole app is the approval bar, which sits on lifted paper because it floats over scrolling content and needs to read as a separate plate; even that keeps a hairline above it rather than a shadow.

### Named Rules
**The Hairline Rule.** Structure is made of 1px rules and space. If a region needs to be set apart, give it a rule and a margin — not a card, not a border box, not a shadow.

**The No-Enclosure Rule.** Nothing in this app is enclosed in a container. A card is the wrong gesture on a printed page.

## Shapes

Flat and rectilinear. The only corner radius in regular use is 4dp (`{rounded.half}`) on buttons and filter chips — just enough that a crimson rectangle does not read as a slab, not enough to read as a pill. Everything else is square: the capacity rule, its segments, the tab underline, every divider.

Circles exist for exactly two jobs: the 6dp status pip beside a risk word or notice title, and the round selection controls (the 22dp `SelectionRing`, the 20dp item tick). App icons are clipped to a quarter of their own size, following the platform's icon geometry rather than inventing one.

The capacity rule's internal hairlines are *cut out of* the segment just drawn rather than added after it, so the rule still measures the device exactly; a segment too narrow to spare the 1dp gap keeps its full width.

## Components

### Buttons
- **Shape:** barely-softened corners (4dp).
- **Primary:** crimson fill read from the theme's primary, paper text, `stencil` caps label — `SCAN THIS PHONE`, `MOVE TO TRASH`.
- **Disabled:** the hairline colour as fill, pencil-grey text. No opacity trick.
- **Outlined:** 1dp outline-coloured stroke, pencil-grey `stencil` label, 52dp tall. Used for secondary model actions.
- **Text button:** zero content padding so the label sits on the page's own margin, `stencil` caps, crimson when it is the live action and pencil grey when it is not. This — not the filled button — is the app's default affordance.

### Chips
- **Style:** Material filter chip at 4dp radius; selected takes the crimson container with a paper label, unselected keeps the paper ground and the hairline outline.

### Cards / Containers
There are none. A grouping is a section heading, a rule above it, and 24dp margins. Rows separate from each other with the faint hairline, sections with the full one.

### Navigation
- **Style:** five text tabs — Storage, Apps, Organise, Trash, AI — spread across the full width between the page margins, closed by a full-bleed rule.
- **Typography:** body-large at Medium weight, never bold.
- **States:** colour and a 2dp underline carry the state, both eased over 220ms. The underline is *always drawn*, exactly as wide as its label, and only coloured when active — a rule that appears and disappears changes the column's width and shoves the other tabs sideways.
- **Behaviour:** no ripple — a grey box flashing behind a word is the stock affordance, and the colour and the rule already answer the tap. Each column takes `IntrinsicSize.Min` width and each label holds one line, ellipsising rather than wrapping. Announced with `Role.Tab` and its selected state.

**The Stable Tab Weight Rule.** A tab's font weight never changes with state. Bold text is wider; an active tab would resize and shove every other tab along the row. Colour and the underline carry state. Nothing else does.

### The Capacity Rule (signature)
One stacked 16dp bar, drawn once per screen, representing the whole device: ink segments in list order, crimson for any span the current plan proposes to free, the hairline track showing what is free, 1dp gaps cut out of the segments. A footnote under it states in words what the rule is.

It replaced a column of per-row bars. Seven per-row tracks against a whole-device denominator is an honest scale rendered dishonestly: at real magnitudes five of the seven categories drew as empty track, so "present but small" read as "absent" — the one thing the size scale forbids. Stacking them keeps every share true against the same denominator, shows the free span that a column of used sizes cannot, and hands the detail to the figures.

**The One Denominator Rule.** Every quantity drawn anywhere in the app — a category share, a finding, the span a plan would free — is drawn through `SizeScale` against the device's total capacity, and is never re-normalised per screen. A megabyte is the same visual length everywhere. A real but tiny quantity still gets a visible sliver (floor 0.4%), capped so the floor can never make something look larger than the next real value up.

### The Category Row (signature)
A contents-page line: rank number in a 20dp column, label, a dotted leader running across to the figure, the proposed amount in crimson if any, then the exact size and its percentage in Playfair, then a drawn chevron. At least 48dp tall, and announced to TalkBack as one sentence.

### The Finding Row (signature)
A proposal, not a notification. Left: a 22dp `SelectionRing` inside a full interactive target — per-item approval is the product's premise, so its control gets a real target rather than a 20dp dot. App findings (uninstall, cache) get a chevron instead, because they are acted on one at a time and never trashed. Middle: title, one line of plain detail, then a 6dp risk pip, its risk word in `stencil` caps, and — when the finding overlaps another — `· SHARED ITEMS COUNTED ONCE`. Right: what it frees, in crimson when selected, with the item count beneath. Closed by a faint hairline.

**The Counted-Once Rule.** A file that belongs to two findings is counted once in the plan, and the overlap is stated on the row rather than quietly resolved. Totals across findings must reconcile with the approval bar.

### The Marks
`CheckMark`, `DashMark`, `ChevronMark`, `ChevronLeftMark` and `SelectionRing` are drawn on a Canvas at one stroke ratio (0.12 of their size, 1.6× for the tick and dash), with round caps, at 12–22dp. An empty selection state reads as empty: an outline ring, never a filled grey disc.

**The Drawn Mark Rule.** No Unicode glyph, icon font or vector pack ever stands in for an icon. A glyph borrowed from the text font is not an icon: set in Playfair, an arrow arrives as a thin double chevron at the font's weight and matches nothing else on the page. A new mark is authored in `Marks.kt` at the same stroke ratio or it does not ship.

### The Approval Bar
The one floating plate. Lifted-paper ground, a hairline above it, navigation-bar padding, `SELECTED` in `stencil` caps over the plan's total and item count, and the crimson primary button at the right. It slides in from the bottom when the plan is non-empty and slides out when it empties. The label reads `SELECTED`, never `APPROVED` — the tap beside it is what approves it.

### The Notice
A 6dp pip in the relevant status colour, a Playfair title, plain body copy, and an optional crimson `stencil` text action. No box, no tint, no banner.

## Do's and Don'ts

### Do:
- **Do** read the accent from `MaterialTheme.colorScheme.primary` in every composable.
- **Do** set every quantity in Playfair, through `StationType.amount` or a larger serif role.
- **Do** format every size and percentage through `Format`, and give every figure a `spokenSize` for TalkBack.
- **Do** draw every bar through `SizeScale` against the device's total capacity.
- **Do** keep every gap on the 8dp module, with 4dp as the only half-step.
- **Do** separate with a 1px rule and 24dp margins.
- **Do** author new marks in `Marks.kt` at the 0.12 stroke ratio.
- **Do** give a tappable row at least 48dp and a selection control a full interactive target.
- **Do** let exactly one composable in a stack own `statusBarsPadding()`.
- **Do** state an overlap on the row when a finding shares items with another.

### Don't:
- **Don't** hardcode `Crimson` or any other palette constant in a composable; it breaks dark mode's contrast.
- **Don't** enable dynamic colour. Crimson is the only accent.
- **Don't** put a quantity on a per-screen or per-row axis; there is one denominator.
- **Don't** round a size for comfort, and never print `0%` for something that exists.
- **Don't** add a shadow, an elevation or a card. Rules and space carry structure.
- **Don't** change a tab's font weight, or any text's weight, to signal state.
- **Don't** use a Unicode glyph, icon font or vector pack as an icon.
- **Don't** add ripple to a text tab.
- **Don't** add `statusBarsPadding()` to a section rendered under the masthead.
- **Don't** let a corner radius exceed 4dp outside the circular selection controls and status pips.
