package com.twosixlabs.dart.forklift.api

import com.twosixlabs.dart.rest.ApiStandards
import com.twosixlabs.dart.rest.scalatra.models.FailureResponse
import sttp.model.{Method, StatusCode}
import sttp.tapir._
import sttp.tapir.docs.openapi.{OpenAPIDocsInterpreter, OpenAPIDocsOptions}
import sttp.tapir.generic.auto._
import io.circe.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.openapi.circe.yaml.RichOpenAPI

import scala.collection.mutable.ListBuffer

trait DartServiceApiDefinition {

    val serviceName : String

    val servicePathName : Option[ String ]

    private val endpointList : ListBuffer[ Endpoint[ _, _, _, _ ] ] = ListBuffer[ Endpoint[ _, _, _, _ ] ]()

    def notFoundErr( desc : String ) : EndpointOutput.StatusMapping[FailureResponse ] = statusMapping( StatusCode.NotFound, jsonBody[ FailureResponse ].description( desc ) )
    def badRequestErr( desc : String ) : EndpointOutput.StatusMapping[FailureResponse ] = statusMapping( StatusCode.BadRequest, jsonBody[ FailureResponse ].description( desc ) )
    def serviceUnavailableErr( desc: String ) : EndpointOutput.StatusMapping[FailureResponse ] = statusMapping( StatusCode.ServiceUnavailable, jsonBody[ FailureResponse ].description( desc ) )
    def authenticationFailure( desc: String ) : EndpointOutput.StatusMapping[FailureResponse ] = statusMapping( StatusCode.Unauthorized, jsonBody[ FailureResponse ].description( desc ) )
    def authorizationFailure( desc: String ) : EndpointOutput.StatusMapping[FailureResponse ] = statusMapping( StatusCode.Forbidden, jsonBody[ FailureResponse ].description( desc ) )

    lazy val basePath : String = ApiStandards.DART_API_PREFIX_V1 + "/" + servicePathName.getOrElse( serviceName.toLowerCase )

    private lazy val basePathSections = basePath.stripPrefix( "/" ).split( '/' ).map( _.trim )

    private def AddToDart[ I, E, O, R ]( endpt : Endpoint[ I, E, O, R ], mappedResponses : EndpointOutput.StatusMapping[ _ ]* ) : Endpoint[ (String, I), (Any, E), O, R ] = {
        val dartEndPt = endpt
          .tag( serviceName )
          .prependErrorOut(
              oneOf(
                  authenticationFailure( "Authentication token missing or invalid" ),
                  authorizationFailure( "User not authorized for this operation" ) +: mappedResponses : _*,
              )
           )
          .prependIn( auth.bearer[ String ]() )
          .prependIn( basePathSections.tail.foldLeft( basePathSections.head : EndpointInput[ Unit ] )( _ / _ ) / servicePathName.getOrElse( serviceName.toLowerCase ) )

        endpointList += dartEndPt
        dartEndPt
    }

    implicit class RegisterableEndpoint[ I, E, O, -R ]( endpt : Endpoint[ I, E, O, R ] ) {
        def addToDart( mappedResponses : EndpointOutput.StatusMapping[ _ ]* ) : Endpoint[ (String, I), (Any, E), O, R ] = {
            AddToDart( endpt, mappedResponses : _* )
        }
    }

    def allEndpoints : List[ Endpoint[ _, _, _, _ ] ] = endpointList.toList

    implicit val openApiOps : OpenAPIDocsOptions = OpenAPIDocsOptions(
        ( ids : Vector[ String ], method : Method ) => method.method.toLowerCase + ids.drop( basePathSections.length ).map( s => {
            val charArray = s.toLowerCase.toCharArray
            charArray( 0 ) = Character.toUpperCase( charArray( 0 ) )
            new String( charArray )
        } ).mkString
    )

    def openApiSpec : String = {
        OpenAPIDocsInterpreter
          .toOpenAPI( allEndpoints, serviceName, "1.0" )
          .toYaml
    }

}
