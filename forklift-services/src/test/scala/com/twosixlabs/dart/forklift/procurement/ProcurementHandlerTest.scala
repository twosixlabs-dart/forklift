package com.twosixlabs.dart.forklift.procurement

import better.files.File
import com.twosixlabs.dart.forklift.api.models.{DocumentMetadata, FileMetadata}
import com.twosixlabs.dart.forklift.exceptions.{UnableToSaveDocumentException, UnableToSaveMetadataException}
import com.twosixlabs.dart.forklift.helpers.Mapper
import com.twosixlabs.dart.test.base.ScalaTestBase
import org.scalamock.scalatest.MockFactory
import org.slf4j.{Logger, LoggerFactory}

import java.io.IOException
import scala.util.{Failure, Success}

class ProcurementHandlerTest extends ScalaTestBase with MockFactory {

    private val procurementRepositoryStub = stub[ ProcurementRepository ]
    private val documentId = "docId"
    private val specMetadata = DocumentMetadata( None,
                                                 None,
                                                 Some( "Think Tank" ),
                                                 Some( "Forklift UI" ),
                                                 Some( List( "spec corpus" ) ),
                                                 None )
    private val specFileName = "test.txt"
    private val specFileContent = File( s"forklift-controllers/src/test/resources/fixtures/${specFileName}" ).byteArray
    private val LOG : Logger = LoggerFactory.getLogger( getClass )

    "ProcurementHandler" should "save document and metadata file with correct name and extension and return success" in {



        val expectedFileMetadataContent = Mapper.marshal( FileMetadata( specMetadata, specFileName ) ).get

        ( procurementRepositoryStub.doSave _ ).when( f"${documentId}.txt.raw", * ).returns( Success( documentId ) )
        ( procurementRepositoryStub.doSave _ ).when( s"${documentId}.meta",
                                                     argThat[ Array[ Byte ] ] {
                                                         fileMetadataByteArray : Array[ Byte ] => fileMetadataByteArray.sameElements( expectedFileMetadataContent.getBytes )
                                                     } ).returns( Success( documentId ) )

        val procurementHandler = new ProcurementHandler( procurementRepositoryStub )

        procurementHandler.doSave( documentId, specFileName, specFileContent, Some( specMetadata ) ) match {
            case Success( value ) => value shouldBe "docId"
            case Failure( e ) => fail( e )
        }
    }

    "Procurement" should "save document and file metadata file when document metadata is not provided" in {
        val expectedFileMetadataContent = Mapper.marshal( FileMetadata( specFileName ) ).get
        LOG.info("expected {}", expectedFileMetadataContent)
        ( procurementRepositoryStub.doSave _ ).when( f"${documentId}.txt.raw", * ).returns( Success( documentId ) )
        ( procurementRepositoryStub.doSave _ ).when( s"${documentId}.meta",
                                                     argThat[ Array[ Byte ] ] {
                                                         fileMetadataByteArray : Array[ Byte ] => fileMetadataByteArray.sameElements( expectedFileMetadataContent.getBytes )
                                                     } ).returns( Success( documentId ) )

        val procurementHandler = new ProcurementHandler( procurementRepositoryStub )

        procurementHandler.doSave( documentId, specFileName, specFileContent, None ) match {
            case Success( value ) => value shouldBe "docId"
            case Failure( e ) => fail( e )
        }
    }

    "ProcurementHandler" should "return failure with UnableToSaveDocumentException when file is unable to be saved" in {

        ( procurementRepositoryStub.doSave _ ).when( f"${documentId}.txt.raw", * ).returns( Failure( new IOException ) )

        val procurementHandler = new ProcurementHandler( procurementRepositoryStub )

        procurementHandler.doSave( documentId, specFileName, specFileContent, Some( specMetadata ) ) match {
            case Success( value ) => fail( value )
            case Failure( e ) => e shouldBe a[ UnableToSaveDocumentException ]
        }
    }

    "ProcurementHandler" should "return failure with UnableToSaveMetadataException when metadata is unable to be saved" in {

        ( procurementRepositoryStub.doSave _ ).when( "docId.txt.raw", * ).returns( Success( "docId" ) )
        ( procurementRepositoryStub.doSave _ ).when( "docId.meta", * ).returns( Failure( new IOException ) )

        val procurementHandler = new ProcurementHandler( procurementRepositoryStub )

        procurementHandler.doSave( documentId, specFileName, specFileContent, Some( specMetadata ) ) match {
            case Success( value ) => fail( value )
            case Failure( e ) => e shouldBe a[ UnableToSaveMetadataException ]
        }
    }

}
