While both features allow you to resume execution further down a Jenkins Pipeline without re-running early steps, they differ fundamentally in how state is preserved, how they are declared, and where they are supported.

| Feature Dimension | Restart Build from Stage | Pipeline Checkpoint Step (`checkpoint`) |
| --- | --- | --- |
| **Availability** | Open Source Jenkins & CloudBees CI (Declarative Pipelines) | CloudBees CI / CloudBees Jenkins Enterprise only (Proprietary) |
| **Pipeline Declaration** | Automatic; requires no extra code in your `Jenkinsfile`. | Manual; requires explicitly placing a `checkpoint 'Name'` step in code. |
| **Program State** | **Not retained.** Re-evaluates script context from scratch; prior variable values are lost. | **Retained.** Serializes and saves full Groovy execution state (variables, objects, context). |
| **Workspace Files** | Workspace is reset unless explicitly stashed/cached across runs. | Workspace is reset; requires `stash`/`unstash` paired with the checkpoint to restore files. |
| **Execution Trigger** | Initiated manually via UI (Stage View, Classic UI, or Blue Ocean) on a completed/failed build. | Initiated manually via UI from recorded checkpoints on previous builds. |

**Restart Pipeline Build from Stage**

* **Mechanism:** Starts a fresh build execution, evaluates the pipeline script from the beginning, but bypasses step execution until it reaches the selected stage.
* **Best Used For:** Recovering from transient external failures (e.g., network timeout during deployment) where earlier build/test stages succeeded, and you do not rely on in-memory pipeline variables generated prior to that stage.

**Pipeline Checkpoint Step (`checkpoint`)**

* **Mechanism:** Saves a snapshot of the pipeline’s internal execution thread (CPS state) to disk at the exact line where `checkpoint` is called. When resumed, a new build spawns that resurrects that exact runtime memory state.
* **Best Used For:** Complex, long-running pipelines where preceding stages perform heavy computations or variable manipulation that cannot be easily re-created or re-evaluated.
