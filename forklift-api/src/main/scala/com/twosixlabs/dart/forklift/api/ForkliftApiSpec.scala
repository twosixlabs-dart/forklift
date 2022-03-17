package com.twosixlabs.dart.forklift.api

import com.twosixlabs.dart.forklift.api.models.{ArchiveSuccessResponse, FileMetadata}
import com.twosixlabs.dart.rest.scalatra.models.FailureResponse
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.generic.auto._
import io.circe.generic.auto._
import sttp.model.Part

import java.io.File

case class ForkliftMultipartInput(
    @encodedName( "metadata" ) metadata : FileMetadata,
    @encodedName( "file" ) @description( "Document for submission" ) file : Part[ File ],
)

case class ForkliftZipMultipartInput(
    @encodedName( "metadata" ) metadata : FileMetadata,
    @encodedName( "file" ) @description( "Zip archive of documents for submission" ) file : Part[ File ],
)

object ForkliftApiSpec extends DartServiceApiDefinition {

    override val serviceName : String = "Forklift"

    override val servicePathName : Option[ String ] = Some( "forklift" )

    val singleUpload : Endpoint[ (String, ForkliftMultipartInput), (Any, Unit), Unit, Any ] = endpoint
      .description( "Upload a single document" )
      .post
      .in( "upload" )
      .in( multipartBody[ ForkliftMultipartInput ] )
      .out( statusCode( StatusCode.Created ).description( "Successfully uploaded document" ) )
      .addToDart( badRequestErr( "Invalid document metadata" ), serviceUnavailableErr( "Unable to reach document persistence datastore" ) )

    val zipUpload : Endpoint[ (String, ForkliftZipMultipartInput), (Any, Unit), Unit, Any ] = endpoint
      .description( "Upload a zip archive of documents" )
      .post
      .in( "upload" / "zip" )
      .in( multipartBody[ ForkliftZipMultipartInput ] )
      .out( statusCode( StatusCode.Created ) )
      .addToDart(
          statusMapping( StatusCode.Created, jsonBody[ ArchiveSuccessResponse ].description( "Successfully uploaded zip archive" ) ),
          badRequestErr( "Invalid document metadata or file was not a valid zip archive" ),
          serviceUnavailableErr( "Unable to reach document persistence datastore" )
       )

}
