import sbt.Keys._

scalaVersion := "2.13.3"
name := """oystr-registry-service"""
version := "1.0-SNAPSHOT"
maintainer := "luan.melo@oystr.com.br"

updateOptions := updateOptions.value.withLatestSnapshots(true)

Compile / scalaSource       := baseDirectory.value / "src/main/java"
Compile / resourceDirectory := baseDirectory.value / "src/main/resources"

Test / scalaSource       := baseDirectory.value / "src/test/java"
Test / resourceDirectory := baseDirectory.value / "src/test/resources"

resolvers ++= Seq(
  Resolver.sonatypeRepo("releases"),
  Resolver.jcenterRepo
)
val AkkaVersion = "2.5.23"
val HibernateVersion = "6.1.5.Final"

libraryDependencies ++= Seq(
  ws,
  guice,
  filters,
  "javax.validation" % "validation-api" % "2.0.1.Final",
  "org.hibernate.validator" % "hibernate-validator" % HibernateVersion,
  "org.hibernate.validator" % "hibernate-validator-annotation-processor" % HibernateVersion,
  "org.glassfish" % "javax.el" % "3.0.1-b11",
  "com.typesafe.akka" % "akka-actor_2.13" % AkkaVersion,
  "com.google.guava" % "guava" % "28.2-jre",
  "org.projectlombok" % "lombok" % "1.18.12"
)

lazy val root = (project in file("."))
  .enablePlugins(PlayJava)
  .enablePlugins(DockerPlugin)

topLevelDirectory    := None
executableScriptName := "run"
packageName in Universal := "package"
