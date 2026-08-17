# Parallel Kubernetes Agent Patterns for Jenkins

This directory contains two declarative Jenkins pipelines that solve the same
problem — build a backend (Maven) and a frontend (Node.js) in parallel — using
two fundamentally different Kubernetes agent-provisioning strategies:

| File | Pattern | Top-level `agent` |
|---|---|---|
| [`Jenkinsfile-global-agent`](./Jenkinsfile-global-agent) | Monolith: one pod, many containers | `kubernetes { yaml ... }` |
| [`Jenkinsfile-stage-agent`](./Jenkinsfile-stage-agent) | Distributed: one pod per stage | `none` |

A Marp slide deck covering the same comparison is available in [`SLIDES.md`](./SLIDES.md).

Both pipelines assume the [Kubernetes plugin](https://plugins.jenkins.io/kubernetes/)
is configured with a Kubernetes cloud, and that a node pool exists matching the
`nodeSelector`/`toleration` pair used in the sample YAML (`workload: agent`).
Adjust those to match your cluster's actual node labels and taints.

---

## 1. Pros and Cons Comparison

### 1.1 Global Agent (Monolith Pattern)

A single pod is requested at pipeline start, containing one container per
tool (`maven`, `node`). Both parallel branches use `container('maven')` /
`container('node')` to execute steps inside sibling containers of that same
pod.

**Pros**

- **Single scheduling event.** Only one pod is scheduled by Kubernetes, so
  there is only one `Pending → Running` transition to wait on. For small
  pods this is often the fastest path to "first `sh` step executes."
- **Trivial file sharing.** All containers in a pod share the same network
  namespace and, critically, the same workspace volume (an `emptyDir`
  mounted into every container by the Kubernetes plugin). A file written by
  the Backend branch is immediately visible, byte-for-byte, to the Frontend
  branch — no artifact stashing, no `stash`/`unstash`, no external cache.
- **Simple mental model.** One `agent` block at the top of the pipeline,
  one place to reason about node placement, tolerations, and service
  account.

**Cons**

- **Executor starvation / resource bottleneck.** The pod's resource
  `requests`/`limits` are declared once, and Kubernetes schedules the pod
  as a single unit sized to the sum of all its containers' requests. If you
  have four parallel branches, you provision (and pay for, and wait for a
  node to fit) one large pod up front — even if, at any given moment, only
  two of the four containers are actively doing CPU-bound work. There is no
  way to scale the Maven container's CPU independently of the Node
  container's CPU; they are permanently coupled inside one pod spec.
- **Coarse-grained failure domain.** If the underlying node is evicted,
  OOM-killed, or the pod is rescheduled, *both* branches lose their agent
  simultaneously, even though only one branch's workload may have caused the
  problem (e.g., a memory-hungry `npm install` OOM-killing the whole pod,
  including the otherwise-healthy Maven container's cgroup neighbors if
  limits are misconfigured).
- **Shared workspace is a double-edged sword.** The same `emptyDir` that
  makes file sharing "free" also means the two branches are **not**
  isolated from each other's filesystem side effects. A `Frontend` step
  deleting or overwriting a file the `Backend` step also touches (e.g. a
  shared `package.json` in a monorepo, or careless `sh 'rm -rf target'`)
  causes a race condition that is difficult to diagnose because both
  branches are, from Jenkins' perspective, running "at the same time" on
  the same disk.
- **Wasted capacity while idle.** Every container in the pod consumes its
  `requests` value against the node's allocatable capacity for the pod's
  *entire lifetime*, even during stages where that container isn't in use
  (e.g., the `node` container sits idle — but still reserved — during any
  Maven-only stage that runs before or after the parallel block).

### 1.2 Stage-Specific Agent (`agent none` Pattern)

`agent none` at the pipeline level means no default executor is claimed;
each `parallel` branch declares its own `agent { kubernetes { yaml ... } }`,
so Kubernetes schedules two independent pods — one sized for Maven, one
sized for Node — potentially on two different nodes.

**Pros**

- **Right-sized resource requests per workload.** The Maven pod can request
  `2Gi`/`2 CPU` (typical for dependency resolution and compilation) while
  the Node pod requests a much smaller `512Mi`/`500m` — each branch pays
  only for what it needs, and the scheduler can bin-pack them onto
  different nodes independently.
- **No executor starvation across branches.** Because each branch is a
  distinct pod requesting its own executor slot from the Kubernetes cloud,
  a resource-hungry branch cannot starve a lightweight one out of capacity
  on a shared pod — Kubernetes' scheduler handles placement for each pod on
  its own merits.
- **Failure isolation.** A node-level failure, eviction, or OOM kill affects
  only the pod (and therefore only the branch) running on that node. The
  other branch continues unaffected.
- **Cleaner security boundaries.** Each pod can carry its own
  `serviceAccount`, `securityContext`, and network policy scoped tightly to
  that toolchain (e.g., only the Node pod needs egress to the npm registry;
  only the Maven pod needs a Maven repository credential mounted).

**Cons**

- **No implicit shared workspace.** Each pod gets its own, separate
  `emptyDir`-backed workspace. If the Frontend stage needs an artifact the
  Backend stage produced (e.g., a generated OpenAPI client, or a compiled
  JAR needed for an integration test), you **must** explicitly move it
  across pods using Jenkins' `stash`/`unstash` steps (or an external
  artifact store). This adds pipeline code, adds I/O overhead, and is a
  common source of "works with one pod, breaks after refactor to
  stage-agents" bugs when a `stash` call is forgotten.
- **N scheduling events instead of one.** Two (or more) pods must each go
  through `Pending → Image Pull → Running`. On a cold node with no cached
  images, this can mean the *slowest* branch's pod-startup latency now
  gates the whole parallel block, and total scheduling overhead is
  multiplied by the branch count instead of paid once.
- **More YAML to maintain.** Pod templates are duplicated per stage (or
  must be factored out into a shared library / `podTemplate` global to avoid
  duplication), increasing pipeline complexity relative to the single
  global block.

### 1.3 Summary Table

| Concern | Global Agent (Monolith) | Stage-Specific Agent (`agent none`) |
|---|---|---|
| Pods scheduled | 1 | N (one per parallel branch) |
| Workspace sharing | Automatic (`emptyDir`, shared) | Manual (`stash`/`unstash` or external store) |
| Resource sizing | Coupled — one spec for all containers | Independent — sized per branch |
| Executor starvation risk | Higher (branches compete inside one pod's limits) | Lower (independent scheduling per branch) |
| Failure blast radius | Whole pod (all branches) | Single pod (single branch) |
| Idle resource waste | Higher (unused containers still reserved) | Lower (only active pods reserve resources) |
| Scheduling latency | Paid once | Paid per branch (parallelizable, but gated by slowest pod) |
| Pipeline/YAML complexity | Lower | Higher (duplication unless factored out) |

**Guidance:** use the **Global Agent** pattern for small, tightly-coupled
build steps that genuinely need to share files cheaply and where the
combined resource footprint is modest. Use the **Stage-Specific Agent**
pattern once branches have meaningfully different resource profiles, need
independent failure isolation, or run in a cluster where efficient
bin-packing / autoscaling matters more than shared-workspace convenience.

---

## 2. Pod Agents vs. Static VMs

The Kubernetes plugin doesn't just change *where* Jenkins agents run — it
changes the operating model relative to traditional static agents (e.g.,
long-lived SSH-connected VMs or bare-metal build machines registered as
permanent Jenkins nodes).

### 2.1 Workspaces and File Sharing

**Static VM agents:** the workspace is a directory on that VM's local disk
(commonly `$JENKINS_HOME/workspace/<job-name>`), persisted across builds
unless explicitly wiped. Multiple executors on the same VM can share that
disk implicitly, but two *different* static agents have no shared
filesystem at all — moving data between them requires `stash`/`unstash`,
`archiveArtifacts`, or an external transfer (SCP, shared NFS mount, etc.),
identical in spirit to the multi-pod case above.

**Kubernetes pod agents:** the "workspace" is a Kubernetes volume
(typically `emptyDir`, though it can be backed by other volume types) that
the plugin mounts identically into **every container in the pod**. This is
why the Global Agent pattern above gets shared files "for free" between the
`maven` and `node` containers — they are literally the same
`emptyDir` mounted twice. Crucially, this volume is **ephemeral**: it is
created when the pod is scheduled and destroyed with the pod at the end of
the build. There is no persistent, build-to-build workspace by default the
way there is on a static VM — each pipeline run starts from a guaranteed
clean, empty volume, which eliminates an entire class of "worked on my
agent, dirty state on the shared build box" bugs but also means any
built-up local caches (e.g., `~/.m2`, `node_modules`) must be handled
deliberately (external cache volumes, a Nexus/Artifactory proxy, or
`PersistentVolumeClaim`-backed cache templates) if you want them to survive
across runs.

### 2.2 Provisioning Speed and Ephemeral Lifecycle

**Static VM agents** are long-lived: a machine is provisioned once (manually
or via infra-as-code), registered with Jenkins as a permanent or cloud
node, and then reused across many builds over days, weeks, or months.
Startup latency for a *build* is near-zero once the VM is registered and
online, because the "agent" already exists — the cost was paid up front at
VM-provisioning time, not at build time. The tradeoff is configuration
drift: a VM that has run 500 builds has 500 builds' worth of accumulated
state (stale caches, leftover processes, disk fragmentation, tool version
skew) unless actively managed.

**Kubernetes pod agents** are provisioned **on demand, per build** (or per
stage, in the distributed pattern). The pod is created when the pipeline
(or stage) needs it and torn down the moment the stage/pipeline completes.
This trades a small, per-build provisioning latency (image pull if not
cached, container start, JNLP/inbound-agent handshake back to the
controller — typically single-digit seconds with warm image caches, longer
on cold nodes) for a strong guarantee: **every build gets a byte-for-byte
identical, brand-new environment**, defined entirely by the pod YAML
committed alongside the pipeline. There is no long-running machine to patch,
no configuration drift to audit, and no risk of one job's leftover state
contaminating the next.

### 2.3 Resource Utilization and Isolation

**Static VMs** allocate a fixed, whole machine (or a fixed slice of one) to
Jenkins, regardless of instantaneous load. Capacity planning is done at
fleet level — you provision N executors/VMs sized for peak concurrent
demand, and that capacity sits reserved (and billed, in cloud environments)
whether or not builds are currently running against it. Isolation between
concurrent builds sharing one VM's multiple executors is weak: they share
the same kernel, filesystem, installed tool versions, and any process both
builds can see (subject only to OS-level user/permission separation, which
is rarely configured per-executor).

**Kubernetes pod agents** let the scheduler bin-pack many short-lived pods
across a shared node pool, and (with a cluster autoscaler) scale the
underlying node count up and down with actual demand rather than
provisioning for peak load year-round. Each pod gets its own cgroup-enforced
CPU/memory `requests`/`limits`, its own container filesystem layer (only
the workspace volume is shared, and only among containers *you* explicitly
put in the same pod), and can carry its own `securityContext`,
`serviceAccount`, and network policy. This gives materially stronger
isolation between concurrent, unrelated builds than co-located executors on
a static VM — one build's runaway memory usage is contained by its pod's
`limits` and cannot starve a sibling build's pod the way it could starve a
sibling executor on the same VM. The cost is operational complexity:
someone must own the Kubernetes cluster, node pools, image registry, and
plugin cloud configuration that static-VM setups don't require.

### 2.4 Summary Table

| Concern | Static VM Agent | Kubernetes Pod Agent |
|---|---|---|
| Lifecycle | Long-lived, reused across many builds | Ephemeral, created and destroyed per build/stage |
| Workspace | Persistent local disk (unless wiped) | Ephemeral `emptyDir` (or other volume), gone at pod teardown |
| Cross-agent file sharing | `stash`/`unstash`, SCP, shared NFS | Same-pod: shared volume; cross-pod: `stash`/`unstash` |
| Provisioning latency at build time | ~None (machine pre-exists) | Small, per-build (image pull + container start) |
| Environment drift | Accumulates over time, needs active management | None — defined declaratively per build from committed YAML |
| Resource reservation | Fixed capacity, reserved continuously | Elastic, reserved only for the pod's lifetime |
| Isolation between builds | Weak (shared kernel/executors on one VM) | Strong (cgroup limits, per-pod filesystem, per-pod identity) |
| Operational ownership | VM/fleet management (patching, image maintenance) | Kubernetes cluster, node pools, plugin cloud config |

---

## 3. References

- [Jenkins Kubernetes Plugin documentation](https://plugins.jenkins.io/kubernetes/)
- [Declarative Pipeline syntax reference — `agent`](https://www.jenkins.io/doc/book/pipeline/syntax/#agent)
- [Declarative Pipeline syntax reference — `parallel`](https://www.jenkins.io/doc/book/pipeline/syntax/#parallel)
