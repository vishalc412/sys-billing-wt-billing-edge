# The review board

Re-read each draft as each reviewer and fix what they would send back. Not a tone pass —
each lens catches a class of omission that first drafts reliably contain, because drafting
attention goes to what you know rather than what is missing.

In order: the Enterprise Architect can invalidate the approach, which is cheaper to find
before the Test Architect has reviewed coverage for a design about to change.

## Enterprise Architect — always

*Does this belong in the landscape, and what does it commit us to?*

- Does it duplicate a capability that exists elsewhere? If knowingly, is the reason stated?
- Are integrations using standard patterns, or is a new one introduced without saying so?
- Who owns each piece of data? Is anything mastered in two places?
- Which decisions are one-way doors? Are they flagged, or buried among reversible ones?
- What does this commit us to operationally and financially beyond build?
- Is anything a local optimum that pushes cost onto a neighbouring team?

*Usual finding:* a design coherent in isolation and quietly inconsistent with everything
around it.

## Principal Engineer — always, for the actual stack

*Is this idiomatic, buildable, and survivable in this language and framework?* Bring real
knowledge of the stack in play; specificity is the whole value here.

- Idiomatic for this framework, or a pattern imported from elsewhere that will fight the
  ecosystem? Name the idiomatic alternative when it isn't.
- Concurrency: what is shared, what is mutable, where can two things interleave? Are the
  guarantees stated or assumed?
- Failure: timeout, partial write, duplicate delivery, restart mid-operation. Is
  idempotency claimed anywhere it isn't actually implemented?
- Are the framework's own mechanisms used, or reimplemented — transactions, DI,
  serialisation, retry, pooling?
- Does anything rely on behaviour that changed or deprecated in the pinned version?
- Where will this be painful to change, and is that where change is most likely?
- Is any performance claim measured, or merely asserted?

*Usual finding:* a design describing behaviour the code cannot provide — most often
idempotency, ordering, or exactly-once.

## Test Architect — always

*Can this be verified, and will we know when it breaks?*

- Is every acceptance criterion checkable? Rewrite any that cannot fail.
- What is untestable as designed, and what seam fixes it? Raise it against the design —
  testability is a design property, not a test-plan problem.
- Does risk prioritisation match where damage would actually occur?
- Is the regression boundary reasoned, rather than "everything" or "the changed files"?
- Does the needed test data exist? Is any of it personal data?
- Are the HLD's NFR targets verifiable by the stated approach? An unmeasured target is
  decoration.
- For a fix: is there a test that fails without it? If not, is the justification real?

*Usual finding:* an NFR table in the HLD with no verification anywhere in the test strategy.

## DevOps & Platform Architect — only when infrastructure is in scope

When not active, the DevOps document is not produced. Do not generate an empty one.

- Deployable without downtime? If not, is that stated and accepted?
- Is the rollback path real, including for data? Where is the point of no return?
- What does this cost to run at the stated load, to the right order of magnitude?
- Does every alert have a defined response? Alerts without runbooks become ignored alerts.
- Are config and secrets handled as everything else here, or is this a special case?
- What happens on cold start, region loss, dependency outage?

*Usual finding:* a deployment strategy assuming the schema change is backward compatible
without anyone having checked.

## Security — a checklist on every document

Kept as a checklist deliberately: a security section living in one document is one every
other document skips.

- Authn/authz at every boundary — who may call this, and what enforces it?
- Data classification: what sensitive data flows through, where it rests, encryption in
  transit and at rest.
- No secrets in documents, config examples, or log samples. Check the examples you wrote.
- Input validation and injection surfaces at trust boundaries.
- What security-relevant events are audited, and are they tamper-evident?
- Any known-vulnerable dependency pinned?

## Technical writer — last

*Will anyone get through this?*

- Does each document open by telling the reader whether it is for them?
- Any section deletable with no loss? Delete it.
- Every figure referenced in prose, saying what the prose does not?
- Tables for facts, prose for reasoning — not the reverse?
- Terminology the codebase's own, used consistently?
- Any hedging that means nothing — "may potentially", "should generally be considered"?
- Any ceremony that crept in: sign-off tables, empty revision history, invented dates?

*Usual finding:* a strong document with a weak first page, which decides whether the rest
gets read.
