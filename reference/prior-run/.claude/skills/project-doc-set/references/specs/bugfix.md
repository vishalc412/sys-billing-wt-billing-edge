> Read `conventions.md` alongside this file. Do not read the other scope specs —
> they describe documents you are not producing.

# Bugfix

## Defect Analysis & Fix Record

One document. The reader is a reviewer approving the fix, and a future engineer meeting
the same symptom. Both need the causal chain, not a narrative of your afternoon.

1. **Summary** — what broke, for whom, since when, and what fixed it. Four sentences.
   Everything after this is evidence for someone who needs it.
2. **Impact** — who and what was affected, how many, over what window, and the business
   consequence. Severity with its justification.
3. **Environment and version** — where it occurred, which version, which configuration.
   Bugs that only appear in one environment make this section the whole story.
4. **Symptom and detection** — what was observed, in concrete terms: error messages,
   status codes, log lines, metrics. How it was found — alert, customer report, test —
   and how long it took, because detection latency is usually its own finding.
5. **Reproduction** — the minimal reliable steps. If it could not be reproduced
   deterministically, say so and describe the conditions that make it appear; an
   intermittent bug marked "reproduced: yes" misleads everyone downstream.
6. **Root cause** — the causal chain from trigger to symptom, ending at the actual defect:
   the specific code, configuration, or assumption that was wrong. Name the file and the
   logic. Stop at the technical cause; do not editorialise about the person who wrote it.
   If the chain has multiple contributing causes — a missing guard *and* a retry that
   amplified it — give each its own link in the chain rather than picking one.
7. **Why it was not caught** — the honest answer. Missing test, untested path, gap in
   monitoring, unrepresentative environment. This section is what turns a fix into an
   improvement, and it is the one most often softened into uselessness.
8. **The fix** — what changed and why this approach. Per file: what changed and the
   effect. Explain the reasoning, not the diff — the diff is in version control and it is
   better at being a diff. Include what you deliberately did not change and why, which
   is usually the most contested part of the review.
9. **Risk of the fix** — what this could break, blast radius, and why the approach is
   safe. Fixes made under pressure are a common source of the next incident.
10. **Testing** — the test that now fails without the fix and passes with it (if there
    isn't one, justify that). Regression scope and its reasoning. Verification in each
    environment, with evidence.
11. **Deployment and rollback** — how it ships, and how it comes back out.
12. **Prevention** — the concrete guardrails: the test added, the alert created, the lint
    rule, the type change, the design decision recorded. Each with an owner if not done
    within this change. Prevention items without owners are how the same bug returns.

*Dies when:* section 6 stops at the symptom ("the service returned 500") instead of the
cause, or when section 7 is answered with "insufficient test coverage" — which is a
restatement of the fact that a bug existed, not a finding.
