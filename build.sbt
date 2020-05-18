name := "modesofcomposition"

scalaVersion := "2.13.2"

val circeVersion = "0.12.3"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core" % "2.2.0-M1",
  "org.typelevel" %% "cats-effect" % "2.1.3",
  "org.typelevel" %% "cats-mtl-core" % "0.7.0",
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic"% circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,

)