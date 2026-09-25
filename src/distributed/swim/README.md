# SWIM

SWIM (Scalable Weakly-consistent Infection-style Membership) is a peer-to-peer
group membership protocol. It splits failure detection from membership-update
dissemination: members probe peers directly, and updates spread by piggybacking
on probe messages, so per-member message load stays constant as the group grows.

This is a Clojure implementation over gRPC, built to match the SWIM paper
(Das, Gupta, Motivala, 2002).

## Run the demo

```bash
lein run -m distributed.swim.demo
```

Three in-process nodes (A/B/C) on localhost ports 9000-9002:

1. join a group,
2. B stops answering so A and C suspect it, then recovers and rejuvenates by
   bumping its incarnation,
3. C is killed and confirmed failed.

It prints each node's membership view along the way and ends with
`SWIM demo: PASS`.

## Code map

| Namespace | Responsibility |
|---|---|
| `state.clj` | Per-node atom, config, and the dissemination buffer (enqueue / pick-piggyback). |
| `message.clj` | Pure protobuf <-> Clojure codec; no I/O. |
| `dispatch.clj` | `handle-message` / `apply-update` multimethods, dispatched on `:type`. |
| `network_peer.clj` | gRPC `Send` service, server, cached blocking stubs, `send!`. |
| `membership.clj` | Membership list, round-robin probe target selection, the JOIN handshake, and the atomic membership-state transitions (incarnation order, one-shot suspicion timer, tombstone). |
| `failure_detector.clj` | ping/ack handling, indirect probe, the protocol-period loop, suspicion sweep, node lifecycle. |
| `dissemination.clj` | Infection-style dissemination and the suspicion/incarnation subprotocol. |
| `demo.clj` | The runnable three-node demo. |

## How it works

### Failure detection

Every protocol period (default 800 ms) a node picks its next probe target
round-robin over a shuffled member order (time-bounded completeness: every
member is probed once per traversal) and pings it, waiting for an ACK (default
300 ms). If no ACK arrives, it asks up to `k` (default 2) random live members to
ping the target on its behalf (`ping-req`), avoiding a congested direct path. If
it still gets no ACK, it marks the target **suspected**.

### Dissemination

Membership updates (SUSPECT / ALIVE / CONFIRM / JOIN) are piggybacked on
ping / ack / ping-req messages, so dissemination generates no extra packets.
A join is announced as a JOIN update for the new member (which clears any
`:confirmed` tombstone at recipients). Each update is gossiped
at most `lambda * log2(N)` times, preferring the least-gossiped entries.

### Suspicion and incarnation numbers

A suspected member stays in the list and is still probed. If it answers before
the suspicion timeout, it is un-suspected (ALIVE). If a member learns it has
been suspected, it bumps its incarnation and broadcasts ALIVE. If the suspicion
times out, the member is CONFIRM failed, removed, and recorded in a
`:confirmed` tombstone set (a stale update cannot resurrect it until it
re-JOINs).

Incarnation numbers order these updates (SWIM paper §4.2). SUSPECT applies
when its incarnation is at least the locally known one; ALIVE applies at `>=`
unless the member is currently suspected, in which case it must be strictly
higher (the self-heal bump); CONFIRM is unconditional and tombstones the
member; JOIN is unconditional and clears the tombstone:

| Message | Applies when |
|---|---|
| `Alive(i)` | `i >=` local incarnation, or the member is unknown and not confirmed; strictly higher (`i >`) when the member is currently suspected |
| `Suspect(i)` | `i >=` local incarnation, member present and not confirmed |
| `Confirm` | unconditional (removes the member and tombstones it) |
| `Join` | unconditional on incarnation (clears the tombstone, retires any buffered stale CONFIRM for the member, and re-adds it) |

Two refinements keep the suspicion timer sound: a suspected member is revived
by ALIVE only at a *strictly higher* incarnation (its self-heal bump, which
makes `Alive(i+1)` override the earlier `Suspect(i)`), and the one-shot timer
is set only on the first suspicion. A stale same-incarnation ALIVE therefore
cannot keep resetting the timer. A member that
answers a direct or indirect probe is un-suspected locally via a separate
unconditional path.

### The one deviation from the paper

The paper sends `ping` and `ack` as separate UDP datagrams. Here the gRPC
`Send` RPC is unary, so `ACK` is the response to a `PING` (request and response
are coupled). Semantics are unchanged.

## Configuration

Defaults (in `state.clj` `default-config`):

| Key | Default | Meaning |
|---|---|---|
| `protocol-period-ms` | 800 | one failure-detection round |
| `ack-timeout-ms` | 300 | direct/indirect probe deadline |
| `k` | 2 | indirect-probe relay count |
| `suspicion-timeout-ms` | 2000 | suspected -> confirmed deadline |
| `lambda` | 3 | gossip cap multiplier (`lambda * log2(N)`) |

The demo overrides `suspicion-timeout-ms` to 8000 (so B is suspected but not
confirmed before it recovers) and `lambda` to 6 (so the SUSPECT update stays
buffered long enough to reach B after it recovers).

## gRPC mapping

There is a single service:

```proto
service Swim {
  rpc Send (SwimMessage) returns (SwimMessage) {}
}
```

`SwimMessage` carries the type (`PING`, `ACK`, `PING_REQ`, `SUSPECT`, `ALIVE`,
`CONFIRM`, `JOIN`), sender, sequence, target, incarnation, and piggybacked
`updates`. Regenerate the Java stubs with:

```bash
bin/gen-swim-proto.sh
```

which writes `gen/swim/SwimGrpc.java` and `gen/swim/SwimSpec.java`. The script
expects `protoc` and the gRPC-Java plugin (see the script for paths).

## Concurrency: why each node is an atom

A node is a single atom. The gRPC server threads (inbound handlers) and the
protocol-period loop thread both mutate membership, incarnation, and the
dissemination buffer, so a plain map would race and lose updates. `swap!`
gives the atomic read-modify-write the protocol needs; it is the smallest
correct primitive (a core.async single-owner loop would be more code, not
less).

## Dev process

1. Add SWIM gRPC contract and generated stubs.
2. Declare SWIM message dispatch multimethods**.
3. Add SWIM message codec and node state.
4. Wire SWIM messages over gRPC transport.
5.  Implement membership list and round-robin probe selection.
6.  Implement SWIM failure detector: ping, ack, indirect probe.
7.  Add infection-style dissemination and suspicion with incarnations.
8.  Add SWIM demo.
9. Harden SWIM membership transitions `:confirmed` tombstone, strict-incarnation suspicion revival.
10. Fix SWIM re-join propagation and node robustness JOIN update type clears the `:confirmed` tombstone at every recipient so a re-join propagates; a malformed ALIVE id can no longer wipe the node atom; a node never removes itself; redundant re-enqueue removed.
11.  Fix SWIM re-join stale-confirm and suspicion edges** — a JOIN now retires buffered CONFIRMs; `fail-if-expired!` gains the self-guard; a higher-inc
     re-suspicion resets the timer; a rejected SUSPECT is no longer re-gossiped.
