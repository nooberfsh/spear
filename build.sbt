lazy val modules: Seq[ProjectReference] = Seq(
  `spear-core`,
  `spear-docs`,
  `spear-examples`,
  `spear-local`,
  `spear-repl`,
  `spear-trees`,
  `spear-utils`
)

lazy val spear = {
  lazy val repl = taskKey[Unit]("Runs the Spear REPL.")

  Project(id = "spear", base = file("."))
    .aggregate(modules: _*)
    // Creates a SBT task alias "repl" that starts the REPL within an SBT session.
    .settings(repl := (run in `spear-repl` in Compile toTask "").value)
}

def spearModule(name: String): Project =
  Project(id = name, base = file(name))
    .settings(commonSettings)

lazy val `spear-utils` = spearModule("spear-utils")
  .settings(libraryDependencies ++= Dependencies.logging)
  .settings(libraryDependencies ++= Dependencies.scala)
  .settings(libraryDependencies ++= Dependencies.testing)

lazy val `spear-trees` = spearModule("spear-trees")
  .dependsOn(`spear-utils` % "compile->compile;test->test")

lazy val `spear-core` = spearModule("spear-core")
  .dependsOn(`spear-trees` % "compile->compile;test->test")
  .settings(libraryDependencies ++= Dependencies.fastparse)
  .settings(libraryDependencies ++= Dependencies.typesafeConfig)

lazy val `spear-local` = spearModule("spear-local")
  .dependsOn(`spear-core` % "compile->compile;test->test")

lazy val `spear-repl` = spearModule("spear-repl")
  .dependsOn(`spear-core` % "compile->compile;test->test")
  .dependsOn(`spear-local` % "compile->compile;test->test;compile->test")
  .settings(runtimeConfSettings)
  .settings(libraryDependencies ++= Dependencies.ammonite)
  .settings(libraryDependencies ++= Dependencies.scopt)

lazy val `spear-examples` = spearModule("spear-examples")
  .dependsOn(`spear-core`, `spear-local`)
  .settings(runtimeConfSettings)

lazy val `spear-docs` = spearModule("spear-docs")
  .dependsOn(`spear-core`, `spear-local`)


lazy val commonSettings = {
  val buildSettings = Seq(
    organization := "spear",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := Dependencies.Versions.scala,
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
    scalacOptions ++= Seq("-Ywarn-unused-import", "-Xlint"),
    javacOptions ++= Seq("-source", "1.7", "-target", "1.7", "-g", "-Xlint:-options")
  )

  val commonTestSettings = Seq(
    // Disables parallel test execution to ensure logging order.
    parallelExecution in Test := false,
    // Does not fork a new JVM process to run the tests.
    fork := false,
    // Shows duration and full exception stack trace
    testOptions in Test += Tests.Argument("-oDF")
  )

  Seq(
    buildSettings,
    commonTestSettings,
  ).flatten
}

lazy val runtimeConfSettings = Seq(
  unmanagedClasspath in Runtime += baseDirectory { _.getParentFile / "conf" }.value
)
