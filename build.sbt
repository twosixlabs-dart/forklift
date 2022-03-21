import sbt.{Def, _}
import Dependencies._
import sbtassembly.AssemblyPlugin.autoImport.assemblyMergeStrategy

/*
   ##############################################################################################
   ##                                                                                          ##
   ##                                  SETTINGS DEFINITIONS                                    ##
   ##                                                                                          ##
   ##############################################################################################
 */

// integrationConfig and wipConfig are used to define separate test configurations for integration testing
// and work-in-progress testing
lazy val IntegrationConfig = config( "integration" ) extend( Test )
lazy val WipConfig = config( "wip" ) extend( Test )

lazy val commonSettings : Seq[ Def.Setting[ _ ] ] = {
    inConfig( IntegrationConfig )( Defaults.testTasks ) ++
    inConfig( WipConfig )( Defaults.testTasks ) ++
    Seq(
        organization in ThisBuild := "com.twosixlabs.dart.forklift",
        scalaVersion in ThisBuild := "2.12.7",
        resolvers ++= Seq(
            "Maven Central" at "https://repo1.maven.org/maven2/",
            "JCenter" at "https://jcenter.bintray.com",
            "Local Ivy Repository" at s"file://${System.getProperty( "user.home" )}/.ivy2/local/default"
        ),
        javacOptions ++= Seq( "-source", "1.8", "-target", "1.8" ),
        scalacOptions += "-target:jvm-1.8",
        useCoursier := false,
        libraryDependencies ++= logging ++
                                scalaTest ++
                                betterFiles ++
                                scalaMock ++
                                dartCommons,
        excludeDependencies ++= Seq( ExclusionRule( "org.slf4j", "slf4j-log4j12" ),
                  ExclusionRule( "org.slf4j", "log4j-over-slf4j" ),
                  ExclusionRule( "log4j", "log4j" ),
                  ExclusionRule( "org.apache.logging.log4j", "log4j-core" ) ),
        dependencyOverrides ++= Seq( "com.google.guava" % "guava" % "15.0",
                                     "com.fasterxml.jackson.core" % "jackson-core" % jacksonOverrideVersion,
                                     "com.fasterxml.jackson.core" % "jackson-annotation" % jacksonOverrideVersion,
                                     "com.fasterxml.jackson.core" % "jackson-databind" % jacksonOverrideVersion ),
        // `sbt test` should skip tests tagged IntegrationTest
        Test / testOptions := Seq( Tests.Argument( "-l", "annotations.IntegrationTest" ) ),
        // `sbt integration:test` should run only tests tagged IntegrationTest
        IntegrationConfig / parallelExecution := false,
        IntegrationConfig / testOptions := Seq( Tests.Argument( "-n", "annotations.IntegrationTest" ) ),
        // `sbt wip:test` should run only tests tagged WipTest
        WipConfig / testOptions := Seq( Tests.Argument( "-n", "annotations.WipTest" ) ),
     )
}

lazy val disablePublish = Seq(
    skip.in( publish ) := true,
)

lazy val assemblySettings = Seq(
    assemblyMergeStrategy in assembly := {
        case PathList( "META-INF", "MANIFEST.MF" ) => MergeStrategy.discard
        case PathList( "reference.conf" ) => MergeStrategy.concat
        case x => MergeStrategy.last
    },
    test in assembly := {},
    mainClass in( Compile, run ) := Some( "Main" ),
)

sonatypeProfileName := "com.twosixlabs"
inThisBuild(List(
    organization := "com.twosixlabs.dart.forklift",
    homepage := Some(url("https://github.com/twosixlabs-dart/forklift")),
    licenses := List("GNU-Affero-3.0" -> url("https://www.gnu.org/licenses/agpl-3.0.en.html")),
    developers := List(
        Developer(
            "twosixlabs-dart",
            "Two Six Technologies",
            "",
            url("https://github.com/twosixlabs-dart")
        )
    )
))

ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
ThisBuild / sonatypeRepository := "https://s01.oss.sonatype.org/service/local"

/*
   ##############################################################################################
   ##                                                                                          ##
   ##                                  PROJECT DEFINITIONS                                     ##
   ##                                                                                          ##
   ##############################################################################################
 */

lazy val root = ( project in file( "." ) )
  .disablePlugins( sbtassembly.AssemblyPlugin )
  .aggregate( forkliftApi, forkliftServices, forkliftControllers, forkliftMicroservice, forkliftClient )
  .settings(
      name := "forklift",
      disablePublish
   )

lazy val forkliftApi = ( project in file( "forklift-api" ) )
  .configs( IntegrationConfig, WipConfig )
  .disablePlugins( sbtassembly.AssemblyPlugin )
  .settings(
      commonSettings,
      libraryDependencies ++= jackson
                              ++ dartRestCommons
                              ++ tapir
                              ++ circe
                              ++ jsonValidator,
   )

lazy val forkliftServices = ( project in file( "forklift-services" ) )
  .dependsOn( forkliftApi )
  .configs( IntegrationConfig, WipConfig )
  .disablePlugins( sbtassembly.AssemblyPlugin )
  .settings(
      commonSettings,
      libraryDependencies ++= okhttp ++
                              database ++
                              operations,
      disablePublish,
   )

lazy val forkliftControllers = ( project in file( "forklift-controllers" ) )
  .dependsOn( forkliftApi, forkliftServices )
  .configs( IntegrationConfig, WipConfig )
  .disablePlugins( sbtassembly.AssemblyPlugin )
  .settings(
      commonSettings,
      libraryDependencies ++= scalatra ++
                              jackson ++
                              jsonValidator ++
                              dartAuthCommons ++
                              dartRestCommons,
   )

lazy val forkliftMicroservice = ( project in file( "forklift-microservice" ) )
  .dependsOn( forkliftApi, forkliftServices, forkliftControllers )
  .configs( IntegrationConfig, WipConfig )
  .enablePlugins( JavaAppPackaging )
  .settings(
      commonSettings,
      libraryDependencies ++= database ++ scalatra,
      assemblySettings,
   )

lazy val forkliftClient = ( project in file( "forklift-client" ) )
  .dependsOn( forkliftApi )
  .disablePlugins( sbtassembly.AssemblyPlugin )
  .configs( IntegrationConfig, WipConfig )
  .settings(
      commonSettings,
      libraryDependencies ++= jackson,
   )

ThisBuild / useCoursier := false
