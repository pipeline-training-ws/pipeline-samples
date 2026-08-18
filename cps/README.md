**Pipeline CPS (Continuation-Passing Style)** is the underlying engine Jenkins uses to execute pipeline code. It rewrites standard Groovy bytecode so the pipeline can pause at any step (e.g., waiting for an agent, a `sh` command, or human input), serialize its state to disk, survive a Jenkins restart, and resume right where it left off.

---

### When to Use `@NonCPS`

Annotate a method with `@NonCPS` to instruct Jenkins to **disable the CPS transformation** for that method and run it as raw, native Groovy.

**Use `@NonCPS` when:**

* **Handling Non-Serializable Objects:** You need to manipulate objects that cannot be saved to disk (e.g., Regex matchers, open file streams, complex third-party library objects).
* **Heavy Data Processing / Performance:** You are running CPU-intensive operations (e.g., massive array transformations, deep map iterations, sorting large collections). CPS transformation introduces heavy execution overhead; `@NonCPS` runs at native Groovy speed.
* **Fixing Groovy CPS Bugs:** Certain native Groovy methods (like standard `.collect()`, `.toSorted()`, or dynamic closure operations) fail or return incorrect results inside the CPS engine.

---

### When NOT to Use `@NonCPS`

**Do NOT use `@NonCPS` if the method contains Jenkins Pipeline steps** (e.g., `sh`, `echo`, `node`, `stage`, `git`, `input`).

Because `@NonCPS` methods execute synchronously on the main thread without the ability to pause or break, calling a pipeline step inside a `@NonCPS` block will cause silent failures, skipped steps, or anomalous behavior (e.g., `expected to call ... but wound up catching ...` errors).

---

### Impact and Risk

* **Performance & Memory (Positive Impact):** `@NonCPS` bypasses state-saving overhead, making tight loops and utility logic execute significantly faster while saving memory on the controller node.
* **Loss of Durability (Risk):** If Jenkins restarts while a `@NonCPS` method is executing, the pipeline **cannot resume** mid-method and will fail.
* **Pipeline Step Breakdown (Risk):** Invoking pipeline steps inside a `@NonCPS` method creates severe bugs, as the CPS continuation mechanism breaks down.
* **State Pollution (Risk):** Variables modified inside `@NonCPS` methods that aren't serializable might cause `NotSerializableException` errors as soon as execution exits the method and returns to the normal CPS pipeline flow.

Here are side-by-side examples showing where `@NonCPS` is required and where using it breaks your pipeline execution.

---

### Example 1: Where `@NonCPS` Works (Fixing CPS Limitations)

Native Groovy objects like regex `Matcher` or functional array transformations (`.collect`, `.findAll`) either fail to serialize or perform terribly under CPS. Wrapping them in `@NonCPS` lets them execute as native Groovy.

```groovy
// Good: NonCPS allows non-serializable objects and fast collection operations
@NonCPS
def extractAppVersions(List<String> logLines) {
    // java.util.regex.Matcher is NOT serializable — CPS will throw NotSerializableException
    def pattern = ~/VERSION: (\d+\.\d+\.\d+)/
    
    return logLines.collect { line ->
        def matcher = pattern.matcher(line)
        matcher.find() ? matcher.group(1) : null
    }.findAll { it != null }
}

pipeline {
    agent any
    stages {
        stage('Parse Logs') {
            steps {
                script {
                    def logs = [
                        "INFO 10:00 - Starting build",
                        "INFO 10:01 - VERSION: 1.4.2 deployed",
                        "INFO 10:02 - VERSION: 2.0.0 deployed"
                    ]
                    
                    // Runs safely at native speed
                    def versions = extractAppVersions(logs)
                    echo "Extracted versions: ${versions.join(', ')}"
                }
            }
        }
    }
}

```

---

### Example 2: Where `@NonCPS` Breaks (Calling Pipeline Steps)

If you attempt to invoke *any* Jenkins pipeline step (`sh`, `echo`, `archiveArtifacts`, `stage`, etc.) inside a `@NonCPS` method, the engine cannot pause/resume execution properly.

```groovy
// BAD: Calling a pipeline step inside @NonCPS
@NonCPS
def runBadCleanup(List<String> files) {
    files.each { fileName ->
        // BREAKS: 'sh' and 'echo' are CPS steps executing in a non-CPS context
        echo "Deleting file: ${fileName}"
        sh "rm -f ${fileName}"
    }
}

pipeline {
    agent any
    stages {
        stage('Cleanup') {
            steps {
                script {
                    def filesToDelete = ['app.tmp', 'cache.bak']
                    
                    // This will cause unexpected pipeline failures, skipped steps,
                    // or "expected to call ... but wound up catching ..." exceptions
                    runBadCleanup(filesToDelete)
                }
            }
        }
    }
}

```

---

### Key Takeaway for Refactoring

If you need to combine standard Groovy logic with pipeline steps, **keep the Pipeline steps in regular CPS code** and pass simple serializable values into `@NonCPS` helper functions:

```groovy
// Correct Approach: Separate CPS steps from NonCPS calculations

// Pure Groovy logic (NonCPS)
@NonCPS
List<String> filterTargetFiles(List<String> files) {
    return files.findAll { it.endsWith('.tmp') }
}

// Pipeline script block (Standard CPS)
pipeline {
    agent any
    stages {
        stage('Clean') {
            steps {
                script {
                    def allFiles = ['app.tmp', 'cache.bak', 'test.tmp']
                    
                    // 1. Compute in NonCPS
                    def targets = filterTargetFiles(allFiles) 
                    
                    // 2. Execute pipeline steps in standard CPS pipeline loop
                    for (file in targets) {
                        echo "Deleting file: ${file}"
                        sh "rm -f ${file}"
                    }
                }
            }
        }
    }
}

```
