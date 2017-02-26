package sbt.plugins.npm

case class NpmConfiguration(name: String, version: String) {
  lazy val buildName = s"$name-$version"
}
