# Frontend code review — 26 October 2026

## Scope

Reviewed the React/TypeScript frontend under `frontend/src`, including authentication and routing, chamber and gyle screens, charting, form validation, and the related API models. The review focused on reproducible behaviour and data correctness.

## Findings

### Moderate

#### M1. Changing chambers reuses the previous gyle chart

**Location:** `src/components/GyleChart.tsx:790-794`, with chart loading in `src/components/GyleChart.tsx:914-916`

`getOrBuildChart` only creates a chart while `chartRef.current` is empty. When navigating from one `/gyle-chart/:chamberId` route to another, React can retain the same component instance, so the effect fetches the new chamber's details but returns the existing chart. The newly fetched readings are then added to series configured for the previous chamber; the title and chamber-specific series can also remain wrong.

**Impact:** Users can see mixed or mislabelled readings when switching chambers without leaving the chart screen.

#### M2. Adding a profile point at an existing interior point mutates a neighbouring point

**Location:** `src/components/TemperatureProfile.tsx:418-422`

When a chart click lands exactly on an interior profile point, the `spaceToRight` calculation assigns `nearestP.x` to the next point's `x` instead of calculating the distance. This changes existing chart data while merely choosing which neighbour to use, potentially creating coincident points and corrupting the profile that will be saved.

**Impact:** A normal add-point action can silently alter an existing temperature-profile point.

#### M3. Leaving the hold temperature blank prevents the gyle form from saving

**Location:** `src/components/GyleManagement.tsx:184`, and the blank-value guidance at `src/components/GyleManagement.tsx:502`

The form schema validates `formTHold` as a number even when it is an empty string. Yup's number conversion treats an empty string as an invalid number, while the UI explicitly permits leaving this field blank to hold the current temperature. As the schema validates this field for every mode, an empty value can prevent submission even when Hold mode is not selected.

**Impact:** Users cannot reliably save a gyle with the documented blank hold-temperature value.

#### M4. An end time without a start time can throw during validation

**Location:** `src/components/GyleManagement.tsx:141-148`

The first test reports that an end time requires a start time, but the following test still calls `dateTimeStrToMillis` on the empty start-time value whenever the end time is valid. That helper throws for an invalid date string, so entering an end time while leaving the start time blank can raise an exception rather than showing only a form validation message.

**Impact:** This invalid input can interrupt form validation and prevent normal correction of the fields.

#### M5. Fridge chart loading can throw on the final generation-4 log

**Location:** `src/components/GyleChart.tsx:845-854`

The fridge log filter evaluates `getGen(readingsLogs[i + 1])` for every generation-4 log. If the final chronological log is generation 4, `i + 1` is out of range and `getGen` attempts to call `substring` on `undefined`.

**Impact:** A fridge with generation-4 logs at the end of its log list can fail before its historical chart is populated.

### Minor

#### m1. Creating a gyle submits a literal placeholder name

**Location:** `src/components/CreateGyle.tsx:58-60`

The create request always sends `newName: 'TODO **** Choose a name ****'`; this screen has no name field. The user is redirected to gyle management and can rename it there, but the new gyle is initially created with the placeholder.

**Impact:** Users can unintentionally leave a visibly incorrect gyle name if they do not notice and correct it after creation.

#### m2. Failed gyle/profile loads can leave the screen loading indefinitely

**Location:** `src/components/GyleManagement.tsx` (the `getGyle().then(...)` load flow), `src/components/TemperatureProfile.tsx:529-538`

These load flows handle authentication failures inside their request helpers, but do not handle other rejected requests at the call site. A network error or server error therefore leaves the loading state active and produces an unhandled rejection instead of presenting an error and a retry path.

**Impact:** A transient backend or network failure can make these screens appear permanently stuck until the user navigates away or reloads.

## Validation

This was a read-only code review; no application code was changed. Automated frontend tests/build were not run because `frontend/node_modules` is not present in the worktree.
