// sbt-github-actions
ThisBuild / githubWorkflowBuild := Seq(
  WorkflowStep.Run(
    name = Some("Start containers"),
    commands = List("docker-compose -f docker-compose.yml up -d")
  ),
  WorkflowStep.Sbt(
    name = Some("Build project"),
    commands = List("compile", "scalafmtCheckAll", "javafmtCheckAll", "plugin/test")
  ),
  WorkflowStep.Sbt(
    name = Some("Test Consumer"),
    commands = List("consumer/test")
  ),
  WorkflowStep.Run(
    name = Some("Upload Consumer Pact"),
    commands = List("./scripts/pact-publish.sh")
  ),
  WorkflowStep.Sbt(
    name = Some("Test Provider"),
    commands = List("provider/test")
  ),
  WorkflowStep.Run(
    cond = Some("always()"),
    name = Some("Stop containers"),
    commands = List("docker-compose -f docker-compose.yml down")
  )
)
// Add windows-latest when https://github.com/sbt/sbt/issues/7082 is resolved
// Add macos-latest when step to install docker on it is done
ThisBuild / githubWorkflowOSes := Seq("ubuntu-latest")
ThisBuild / githubWorkflowJavaVersions := Seq(
  JavaSpec.temurin("17"),
  JavaSpec.temurin("20")
)
ThisBuild / githubWorkflowTargetBranches := Seq("main")
ThisBuild / githubWorkflowTargetTags := Seq("v*")
ThisBuild / githubWorkflowPublishTargetBranches := Seq(
  RefPredicate.StartsWith(Ref.Tag("v"))
)

ThisBuild / githubWorkflowPublish := Seq(
  WorkflowStep.Use(
    UseRef.Public("actions", "setup-node", "v1"),
    name = Some("Doc - Install node"),
    params = Map("node-version" -> "16.x")
  ),
  WorkflowStep.Run(
    name = Some("Doc - Install dependencies"),
    commands = List("npm ci")
  ),
  WorkflowStep.Run(
    name = Some("Doc - build"),
    commands = List("./scripts/docBuild.sh ${{ github.ref }}")
  ),
  WorkflowStep.Sbt(
    name = Some("Build package"),
    commands = List("universal:packageZipTarball")
  ),
  WorkflowStep.Run(
    name = Some("Prepare Artifacts"),
    commands = List("./scripts/prepArtifacts.sh")
  ),
  WorkflowStep.Use(
    UseRef.Public("svenstaro", "upload-release-action", "v2"),
    name = Some("Upload Release Assets"),
    id = Some("upload-release-asset"),
    params = Map(
      "repo_token" -> "${{ secrets.GITHUB_TOKEN }}",
      "file" -> "target/artifacts/*",
      "file_glob" -> "true",
      "tag" -> "${{ github.ref }}"
    )
  ),
  WorkflowStep.Sbt(
    name = Some("Publish docs"),
    commands = List("publishToGitHubPages"),
    env = Map(
      "GITHUB_TOKEN" -> "${{ secrets.GITHUB_TOKEN }}"
    )
  )
)
