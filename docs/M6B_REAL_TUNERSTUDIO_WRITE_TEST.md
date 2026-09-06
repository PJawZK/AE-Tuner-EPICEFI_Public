# M6B real TunerStudio working-tune write test

Status: **PASS — COMPLETED IN REAL TUNERSTUDIO**

M6B verifies that the first writable AE Tuner recipe can change exactly its reviewed working-tune target, read it back, and restore it without an ECU burn or collateral `.msq` changes.

The recipe under test was Predictive MAP Blend Duration.

## Physical candidate authority

- version: `0.4.2-dev.2`
- frozen source: `8060e1115be1ee20da78d094e643bc298302905a`
- candidate branch: `candidate/0.4.2-dev.2-write-test.4`
- retained JAR: `candidate-artifacts/ae-tuner-epicefi-0.4.2-dev.2.jar`
- JAR SHA-256: `7b0e634bce1168b3734bbf41c91314fccbbc55db93fc84a88df88c321d5ce593`
- candidate workflow: `31402650370`
- full validation: PASS
- synthetic Swing/plugin integration: PASS

## Working-tune state

The physically tested Blend Duration axis was:

- 1500 RPM -> 0.30 s
- 2600 RPM -> 0.26 s
- 3800 RPM -> 0.24 s
- 5000 RPM -> 0.18 s

The actual contract test occurred at the selected **1500 RPM actual table cell**. This differs from the earlier planned 2600-RPM first-test point, but still exercises the same generic write contract and actual-bin targeting rule.

The numerical proposal generated during this session was:

`predictiveMapBlendDurationValues[0]: 0.30 -> 0.08 s`

That numerical value remains **unaccepted tuning evidence**. M6B evaluates write correctness only.

## Apply result — PASS

The successful Apply export contained `guided-apply-manifest.json` declaring exactly:

- recipe: Predictive MAP Blend Duration;
- context: 1500 RPM;
- target: `predictiveMapBlendDurationValues[0]`;
- baseline: `0.30`;
- proposed value: `0.08`.

The real TunerStudio plugin reported successful readback and explicitly reported that no burn was performed.

A semantic comparison of the available clean earlier baseline tune and the after-Apply tune checked all **5,960 MSQ constants**:

- intended changed constants: 1;
- observed changed constants: 1;
- changed target: `predictiveMapBlendDurationValues[0]` only;
- `0.30 -> 0.08`;
- undeclared changed constants: **0**.

One file originally named `M6b_before_apply.msq` had actually been saved after the Apply and therefore already contained 0.08. The true pre-Apply state was independently established by the earlier clean MSQ, the captured Guided proposal baseline, the Apply manifest and the session recording; all agreed on 0.30. This provenance detail is retained rather than hidden.

## Restore result — PASS

The same live TunerStudio/plugin session retained the successful Apply on its restore stack.

`Restore Previous Apply` was then executed. The plugin reported:

`Previous AE Tuner apply restored and read back — no burn performed`

The new Guided export contained the reversed manifest:

`predictiveMapBlendDurationValues[0]: 0.08 -> 0.30 s`

Semantic comparison across all **5,960 MSQ constants** showed:

- after Apply -> after Restore: exactly one change, `0.08 -> 0.30` at the declared target;
- original clean baseline -> after Restore: **0 changes**.

Thus Restore returned the complete tune to the original baseline with no collateral change.

## M6B disposition

PASS:

- explicit reviewed Apply only;
- actual selected table-cell target;
- stale/baseline checks active;
- successful complete-parameter readback;
- zero undeclared `.msq` changes;
- no burn;
- successful LIFO Restore;
- Restore manifest exact inverse of Apply;
- final tune identical to original baseline.

This closes the first real working-tune write-contract gate.

## Manifest rule retained for future writable recipes

`guided-apply-manifest.json` represents the **most recent successful Apply or Restore operation in that Guided export**.

For future recipe validation:

1. save the true pre-Apply `.msq` before pressing Apply;
2. Apply once and require readback PASS / no burn;
3. save the after-Apply `.msq`;
4. export Guided Session **before Restore**;
5. preserve that Apply export;
6. Restore and require readback PASS / no burn;
7. save the after-Restore `.msq`;
8. export again so the reversed Restore manifest is preserved;
9. require zero undeclared semantic `.msq` changes in both directions.

## Next phase

M6B does not approve any generated Blend Duration number. Numerical physical validation remains separate.

Before the full numerical campaign, the post-M6B `0.4.2-dev.3` UI/evidence increment is being stabilized to address issues exposed by the first real session:

- responsive Guided action wrapping;
- clear post-Apply `APPLIED / VERIFIED` state;
- accurate report wording for read-only capture versus explicit RAM Apply/Restore;
- clearer valid-versus-comparable progress feedback;
- reorganized Overview, Passive calibration and Evidence / Diagnostics surfaces.

After that candidate passes, resume controlled numerical evaluation across 1500 / 2600 / 3800 / 5000 RPM. Keep operating conditions/gear consistent within each comparable series where practical.
