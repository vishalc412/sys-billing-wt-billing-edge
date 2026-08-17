# The interview

Captures what code and crib cannot tell you: intent, constraints, what was rejected, what
the numbers mean, who decides. Everything else you should already have.

**Come informed.** Opening with "what language is this?" tells the user their time is about
to be wasted. Opening with "this is Spring Boot 3.2 with a consumer on `orders.v1` — what I
can't tell from the code is whether that consumer is meant to be idempotent" earns the next
twenty minutes.

**Batch and recommend.** Three or four related questions at a time, each with your
recommended answer and why. Confirming a considered position takes seconds; filling a blank
form takes effort and yields thinner answers. Use an interactive question tool if available.

**Chase load-bearing answers.** Some reshape everything — an availability target forcing
multi-region, a compliance regime dictating data handling, a hard integration contract. Ask
those early and follow them before moving on.

**Stop when the next question wouldn't change a sentence.** Unasked detail becomes a named
open question, which is a legitimate output.

**Record the source.** "Retention is 7 years (Priya, compliance)" survives challenge;
the same sentence unattributed does not.

## Every scope, first

1. Who reads this, and what do they do with it? (Sets register and depth.)
2. What decision or action must it enable — approve, build, operate, hand over, defend?
3. Where is it published, and must it fit a house format?
4. Who owns each document afterwards? Unowned documents go stale in six weeks.

## Greenfield

Earlier blocks constrain later ones, so keep the order.

**Problem and scope** — What problem, for whom, and what happens today without it? What is
explicitly out of scope this phase? What does success look like, measurably, six months
after launch? Any date the design must respect?

**Constraints you cannot change** — Systems it must integrate with and protocols mandated.
Compliance regimes and what they demand of data. Team shape and skills, which is a real
architectural constraint and usually goes unstated — a design the team cannot operate is
not a good design. Cost ceilings and licensing.

**Non-functional targets** — push for numbers; a target without one cannot be designed
against or tested. Load at launch and at twelve months. Availability, and what downtime
costs. Latency on the paths users notice. Durability, retention, residency, RTO/RPO.
Security posture: auth model, data classification, threats specific to this system.

**Architecture intent** — Is a shape already assumed, by whom, how firmly? What has been
tried before here or elsewhere, and what happened? Which parts will change most in the next
year (this drives where seams go)? Build, buy or extend — and what is already ruled out?

**Operations** — Who runs this in production and on what on-call model? Deployment cadence
and risk appetite? What must be observable for that team to trust it?

**Users** — Who uses it, and what three tasks do they perform most? Technical level and
vocabulary? Existing product vocabulary to match?

## Enhancement

**The change** — The ticket, and the business reason behind it; the reason often reveals
the asked-for change is not the needed one. What is explicitly not changing? Deadline, and
what happens if it slips?

**Existing system** — Which documents should this reference rather than restate, and where
are they? What did the original design intend here, and is that intent still valid? What is
fragile or sharp in this area?

**Impact** — Which components do you expect to be touched? (Compare with what the scan
found; the gap is worth discussing.) Any consumers of the interfaces changing, inside or
outside? Backward-compatible path needed, and for how long? Data migration or backfill —
is it reversible?

**Delivery** — Behind a flag, big bang, or phased? Rollback appetite, and is there a point
of no return? Who needs telling, and when?

## Bugfix

Keep it tight — six focused questions beat twenty, and the reporter's time is stretched.

**The defect** — What was observed versus expected, with exact messages or codes? Who was
affected, how many, over what window? How was it found, and how long had it been happening?

**Diagnosis** — Reproducible reliably, and under what conditions? What is already suspected
about the cause? Anything changed shortly before it started — deploy, config, data,
dependency, traffic?

**The fix** — The intended fix, and were alternatives considered? (Under pressure the first
workable fix ships; the document should record whether that was a choice.) Anything
deliberately not fixed, and why? How urgently must it ship, and does that constrain the fix?

**Prevention** — ask even when it's uncomfortable; it is the part with lasting value. Why
did tests or monitoring not catch it? What guardrail would have, and is it worth adding now?

## Confirm at the end

- **DevOps lens** — does this change infrastructure, pipelines, topology, or observability?
  If no, that document is not produced.
- **Data lens** — schemas, data flows, ownership, or retention changing?
- **Security depth** — threat model expected, or is the cross-cutting checklist enough?
- **Publishing** — destinations for this run, and the ticket, repo, or page they attach to.
