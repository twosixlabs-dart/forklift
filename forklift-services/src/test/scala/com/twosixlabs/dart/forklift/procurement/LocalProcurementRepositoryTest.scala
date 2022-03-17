package com.twosixlabs.dart.forklift.procurement

import better.files.{File, Resource}
import com.twosixlabs.dart.forklift.exceptions.UnableToSaveDocumentException
import com.twosixlabs.dart.forklift.procurement.repositories.LocalProcurementRepository
import com.twosixlabs.dart.test.base.StandardTestBase3x
import org.scalamock.scalatest.MockFactory

import java.io.InputStream
import scala.util.{Failure, Success}

class LocalProcurementRepositoryTest extends StandardTestBase3x with MockFactory {

//    private val fixturesDir = "forklift-controllers/src/test/resources/fixtures/"

    "LocalProcurementRepositoryTest" should "save the file and return failure" in {

        val tempDirectory = System.getProperty( "java.io.tmpdir" )
        val specFileName = "test.txt"
        val specFileContent : Array[ Byte ] = Resource.getAsString( "fixtures/test.txt" ).getBytes

        val localProcurementRepository = new LocalProcurementRepository( tempDirectory )

        localProcurementRepository.doSave( specFileName, specFileContent ) match {
            case Success( value ) =>
                value shouldBe "test.txt"
                assert( File( s"${tempDirectory}/test.txt" ).exists )
            case Failure( e ) => fail( e )
        }
    }

    "LocalProcurementRepositoryTest" should "not save the file and return failure" in {

        val specFileName = "test.txt"
        val specFileContent = Resource.getAsString( "fixtures/test.txt" ).getBytes

        val localProcurementRepositry = new LocalProcurementRepository( "src/test/resources/DOESNOTEXIST" )

        localProcurementRepositry.doSave( specFileName, specFileContent ) match {
            case Success( value ) => fail( value )
            case Failure( e ) => e shouldBe a[ UnableToSaveDocumentException ]
        }
    }

}
