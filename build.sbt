import AssemblyKeys._

name := "CommonSpace"

scalaVersion := "2.10.0"

resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Seq(
  "com.azavea.geotrellis" %% "geotrellis" % "0.8.1-RC2",
  "com.azavea.geotrellis" %% "geotrellis-server" % "0.8.1-RC2",
  "org.spire-math" %% "spire" % "0.3.0",
  "org.scalatest" %% "scalatest" % "1.6.1" % "test"
)

assemblySettings

mergeStrategy in assembly <<= (mergeStrategy in assembly) {
  (old) => {
    case "reference.conf" => MergeStrategy.concat
    case "application.conf" => MergeStrategy.concat
    case "META-INF/MANIFEST.MF" => MergeStrategy.discard
    case "META-INF\\MANIFEST.MF" => MergeStrategy.discard
    case _ => MergeStrategy.first
  }
}