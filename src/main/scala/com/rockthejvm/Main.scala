package com.rockthejvm

import com.rockthejvm.controllers.{ContactsController, Redirects}
import com.rockthejvm.db.{Db, DbConfig, DbMigrator}
import com.rockthejvm.domain.config.Configuration
import com.rockthejvm.repositories.ContactsRepository
import com.rockthejvm.services.ContactService
import zio.*
import zio.http.*
import zio.logging.LogFormat
import zio.logging.backend.SLF4J

import scala.jdk.CollectionConverters._
import java.io.File
import java.net.{JarURLConnection, URL}
import java.util.jar.JarFile

object Main extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    SLF4J.slf4j(LogFormat.colored)

  override def run: ZIO[Any & ZIOAppArgs & Scope, Any, Any] = {

    val resources = listResources("/") // Adjust if your resources are in a different path
    println("Available resources:")
    resources.foreach(println)

    val basicRoutes = Routes(
      Method.GET / "" -> handler(Response.redirect(Redirects.contacts)),
      Method.GET / "static" / "css" / "main.css" -> Handler.fromResource("css/main.css").orDie
    )

    val htmxApp = ZIO.service[ContactsController].map { contacts =>
      basicRoutes.toHttpApp ++ contacts.routes.toHttpApp
    }
    
    val program = 
      for {
        migrator <- ZIO.service[DbMigrator]
        _ <- Console.printLine(s"Running db migrations")
        _ <- migrator.migrate()
        _ <- ZIO.logInfo("Successfully ran migrations")
        app <- htmxApp
        _ <- ZIO.logInfo("Starting server....")
        _ <- Server.serve(app @@ Middleware.debug @@ Middleware.flashScopeHandling)
        _ <- ZIO.logInfo("Server started - Rock the JVM!")
      } yield ()
      
    program.provide(
        Configuration.live,
        DbConfig.live,
        Db.dataSourceLive,
        Db.quillLive,
        DbMigrator.live,
        ContactsRepository.live,
        ContactsController.live,
        ContactService.live,
        Server.default
      )
      .exitCode
  }


  def listResources(path: String): Seq[String] = {
    val resources = getClass.getClassLoader.getResources(path).asScala
    resources.flatMap { url =>
      url.getProtocol match {
        case "file" => listFilesInDirectory(new File(url.toURI))
        case "jar"  => listFilesInJar(url, path)
        case _      => Seq.empty
      }
    }.toSeq
  }

  private def listFilesInDirectory(dir: File): Seq[String] = {
    if (dir.exists && dir.isDirectory) {
      dir.listFiles.flatMap {
        case file if file.isDirectory => listFilesInDirectory(file)
        case file                     => Seq(file.getPath)
      }
    } else Seq.empty
  }

  private def listFilesInJar(url: URL, path: String): Seq[String] = {
    val jarConn = url.openConnection().asInstanceOf[JarURLConnection]
    val jarFile = jarConn.getJarFile
    jarFile.entries().asScala
      .map(_.getName)
      .filter(_.startsWith(path))
      .toSeq
  }

}
