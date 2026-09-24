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
| `membership.clj` | Membership list, round-robin probe target selection, the JOIN handshake. |
| `failure_detector.clj` | ping/ack handling, indirect probe, the protocol-period loop, the suspicion timer, node lifecycle. |
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
Each update is gossiped at most `lambda * log2(N)` times, preferring the
least-gossiped entries.

### Suspicion and incarnation numbers

A suspected member stays in the list and is still probed. If it answers before
the suspicion timeout, it is un-suspected (ALIVE). If a member learns it has
been suspected, it bumps its incarnation and broadcasts ALIVE. If the suspicion
times out, the member is CONFIRM failed and removed.

Incarnation numbers order these updates (SWIM paper §4.2):

| Message | Overrides |
|---|---|
| `Alive(i)` | `Suspect(i)`, and any `Alive(j)`/`Suspect(j)` with `j < i` |
| `Suspect(i)` | any `Suspect(j)`/`Alive(j)` with `j < i` |
| `Confirm` | any `Alive` or `Suspect` |

In the code this falls out of "apply only when the update's incarnation is at
least the locally known one" (`CONFIRM` is unconditional), plus the one-shot
suspicion timer: the self-heal bumps the incarnation, so its `Alive(i+1)`
overrides the earlier `Suspect(i)`.

### The one deviation from the paper

The paper sends `ping` and `ack` as separate UDP datagrams. Here the gRPC
`Send` RPC is unary, so `ACK` is the response to a `PING` (request and response
are coupled). Semantics are unchanged.

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

## Dev process (tutorial walkthrough)

The commit history teaches the protocol bottom-up, one piece at a time:

1. **Remove broken TCP-based SWIM stubs** — clear out the old aleph/gloss code.
2. **Add SWIM gRPC contract and generated stubs** — define the wire format.
3. **Declare SWIM message dispatch multimethods** — the extensibility seam.
4. **Add SWIM message codec and node state** — pure data plus the buffer.
5. **Wire SWIM messages over gRPC transport** — server, stub, `send!`.
6. **Implement membership list and round-robin probe selection**.
7. **Implement SWIM failure detector: ping, ack, indirect probe**.
8. **Add infection-style dissemination and suspicion with incarnations**.
9. **Add SWIM demo** — end-to-end proof.
10. **Document SWIM design and dev process** — this file.
