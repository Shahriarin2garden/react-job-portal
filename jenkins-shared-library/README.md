# react-job-portal Jenkins Shared Library

Reusable pipeline steps for the `react-job-portal` CI/CD pipeline.

This directory is a **standalone Jenkins Shared Library repository**. Push it to
its own Git repository (for example
`https://github.com/Shahriarin2garden/react-job-portal-shared-lib.git`) and
register it in Jenkins under:

`Manage Jenkins` -> `System` -> `Global Trusted Pipeline Libraries`.

| Setting | Value |
| --- | --- |
| Name | `react-job-portal-shared-lib` |
| Default version | `main` |
| Retrieval method | Modern SCM -> Git |
| Project Repository | URL of this repository |
| Credentials | same credential used for the private app repo (optional) |
| Load implicitly | disabled (the `Jenkinsfile` imports it explicitly) |

After registration the `Jenkinsfile` loads it with:

```groovy
@Library('react-job-portal-shared-lib@main') _
```

## Provided steps

| Step | Purpose |
| --- | --- |
| `checkoutWithCredentials(map)` | Git checkout using a private-repo credential |
| `buildDockerImage(map)` | `docker build` driven by the project `Dockerfile` |
| `notifyEmail(map)` | HTML success/failure email through Jenkins SMTP |

## Layout

```
jenkins-shared-library/
  vars/
    checkoutWithCredentials.groovy
    buildDockerImage.groovy
    notifyEmail.groovy
  resources/
    email/success.html
    email/failure.html
  src/org/jobportal/Notify.groovy
```
