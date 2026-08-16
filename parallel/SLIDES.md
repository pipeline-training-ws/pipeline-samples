---
marp: true
theme: default
paginate: true
size: 16:9
---

# Parallel Kubernetes Agent Patterns for Jenkins

Two architectures for running parallel build steps on Kubernetes pod agents

**Files covered:**
- `Jenkinsfile-global-agent`
- `Jenkinsfile-stage-agent`
- `README.md`

---

## Why Two Patterns?

Both pipelines do the same thing functionally:

- Build a **Backend** (Maven) branch
- Build a **Frontend** (Node.js) branch
- Run them **in parallel**

They differ in **where the Kubernetes pod boundary is drawn**:

| | Global Agent | Stage-Specific Agent |
|---|---|---|
| Top-level `agent` | `kubernetes { yaml }` | `none` |
| Pods per run | 1 | 2 (one per branch) |
| Workspace | Shared `emptyDir` | Isolated per pod |

---

## Pattern 1: Global Agent (Monolith)

`Jenkinsfile-global-agent` — one pod, two containers, shared workspace

```mermaid
graph TB
    subgraph Pod["Single Kubernetes Pod"]
        direction LR
        M["Container: maven<br/>maven:3.9.9-eclipse-temurin-21"]
        N["Container: node<br/>node:20-alpine"]
        WS[("Shared emptyDir<br/>Workspace Volume")]
        M -.mount.- WS
        N -.mount.- WS
    end

    PL["pipeline { agent { kubernetes { yaml ... } } }"] --> Pod
    Pod --> BStage["stage('Backend')<br/>container('maven')<br/>sh 'mvn -version'"]
    Pod --> FStage["stage('Frontend')<br/>container('node')<br/>sh 'node -v'"]
```

**Key trait:** both branches execute inside the *same* pod — file sharing is free, resource sizing is coupled.

---

## Pattern 2: Stage-Specific Agent (Distributed)

`Jenkinsfile-stage-agent` — independent pods, isolated workspaces

```mermaid
graph TB
    PL["pipeline { agent none }"] --> Par["parallel"]

    Par --> BStage["stage('Backend')<br/>agent { kubernetes { yaml ... } }"]
    Par --> FStage["stage('Frontend')<br/>agent { kubernetes { yaml ... } }"]

    BStage --> PodB["Pod 1<br/>container: maven<br/>requests: 1 CPU / 1Gi"]
    FStage --> PodF["Pod 2<br/>container: node<br/>requests: 500m / 512Mi"]

    PodB --> WSB[("Workspace A<br/>emptyDir")]
    PodF --> WSF[("Workspace B<br/>emptyDir")]

    PodB --> CmdB["sh 'mvn -version'"]
    PodF --> CmdF["sh 'node -v'"]
```

**Key trait:** each branch schedules its *own* pod — resource sizing is independent, but workspaces are isolated by default.

---

## Scheduling Timeline Comparison

```mermaid
sequenceDiagram
    participant J as Jenkins Controller
    participant K as Kubernetes Scheduler

    rect rgb(240,240,255)
    note over J,K: Global Agent Pattern
    J->>K: Request 1 pod (maven + node containers)
    K-->>J: Pod Running
    par Backend in container('maven')
        J->>J: sh 'mvn -version'
    and Frontend in container('node')
        J->>J: sh 'node -v'
    end
    end

    rect rgb(255,245,235)
    note over J,K: Stage-Specific Agent Pattern
    par
        J->>K: Request Pod 1 (maven)
        K-->>J: Pod 1 Running
        J->>J: sh 'mvn -version'
    and
        J->>K: Request Pod 2 (node)
        K-->>J: Pod 2 Running
        J->>J: sh 'node -v'
    end
    end
```

Global pattern pays **one** scheduling wait; distributed pattern pays **N** — but they can happen concurrently.

---

## Pros & Cons at a Glance

```mermaid
graph LR
    subgraph Global["Global Agent"]
        G1["✅ Free file sharing"]
        G2["✅ Single scheduling event"]
        G3["❌ Coupled resource sizing"]
        G4["❌ Whole-pod failure blast radius"]
    end

    subgraph Distributed["Stage-Specific Agent"]
        D1["✅ Independent resource sizing"]
        D2["✅ Isolated failure domains"]
        D3["❌ Manual stash/unstash for shared files"]
        D4["❌ N scheduling events"]
    end
```

Full breakdown: see `README.md` §1 (executor starvation, resource bottlenecks, shared workspaces).

---

## Chapter 2: Pod Agents vs. Static VMs

The deeper architectural shift this whole comparison sits on top of.

```mermaid
graph TB
    subgraph VM["Static VM Agent"]
        VM1["Long-lived machine"]
        VM2["Persistent local disk workspace"]
        VM3["Fixed capacity, always reserved"]
        VM4["Weak isolation between builds"]
    end

    subgraph Pod["Kubernetes Pod Agent"]
        P1["Ephemeral — created per build/stage"]
        P2["emptyDir workspace, destroyed at teardown"]
        P3["Elastic — reserved only for pod lifetime"]
        P4["cgroup-enforced isolation per pod"]
    end
```

---

## Ephemeral Lifecycle

```mermaid
sequenceDiagram
    participant Build as Pipeline Run
    participant Cloud as Kubernetes Cloud

    Build->>Cloud: Request pod (per Jenkinsfile yaml)
    Cloud->>Cloud: Schedule + pull images
    Cloud-->>Build: Pod Running (fresh, clean volume)
    Build->>Build: Execute stage(s)
    Build->>Cloud: Stage/pipeline complete
    Cloud->>Cloud: Terminate pod + delete emptyDir
    note over Cloud: No drift carried into next build —<br/>every run starts from committed YAML
```

Contrast with a static VM: the machine (and its workspace) **persists** across hundreds of builds unless actively wiped.

---

## Decision Guide

```mermaid
flowchart TD
    Start["Choosing a parallel agent pattern"] --> Q1{"Do branches need to<br/>share files cheaply?"}
    Q1 -- Yes --> Q2{"Is combined resource<br/>footprint modest?"}
    Q1 -- No --> Stage["Use Stage-Specific Agent<br/>(agent none)"]
    Q2 -- Yes --> Global["Use Global Agent<br/>(single pod)"]
    Q2 -- No --> Stage
```

---

## Summary

- **Global Agent** → simplicity + free file sharing, at the cost of coupled sizing and blast radius
- **Stage-Specific Agent** → independent scaling + isolation, at the cost of explicit `stash`/`unstash` and more scheduling overhead
- **Kubernetes pods (either pattern)** → ephemeral, declarative, elastic — a fundamentally different operating model from long-lived static VM agents

**See also:**
- `README.md` — full technical write-up
- `Jenkinsfile-global-agent` — monolith pattern source
- `Jenkinsfile-stage-agent` — distributed pattern source
