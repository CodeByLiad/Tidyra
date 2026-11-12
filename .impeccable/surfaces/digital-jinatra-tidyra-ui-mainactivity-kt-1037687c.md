---
version: 1
slug: "digital-jinatra-tidyra-ui-mainactivity-kt-1037687c"
primary_target: "app/src/main/java/digital/jinatra/tidyra/ui/MainActivity.kt"
related_targets: []
---

Scope: the Tidyra Android app's working screens — station (home), findings and approval, cleanup result, apps, organise, model, permissions. Visitor mode: Operate.

Audience: the owner of a filling-up Android phone doing periodic maintenance, not emergency triage; plus power users who pair Shizuku. Task: understand what is taking space, approve a specific subset, watch it happen, and file loose files. Content: exact byte counts from a real scan, per-app sizes, hash-verified duplicate sets. Constraints: Material 3 components and navigation throughout, 48dp touch targets, TalkBack, large font scales, works at every permission level and with no AI model present.

## Direction contract

THESIS: Storage as a public sorting station — every byte already belongs to a labelled container, and nothing leaves the site without a signed slip. It refuses the category's gauge-and-rocket "booster" dashboard and equally refuses that dashboard's predictable opposite, the grey screen with one blue accent and a donut chart.

OWN-WORLD: Municipal waste-sorting signage. Hi-vis yellow (#FFC400) on poured-concrete warm grey (#E8E3D9), ink near-black (#171614), and a fixed container palette that codes category everywhere it appears: paper-blue #1C5FA8, glass-green #1E7A4B, metal-grey #6E7378, organic-brown #7A5230, hazard-red #C8321E. Flat enamel plates with a 1px outline, never elevation-as-decoration. Signage caps from the condensed width of the system face for labels; tabular lining numerals for every size. No drawn bins, no illustrated trash cans, no cartoon anywhere — the moment a container becomes an illustration this world is dead.

STORY: The visitor opens the app and reads, in one screen, exactly how full the phone is and what the fullness is made of. They understand each finding as a specific proposal with a specific cost. They approve a subset, see the plan's true total, confirm once, and watch the containers empty by exactly that much.

FIRST VIEWPORT: Ink top app bar with the wordmark in tracked condensed caps, the free figure beneath it at display scale in hi-vis yellow with "FREE OF 228 GB" small beside it. Under it on concrete ground, the sorting station: full-width enamel container bars, one per category, each filled to its true share at the one shared scale used everywhere in the app, label in signage caps on the left and exact size right-aligned, with a hatched zone marking any part Tidyra proposes to remove. Beneath, collection slips — one per finding, hi-vis top rule, title, one line of plain-language detail, and what it frees in condensed numerals at the right. An extended FAB, SCAN, sits above the navigation bar.

FORM: Municipal sorting station, candidate 3 of my ordered grounded list, chosen by the roll over archival appraisal (1) and the port bay plan (2). seed:cd7fa5ae9d3f55f4

RAISES (each named for its donor, written into the build): from the botanical folio, one shared size scale across every screen so a megabyte is the same visual length on the container row, in a list row and in the approval total; from Crouwel, one visible 8dp module with nothing off-grid; from Ikeda, tabular lining numerals and exact sizes, never "about 0.7 GB"; from the transit diagram, interchange markers where a file belongs to two findings, drawn once and counted once.

SIGNATURE INTERACTION: The container row is live. Selecting a finding hatches the exact span it would free inside the affected container, at the shared scale, so the proposal is measured against the whole phone before anything is approved. On confirmation the hatched span retracts to the container's new fill — the bar is the progress indicator, not a separate spinner. Motion grammar: one authored moment, an exponential ease-out retraction of the fill, honoured as an instant cut under reduced motion.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
