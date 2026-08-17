# Cross Team Collaboration Events

Two independent pipelines demonstrating CloudBees CI's cross-team collaboration event bus: one
publishes an event, the other subscribes to matching events and reacts.

| File | Role | What it does |
| --- | --- | --- |
| [`Jenkinsfile-producer`](Jenkinsfile-producer) | Producer | Runs on a `maven` Kubernetes agent and calls `publishEvent` with a Maven-artifact-shaped JSON event (`com.example:cloudbees-jar:0.5-SNAPSHOT`). |
| [`Jenkinsfile-consumer`](Jenkinsfile-consumer) | Consumer | Declares `triggers { eventTrigger jmespathQuery(...) }` filtering for events where `event` contains `com.example:` and `-SNAPSHOT`; on match, runs on a `curl` agent and prints `called`. |

Both pipelines run on `kubernetes` agents pinned to the `workload: agent` node pool via
`nodeSelector`/`tolerations` — adjust those to match your cluster's labels/taints.

## Resources

* <https://docs.cloudbees.com/docs/cloudbees-ci/latest/pipelines/cross-team-collaboration>
* <https://www.youtube.com/watch?v=M_Hwwy7dzZY>
