package sbt.plugins.npm

abstract case class NpmBuildMode(value: String, command: String)

object NpmBuildMode {
  object Production extends NpmBuildMode("production", "-p")
  object Development extends NpmBuildMode("development", "-d")

  def apply(value: String): NpmBuildMode = value match {
    case Production.value => Production
    case _ => Development
  }
}