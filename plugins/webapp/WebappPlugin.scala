package sbt.plugins.webapp

import sbt._
import sbt.Keys.{ target => trg, _ }
import sbt.plugins.npm.NpmPlugin

object WebappPlugin extends AutoPlugin {
  override def requires = NpmPlugin

  object autoImport {
    lazy val webappDevelopmentPackage: TaskKey[File] = taskKey[File]("packages the webapp for development purposes.")
    lazy val webappProductionPackage: TaskKey[File] = taskKey[File]("packages the webapp for production purposes.")
    lazy val webappMappingsTask: TaskKey[Seq[(File, String)]] = taskKey[Seq[(File, String)]]("mappings task.")
    lazy val webappWebXml: TaskKey[File] = taskKey[File]("creates the web.xml for jee7 servlet.")

    def webappSettings(conf: Configuration): Seq[Setting[_]] = {
      import NpmPlugin.autoImport._
      Defaults.packageTaskSettings(webappDevelopmentPackage, webappMappingsTask in NpmDevelopment) ++
        Defaults.packageTaskSettings(webappProductionPackage, webappMappingsTask in NpmProduction) ++
        (conf.name match {
          case "development" => Seq(artifact in (conf, webappDevelopmentPackage) <<= moduleName(n => Artifact(n, "war", "war")))
          case "production" => Seq(artifact in (conf, webappProductionPackage) <<= moduleName(n => Artifact(n, "war", "war")))
        })
    }
  }

  import autoImport._
  import NpmPlugin.autoImport._

  lazy val webappCommonSetting: Seq[Setting[_]] = Seq(
    trg in webappWebXml := baseDirectory.value / "target/webapp",
    webappWebXml := {
      val xmlFile = (trg in webappWebXml).value / "WEB-INF/web.xml"
      val xml = """<?xml version="1.0"?>""" + "\n" + {
        <web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee http://xmlns.jcp.org/xml/ns/javaee/web-app_3_1.xsd"
                   version="3.1"
                   metadata-complete="true">
            <!-- In order to speed up web application startup, we've disabled scanning of WEB-INF/lib jars for Servlet 3.0
    specific annotations. If you wish to use these annotations, remove the 'metadata-complete' attribute. -->

            <listener>
              <listener-class>spray.servlet.Initializer</listener-class>
            </listener>

            <servlet>
              <servlet-name>SprayConnectorServlet</servlet-name>
              <servlet-class>spray.servlet.Servlet30ConnectorServlet</servlet-class>
              <load-on-startup>-1</load-on-startup>
              <async-supported>true</async-supported>
            </servlet>

            <servlet-mapping>
              <servlet-name>SprayConnectorServlet</servlet-name>
              <url-pattern>/*</url-pattern>
            </servlet-mapping>
          </web-app>
      }
      IO write (xmlFile, xml)
      xmlFile
    }
  )

  lazy val webappDevelopmentSettings: Seq[Setting[_]] = webappCommonSetting ++ Seq(
    webappMappingsTask in NpmDevelopment <<= (npmBuild in NpmDevelopment, sourceDirectory in Compile, classDirectory in Compile, products in Compile, trg in webappWebXml, webappWebXml) map { (npm, srcDir, classDir, products, webappDir, _) =>
      val (npmBuildBaseDirectory, npmBuildFiles) = npm
      mappings(npmBuildBaseDirectory, npmBuildFiles, srcDir, classDir, products, webappDir)
    }
  )

  lazy val webappProductionSettings: Seq[Setting[_]] = webappCommonSetting ++ Seq(
    webappMappingsTask in NpmProduction <<= (npmBuild in NpmProduction, sourceDirectory in Compile, classDirectory in Compile, products in Compile, trg in webappWebXml, webappWebXml) map { (npm, srcDir, classDir, products, webappDir, _) =>
      val (npmBuildBaseDirectory, npmBuildFiles) = npm
      mappings(npmBuildBaseDirectory, npmBuildFiles, srcDir, classDir, products, webappDir)
    }
  )

  override lazy val projectSettings: Seq[Setting[_]] = {
    inConfig(NpmDevelopment)(webappDevelopmentSettings) ++
      inConfig(NpmProduction)(webappProductionSettings)
  }

  private def mappings(npmBuildBaseDirectory: File, npmBuildFiles: Seq[File], srcDir: File, classDir: File, products: Seq[File], webappDir: File): Seq[(File, String)] = {
    val classes = (products * ("*" -- "bundle")).get.***.get
    Seq(
      copy(npmBuildBaseDirectory, npmBuildFiles, "WEB-INF/classes/public/"),
      copy(classDir, classes, "WEB-INF/classes"),
      copy(webappDir, "")
    ).flatten
  }
  private def copy(from: File, to: String): Seq[(File, String)] = {
    from.*** pair rebase(from, to)
  }
  private def copy(from: Seq[File], to: String): Seq[(File, String)] = {
    from.*** pair rebase(from, new File(to)) map { case (f, t) => f -> t.getPath }
  }
  private def copy(from: File, files: Seq[File], to: String): Seq[(File, String)] = {
    files pair rebase(from, to)
  }
}