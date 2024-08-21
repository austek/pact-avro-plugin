// sbt-github-actions

ThisBuild / githubWorkflowOSes := Seq("ubuntu-latest", "macos-latest", "windows-2019")
ThisBuild / githubWorkflowJavaVersions := Seq(
  JavaSpec.zulu("17"),
  JavaSpec.zulu("20")
)
ThisBuild / githubWorkflowTargetBranches := Seq("main")
ThisBuild / githubWorkflowTargetTags := Seq("v*")

ThisBuild / githubWorkflowEnv := Map(
  "PACT_BROKER_BASE_URL" -> "https://test.pactflow.io",
  "PACT_BROKER_USERNAME" -> "dXfltyFMgNOFZAxr8io9wJ37iUpY42M",
  "PACT_BROKER_PASSWORD" -> "O5AIZWxelWbLvqMd8PkAVycBJh2Psyg1",
  "GITHUB_TOKEN" -> "${{ secrets.GITHUB_TOKEN }}"
)
ThisBuild / githubWorkflowBuildMatrixFailFast := Some(false)
ThisBuild / githubWorkflowBuild := Seq(
  WorkflowStep.Run(
    name = Some("Set outputs"),
    id = Some("vars"),
    commands = List(
      """echo "sha_short=$(git rev-parse --short ${{ github.sha }})" >> $GITHUB_OUTPUT""",
      """echo "git_tag=$(git describe --tags)" >> $GITHUB_OUTPUT"""
    )
  ),
  WorkflowStep.Use(
    UseRef.Public("pactflow", "actions", "main"),
    name = Some("Pactflow Setup")
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
    name = Some("Pact publish Windows"),
    commands = List("""pact-broker.bat publish
        | "modules/examples/consumer/target/pacts"
        | --consumer-app-version=${{ steps.vars.outputs.git_tag }}-${{ runner.os }}
        | --tag=${{ steps.vars.outputs.git_tag }}-${{ runner.os }}
        | """.stripMargin.replaceAll("\n", "")),
    cond = Some("contains(runner.os, 'windows')")
  ),
  WorkflowStep.Run(
    name = Some("Pact publish *nix"),
    commands = List("""pact-broker publish
        | "modules/examples/consumer/target/pacts"
        | --consumer-app-version=${{ steps.vars.outputs.git_tag }}-${{ runner.os }}
        | --tag=${{ steps.vars.outputs.git_tag }}-${{ runner.os }}
        | """.stripMargin.replaceAll("\n", "")),
    cond = Some("!contains(runner.os, 'windows')")
  ),
  WorkflowStep.Sbt(
    name = Some("Test Provider"),
    commands = List("provider/test"),
    env = Map(
      "PACT_BROKER_TAG" -> "${{ steps.vars.outputs.git_tag }}-${{ runner.os }}",
    )
  )
)

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
