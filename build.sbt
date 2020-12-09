import sbt.Keys._

scalaVersion := "2.13.3"
name := """oystr-registry-service"""
version := "0.0.1"
maintainer := "luan.melo@oystr.com.br"

PlayKeys.devSettings := Seq(
  "play.server.http.port" -> "10000"
)

// Ref: https://www.playframework.com/documentation/2.8.x/JavaJPA#Deploying-Play-with-JPA
PlayKeys.externalizeResourcesExcludes += baseDirectory.value / "src" / "main" / "resources" / "META-INF" / "persistence.xml"

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
val AkkaSttpVersion = "1.7.2"
val HibernateValidatorVersion = "6.1.5.Final"
val apacheCommonsVersion = "2.6"

libraryDependencies ++= Seq(
  ws,
  guice,
  filters,
  javaJpa,
  "javax.validation"              % "validation-api"                           % "2.0.1.Final",
  "org.hibernate.validator"       % "hibernate-validator"                      % HibernateValidatorVersion,
  "org.hibernate.validator"       % "hibernate-validator-annotation-processor" % HibernateValidatorVersion,
  "org.glassfish"                 % "javax.el"                                 % "3.0.1-b11",
  "com.typesafe.akka"             % "akka-actor_2.13"                          % AkkaVersion,
  "com.softwaremill.sttp"         %% "core"                                    % AkkaSttpVersion,
  "com.softwaremill.sttp.client3" %% "okhttp-backend"                          % "3.0.0-RC11",
  "com.softwaremill.sttp.client3" %% "async-http-client-backend-future"        % "3.0.0-RC11",
  "com.google.guava"              % "guava"                                    % "28.2-jre",
  "org.projectlombok"             % "lombok"                                   % "1.18.12",
  "com.typesafe.play"             %% "play-caffeine-cache"                     % "2.8.0-M4",
  "commons-io"                    %  "commons-io"                              % apacheCommonsVersion,
  "commons-lang"                  %  "commons-lang"                            % apacheCommonsVersion,
  "org.asynchttpclient"           % "async-http-client"                        % "2.12.1",
  "org.postgresql"                % "postgresql"                               % "42.2.18",
  "org.hibernate"                 % "hibernate-core"                           % "5.4.25.Final"
)

lazy val root = (project in file("."))
  .enablePlugins(PlayJava)
  .enablePlugins(DockerPlugin)

topLevelDirectory    := None
executableScriptName := "run"
packageName in Universal := "package"
