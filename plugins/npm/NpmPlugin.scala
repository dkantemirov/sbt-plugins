package sbt.plugins.npm

import sbt._
import sbt.Keys.{ target => trg, _ }
import org.json4s._
import org.json4s.ext.JavaTypesSerializers
import org.json4s.native.JsonMethods._

object NpmPlugin extends AutoPlugin {
  implicit val formats: Formats = DefaultFormats ++ JavaTypesSerializers.all
  private var npmProcess: Option[Process] = None

  object autoImport {
    // npm configurations
    lazy val NpmDevelopment: Configuration = config("npmDevelopment")
    lazy val NpmProduction: Configuration = config("npmProduction")
    // npm keys
    lazy val npmInstall: TaskKey[Unit] = taskKey[Unit]("""runs "npm install".""")
    lazy val npmTest: TaskKey[Unit] = taskKey[Unit]("""runs "npm test".""")
    lazy val npmBuild: TaskKey[(File, Seq[File])] = taskKey[(File, Seq[File])]("""runs "npm install" and then "npm run build-[mode]".""")
    lazy val npmStart: TaskKey[Unit] = taskKey[Unit]("""runs "npm install" and then starts "npm start" as background process.""")
    lazy val npmStop: TaskKey[Unit] = taskKey[Unit]("""stops "npm start" background process.""")
    lazy val nodeVersion: TaskKey[Option[ToolVersion]] = taskKey[Option[ToolVersion]]("version of node.")
    lazy val npmVersion: TaskKey[Option[ToolVersion]] = taskKey[Option[ToolVersion]]("version of npm.")
    lazy val npmConfiguration: TaskKey[NpmConfiguration] = taskKey[NpmConfiguration]("reads npm configuration from package.json.")
    lazy val npmProjectDir: SettingKey[File] = settingKey[File]("path to the project directory.")
    lazy val npmBuildMode: SettingKey[NpmBuildMode] = settingKey[NpmBuildMode]("npm build mode.")
  }

  import autoImport._

  lazy val npmCommonSetting: Seq[Setting[_]] = Seq(
    npmProjectDir := baseDirectory.value,
    trg in npmBuild := baseDirectory.value / "target/javascript",
    excludeFilter in npmBuild := "static",
    nodeVersion := toolVersion("node"),
    npmVersion := toolVersion("npm"),
    npmInstall <<= (streams, npmProjectDir) map { (s, dir) =>
      s.log.info("""Running "npm prune".""")
      run("npm" :: "prune" :: Nil, dir, s.log)
      s.log.info("""Running "npm install".""")
      run("npm" :: "install" :: Nil, dir, s.log)
    },
    npmTest <<= (streams, npmProjectDir, npmInstall) map { (s, dir, _) =>
      s.log.info("""Running "npm test".""")
      run("npm" :: "test" :: Nil, dir, s.log)
    },
    npmStart <<= (streams, npmProjectDir, npmStop) map { (s, dir, _) =>
      s.log.info("Starting npm.")
      npmProcess = Some(Process("npm" :: "start" :: Nil, dir).run(s.log))
    },
    npmStop <<= streams map { s =>
      if (npmProcess.isDefined) {
        s.log.info("Stopping npm.")
        npmProcess.get.destroy()
        npmProcess = None
      }
    },
    npmConfiguration <<= (streams, npmProjectDir) map { (s, dir) =>
      val configurationFile = dir / "package.json"
      s.log.info(s"Read npm configuration from ${configurationFile.toString}.")
      val parsed = parse(IO read configurationFile)
      val npmConfiguration = NpmConfiguration(
        (parsed \ "name").extract[String],
        (parsed \ "version").extract[String]
      )
      s.log.info(s"Npm configuration [$npmConfiguration].")
      npmConfiguration
    }
  )

  override lazy val projectSettings: Seq[Setting[_]] = {
    inConfig(NpmDevelopment)(npmCommonSetting ++ Seq(
      npmBuildMode := NpmBuildMode.Development,
      npmBuild <<= (streams, npmProjectDir, trg in npmBuild, excludeFilter in npmBuild, npmBuildMode in NpmDevelopment, npmInstall, npmConfiguration) map { (s, dir, targetDir, f, buildMode, _, c) =>
        build(s, dir, targetDir, f, buildMode, c)
      }
    )) ++
      inConfig(NpmProduction)(npmCommonSetting ++ Seq(
        npmBuildMode := NpmBuildMode.Production,
        npmBuild <<= (streams, npmProjectDir, trg in npmBuild, excludeFilter in npmBuild, npmBuildMode in NpmProduction, npmInstall, npmConfiguration) map { (s, dir, targetDir, f, buildMode, _, c) =>
          build(s, dir, targetDir, f, buildMode, c)
        }
      ))
  }

  private def build(s: TaskStreams, dir: File, targetDir: File, f: FileFilter, buildMode: NpmBuildMode, c: NpmConfiguration): (File, Seq[File]) = {
    val webappBuildMode = buildMode.value
    val webappTargetDir = targetDir / s"${c.buildName}-$webappBuildMode"
    val configurationFile = dir / "project" / (buildMode match {
      case NpmBuildMode.Development => "DevelopmentConfiguration.json"
      case NpmBuildMode.Production => "ProductionConfiguration.json"
    })
    val targetConfigurationFile = targetDir / "sbt/generated/Configuration.json"
    val targetConfigurationFileStr = targetConfigurationFile.toString
    s.log.info(s"Read application configuration from $targetConfigurationFileStr.")
    val parsed = parse(IO read configurationFile)
    val configurationStr = {
      val settings = classOf[JsonConfiguration].getDeclaredFields
        .map { f =>
          val name = f.getName
          val value = name match {
            case JsonConfigurationSetting.apiPathName => Option("../api/")
            case JsonConfigurationSetting.apiPortName => None
            case JsonConfigurationSetting.apiHostnameName => None
            case _ => (parsed \ name).extract[Option[String]]
          }
          JsonConfigurationSetting(name, value)
        }
        .filter(_.value.nonEmpty)
      settings
        .map {
          case setting @ _ if setting eq settings.last => s""" "${setting.name}": "${setting.value.get}""""
          case setting @ _ => s""" "${setting.name}": "${setting.value.get}","""
        }
        .mkString("{\n", "\n", "\n}")
    }
    IO write (targetConfigurationFile, configurationStr)
    IO delete webappTargetDir
    s.log.info("""Running "npm run build".""")
    run("npm" :: "run" :: "build" :: "--" :: s"${buildMode.command}" :: "--c" :: s""""$targetConfigurationFileStr""" :: Nil, dir, s.log)
    webappTargetDir -> (webappTargetDir * ("*" -- f)).get.***.get
  }

  private def run(commands: Seq[String], cwd: File, log: Logger): Unit = {
    val cmd = "cmd" :: "/c" :: Nil
    val exitCode = Process(cmd ++: commands, cwd).!(log)
    if (exitCode != 0) throw new Exception(s"""Running "${commands.mkString(" ")}" failed with exit code $exitCode.""")
  }
  private def toolVersion(name: String): Option[ToolVersion] = {
    try Some(ToolVersion(s"$name --version".!!)) catch {
      case e: Exception => None
    }
  }
}