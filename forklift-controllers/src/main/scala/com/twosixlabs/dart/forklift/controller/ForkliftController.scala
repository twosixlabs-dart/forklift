package com.twosixlabs.dart.forklift.controller

import better.files.File
import com.twosixlabs.dart.auth.controllers.SecureDartController
import com.twosixlabs.dart.auth.permissions.DartOperations.{AddDocument, TenantOperation}
import com.twosixlabs.dart.auth.tenant.{CorpusTenant, CorpusTenantIndex, DartTenant, GlobalCorpus}
import com.twosixlabs.dart.auth.user.DartUser
import com.twosixlabs.dart.exceptions.ExceptionImplicits.TryExceptionLogging
import com.twosixlabs.dart.exceptions.{AuthorizationException, BadRequestBodyException, GenericServerException}
import com.twosixlabs.dart.forklift.api.models.{ArchiveSuccessResponse, DocumentMetadata, ResponseErrorCode, SuccessResponse}
import com.twosixlabs.dart.forklift.exceptions.{UnableToSaveDocumentException, UnableToSaveMetadataException}
import com.twosixlabs.dart.forklift.helpers.Mapper
import com.twosixlabs.dart.operations.status.PipelineStatus
import com.twosixlabs.dart.operations.status.PipelineStatus.{ProcessorType, Status}
import com.twosixlabs.dart.operations.status.client.PipelineStatusUpdateClient
import com.twosixlabs.dart.rest.scalatra.DartScalatraServlet
import com.twosixlabs.dart.utils.IdGenerator
import org.hungerford.rbac.exceptions.{AuthorizationException => RbacAuthorizationException}
import org.hungerford.rbac.{Permissible, PermissibleSet, PermissionSource}
import org.scalatra.servlet.FileUploadSupport
import org.scalatra.{CorsSupport, Created}
import org.slf4j.{Logger, LoggerFactory}

import java.util.zip.ZipInputStream
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}



trait ForkliftControllerDependencies extends SecureDartController.Dependencies {
    val docHandler : ProcurementHandler
    val opsUpdateClient : PipelineStatusUpdateClient
    val tenantIndex : CorpusTenantIndex
    override val serviceName : String = "forklift"
}

class ForkliftController( dependencies : ForkliftControllerDependencies ) extends DartScalatraServlet with SecureDartController with FileUploadSupport with CorsSupport {

    implicit class DartOperationSet( permissibleSet : PermissibleSet ) {
        def secureDart[ T ]( block : => T )( implicit ps : PermissionSource ) : T = Try( permissibleSet.secure( block ) ) match {
            case Success( res ) => res
            case Failure( e : RbacAuthorizationException ) => throw new AuthorizationException( e.getMessage )
            case Failure( e ) => throw e
        }
    }

    override val secretKey : Option[ String ] = dependencies.secretKey
    override val useDartAuth : Boolean = dependencies.useDartAuth
    override val serviceName : String = dependencies.serviceName
    override val basicAuthCredentials: Seq[ (String, String) ] = dependencies.basicAuthCredentials

    private val docHandler = dependencies.docHandler
    private val opsUpdateClient = dependencies.opsUpdateClient
    private val tenantIndex = dependencies.tenantIndex

    override val LOG : Logger = LoggerFactory.getLogger( getClass )
    implicit val ec : ExecutionContext = scala.concurrent.ExecutionContext.global

    setStandardConfig()

    post( "/zip" )( handleOutput {
        AuthenticateRoute.withUser { implicit user : DartUser =>
            val metadata = params.get( "metadata" ) match {
                case Some( metadata ) =>
                    Mapper.unmarshal( metadata, classOf[ DocumentMetadata ] ).loggedDebug match {
                        case Success( docMetadata ) => Some( docMetadata )
                        case Failure( _ ) => throw new BadRequestBodyException( ResponseErrorCode.MalformedMetadata.toString )
                    }
                case None => None
            }

            val tenants : Set[ String ] = metadata.flatMap( _.tenants ).getOrElse( Set() )
            val dartTenants : Set[ DartTenant ] = tenants.map( DartTenant.fromString )
            val corpusTenants : Set[ CorpusTenant ] = dartTenants collect {
                case ct@CorpusTenant( _, _ ) => ct
            }

            val tenantsOperations : Set[ TenantOperation ] = dartTenants.map(tenant => TenantOperation(tenant, AddDocument))

            Permissible.all( tenantsOperations ).secureDart {
                contentType = "application/json;charset=utf-8"
                val file = fileParams.get( "file" ) match {
                    case Some( file ) =>
                        File( file.name ).extension match {
                            case Some( ".zip" ) => file
                            case Some( _ ) => throw new BadRequestBodyException( ResponseErrorCode.NotZipFile.toString )
                            case None => throw new BadRequestBodyException( ResponseErrorCode.NotZipFile.toString )
                        }
                    case None => throw new BadRequestBodyException( ResponseErrorCode.MissingFile.toString )
                }

                val zis = new ZipInputStream( file.getInputStream )

                val documents = Stream.continually( zis.getNextEntry ).takeWhile( _ != null )
                  .withFilter( file => {
                      Try {
                          !file.getName.startsWith( "__MACOSX" ) && !file.isDirectory && !File( file.getName ).name.startsWith( "." )
                      }.loggedWarning match {
                          case Failure( _ ) => false
                          case Success( value ) => value
                      }
                  } )
                  .map( file => {
                      try {
                          val fileName = File( file.getName ).name
                          val fileData = Stream.continually( zis.read() ).takeWhile( _ != -1 ).map( _.toByte ).toArray
                          val docId = IdGenerator.getMd5Hash( fileData )
                          Some( (docId, fileName, fileData) )
                      } catch {
                          case e : Exception =>
                              LOG.error( s"Unable to extract: ${file.getName} with Error: ${e.getLocalizedMessage}" )
                              None
                      }
                  } )

                val numFailedDocuments = documents.count( _.isEmpty )
                val flattenDocuments = documents.flatten

                doBatchRawDocPersistAsync( flattenDocuments, metadata, corpusTenants )

                if ( flattenDocuments.isEmpty ) throw new BadRequestBodyException( ResponseErrorCode.UnableToExtractFromZip.toString )

                Created( ArchiveSuccessResponse( flattenDocuments.map( doc => SuccessResponse( doc._1, doc._2 ) ), numFailedDocuments ) )
            }
        }
    } )

    post( "/" )( handleOutput {
        AuthenticateRoute.withUser { implicit user : DartUser =>
            val metadata : Option[ DocumentMetadata ] = params.get( "metadata" ) match {
                case Some( metadata ) =>
                    Mapper.unmarshal( metadata, classOf[ DocumentMetadata ] ).loggedDebug match {
                        case Success( docMetadata ) => Some( docMetadata )
                        case Failure( _ ) => throw new BadRequestBodyException( ResponseErrorCode.MalformedMetadata.toString )
                    }
                case None => None
            }

            val tenants : Set[ String ] = metadata.flatMap( _.tenants ).getOrElse( Set() )
            val dartTenants : Set[ DartTenant ] = tenants.map( DartTenant.fromString )
            val corpusTenants : Set[ CorpusTenant ] = dartTenants collect {
                case ct@CorpusTenant( _, _ ) => ct
            }

            val tenantsOperations : Set[ TenantOperation ] = dartTenants.map(tenant => TenantOperation(tenant, AddDocument))
            Permissible.all( tenantsOperations ).secureDart {
                LOG.info( user.toString )
                contentType = "application/json;charset=utf-8"
                val file = fileParams.get( "file" ) match {
                    case Some( file ) => file
                    case None => throw new BadRequestBodyException( ResponseErrorCode.MissingFile.toString )
                }

                val docId = IdGenerator.getMd5Hash( file.get )

                docHandler.doSave( docId, file.getName, file.get, metadata ) match {
                    case Success( _ ) => LOG.info( "File and Metadata Successfully saved: {}", docId )
                        tenantIndex.addDocumentToTenants( docId, corpusTenants.map(_.id) )
                        updateStatus( docId, Status.SUCCESS, "" )
                        Created( SuccessResponse( docId, file.getName ) )
                    case Failure( e : UnableToSaveDocumentException ) =>
                        LOG.error( s"Server Error when saving document with error: ${e.getMessage}" )
                        updateStatus( docId, Status.FAILURE, ResponseErrorCode.UnableToSaveDocument.toString )
                        throw new GenericServerException( ResponseErrorCode.UnableToSaveDocument.toString, e )
                    case Failure( e : UnableToSaveMetadataException ) =>
                        LOG.error( s"Server Error when saving metadata with error: ${e.getMessage}" )
                        updateStatus( docId, Status.FAILURE, ResponseErrorCode.UnableToSaveMetaData.toString )
                        throw new GenericServerException( ResponseErrorCode.UnableToSaveMetaData.toString, e )
                    case Failure( e : Throwable ) =>
                        LOG.error( s"Server Error when saving document and metadata ${e.getMessage}" )
                        updateStatus( docId, Status.FAILURE, ResponseErrorCode.ServerError.toString )
                        throw new GenericServerException( ResponseErrorCode.ServerError.toString, e )
                }
            }
        }
    } )

    private def doBatchRawDocPersistAsync(
        documents : Seq[ (String, String, Array[ Byte ]) ],
        metadata : Option[ DocumentMetadata ],
        tenant : Set[ CorpusTenant ]
    ) : Unit = {

        documents.foreach( document => {
            Future {
                docHandler.doSave( document._1, document._2, document._3, metadata ) match {
                    case Success( _ ) => LOG.info( "File and Metadata Successfully saved: {}", document._1 )
                        tenantIndex.addDocumentToTenants( document._1, tenant.map(_.id))
                        updateStatus( document._1, Status.SUCCESS, "" )
                    case Failure( e : UnableToSaveDocumentException ) =>
                        LOG.error( s"Server Error when saving document with error: ${e.getMessage}" )
                        updateStatus( document._1, Status.FAILURE, ResponseErrorCode.UnableToSaveDocument.toString )
                        throw new GenericServerException( ResponseErrorCode.UnableToSaveDocument.toString, e )
                    case Failure( e : UnableToSaveMetadataException ) =>
                        LOG.error( s"Server Error when saving metadata with error: ${e.getMessage}" )
                        updateStatus( document._1, Status.FAILURE, ResponseErrorCode.UnableToSaveMetaData.toString )
                        throw new GenericServerException( ResponseErrorCode.UnableToSaveMetaData.toString, e )
                    case Failure( e : Throwable ) =>
                        LOG.error( s"Server Error when saving document and metadata ${e.getMessage}" )
                        updateStatus( document._1, Status.FAILURE, ResponseErrorCode.ServerError.toString )
                        throw new GenericServerException( ResponseErrorCode.ServerError.toString, e )
                }
            }
        } )
    }

    private def updateStatus( docId : String, status : Status, message : String ) = {
        opsUpdateClient
          .fireAndForget( new PipelineStatus( docId, "forklift", ProcessorType.CORE, status, "DART", System.currentTimeMillis(), System.currentTimeMillis(), message ) )
    }
}
