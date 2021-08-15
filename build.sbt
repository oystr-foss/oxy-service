import sbt.Keys._

scalaVersion := "2.13.3"
name := """oystr-registry-service"""
version := "v1.0.0"
maintainer := "rafael.silverio.it@gmail.com"

PlayKeys.devSettings := Seq(
  "play.server.http.port" -> "10000"
)

updateOptions := updateOptions.value.withLatestSnapshots(true)
Compile / scalaSource       := baseDirectory.value / "src/main/java"
Compile / resourceDirectory := baseDirectory.value / "src/main/resources"

Test / scalaSource       := baseDirectory.value / "src/test/java"
Test / resourceDirectory := baseDirectory.value / "src/test/resources"

resolvers ++= Seq(
  Resolver.sonatypeRepo("releases"),
  Resolver.jcenterRepo
)
val akkaVersion = "2.6.5"
val sttpVersion = "1.7.2"
val hibernateValidatorVersion = "6.1.5.Final"
val apacheCommonsVersion = "2.6"

libraryDependencies ++= Seq(
  ws,
  guice,
  filters,
  "javax.validation"              %  "validation-api"                           % "2.0.1.Final",
  "org.hibernate.validator"       %  "hibernate-validator"                      % hibernateValidatorVersion,
  "org.hibernate.validator"       %  "hibernate-validator-annotation-processor" % hibernateValidatorVersion,
  "org.glassfish"                 %  "javax.el"                                 % "3.0.1-b11",
  "com.typesafe.akka"             %  "akka-actor_2.13"                          % akkaVersion,
  "com.google.guava"              %  "guava"                                    % "28.2-jre",
  "com.typesafe.play"             %% "play-caffeine-cache"                      % "2.8.0-M4",
  "commons-io"                    %  "commons-io"                               % apacheCommonsVersion,
  "commons-lang"                  %  "commons-lang"                             % apacheCommonsVersion,
  "org.asynchttpclient"           %  "async-http-client"                        % "2.12.1",
  "com.google.cloud"              %  "google-cloud-logging"                     % "1.100.0",
  "org.projectlombok"             %  "lombok"                                   % "1.18.12"
)

lazy val root = (project in file("."))
  .enablePlugins(PlayJava)
  .enablePlugins(DockerPlugin)

topLevelDirectory    := None
executableScriptName := "run"
packageName in Universal := "package"
