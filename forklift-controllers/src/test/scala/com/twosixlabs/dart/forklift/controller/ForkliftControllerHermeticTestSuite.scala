package com.twosixlabs.dart.forklift.controller

import better.files.File
import com.twosixlabs.dart.auth.groups.ProgramManager
import com.twosixlabs.dart.auth.tenant.CorpusTenantIndex
import com.twosixlabs.dart.auth.tenant.indices.InMemoryCorpusTenantIndex
import com.twosixlabs.dart.auth.user.DartUser
import com.twosixlabs.dart.forklift.procurement.repositories.LocalProcurementRepository
import com.twosixlabs.dart.operations.status.client.{PipelineStatusUpdateClient, SqlPipelineStatusUpdateClient}
import com.twosixlabs.dart.rest.ApiStandards
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatra.test.scalatest.ScalatraSuite

import javax.servlet.http.HttpServletRequest

class ForkliftControllerHermeticTestSuite extends AnyFlatSpec with ScalatraSuite with MockFactory with BeforeAndAfterEach {

    private val tmpDir = System.getProperty( "java.io.tmpdir" )
    private val fixturesDir = "forklift-controllers/src/test/resources/fixtures/"
    private val procurementHandler = new ProcurementHandler( new LocalProcurementRepository( tmpDir ) )
    private val mockOpsUpdateClient = mock[ SqlPipelineStatusUpdateClient ]
    private val documents = Seq( "1e7416e6abcd8dcae23a57cddf2ee6c8.factiva",
                                 "3cd880b2bc7baa32827fb5917bf6d063.factiva",
                                 "3c1d32e0315f794084c1bc223b9a5e89.factiva",
                                 "5d248b4e63f81c992570209854e921fe.factiva",
                                 "4eec49617266c68388e808b4de234042.factiva",
                                 "01f06ac0087087b0fefbdca064cfeb85.factiva" )

    private val controllerDependencies = new ForkliftControllerDependencies {
        override val docHandler : ProcurementHandler = procurementHandler
        override val opsUpdateClient : PipelineStatusUpdateClient = mockOpsUpdateClient
        override val tenantIndex : CorpusTenantIndex = new InMemoryCorpusTenantIndex
        override val secretKey : Option[ String ] = None
        override val useDartAuth : Boolean = true
        override val basicAuthCredentials: Seq[ (String, String) ] = Seq.empty
    }

    private val forkliftController = new ForkliftController( controllerDependencies ) {
        override def authenticateUser( req : HttpServletRequest ) : DartUser = DartUser( "super-user", Set( ProgramManager ) )
    }

    addServlet( forkliftController, ApiStandards.DART_API_PREFIX_V1 + "/forklift/upload/*" )

    override def afterEach( ) : Unit = {
        documents.foreach( document => File( tmpDir + "/" + s"${document}.raw" ).delete() )
        documents.foreach( document => File( tmpDir + "/" + s"${document.split( ".factiva" )( 0 )}.meta" ).delete() )
    }

    "POST to /forklift/zip" should "unzip the file and persist all documents" in {
        val mockedMetaData =
            """{
              |  "genre": "Think Tank",
              |  "ingestion_system": "Forklift UI",
              |  "labels": ["Think Tank", "Spec Corpus"]
              |}
              |""".stripMargin

        val testFile : File = File( s"${fixturesDir}documents.zip" )


        submitMultipart( method = "POST",
                         path = ApiStandards.DART_API_PREFIX_V1 + "/forklift/upload/zip",
                         params = Array( ("metadata", mockedMetaData), ("tenant", "baltics") ),
                         files = Array( ("file", testFile.toJava) ) ) {

            status shouldBe 201
        }
        documents.foreach( document => File( tmpDir + "/" + s"${document}.raw" ).contentAsString
                                         .equals( File( fixturesDir + s"documents/${document}" ).contentAsString ) shouldBe true )

    }

    "POST to /forklift/zip" should "unzip all files from zip files with directories and persist all documents" in {
        val mockedMetaData =
            """{
              |  "genre": "Think Tank",
              |  "ingestion_system": "Forklift UI",
              |  "labels": ["Think Tank", "Spec Corpus"]
              |}
              |""".stripMargin

        val testFile : File = File( s"${fixturesDir}documents-with-directories.zip" )


        submitMultipart( method = "POST",
                         path = ApiStandards.DART_API_PREFIX_V1 + "/forklift/upload/zip",
                         params = Array( ("metadata", mockedMetaData), ("tenant", "baltics") ),
                         files = Array( ("file", testFile.toJava) ) ) {

            status shouldBe 201
        }
        documents.foreach( document => File( tmpDir + "/" + s"${document}.raw" ).contentAsString
                                         .equals( File( fixturesDir + s"documents/${document}" ).contentAsString ) shouldBe true )

    }

    override def header = null
}
