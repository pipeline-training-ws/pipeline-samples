# CI Jira Integration

`Jenkinsfile.groovy` loads the `shared-library` (as `ci-shared-library@main`) and runs on a
Kubernetes agent with a single `shell` container (`curlimages/curl`). It builds a config map from
inline YAML and uses it to create a Jira issue via the shared library's `jiraCreateIssue` step,
then adds a comment to a (currently hardcoded) issue key using `jiraComment`.

```yaml
JIRA_KEY: 'SCRUM'
JIRA_ISSUE_TYPE: 'Task'
JIRA_DESCRIPTION: 'TEST1'
JIRA_SUMMARY: 'TEST2'
JIRA_URL: 'https://cloudbees-acaternberg-test.atlassian.net/'
```

> **Note:** the pipeline currently comments on a static `issueKey: 'SCRUM-1'` rather than the key
> of the issue it just created — the `TODO` in the Jenkinsfile calls out extracting the real
> issue key (e.g. with `jq`) from the `jiraCreateIssue` response.
>
> **Also note:** `jiraComment` is not currently defined under `shared-library/vars/` — only
> `jiraCreateIssue` exists. This pipeline will fail at the `jiraComment` step until that global
> step is added to the shared library.

## Prerequisites

- A `jira-user-token` Username/Password credential configured in Jenkins, as consumed by
  [`jiraCreateIssue`](../../shared-library/vars/jiraCreateIssue.groovy).
- Network access from the agent to the configured `JIRA_URL`.
- An issue matching the hardcoded `issueKey` must already exist for the `jiraComment` step to succeed.

## Related Resources

- [`shared-library/vars/jiraCreateIssue.groovy`](../../shared-library/vars/jiraCreateIssue.groovy)
- [Jira Cloud REST API v2 documentation](https://developer.atlassian.com/cloud/jira/platform/rest/v2/intro/)
