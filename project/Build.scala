import sbt._
import sbt.Keys._

object CommonspaceBuild extends Build {
  val scalaOptions = Seq(
        "-deprecation",
        "-unchecked",
        "-Yclosure-elim",
        "-optimize",
        "-language:implicitConversions",
        "-language:postfixOps",
        "-language:existentials",
        "-feature"
  )

  val key = AttributeKey[Boolean]("javaOptionsPatched")

  lazy val root = 
    Project("root", file(".")).settings(
      organization := "com.azavea.commonspace",
      name := "commonspace",
      version := "0.1.0-SNAPSHOT",
      scalaVersion := "2.10.2-RC2",
      
      scalacOptions ++= Seq("-deprecation",
        "-unchecked",
        "-Yclosure-elim",
        "-optimize",
        "-language:implicitConversions",
        "-language:postfixOps",
        "-language:existentials",
        "-feature"),
      scalacOptions in Compile in doc ++= Seq("-diagrams", "-implicits"),
      parallelExecution := false,

      fork in test := false,

      mainClass := Some("commonspace.Main"),

      javaOptions in run += "-Xmx10G",

      libraryDependencies ++= Seq(
        "com.azavea.geotrellis" %% "geotrellis" % "0.9.0-SNAPSHOT",
        "com.azavea.geotrellis" %% "geotrellis-server" % "0.9.0-SNAPSHOT",
        "com.typesafe" % "config" % "1.0.2",
        "org.spire-math" %% "spire" % "0.3.0",
        "org.scalatest" %% "scalatest" % "2.0.M5b" % "test",
        "org.scalesxml" %% "scales-xml" % "0.4.5",
        "org.scala-lang" %% "scala-pickling" % "0.8.0-SNAPSHOT"
      ),
      resolvers += Resolver.sonatypeRepo("snapshots"),

      licenses := Seq("MIT License" -> url("http://www.opensource.org/licenses/mit-license.html")),
      homepage := Some(url("http://github.com/azavea/commonspace")),

      pomExtra := (
        <scm>
          <url>git@github.com:azavea/commonspace.git</url>
          <connection>scm:git:git@github.com:azavea/commonspace.git</connection>
          </scm>
          <developers>
          <developer>
          <id>lossyrob</id>
          <name>Rob Emanuele</name>
          <url>http://github.com/lossyrob/</url>
            </developer>
          <developer>
          <id>joshmarcus</id>
          <name>Josh Marcus</name>
          <url>http://github.com/joshmarcus/</url>
            </developer>
          </developers>
      )
    )
}
