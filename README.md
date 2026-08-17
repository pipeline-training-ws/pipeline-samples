* CloudBee Properitary Pipeline features

<https://docs.cloudbees.com/docs/cloudbees-ci/latest/pipelines/pipeline-benefits>

Comparison between **Scripted** and **Declarative** Jenkins Pipelines, along with clear recommendations on when to choose which style.

---

## 📦 Samples in This Repository

| Sample | Description |
| --- | --- |
| [`checkpoints`](checkpoints) | Compares CloudBees CI's `checkpoint` step against restart-from-stage for resuming a Pipeline without re-running earlier stages. |
| [`cross-team-collaboratiion-events`](cross-team-collaboratiion-events) | Producer/consumer pipelines using `publishEvent` and `eventTrigger` for cross-team collaboration. |
| [`parallel`](parallel) | Two Kubernetes agent-provisioning patterns (global pod vs. per-stage pod) for running parallel build branches. |
| [`ci-jfrog-integration`](ci-jfrog-integration) | Uploads and downloads a build artifact to/from JFrog Artifactory via the shared library's `jfrogUploadArtifact`/`jfrogDownloadArtifact` steps. |
| [`ci-jira-integration`](ci-jira-integration) | Creates a Jira issue via the shared library's `jiraCreateIssue` step. |

---

### Comparison: Scripted vs. Declarative Jenkins Pipelines

| Feature / Aspect | Scripted Pipeline | Declarative Pipeline |
| --- | --- | --- |
| **Syntax Style** | Groovy-based imperative programming code. | Structured, domain-specific language (DSL) with predefined block keywords. |
| **Learning Curve** | **Steep** — Requires solid knowledge of Groovy syntax, control logic, and Jenkins internals. | **Gentle** — Easy to learn, read, and maintain for developer and DevOps teams alike. |
| **Flexibility & Control** | **Unlimited** — Full access to Groovy features (loops, functions, complex condition logic, try-catch). | **Restricted** — Bounded by strict syntax block constraints; requires `script {}` blocks for custom Groovy. |
| **Structure & Readability** | Harder to standardize; code can quickly turn into complex "spaghetti" logic if unmanaged. | Highly structured and predictable (`pipeline`, `agent`, `stages`, `stage`, `steps`, `post`). |
| **Error Handling** | Programmatic via traditional `try / catch / finally` blocks. | Built-in declarative `post` conditions (`always`, `success`, `failure`, `changed`, `unstable`). |
| **Validation & Linting** | Standard Groovy syntax check; logic errors often only surface at runtime. | Validated before execution via Jenkins Linter API and VS Code Jenkins extensions. |
| **Blue Ocean / Visualization** | Minimal native layout support; stages may not render visually if generated dynamically. | Native support for Jenkins Blue Ocean visualization and stage progression graphics. |
| **Parallel Execution** | Expressed using custom `parallel` map constructs and Groovy closures. | Declarative `parallel` block syntax built into stages. |

---

### Recommendations: When to Use Which and Why

```
                 Do you need complex Groovy logic, dynamic stage generation,
                 or support for legacy Groovy-based pipeline code?
                                       / \
                                      /   \
                                YES  /     \  NO
                                    /       \
                         [ Scripted ]       [ Declarative ]
                         (Imperative)        (Standard / Default)

```

#### Choose **Declarative Pipeline** (Recommended Default)

* **When:**
* Setting up standard CI/CD workflows for microservices, standard Web applications, or modern containers (Kubernetes pod templates).
* Onboarding new developers or cross-functional teams who need to edit pipeline configurations without knowing Groovy.
* Building pipelines that follow standard stages: `Checkout` → `Build` → `Test` → `Lint` → `Deploy`.

* **Why:**
* **Maintainability & Governance:** Standardized syntax keeps pipeline definitions predictable across dozens or hundreds of repositories.
* **Fewer Runtime Surprises:** Jenkins validates Declarative syntax before executing the job steps, catching simple syntax mistakes instantly.
* **Built-in Features:** Simplifies agent provisioning, environment variable handling, credentials management, and clean post-build notification handling without boilerplate code.

---

#### Choose **Scripted Pipeline**

* **When:**
* Building highly dynamic workflows where stages must be programmatically generated at runtime (e.g., iterating over a dynamic matrix generated from an external API payload).
* Refactoring legacy Jenkins pipelines heavy on custom Groovy classes, helper functions, and intricate multi-level try-catch error recovery loops.
* Developing advanced Shared Libraries that require low-level Jenkins API interaction.

* **Why:**
* **Unrestricted Expressiveness:** Provides the full power of a programming language rather than a constrained configuration DSL.
* **Dynamic Execution:** Enables complex looping, conditional execution, and multi-branch execution flows that are impossible or awkward to represent in Declarative blocks without nesting `script {}` overrides.

---

## ⚙️ Job Settings: Branch Suppression Strategies

Suppress automatic triggering for all branches, except PRs:

```yaml
strategy:
  namedBranchesDifferent:
    defaultProperties:
      - suppressAutomaticTriggering:
          triggeredBranchesRegex: ^.*$
          strategy: INDEXING
    namedExceptions:
      - named:
          name: PR-\d+
          props:
            - suppressAutomaticTriggering:
                triggeredBranchesRegex: ''
                strategy: NONE
```

---

## 📚 Documentation & Videos

### Pipeline Best Practices

* 📝 [Just Enough Pipeline](https://www.jenkins.io/blog/2021/10/26/just-enough-pipeline/)
* 📘 [CloudBees CI Pipeline Best Practices](https://docs.cloudbees.com/docs/cloudbees-ci/latest/pipelines/pipeline-best-practices)
* 🎥 [Scripted vs. Declarative Pipelines – YouTube](https://www.youtube.com/watch?v=GJBlskiaRrI=)
* 🧠 [Scripted vs. Declarative - Blog](https://e.printstacktrace.blog/jenkins-scripted-pipeline-vs-declarative-pipeline-the-4-practical-differences/)

### Multibranch Pipelines

* 🎥 [How to Create a GitHub Multibranch Pipeline – YouTube](https://www.youtube.com/watch?v=ZWwmh4gqia4)
* 📘 [CloudBees Docs: Multibranch Pipelines](https://docs.cloudbees.com/docs/cloudbees-ci/latest/pipelines/pipeline-as-code#_multibranch_pipeline_projects)

### Template Catalogs

* 🎥 [Pipeline Template Catalogs – YouTube](https://www.youtube.com/watch?v=pPwI_kTSCmA)
* 📘 [Pipeline Template Catalogs Docs](https://docs.cloudbees.com/docs/cloudbees-ci/latest/pipeline-templates-user-guide/)

### Organization Folders

* 🎥 [Create GitHub Org Folder – YouTube](https://www.youtube.com/watch?v=w5YupbQ1vHI)
* 📘 [CloudBees Docs: Org Folders](https://docs.cloudbees.com/docs/cloudbees-ci/latest/pipelines/pipeline-as-code#_organization_folders)

### Marker Files

* 📘 [Marker Files](https://docs.cloudbees.com/docs/cloudbees-ci/latest/pipelines/pipeline-as-code#custom-pac-scripts)
* 🧠 [Using Marker Files for Governance](https://www.cloudbees.com/blog/ensuring-corporate-standards-pipelines-custom-marker-files)

### GitHub App Authentication

* 🔐 [Using GitHub App Authentication](https://docs.cloudbees.com/docs/cloudbees-ci/latest/traditional-admin-guide/github-app-auth)

### Cross Team Collaboartion

* <https://docs.cloudbees.com/docs/cloudbees-ci/latest/pipelines/cross-team-collaboration>
* <https://www.youtube.com/watch?v=M_Hwwy7dzZY>

### Plugable Storage

### Workdspace Caching
