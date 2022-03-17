package com.twosixlabs.dart.forklift.procurement.repositories

import better.files.File
import com.twosixlabs.dart.forklift.exceptions.UnableToSaveDocumentException
import com.twosixlabs.dart.forklift.procurement.ProcurementRepository
import com.twosixlabs.dart.utils.RetryHelper
import org.slf4j.{Logger, LoggerFactory}

import scala.util.{Failure, Success, Try}

class LocalProcurementRepository( persistenceDirPath : String ) extends ProcurementRepository {

    private val LOG : Logger = LoggerFactory.getLogger( getClass )
    private final val NUMBER_OF_RETRIES = 3

    override def doSave( fileName : String, fileContent : Array[ Byte ] ) : Try[ String ] = {

        Try {
            File( s"${persistenceDirPath}/${fileName}" )
        } match {
            case Success( value ) =>
                RetryHelper.retry( NUMBER_OF_RETRIES )( saveFile( value, fileContent ) ) match {
                    case Success( _ ) => Success( fileName )
                    case Failure( e ) =>
                        logError( e )
                        Failure( new UnableToSaveDocumentException( s"Unable To Save ${fileName} to local disk" ) )
                }
            case Failure( e ) =>
                logError( e )
                Failure( new UnableToSaveDocumentException( s"Unable to create ${fileName} on local disk" ) )
        }
    }

    private def saveFile( file : File, fileContent : Array[ Byte ] ) : Try[ File ] = {
        Try {
            file.writeByteArray( fileContent )
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
}
