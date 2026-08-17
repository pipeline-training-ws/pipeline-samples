# CI JFrog Integration

`Jenkinsfile.groovy` loads the `shared-library` (as `ci-shared-library@main`) and runs on a
Kubernetes agent with a single `shell` container (`curlimages/curl`). It builds a config map from
inline YAML and uses it to:

1. Write a `test.txt` file.
2. Upload it to JFrog Artifactory via the shared library's `jfrogUploadArtifact` step.
3. Download it back via `jfrogDownloadArtifact`.

```yaml
FILE_PATH: 'test.txt'
REPO_NAME: 'holy-generic-local'
ARTIFACT_PATH: 'test/test.txt'
ARTIFACTORY_URL: 'https://trial79nt49.jfrog.io'
```

## Prerequisites

- A `jfrog-user-token` Secret Text credential configured in Jenkins (Bearer token), as consumed by
  [`jfrogUploadArtifact`](../../shared-library/vars/jfrogUploadArtifact.groovy) /
  [`jfrogDownloadArtifact`](../../shared-library/vars/jfrogDownloadArtifact.groovy).
- Network access from the agent to the configured `ARTIFACTORY_URL`.

## Related Resources

- [`shared-library/vars/jfrogUploadArtifact.groovy`](../../shared-library/vars/jfrogUploadArtifact.groovy)
- [`shared-library/vars/jfrogDownloadArtifact.groovy`](../../shared-library/vars/jfrogDownloadArtifact.groovy)
- [JFrog Artifactory REST API documentation](https://jfrog.com/help/r/jfrog-rest-apis/artifactory-rest-apis)
