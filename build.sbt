import java.time.{LocalDateTime, ZoneOffset}
import java.time.format.DateTimeFormatter

import com.typesafe.sbt.packager.linux.LinuxPackageMapping
import sbt.Keys.{libraryDependencies, organization}
import sbt.classpath.ClasspathUtilities
import sbtassembly.AssemblyKeys.assemblyMergeStrategy
import sbtassembly.MergeStrategy


val major = "0"
val minor = "0"
val patch = "0"
val preReleaseWithPrefix = ""

val jarVersion = "0.1"

val akka = "2.5.18"
val akka_http = "10.1.5"
val scala_test = "3.0.3"
val jackson = "2.8.8"
val dispatchVersion = "0.13.1"


lazy val allConfFiles = Set("application.conf")

lazy val commonSettings = Seq(
  version := jarVersion,
  organization := "com.evernym",
  scalaVersion := "2.12.2",
  scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8", "-Xmax-classfile-name", "128"),
  resolvers += "Lib-indy" at "https://repo.evernym.com/artifactory/libindy-maven-local",
  resolvers += "Velvia maven" at "http://dl.bintray.com/velvia/maven",
  resolvers += Resolver.bintrayRepo("bfil", "maven"),
  parallelExecution in Test := false,
  parallelExecution in Global := false,
  assemblyMergeStrategy in assembly := {
    case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.concat
    case s if allConfFiles.contains(s) => MergeStrategy.discard
    case s => MergeStrategy.defaultMergeStrategy(s)
  },
  PB.targets in Compile := Seq(
    scalapb.gen(flatPackage = true) -> (sourceManaged in Compile).value
  )
)

def commonTestSettings(projectName: String) = Seq (
  testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-h", s"target/test-reports/$projectName")
)

val akkaGrp = "com.typesafe.akka"

lazy val commonLibraryDependencies = {

  val coreDeps = Seq.apply(
    "com.evernym" %% "agent-common" % "0.0.0" classifier "assembly"
  )

  //test dependencies
  val testDeps = Seq(
    //persistence for tests
    "org.scalatest" % "scalatest_2.12" % scala_test,
    "org.pegdown" % "pegdown" % "1.6.0",
    "org.mockito" % "mockito-all" % "1.9.5",
    akkaGrp %% "akka-testkit" % akka
  ).map(_ % "test")

  coreDeps ++ testDeps

}

lazy val agentLibraryDependencies = {

  //akka related
  val coreDeps = Seq.apply(
    akkaGrp %% "akka-cluster-sharding" % akka,

    //persistence dependencies
    "org.iq80.leveldb" % "leveldb" % "0.10",

    //TODO: why this dependency was not inherited via agent-common dependency
    "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"
  )

  //test dependencies
  val testDeps = Seq(
    //persistence for tests
    "org.iq80.leveldb" % "leveldb" % "0.7", //to be used in E2E tests
    akkaGrp %% "akka-testkit" % akka
  ).map(_ % "test")

  commonLibraryDependencies ++ coreDeps ++ testDeps

}

lazy val transportLibraryDependencies = {

  //akka related
  val coreDeps = Seq.apply(
    akkaGrp %% "akka-actor" % akka,
    akkaGrp %% "akka-stream" % akka,
    akkaGrp %% "akka-http" % akka_http
  )

  //test dependencies
  val testDeps = Seq(
    //persistence for tests
    akkaGrp %% "akka-http-testkit" % akka_http
  ).map(_ % "test")

  commonLibraryDependencies ++ coreDeps ++ testDeps

}

//mapping of "jarname to be searched" with "target jar file name"
lazy val nonAssemblyJarsToBePutIntoPackage: Map[String, String] = Map.empty

def addDeps(deps:Seq[ModuleID], modifyDepTagForDeps:Seq[String], tags:String )={
  deps.map { dep =>
    if(modifyDepTagForDeps contains dep.name){
      dep.organization % dep.name % dep.revision % tags
    } else {
      dep
    }
  }
}

def getBuildMetadataWithPrefix(commitDate:String, commitHash:String) : String = {
  val secondsFromEpoch = (LocalDateTime.parse(commitDate,
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ"))
    .atZone(ZoneOffset.UTC).toInstant.toEpochMilli / 1000) - 1500000000
  val trimmedHash = commitHash.substring(0, 7)
  s"+$secondsFromEpoch.$trimmedHash"
}

def getVersion(commitDate:String, commitHash:String) : String = {
  val buildMetadataWithPrefix = getBuildMetadataWithPrefix(commitDate, commitHash)
  //s"$major.$minor.$patch$preReleaseWithPrefix$buildMetadataWithPrefix"
  s"$major.$minor.$patch"
}

def buildPackageMappings(
                          sourceDir: String, targetDir: String,
                          includeFiles: Set[String] = Set.empty,
                          excludeFiles: Set[String] = Set.empty,
                          replaceFilesIfExists: Boolean = false): LinuxPackageMapping = {
  val d = new File(sourceDir)
  val fms = if (d.exists) {
    val files = d.listFiles.filter { f =>
      f.isFile &&
        (includeFiles.isEmpty || includeFiles.contains(f.name)) &&
        (excludeFiles.isEmpty || ! excludeFiles.contains(f.name))
    }.toSeq

    files.map { f =>
      (file(s"$sourceDir/${f.name}"), s"$targetDir/${f.name}")
    }
  } else Seq.empty
  val r = packageMapping(fms: _*)
  if (replaceFilesIfExists) r else r.withConfig("noreplace")
}

lazy val dprDirPath = "deb-package-resources"
lazy val targetDirPathPrefix = "/usr/share/agent"
lazy val publishDeb = taskKey[Unit]("Publish deb package")

def getNonAssemblyJarFileMapping(pName: String,  dependencies: Keys.Classpath):Map[File, String] = {
  val depLibs = dependencies.map(_.data).filter(ClasspathUtilities.isArchive)
  val nonAssemblyJarsToBePutIntoDebPckg = nonAssemblyJarsToBePutIntoPackage.map { djn =>
    depLibs.find(_.getName.contains(djn._1)).get -> djn._2
  }
  nonAssemblyJarsToBePutIntoDebPckg.map { djn =>
    (djn._1, s"/usr/lib/$pName/${djn._2}")
  }
}

def commonPackageSettings(targetRootPath: String) = Seq (
  maintainer := "Evernym Inc <dev@evernym.com>",
  packageName := name.value,
  version := getVersion(git.gitHeadCommitDate.value.get, git.gitHeadCommit.value.get),
  linuxPackageMappings += {
    val pName = name.value
    val basePackageMapping = Seq(
      (assembly.value, s"/usr/lib/$pName/$pName-assembly.jar"),
      (baseDirectory.value / "src" / "main" / "resources" / "systemd" / "systemd.service",
        s"/usr/lib/systemd/system/${packageName.value}.service")
    )
    val dependencies = (externalDependencyClasspath in assembly).value
    val extraJarDepMapping = getNonAssemblyJarFileMapping(pName, dependencies)
    packageMapping(basePackageMapping ++ extraJarDepMapping: _*)
  },
  linuxPackageMappings += {
    buildPackageMappings(s"src/main/resources/$dprDirPath",
      s"$targetRootPath/${packageName.value}",
      includeFiles = allConfFiles, replaceFilesIfExists = true)
  },
  publishDeb := {
    val artifact = target.value + "/" + name.value + "_" + version.value + "_all.deb"
    val code = ("sh upload_deb.sh " + artifact !)
    code match {
      case 0 => println("Successful Upload!!")
      case n => sys.error(s"Error in Upload Script, exit code: $n")
    }
  }
)

lazy val common = (project in file("common")).
  enablePlugins(DebianPlugin).
  settings(
    name := "agent-ext-agency-common",
    libraryDependencies ++= commonLibraryDependencies,
    commonTestSettings("agent-ext-agency-common"),
    commonSettings,
    commonPackageSettings(s"$targetDirPathPrefix"),
    //libindy provides libindy.so
    debianPackageDependencies in Debian ++= Seq("default-jre", "libindy(>= 1.6.8)")
  )

lazy val businessProtocol = (project in file("business-protocol")).
  enablePlugins(DebianPlugin).
  settings(
    name := "extension-agency-business-protocol",
    packageSummary := "extension-agency-business-protocol",
    packageDescription := "Scala and Akka package to run agency agent",
    libraryDependencies ++= agentLibraryDependencies,
    commonTestSettings("extension-agency-business-protocol"),
    commonSettings,
    commonPackageSettings(s"$targetDirPathPrefix"),
    //libindy provides libindy.so
    debianPackageDependencies in Debian ++= Seq("default-jre", "libindy(>= 1.6.8)")
  ).dependsOn(common % "test -> test; compile -> compile")


lazy val transportProtocol = (project in file("transport-protocol")).
  enablePlugins(DebianPlugin).
  settings(
    name := "extension-agency-transport-protocol",
    packageSummary := "extension-agency-transport-protocol",
    packageDescription := "Scala and Akka package to run agency transport",
    libraryDependencies ++= transportLibraryDependencies,
    commonTestSettings("extension-agency-transport-protocol"),
    commonSettings,
    commonPackageSettings(s"$targetDirPathPrefix"),
    //libindy provides libindy.so
    debianPackageDependencies in Debian ++= Seq("default-jre")
  ).dependsOn(common % "test -> test; compile -> compile")

Revolver.settings

