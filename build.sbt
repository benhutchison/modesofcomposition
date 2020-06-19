name := "modesofcomposition"

scalaVersion := "2.13.2"

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
  "cats.mtl",
  "cats.mtl.implicits",
  "eu.timepit.refined",
  "eu.timepit.refined.api",
  "eu.timepit.refined.auto",
  "eu.timepit.refined.numeric",
  "eu.timepit.refined.cats",
)
scalacOptions += s"-Yimports:${imports.mkString(",")}"

addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full)

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")

addCompilerPlugin("org.augustjune" %% "context-applied" % "0.1.3")

val circeVersion = "0.12.3"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core" % "2.2.0-M1",
  "org.typelevel" %% "cats-effect" % "2.1.3",
  "org.typelevel" %% "cats-mtl-core" % "0.7.0",
  "org.typelevel" %% "alleycats-core" % "2.2.0-M1",
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic"% circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "io.circe" %% "circe-refined" % circeVersion,
  "eu.timepit" %% "refined" % "0.9.14",
  "eu.timepit" %% "refined-cats" % "0.9.14",
  "io.chrisdavenport" %% "cats-effect-time" % "0.1.2",
  "org.scalameta" %% "munit" % "0.7.7" % Test,
)

testFrameworks += new TestFramework("munit.Framework")