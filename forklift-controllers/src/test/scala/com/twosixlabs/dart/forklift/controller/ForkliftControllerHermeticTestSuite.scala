package com.twosixlabs.dart.forklift.controller

import better.files.File
import com.twosixlabs.dart.auth.groups.ProgramManager
import com.twosixlabs.dart.auth.tenant.CorpusTenantIndex
import com.twosixlabs.dart.auth.tenant.indices.InMemoryCorpusTenantIndex
import com.twosixlabs.dart.auth.user.DartUser
import com.twosixlabs.dart.forklift.procurement.repositories.LocalProcurementRepository
import com.twosixlabs.dart.operations.status.client.{ PipelineStatusUpdateClient, SqlPipelineStatusUpdateClient }
import com.twosixlabs.dart.rest.ApiStandards
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatra.test.scalatest.ScalatraSuite

import javax.servlet.http.HttpServletRequest
import scala.util.Try

class ForkliftControllerHermeticTestSuite extends AnyFlatSpec with ScalatraSuite with MockFactory with BeforeAndAfterEach {

    private val tmpDir = System.getProperty( "java.io.tmpdir" )
    private val fixturesDir = "forklift-controllers/src/test/resources/fixtures/"
    private val procurementHandler = new ProcurementHandler( new LocalProcurementRepository( tmpDir ) )
    private val mockOpsUpdateClient = mock[ SqlPipelineStatusUpdateClient ]
    private val documents = Seq( "e7c8d6364b2da849722272dbabae7794.json",
                                 "73dedb030b06e51ae7c4be99473ab810.json",
                                 "d93641093067c4fa03f860e754f875b2.json",
                                 "932e6ac6ffcf9fa2204f0fa36df3fe00.json",
                                 "300a67792986744a2f04395dd0cf6f97.json",
                                 "575138d7a7100853bf128fda3c54aeb3.json" )

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

    def deleteAll( ) : Unit = {
        documents.foreach( document => Try( File( tmpDir + "/" + s"${document}.raw" ).delete() ) )
        documents.foreach( document => Try( File( tmpDir + "/" + s"${document.split( ".json" )( 0 )}.meta" ).delete() ) )
    }

    override def beforeEach( ) : Unit = {
        deleteAll()
    }

    override def afterEach( ) : Unit = {
        deleteAll( )
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
        Thread.sleep( 1000 )
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
        Thread.sleep( 1000 )
        documents.foreach( document => File( tmpDir + "/" + s"${document}.raw" ).contentAsString
                                         .equals( File( fixturesDir + s"documents/${document}" ).contentAsString ) shouldBe true )

    }

    override def header = null
}
