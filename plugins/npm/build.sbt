name := "sbt-plugins-npm"

version := "1.0.0-SNAPSHOT"

libraryDependencies ++= Seq(
  "org.json4s" %% "json4s-native" % "3.4.2",
  "org.json4s" %% "json4s-ext" % "3.4.2"
)

sbtPlugin := true