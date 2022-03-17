package com.twosixlabs.dart.forklift.procurement

import com.twosixlabs.dart.forklift.api.models.{DocumentMetadata, FileMetadata}
import com.twosixlabs.dart.forklift.exceptions.{UnableToSaveDocumentException, UnableToSaveMetadataException}
import com.twosixlabs.dart.forklift.helpers.Mapper
import org.slf4j.{Logger, LoggerFactory}

import scala.util.{Failure, Success, Try}

class ProcurementHandler( procurementRepository : ProcurementRepository ) {

    private val LOG : Logger = LoggerFactory.getLogger( getClass )

    @throws( classOf[ UnableToSaveDocumentException ] )
    @throws( classOf[ UnableToSaveMetadataException ] )
    def doSave( documentId : String, fileName : String, fileContent : Array[ Byte ], metadata : Option[ DocumentMetadata ] ) : Try[ String ] = {

        val documentFilename = s"${documentId}.${extractFileExtension( fileName )}.raw"
        val metaDataFilename = s"${documentId}.meta"

        val fileMetadata = metadata match {
            case Some( documentMetadata ) => FileMetadata( documentMetadata, fileName )
            case None => FileMetadata(fileName)
        }
        Mapper.marshal( fileMetadata ) match {
            case Success( fileMetadataContent ) =>
                procurementRepository.doSave( documentFilename, fileContent ) match {
                    case Success( _ ) =>
                        procurementRepository.doSave( metaDataFilename, fileMetadataContent.getBytes ) match {
                            case Success( value ) =>
                                LOG.info( "File and Metadata Successfully saved: {}", metaDataFilename )
                                Success( value )
                            case Failure( e ) =>
                                logError( e )
                                Failure( new UnableToSaveMetadataException( s"Failed to save: ${metaDataFilename}" ) )
                        }
                    case Failure( e ) =>
                        logError( e )
                        Failure( new UnableToSaveDocumentException( s"Failed to save: ${documentFilename}" ) )
                }
            case Failure( e ) =>
                logError( e )
                Failure( e )
        }
    }

    private def logError( e : Throwable ) = {
        LOG.error(
            s"""${e.getClass}: ${e.getMessage}
               |${e.getCause}
               |${
                e.getStackTrace.mkString( "\n" )
            }""".stripMargin )
    }

    private def extractFileExtension( fileName : String ) : String = {
        fileName.split( "\\." ).last
    }
}