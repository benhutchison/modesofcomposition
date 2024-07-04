name := "modesofcomposition"

lazy val step1 = project.in(file("step1")).settings(commonSettings).dependsOn(common % "test->test;compile->compile")
lazy val step1solution = project.in(file("step1solution")).settings(commonSettings).dependsOn(common % "test->test;compile->compile")
lazy val step2 = project.in(file("step2")).settings(commonSettings).dependsOn(common % "test->test;compile->compile")
lazy val step2solution = project.in(file("step2solution")).settings(commonSettings).dependsOn(common % "test->test;compile->compile")
lazy val step3 = project.in(file("step3")).settings(commonSettings).dependsOn(common % "test->test;compile->compile")
lazy val step3solution = project.in(file("step3solution")).settings(commonSettings).dependsOn(common % "test->test;compile->compile")
lazy val step4 = project.in(file("step4")).settings(commonSettings).dependsOn(common % "test->test;compile->compile")
lazy val step4solution = project.in(file("step4solution")).settings(commonSettings).dependsOn(common % "test->test;compile->compile")
lazy val step5 = project.in(file("step5")).settings(commonSettings).dependsOn(common % "test->test;compile->compile")
lazy val step5solution = project.in(file("step5solution")).settings(commonSettings).dependsOn(common % "test->test;compile->compile")

lazy val solution = project.in(file("solution")).settings(commonSettings).dependsOn(common % "test->test;compile->compile")

//Common domain model and test support code. Not changed by or affected by exercises
lazy val common = project.in(file("common")).settings(commonSettings)


//these imports are automatically available in every file
val imports = Array(
  "java.lang",
  "scala",
  "scala.Predef",
  "cats",
  "cats.data",
  "cats.implicits",
  "cats.effect",
  "cats.effect.implicits",
  "fs2",
  "mouse.all",
  "eu.timepit.refined",
  "eu.timepit.refined.api",
  "eu.timepit.refined.auto",
  "eu.timepit.refined.numeric",
  "eu.timepit.refined.cats",
  "io.circe",
  "io.circe.generic.auto",
  "io.circe.parser",
  "io.circe.syntax",
  "io.circe.refined",
)
val circeVersion = "0.14.9"

val commonSettings = Seq(
  scalaVersion := "2.13.5",
  scalacOptions ++= Seq(
    s"-Yimports:${imports.mkString(",")}",
    "-Ywarn-value-discard"
  ),

  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.3" cross CrossVersion.full),

  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-core" % "2.6.0",
    "org.typelevel" %% "cats-effect" % "3.1.0",
    "org.typelevel" %% "alleycats-core" % "2.6.0",
    "co.fs2" %% "fs2-core" % "3.0.2",
    "org.typelevel" %% "mouse" % "1.0.2",
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic"% circeVersion,
    "io.circe" %% "circe-parser" % circeVersion,
    "io.circe" %% "circe-refined" % circeVersion,
    "eu.timepit" %% "refined" % "0.9.24",
    "eu.timepit" %% "refined-cats" % "0.9.24",
    "org.scalameta" %% "munit" % "0.7.25" % Test,
    "org.typelevel" %% "munit-cats-effect-3" % "1.0.2" % Test,
  ),
  //munit is a simple, modern alternative to the complexity of ScalaTest & Specs2
  testFrameworks += new TestFramework("munit.Framework")
)

val testWorking = TaskKey[Unit]("testWorking", "Compile all problems and run all solution tests")
testWorking := Seq(
  step1 / Test / compile,
  step1solution / Test / test,
  step2 / Test / compile,
  step2solution / Test / test,
  step3 / Test / compile,
  step3solution / Test / test,
  step4 / Test / compile,
  step4solution / Test / test,
  step5 / Test / compile,
  step5solution / Test / test,
  solution / Test / test,
).dependOn.value

ThisBuild / githubWorkflowBuild := Seq(WorkflowStep.Sbt(List("testWorking")))

ThisBuild / githubWorkflowPublishTargetBranches := Seq()