package com.twosixlabs.dart.forklift.controller

import annotations.WipTest
import better.files.File
import com.twosixlabs.dart.auth.groups.{ ProgramManager, TenantGroup }
import com.twosixlabs.dart.auth.tenant.indices.InMemoryCorpusTenantIndex
import com.twosixlabs.dart.auth.tenant.{ CorpusTenant, CorpusTenantIndex, GlobalCorpus, Member, ReadOnly }
import com.twosixlabs.dart.auth.user.DartUser
import com.twosixlabs.dart.forklift.api.models.DocumentMetadata
import com.twosixlabs.dart.forklift.exceptions.{ UnableToSaveDocumentException, UnableToSaveMetadataException }
import com.twosixlabs.dart.operations.status.PipelineStatus
import com.twosixlabs.dart.operations.status.PipelineStatus.Status
import com.twosixlabs.dart.operations.status.client.{ PipelineStatusUpdateClient, SqlPipelineStatusUpdateClient }
import com.twosixlabs.dart.utils.IdGenerator
import org.scalamock.matchers.ArgCapture.{ CaptureAll, CaptureOne }
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatra.test.scalatest.ScalatraSuite
import org.slf4j.{ Logger, LoggerFactory }

import javax.servlet.http.HttpServletRequest
import scala.util.{ Failure, Success }

class ForkliftControllerTest extends AnyFlatSpecLike with ScalatraSuite with MockFactory {

    private val fixturesDir = "forklift-controllers/src/test/resources/fixtures/"
    private val procurementHandler : ProcurementHandler = mock[ ProcurementHandler ]
    private val mockOpsUpdateClient : SqlPipelineStatusUpdateClient = mock[ SqlPipelineStatusUpdateClient ]
    private val corpusTenantOne : CorpusTenant = CorpusTenant("spec-tenant-1", GlobalCorpus)
    private val corpusTenantTwo : CorpusTenant = CorpusTenant("spec-tenant-2", GlobalCorpus)
    private val corpusTenantThree : CorpusTenant = CorpusTenant("spec-tenant-3", GlobalCorpus)

    private val controllerDependencies = new ForkliftControllerDependencies {
        override val docHandler : ProcurementHandler = procurementHandler
        override val opsUpdateClient : PipelineStatusUpdateClient = mockOpsUpdateClient
        override val tenantIndex : CorpusTenantIndex = new InMemoryCorpusTenantIndex
        override val secretKey : Option[ String ] = None
        override val useDartAuth : Boolean = true
        override val basicAuthCredentials: Seq[ (String, String) ] = Seq.empty
    }

    private val superUserforkliftController = new ForkliftController( controllerDependencies ) {
        override def authenticateUser( req : HttpServletRequest ) : DartUser = DartUser( "super-user", Set( ProgramManager  ) )
    }

    private val readOnlyUserForkliftController = new ForkliftController( controllerDependencies ) {
        override def authenticateUser( req : HttpServletRequest ) : DartUser = DartUser( "super-user", Set( TenantGroup( GlobalCorpus, ReadOnly ) ) )
    }

    private val globalWriteForkliftController = new ForkliftController( controllerDependencies ) {
        override def authenticateUser( req : HttpServletRequest ) : DartUser = DartUser( "super-user", Set( TenantGroup( GlobalCorpus, Member ) ) )
    }

    private val readWriteUserForkliftController = new ForkliftController( controllerDependencies ) {
        override def authenticateUser( req : HttpServletRequest ) : DartUser = DartUser( "test-user", Set( TenantGroup( corpusTenantOne, Member ),
                                                                                                           TenantGroup(corpusTenantTwo, ReadOnly),
                                                                                                           TenantGroup( corpusTenantThree, Member)) )
    }

    private val LOG : Logger = LoggerFactory.getLogger( getClass )

    addServlet( superUserforkliftController, "/superuser/forklift/upload/*" )
    addServlet( readOnlyUserForkliftController, "/readonly/forklift/upload/*" )
    addServlet( globalWriteForkliftController, "/global/forklift/upload/*")
    addServlet( readWriteUserForkliftController, "/readwrite/forklift/upload/*")

    "POST to /forklift/zip" should "unzip the file persist all the documents" in {
        val document_ids = Seq( "e7c8d6364b2da849722272dbabae7794",
                               "73dedb030b06e51ae7c4be99473ab810",
                               "d93641093067c4fa03f860e754f875b2",
                               "932e6ac6ffcf9fa2204f0fa36df3fe00",
                               "300a67792986744a2f04395dd0cf6f97",
                               "575138d7a7100853bf128fda3c54aeb3" )
        val mockedMetaData =
            """{
              |  "genre": "Think Tank",
              |  "ingestion_system": "Forklift UI",
              |  "labels": ["Think Tank", "Spec Corpus"]
              |}
              |""".stripMargin

        val rawDocMetaData = DocumentMetadata( None,
                                               None,
                                               Some( "Think Tank" ),
                                               Some( "Forklift UI" ),
                                               Some( List( "Think Tank", "Spec Corpus" ) ),
                                               None )
        val testFile : File = File( s"${fixturesDir}/documents-with-directories.zip" )
        val expectedResponse = """{"documents":[{"document_id":"d93641093067c4fa03f860e754f875b2","filename":"d93641093067c4fa03f860e754f875b2.json"},{"document_id":"73dedb030b06e51ae7c4be99473ab810","filename":"73dedb030b06e51ae7c4be99473ab810.json"},{"document_id":"e7c8d6364b2da849722272dbabae7794","filename":"e7c8d6364b2da849722272dbabae7794.json"},{"document_id":"575138d7a7100853bf128fda3c54aeb3","filename":"575138d7a7100853bf128fda3c54aeb3.json"},{"document_id":"932e6ac6ffcf9fa2204f0fa36df3fe00","filename":"932e6ac6ffcf9fa2204f0fa36df3fe00.json"},{"document_id":"300a67792986744a2f04395dd0cf6f97","filename":"300a67792986744a2f04395dd0cf6f97.json"}],"num_docs_failed":0}""".stripMargin

        ( procurementHandler.doSave( _ : String, _ : String, _ : Array[ Byte ], _ : Option[ DocumentMetadata ] ) ).expects( "e7c8d6364b2da849722272dbabae7794", *, *, Some( rawDocMetaData ) ).returns( Success( testFile.name ) ).once()
        ( procurementHandler.doSave( _ : String, _ : String, _ : Array[ Byte ], _ : Option[ DocumentMetadata ] ) ).expects( "73dedb030b06e51ae7c4be99473ab810", *, *, Some( rawDocMetaData ) ).returns( Success( testFile.name ) ).once()
        ( procurementHandler.doSave( _ : String, _ : String, _ : Array[ Byte ], _ : Option[ DocumentMetadata ] ) ).expects( "d93641093067c4fa03f860e754f875b2", *, *, Some( rawDocMetaData ) ).returns( Success( testFile.name ) ).once()
        ( procurementHandler.doSave( _ : String, _ : String, _ : Array[ Byte ], _ : Option[ DocumentMetadata ] ) ).expects( "932e6ac6ffcf9fa2204f0fa36df3fe00", *, *, Some( rawDocMetaData ) ).returns( Success( testFile.name ) ).once()
        ( procurementHandler.doSave( _ : String, _ : String, _ : Array[ Byte ], _ : Option[ DocumentMetadata ] ) ).expects( "300a67792986744a2f04395dd0cf6f97", *, *, Some( rawDocMetaData ) ).returns( Success( testFile.name ) ).once()
        ( procurementHandler.doSave( _ : String, _ : String, _ : Array[ Byte ], _ : Option[ DocumentMetadata ] ) ).expects( "575138d7a7100853bf128fda3c54aeb3", *, *, Some( rawDocMetaData ) ).returns( Success( testFile.name ) ).once()

        val opsUpdateCapture = CaptureAll[ PipelineStatus ]()
       mockOpsUpdateClient.fireAndForget _ expects capture( opsUpdateCapture ) repeat 6

        submitMultipart( method = "POST",
                         path = "/superuser/forklift/upload/zip",
                         params = Array( ("metadata", mockedMetaData) ),
                         files = Array( ("file", testFile.toJava) ) ) {
            response.headers should contain( "Content-Type" -> List( "application/json;charset=utf-8" ) )
            status shouldBe 201
            LOG.info( s"${body}" )
            body shouldBe expectedResponse
        }
        Thread.sleep( 1000 )
        opsUpdateCapture.values.length shouldBe document_ids.length
        opsUpdateCapture.values.map( opsUpdateStatus => {
            assert( document_ids.contains( opsUpdateStatus.getDocumentId ) )
            opsUpdateStatus.getStatus shouldBe Status.SUCCESS
        } )

    }

    "POST to /forklift/zip" should "return 400 if not a zip file" in {

        val mockedMetaData =
            """{
              |  "genre": "Think Tank",
              |  "ingestion_system": "Forklift UI",
              |  "labels": ["Think Tank", "Spec Corpus"]
              |}
              |""".stripMargin
        val expectedResponse = """{"status":400,"error_message":"Bad request: invalid request body: File extension is not .zip"}"""

        val testFile : File = File( s"${fixturesDir}/test.txt" )

        submitMultipart( method = "POST",
                         path = "/superuser/forklift/upload/zip",
                         params = Array( ("metadata", mockedMetaData) ),
                         files = Array( ("file", testFile.toJava) ) ) {
            response.headers should contain( "Content-Type" -> List( "application/json;charset=utf-8" ) )
            status shouldBe 400
            body shouldBe expectedResponse
        }
    }

    "POST to /forklift/zip" should "return 400 when unable to extract files from the zip file" in {

        val mockedMetaData =
            """{
              |  "genre": "Think Tank",
              |  "ingestion_system": "Forklift UI",
              |  "labels": ["Think Tank", "Spec Corpus"]
              |}
              |""".stripMargin
        val expectedResponse = """{"status":400,"error_message":"Bad request: invalid request body: Unable to extract documents from zip"}"""

        val testFile : File = File( s"${fixturesDir}/not-a-zip-file.zip" )

        submitMultipart( method = "POST",
                         path = "/superuser/forklift/upload/zip",
                         params = Array( ("metadata", mockedMetaData) ),
                         files = Array( ("file", testFile.toJava) ) ) {
            response.headers should contain( "Content-Type" -> List( "application/json;charset=utf-8" ) )
            LOG.info( "\n\n\n" + body + "\n\n\n" )
            status shouldBe 400
            body shouldBe expectedResponse
        }
    }

    "POST to /forklift/upload" should "return 201 save document and metadata and return correct response when posting valid file and metadata" in {

        val mockedMetaData =
            """{
              |  "genre": "Think Tank",
              |  "ingestion_system": "Forklift UI",
              |  "labels": ["Think Tank", "Spec Corpus"]
              |}
              |""".stripMargin

        val rawDocMetaData = DocumentMetadata( None,
                                               None,
                                               Some( "Think Tank" ),
                                               Some( "Forklift UI" ),
                                               Some( List( "Think Tank", "Spec Corpus" ) ),
                                               None )
        val testFile : File = File( s"${fixturesDir}/test.txt" )
        val docId = IdGenerator.getMd5Hash( testFile.byteArray )

        val expectedResponse = s"""{"document_id":"${docId}","filename":"test.txt"}"""

        val opsUpdateCapture = CaptureOne[ PipelineStatus ]()
       mockOpsUpdateClient.fireAndForget _ expects capture( opsUpdateCapture )

        ( procurementHandler.doSave( _ : String, _ : String, _ : Array[ Byte ], _ : Option[ DocumentMetadata ] ) ).expects( *, testFile.name, *, Some( rawDocMetaData ) ).returns( Success( testFile.name ) )

        submitMultipart( method = "POST",
                         path = "/superuser/forklift/upload",
                         params = Array( ("metadata", mockedMetaData) ),
                         files = Array( ("file", testFile.toJava) ) ) {
//            response.headers should contain( "Content-Type" -> List( "application/json;charset=utf-8" ) )
            status shouldBe 201
            body shouldBe expectedResponse
        }
        opsUpdateCapture.value.getDocumentId shouldBe docId
        opsUpdateCapture.value.getStatus shouldBe Status.SUCCESS
    }

    "POST to /forklift/upload" should "return 201 with correct response when posting metadata with unknown fields" in {

        val mockedMetadata =
            """{
              |  "relevance": "High",
              |  "genre": "Think Tank",
              |  "ingestion_system": "Forklift UI",
              |  "labels": ["Think Tank", "Spec Corpus"],
              |  "extra_field": "test"
              |}
              |""".stripMargin

        val testFile : File = File( s"${fixturesDir}/test.txt" )
        val docId = IdGenerator.getMd5Hash( testFile.byteArray )

        val documentMetadata = DocumentMetadata( None,
                                                 None,
                                                 Some( "Think Tank" ),
                                                 Some( "Forklift UI" ),
                                                 Some( List( "Think Tank", "Spec Corpus" ) ),
                                                 None )

        ( procurementHandler.doSave( _ : String, _ : String, _ : Array[ Byte ], _ : Option[ DocumentMetadata ] ) ).expects( *, testFile.name, *, Some( documentMetadata ) ).returns( Success( "test.txt" ) )
        val opsUpdateCapture = CaptureOne[ PipelineStatus ]()
       mockOpsUpdateClient.fireAndForget _ expects capture( opsUpdateCapture )

        submitMultipart( method = "POST",
                         path = "/superuser/forklift/upload",
                         params = Array( ("metadata", mockedMetadata) ),
                         files = Array( ("file", testFile.toJava) ) ) {
//            response.headers should contain( "Content-Type" -> List( "application/json;charset=utf-8" ) )
            status shouldBe 201
        }

        opsUpdateCapture.value.getDocumentId shouldBe docId
    }

    "POST to /forklift/upload" should "return 201 and save document with metadata that contains filename only when metadata is not provided" in {

        val testFile : File = File( s"${fixturesDir}/test.txt" )
        val docId = IdGenerator.getMd5Hash( testFile.byteArray )
        val expectedResponse = s"""{"document_id":"${docId}","filename":"test.txt"}"""

        ( procurementHandler.doSave( _ : String, _ : String, _ : Array[ Byte ], _ : Option[ DocumentMetadata ] ) ).expects( *, testFile.name, *, None ).returns( Success( "test.txt" ) )
        val opsUpdateCapture = CaptureOne[ PipelineStatus ]()
       mockOpsUpdateClient.fireAndForget _ expects capture( opsUpdateCapture )

        submitMultipart( method = "POST",
                         path = "/superuser/forklift/upload",
                         params = Array( ("tenant", "baltics") ),
                         files = Array( ("file", testFile.toJava) ) ) {
//            response.headers should contain( "Content-Type" -> List( "application/json;charset=utf-8" ) )
            status shouldBe 201
            body shouldBe expectedResponse
        }

        opsUpdateCapture.value.getDocumentId shouldBe docId
    }

    "POST to /forklift/upload" should "return 201 and save document with for when posting blank metadata" in {

        val mockedMetadata =
            """{}""".stripMargin
        val testFile : File = File( s"${fixturesDir}/test.txt" )
        val documentMetaData = DocumentMetadata( None, None, None, None, None, None )
        val docId = IdGenerator.getMd5Hash( testFile.byteArray )

        val expectedResponse = s"""{"document_id":"${docId}","filename":"test.txt"}"""

        ( procurementHandler.doSave( _ : String, _ : String, _ : Array[ Byte ], _ : Option[ DocumentMetadata ] ) ).expects( *, testFile.name, *, Some( documentMetaData ) ).returns( Success( "test.txt" ) )
        val opsUpdateCapture = CaptureOne[ PipelineStatus ]()
       mockOpsUpdateClient.fireAndForget _ expects capture( opsUpdateCapture )

        submitMultipart( method = "POST",
                         path = "/superuser/forklift/upload",
                         params = Array( ("metadata", mockedMetadata) ),
                         files = Array( ("file", testFile.toJava) ) ) {
//            response.headers should contain( "Content-Type" -> List( "application/json;charset=utf-8" ) )
            status shouldBe 201
            body shouldBe expectedResponse
        }

        opsUpdateCapture.value.getDocumentId shouldBe docId
    }

    "POST to /forklift/upload" should "return 201 when posting metadata with all unknown fields" in {

        val mockedMetaData =
            """{
              |"test":"spec"
              |}""".stripMargin

        val testFile : File = File( s"${fixturesDir}/test.txt" )
        val documentMetaData = DocumentMetadata( None, None, None, None, None, None )
        val docId = IdGenerator.getMd5Hash( testFile.byteArray )

        val expectedResponse = s"""{"document_id":"${docId}","filename":"test.txt"}"""

        ( procurementHandler.doSave( _ : String, _ : String, _ : Array[ Byte ], _ : Option[ DocumentMetadata ] ) ).expects( *, testFile.name, *, Some( documentMetaData ) ).returns( Success( "test.txt" ) )
        val opsUpdateCapture = CaptureOne[ PipelineStatus ]()
       mockOpsUpdateClient.fireAndForget _ expects capture( opsUpdateCapture )

        submitMultipart( method = "POST",
                         path = "/superuser/forklift/upload",
                         params = Array( ("metadata", mockedMetaData) ),
                         files = Array( ("file", testFile.toJava) ) ) {
//            response.headers should contain( "Content-Type" -> List( "application/json;charset=utf-8" ) )
            status shouldBe 201
            body shouldBe expectedResponse
        }
        opsUpdateCapture.value.getDocumentId shouldBe docId
    }

    "POST to /forklift/upload" should "return 400 when file is not supplied in the request" in {

        val mockedMetaData =
            """{
              |  "genre": "Think Tank",
              |  "ingestion_system": "Forklift UI",
              |  "labels": ["Think Tank", "Spec Corpus"]
              |}
              |""".stripMargin

        val expectedResponse = """{"status":400,"error_message":"Bad request: invalid request body: File was not provided"}"""

        submitMultipart( method = "POST",
                         path = "/superuser/forklift/upload",
                         params = Array( ("metadata", mockedMetaData) )
                         ) {
//            response.headers should contain( "Content-Type" -> List( "application/json;charset=utf-8" ) )
            status shouldBe 400
            body shouldBe expectedResponse
        }
    }

    "POST to /forklift/upload" should "return 400 when metadata is invalid JSON" in {

        val mockedMetaData =
            """{
              |  derp derp derp derp
              |  "ingestion_system": "Forklift UI",
              |  "labels": ["Think Tank", "Spec Corpus"]
              |}
              |""".stripMargin

        val expectedResponse = """{"status":400,"error_message":"Bad request: invalid request body: Metadata was not correctly provided"}"""

        submitMultipart( method = "POST",
                         path = "/superuser/forklift/upload",
                         params = Array( ("metadata", mockedMetaData) )
                         ) {
            //            response.headers should contain( "Content-Type" -> List( "application/json;charset=utf-8" ) )
            status shouldBe 400
            body shouldBe expectedResponse
        }
    }

    "POST to /forklift/upload" should "return 500 with correct message when unable to save the document" in {

        val mockedMetaData =
            """{
              |  "genre": "Think Tank",
              |  "ingestion_system": "Forklift UI",
              |  "labels": ["Think Tank", "Spec Corpus"]
              |}
              |""".stripMargin

        val expectedResponse = """{"status":500,"error_message":"Internal server error: Unable to save document"}"""

        val testFile : File = File( s"${fixturesDir}/test.txt" )
        val documentMetaData = DocumentMetadata( None,
                                                 None,
                                                 Some( "Think Tank" ),
                                                 Some( "Forklift UI" ),
                                                 Some( List( "Think Tank", "Spec Corpus" ) ),
                                                 None )
        val docId = IdGenerator.getMd5Hash( testFile.byteArray )

        ( procurementHandler.doSave( _ : String, _ : String, _ : Array[ Byte ], _ : Option[ DocumentMetadata ] ) ).expects( *, testFile.name, *, Some( documentMetaData ) ).returns( Failure( new UnableToSaveDocumentException( "Failed to save: docId.txt" ) ) )
        val opsUpdateCapture = CaptureOne[ PipelineStatus ]()
       mockOpsUpdateClient.fireAndForget _ expects capture( opsUpdateCapture )

        submitMultipart( method = "POST",
                         path = "/superuser/forklift/upload",
                         params = Array( ("metadata", mockedMetaData) ),
                         files = Array( ("file", testFile.toJava) )
                         ) {
//            response.headers should contain( "Content-Type" -> List( "application/json;charset=utf-8" ) )
            status shouldBe 500
            body shouldBe expectedResponse

        }
        opsUpdateCapture.value.getDocumentId shouldBe docId
        opsUpdateCapture.value.getStatus shouldBe Status.FAILURE
    }

    "POST to /forklift/upload" should "return 500 with correct message when unable to save metadata file" in {

        val mockedMetaData =
            """{
              |  "genre": "Think Tank",
              |  "ingestion_system": "Forklift UI",
              |  "labels": ["Think Tank", "Spec Corpus"]
              |}
              |""".stripMargin

        val expectedResponse = """{"status":500,"error_message":"Internal server error: Unable to save metadata"}"""

        val testFile : File = File( s"${fixturesDir}/test.txt" )
        val rawDocMetaData = DocumentMetadata( None,
                                               None,
                                               Some( "Think Tank" ),
                                               Some( "Forklift UI" ),
                                               Some( List( "Think Tank", "Spec Corpus" ) ),
                                               None )
        val docId = IdGenerator.getMd5Hash( testFile.byteArray )

        ( procurementHandler.doSave( _ : String, _ : String, _ : Array[ Byte ], _ : Option[ DocumentMetadata ] ) ).expects( *, testFile.name, *, Some( rawDocMetaData ) ).returns( Failure( new UnableToSaveMetadataException( "Failed to save: docId-meta.json" ) ) )
        val opsUpdateCapture = CaptureOne[ PipelineStatus ]()
       mockOpsUpdateClient.fireAndForget _ expects capture( opsUpdateCapture )

        submitMultipart( method = "POST",
                         path = "/superuser/forklift/upload",
                         params = Array( ("metadata", mockedMetaData) ),
                         files = Array( ("file", testFile.toJava) ) ) {
//            response.headers should contain( "Content-Type" -> List( "application/json;charset=utf-8" ) )
            status shouldBe 500
            body shouldBe expectedResponse

        }
        opsUpdateCapture.value.getDocumentId shouldBe docId
        opsUpdateCapture.value.getStatus shouldBe Status.FAILURE
    }

    "POST to /forklift/upload" should "return 403 when member is not authorized to upload a document to the tenant" in {

        val mockedMetaData =
            """{
              |  "genre": "Think Tank",
              |  "ingestion_system": "Forklift UI",
              |  "tenants": ["spec_1"],
              |  "labels": ["Think Tank", "Spec Corpus"]
              |}
              |""".stripMargin

        val testFile : File = File( s"${fixturesDir}/test.txt" )

        submitMultipart( method = "POST",
                         path = "/readonly/forklift/upload",
                         params = Array( ("metadata", mockedMetaData) ),
                         files = Array( ("file", testFile.toJava) ) ) {
//            response.headers should contain( "Content-Type" -> List( "application/json;charset=utf-8" ) )
            println(s"body+=== ${body}")
            status shouldBe 403
            body should include( "Operation not authorized" )
        }
    }

    "POST to /forklift/upload" should "return 403 when member is not authorized to upload a zip archive to the tenant" in {

        val mockedMetaData =
            """{
              |  "genre": "Think Tank",
              |  "ingestion_system": "Forklift UI",
              |  "tenants": ["spec_1"],
              |  "labels": ["Think Tank", "Spec Corpus"]
              |}
              |""".stripMargin

        val testFile : File = File( s"${fixturesDir}/documents-with-directories.zip" )

        submitMultipart( method = "POST",
            path = "/readonly/forklift/upload/zip",
            params = Array( ("metadata", mockedMetaData) ),
            files = Array( ("file", testFile.toJava) ) ) {
            //            response.headers should contain( "Content-Type" -> List( "application/json;charset=utf-8" ) )
            println(s"body+=== ${body}")
            status shouldBe 403
            body should include( "Operation not authorized" )
        }
    }

    "POST to /forklift/upload" should "return 403 when tenants include global and member is not authorized to upload a document to the global corpus" in {

        val mockedMetaData =
            """{
              |  "genre": "Think Tank",
              |  "ingestion_system": "Forklift UI",
              |  "tenants": ["global"],
              |  "labels": ["Think Tank", "Spec Corpus"]
              |}
              |""".stripMargin

        val testFile : File = File( s"${fixturesDir}/test.txt" )

        submitMultipart( method = "POST",
            path = "/readwrite/forklift/upload",
            params = Array( ("metadata", mockedMetaData) ),
            files = Array( ("file", testFile.toJava) ) ) {
            //            response.headers should contain( "Content-Type" -> List( "application/json;charset=utf-8" ) )
            println(s"body+=== ${body}")
            status shouldBe 403
            body should include( "Operation not authorized" )
        }
    }

    "POST to /forklift/upload" should "return 201 when tenants include global and member is authorized to upload a document to the global corpus" in {

        val mockedMetaData =
            """{
              |  "genre": "Think Tank",
              |  "ingestion_system": "Forklift UI",
              |  "tenants": ["global"],
              |  "labels": ["Think Tank", "Spec Corpus"]
              |}
              |""".stripMargin

        val documentMetadata = DocumentMetadata( Some( Set( "global" ) ),
                                                 None,
                                                 Some( "Think Tank" ),
                                                 Some( "Forklift UI" ),
                                                 Some( List( "Think Tank", "Spec Corpus" ) ),
                                                 None )

        val testFile : File = File( s"${fixturesDir}/test.txt" )
        ( procurementHandler.doSave( _ : String, _ : String, _ : Array[ Byte ], _ : Option[ DocumentMetadata ] ) ).expects( *, testFile.name, *, Some( documentMetadata ) ).returns( Success( "test.txt" ) )
        val opsUpdateCapture = CaptureOne[ PipelineStatus ]()
        mockOpsUpdateClient.fireAndForget _ expects capture( opsUpdateCapture )


        submitMultipart( method = "POST",
            path = "/global/forklift/upload",
            params = Array( ("metadata", mockedMetaData) ),
            files = Array( ("file", testFile.toJava) ) ) {
            //            response.headers should contain( "Content-Type" -> List( "application/json;charset=utf-8" ) )
            println(s"body+=== ${body}")
            status shouldBe 201
        }
    }

    "POST to /forklift/upload" should "return 201 save document and metadata and return correct response when tenant operation is allowed and posting valid file and metadata" in {

        val mockedMetaData =
            s"""{
              |  "genre": "Think Tank",
              |  "ingestion_system": "Forklift UI",
              |  "tenants": ["${corpusTenantOne.id}"],
              |  "labels": ["Think Tank", "Spec Corpus"]
              |}
              |""".stripMargin

        val rawDocMetaData = DocumentMetadata( Some(Set(corpusTenantOne.id)),
                                               None,
                                               Some( "Think Tank" ),
                                               Some( "Forklift UI" ),
                                               Some( List( "Think Tank", "Spec Corpus" ) ),
                                               None )
        val testFile : File = File( s"${fixturesDir}/test.txt" )
        val docId = IdGenerator.getMd5Hash( testFile.byteArray )

        val expectedResponse = s"""{"document_id":"${docId}","filename":"test.txt"}"""

        val opsUpdateCapture = CaptureOne[ PipelineStatus ]()
        mockOpsUpdateClient.fireAndForget _ expects capture( opsUpdateCapture )

        ( procurementHandler.doSave( _ : String, _ : String, _ : Array[ Byte ], _ : Option[ DocumentMetadata ] ) ).expects( *, testFile.name, *, Some( rawDocMetaData ) ).returns( Success( testFile.name ) )

        submitMultipart( method = "POST",
            path = "/readwrite/forklift/upload",
            params = Array( ("metadata", mockedMetaData) ),
            files = Array( ("file", testFile.toJava) ) ) {
            //            response.headers should contain( "Content-Type" -> List( "application/json;charset=utf-8" ) )
            status shouldBe 201
            body shouldBe expectedResponse
        }
        opsUpdateCapture.value.getDocumentId shouldBe docId
        opsUpdateCapture.value.getStatus shouldBe Status.SUCCESS
    }

  "POST to /forklift/upload" should "return 201 save document and metadata and return correct response when tenant operation is allowed for multiple tenants" in {

    val mockedMetaData =
      s"""{
         |  "genre": "Think Tank",
         |  "ingestion_system": "Forklift UI",
         |  "tenants": ["${corpusTenantOne.id}","${corpusTenantThree.id}"],
         |  "labels": ["Think Tank", "Spec Corpus"]
         |}
         |""".stripMargin

    val rawDocMetaData = DocumentMetadata( Some(Set(corpusTenantOne.id,corpusTenantThree.id)),
                                           None,
                                           Some( "Think Tank" ),
                                           Some( "Forklift UI" ),
                                           Some( List( "Think Tank", "Spec Corpus" ) ),
                                           None )
    val testFile : File = File( s"${fixturesDir}/test.txt" )
    val docId = IdGenerator.getMd5Hash( testFile.byteArray )

    val expectedResponse = s"""{"document_id":"${docId}","filename":"test.txt"}"""

    val opsUpdateCapture = CaptureOne[ PipelineStatus ]()
    mockOpsUpdateClient.fireAndForget _ expects capture( opsUpdateCapture )

    ( procurementHandler.doSave( _ : String, _ : String, _ : Array[ Byte ], _ : Option[ DocumentMetadata ] ) ).expects( *, testFile.name, *, Some( rawDocMetaData ) ).returns( Success( testFile.name ) )

    submitMultipart( method = "POST",
      path = "/readwrite/forklift/upload",
      params = Array( ("metadata", mockedMetaData) ),
      files = Array( ("file", testFile.toJava) ) ) {
      //            response.headers should contain( "Content-Type" -> List( "application/json;charset=utf-8" ) )
      status shouldBe 201
      body shouldBe expectedResponse
    }
    opsUpdateCapture.value.getDocumentId shouldBe docId
    opsUpdateCapture.value.getStatus shouldBe Status.SUCCESS
  }

  "POST to /forklift/upload" should "return 403 when posting a file to multiple tenants where users is not authorized to write data to some tenants" in {

    val mockedMetaData =
      s"""{
         |  "genre": "Think Tank",
         |  "ingestion_system": "Forklift UI",
         |  "tenants": ["${corpusTenantOne.id}", "${corpusTenantTwo.id}"],
         |  "labels": ["Think Tank", "Spec Corpus"]
         |}
         |""".stripMargin

    val testFile : File = File( s"${fixturesDir}/test.txt" )
    val docId = IdGenerator.getMd5Hash( testFile.byteArray )

    submitMultipart( method = "POST",
      path = "/readwrite/forklift/upload",
      params = Array( ("metadata", mockedMetaData) ),
      files = Array( ("file", testFile.toJava) ) ) {
      //            response.headers should contain( "Content-Type" -> List( "application/json;charset=utf-8" ) )
      status shouldBe 403
      body should include( "Operation not authorized" )
    }
  }
//    "POST to /forklift/upload" should "return 201 when posting metadata with only reannotate field" in {
//
//        val mockedMetaData =
//            """{
//              |"reannotate": "true"
//              |}""".stripMargin
//
//        val testFile : File = File( s"${fixturesDir}/test.txt" )
//        val documentMetaData = DocumentMetadata( None, None, None, None, None, Some( true ))
//        val docId = IdGenerator.getMd5Hash( testFile.byteArray )
//
//        val expectedResponse = s"""{"document_id":"${docId}","filename":"test.txt","reannotate":"true"}"""
//
//        ( procurementHandler.doSave( _ : String, _ : String, _ : Array[ Byte ], _ : Option[ DocumentMetadata ] ) ).expects( *, testFile.name, *, Some( documentMetaData ) ).returns( Success( "test.txt" ) )
//        val opsUpdateCapture = CaptureOne[ PipelineStatus ]()
//        mockOpsUpdateClient.fireAndForget _ expects capture( opsUpdateCapture )
//
//        submitMultipart( method = "POST",
//                         path = "/superuser/forklift/upload",
//                         params = Array( ("metadata", mockedMetaData) ),
//                         files = Array( ("file", testFile.toJava) ) ) {
//            //            response.headers should contain( "Content-Type" -> List( "application/json;charset=utf-8" ) )
//            status shouldBe 201
//            body shouldBe expectedResponse
//        }
//        opsUpdateCapture.value.getDocumentId shouldBe docId
//    }
    "POST to /forklift/zip" should "unzip the file persist all the documents to multiple tenants when tenant operation is allowed" in {
        val document_ids = Seq( "e7c8d6364b2da849722272dbabae7794",
            "73dedb030b06e51ae7c4be99473ab810",
            "d93641093067c4fa03f860e754f875b2",
            "932e6ac6ffcf9fa2204f0fa36df3fe00",
            "300a67792986744a2f04395dd0cf6f97",
            "575138d7a7100853bf128fda3c54aeb3" )
        val mockedMetaData =
            s"""{
              |  "genre": "Think Tank",
              |  "ingestion_system": "Forklift UI",
              |  "tenants": ["${corpusTenantOne.id}","${corpusTenantThree.id}"],
              |  "labels": ["Think Tank", "Spec Corpus"]
              |}
              |""".stripMargin

        val rawDocMetaData = DocumentMetadata( Some(Set(corpusTenantOne.id, corpusTenantThree.id)),
                                               None,
                                               Some( "Think Tank" ),
                                               Some( "Forklift UI" ),
                                               Some( List( "Think Tank", "Spec Corpus" ) ),
                                               None )
        val testFile : File = File( s"${fixturesDir}/documents-with-directories.zip" )
        val expectedResponse = """{"documents":[{"document_id":"d93641093067c4fa03f860e754f875b2","filename":"d93641093067c4fa03f860e754f875b2.json"},{"document_id":"73dedb030b06e51ae7c4be99473ab810","filename":"73dedb030b06e51ae7c4be99473ab810.json"},{"document_id":"e7c8d6364b2da849722272dbabae7794","filename":"e7c8d6364b2da849722272dbabae7794.json"},{"document_id":"575138d7a7100853bf128fda3c54aeb3","filename":"575138d7a7100853bf128fda3c54aeb3.json"},{"document_id":"932e6ac6ffcf9fa2204f0fa36df3fe00","filename":"932e6ac6ffcf9fa2204f0fa36df3fe00.json"},{"document_id":"300a67792986744a2f04395dd0cf6f97","filename":"300a67792986744a2f04395dd0cf6f97.json"}],"num_docs_failed":0}""".stripMargin

        ( procurementHandler.doSave( _ : String, _ : String, _ : Array[ Byte ], _ : Option[ DocumentMetadata ] ) ).expects( "e7c8d6364b2da849722272dbabae7794", *, *, Some( rawDocMetaData ) ).returns( Success( testFile.name ) ).once()
        ( procurementHandler.doSave( _ : String, _ : String, _ : Array[ Byte ], _ : Option[ DocumentMetadata ] ) ).expects( "73dedb030b06e51ae7c4be99473ab810", *, *, Some( rawDocMetaData ) ).returns( Success( testFile.name ) ).once()
        ( procurementHandler.doSave( _ : String, _ : String, _ : Array[ Byte ], _ : Option[ DocumentMetadata ] ) ).expects( "d93641093067c4fa03f860e754f875b2", *, *, Some( rawDocMetaData ) ).returns( Success( testFile.name ) ).once()
        ( procurementHandler.doSave( _ : String, _ : String, _ : Array[ Byte ], _ : Option[ DocumentMetadata ] ) ).expects( "932e6ac6ffcf9fa2204f0fa36df3fe00", *, *, Some( rawDocMetaData ) ).returns( Success( testFile.name ) ).once()
        ( procurementHandler.doSave( _ : String, _ : String, _ : Array[ Byte ], _ : Option[ DocumentMetadata ] ) ).expects( "300a67792986744a2f04395dd0cf6f97", *, *, Some( rawDocMetaData ) ).returns( Success( testFile.name ) ).once()
        ( procurementHandler.doSave( _ : String, _ : String, _ : Array[ Byte ], _ : Option[ DocumentMetadata ] ) ).expects( "575138d7a7100853bf128fda3c54aeb3", *, *, Some( rawDocMetaData ) ).returns( Success( testFile.name ) ).once()

        val opsUpdateCapture = CaptureAll[ PipelineStatus ]()
        mockOpsUpdateClient.fireAndForget _ expects capture( opsUpdateCapture ) repeat 6

        submitMultipart( method = "POST",
            path = "/readwrite/forklift/upload/zip",
            params = Array( ("metadata", mockedMetaData) ),
            files = Array( ("file", testFile.toJava) ) ) {
            response.headers should contain( "Content-Type" -> List( "application/json;charset=utf-8" ) )
            status shouldBe 201
            LOG.info( s"${body}" )
            body shouldBe expectedResponse
        }
        Thread.sleep( 1000 )
        opsUpdateCapture.values.length shouldBe document_ids.length
        opsUpdateCapture.values.map( opsUpdateStatus => {
            assert( document_ids.contains( opsUpdateStatus.getDocumentId ) )
            opsUpdateStatus.getStatus shouldBe Status.SUCCESS
        } )

    }

    override def header = null
}
